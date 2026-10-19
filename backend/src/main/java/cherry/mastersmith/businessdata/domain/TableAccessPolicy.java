/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.businessdata.domain;

import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslForeignKey;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 出し分けと許す操作の純粋な関数（ADR-005、BR2.1〜BR2.7、BR5.2・BR7.9）。許す操作はこの関数だけで求め、ほかの場所で組み立てない。
 *
 * <p>権限を足しても、出るカラムと許す操作は減らない（BR2.5。性質ベースのテストで確かめる）。
 */
public final class TableAccessPolicy {

    /** 型の分類の外の型・BR7.9 の型を完全一致に使えないものとして扱う、型が分からないカラムの既定。 */
    private static final ColumnKind UNKNOWN_KIND = new ColumnKind(ValueType.OTHER, false);

    private TableAccessPolicy() {}

    /**
     * 判定の材料を作る。
     *
     * @param input 入力
     * @return 判定の材料
     */
    public static TableAccessView evaluate(TableAccessInput input) {
        DslTable table = input.table();
        boolean readable = input.tableMain() != MainPermission.NONE;
        Set<String> primaryKey = Set.copyOf(table.primaryKey());
        List<ColumnAccess> columns = new ArrayList<>();
        Map<String, ColumnWriteRules> writeRules = new LinkedHashMap<>();
        for (DslColumn column : table.columns().values()) {
            MainPermission main = readable
                    ? input.columnMain().getOrDefault(column.name(), MainPermission.NONE)
                    : MainPermission.NONE;
            if (main == MainPermission.NONE) {
                continue;
            }
            ColumnKind kind = kind(input, column.name());
            ColumnWriteRules rules = ColumnWriteRulesFactory.create(column, kind);
            writeRules.put(column.name(), rules);
            columns.add(columnAccess(column, main, kind, primaryKey.contains(column.name()), rules));
        }
        Set<AllowedOperation> operations = operations(input, columns, readable);
        return new TableAccessView(
                input.schema(),
                table.name(),
                table.label(),
                input.definitionId(),
                table.view(),
                table.primaryKey(),
                input.tableMain(),
                input.canCreate(),
                input.canDelete(),
                columns,
                operations,
                restrictionReason(input),
                input.writeConfigured(),
                rowLabelColumns(operations, columns, table.primaryKey()),
                input.knownIdentifiers(),
                writeRules,
                foreignKeys(input));
    }

    /** 違反の後の確かめに使う外部キー（参照先の DSL のカラムの名前が分かるものだけ。U2 の BR6.2）。 */
    private static List<ForeignKeyRef> foreignKeys(TableAccessInput input) {
        List<ForeignKeyRef> keys = new ArrayList<>();
        for (DslForeignKey key : input.table().foreignKeys()) {
            Set<String> referenced = input.referencedColumns().get(key.referencedTable());
            if (referenced == null
                    || !input.knownIdentifiers().tables().contains(key.referencedTable())
                    || !referenced.containsAll(key.referencedColumns())
                    || key.columns().size() != key.referencedColumns().size()) {
                continue;
            }
            keys.add(new ForeignKeyRef(key.columns(), key.referencedTable(), key.referencedColumns(), referenced));
        }
        return keys;
    }

    private static ColumnKind kind(TableAccessInput input, String column) {
        return input.kinds().getOrDefault(column, UNKNOWN_KIND);
    }

    private static ColumnAccess columnAccess(
            DslColumn column, MainPermission main, ColumnKind kind, boolean key, ColumnWriteRules rules) {
        boolean masked = column.formPart() == FormPart.PASSWORD;
        ValueType type = kind.valueType();
        boolean binary = type == ValueType.BINARY;
        // password・BINARY・OTHER は並べ替えに使わない（BR5.2）。既定の並びにも同じ決まりを当てる。
        boolean orderable = !masked && !binary && type != ValueType.OTHER;
        boolean editableOnCreate =
                column.detail().visible() && main == MainPermission.FULL && !binary && type != ValueType.OTHER;
        return new ColumnAccess(
                column.name(),
                column.label(),
                main,
                type,
                column.dbType().name(),
                key,
                column.list().visible(),
                column.list().order(),
                column.list().width(),
                column.list().format(),
                column.detail().visible(),
                SearchOperatorPolicy.rule(column.search(), column.options(), kind, masked)
                        .orElse(null),
                column.search().defaultValue(),
                column.search().collapsed(),
                orderable && column.list().sortable(),
                orderable ? column.list().defaultSort() : null,
                editableOnCreate,
                editableOnCreate && !key,
                masked,
                // 検証の材料はサーバーの検証と同じ材料から写す（U2 の BR14.1・BR14.2）。
                ColumnValidation.from(rules, editableOnCreate));
    }

    /** 許す操作（BR2.3、FR9.1 の表、BR7.9）。 */
    private static Set<AllowedOperation> operations(
            TableAccessInput input, List<ColumnAccess> columns, boolean readable) {
        Set<AllowedOperation> operations = EnumSet.noneOf(AllowedOperation.class);
        if (!readable) {
            return operations;
        }
        operations.add(AllowedOperation.LIST);
        DslTable table = input.table();
        boolean detail = !table.primaryKey().isEmpty()
                && table.primaryKey().stream().allMatch(name -> keyUsable(input, columns, name));
        boolean writable = !table.view() && input.writeConfigured();
        if (detail) {
            operations.add(AllowedOperation.DETAIL);
        }
        if (writable && input.canCreate()) {
            operations.add(AllowedOperation.CREATE);
        }
        if (detail && writable && columns.stream().anyMatch(ColumnAccess::editableOnUpdate)) {
            operations.add(AllowedOperation.UPDATE);
        }
        if (detail && writable && input.canDelete()) {
            operations.add(AllowedOperation.DELETE);
        }
        return operations;
    }

    /** 主キーのカラムが READ 以上で、行を指すのに使える型か（BINARY と BR7.9 の型は使えない）。 */
    private static boolean keyUsable(TableAccessInput input, List<ColumnAccess> columns, String name) {
        if (columns.stream().noneMatch(c -> c.column().equals(name))) {
            return false;
        }
        ColumnKind kind = kind(input, name);
        return kind.valueType() != ValueType.BINARY && (kind.valueType() != ValueType.OTHER || kind.equalsSupported());
    }

    /**
     * 行えない操作の理由（BR2.7）。ビュー → VIEW、主キーが無い → NO_PRIMARY_KEY、主キーのカラムのどれかが NONE →
     * PRIMARY_KEY_HIDDEN、それ以外は null。主キーの型（BINARY・BR7.9 の型）で DETAIL を許さないときの理由の値は設計で決めていない
     * ため、null のままにする（generation-notes.md に記録した）。
     */
    private static RestrictionReason restrictionReason(TableAccessInput input) {
        DslTable table = input.table();
        if (table.view()) {
            return RestrictionReason.VIEW;
        }
        if (table.primaryKey().isEmpty()) {
            return RestrictionReason.NO_PRIMARY_KEY;
        }
        boolean hidden = input.tableMain() == MainPermission.NONE
                || table.primaryKey().stream()
                        .anyMatch(name ->
                                input.columnMain().getOrDefault(name, MainPermission.NONE) == MainPermission.NONE);
        return hidden ? RestrictionReason.PRIMARY_KEY_HIDDEN : null;
    }

    /**
     * 行を見分ける値のカラム（BR2.6）。DETAIL が許されなければ空。主キーのカラムがすべて一覧の列にあれば主キーのカラム、無ければ
     * 一覧の列のうち最初のカラム（password・BINARY を除く）。
     */
    private static List<String> rowLabelColumns(
            Set<AllowedOperation> operations, List<ColumnAccess> columns, List<String> primaryKey) {
        if (!operations.contains(AllowedOperation.DETAIL)) {
            return List.of();
        }
        List<ColumnAccess> listed = columns.stream()
                .filter(ColumnAccess::listVisible)
                .sorted(Comparator.comparing(ColumnAccess::listOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Set<String> listedNames = listed.stream().map(ColumnAccess::column).collect(Collectors.toSet());
        if (listedNames.containsAll(primaryKey)) {
            return primaryKey;
        }
        return listed.stream()
                .filter(ColumnAccess::readsValue)
                .findFirst()
                .map(c -> List.of(c.column()))
                .orElse(List.of());
    }
}
