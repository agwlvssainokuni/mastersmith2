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

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 1要求の判定の材料（契約 C1 の accessFor の結果、entities.md の TableAccessView）。権限の写し1つとその写しの DSL から、
 * {@link TableAccessPolicy} だけが作る。
 *
 * <p>文字列にするときはテーブルの名前と数だけを出す（表示名・設定を出さない）。
 *
 * @param schema 写しの DSL にあるスキーマ名
 * @param table 写しの DSL にあるテーブル名
 * @param displayName テーブルの表示名（DSL のまま）
 * @param definitionId 今の定義の識別
 * @param view ビューなら true
 * @param primaryKey DSL の主キーのカラム名（無ければ空。NONE のカラムを含みうる）
 * @param tableMain テーブルの実効の主権限
 * @param canCreate 補助権限 CREATE
 * @param canDelete 補助権限 DELETE
 * @param columns カラム（NONE のカラムは含めない。DSL の順）
 * @param operations 許す操作
 * @param restrictionReason 行えない操作の理由（無ければ null）
 * @param writeConfigured 書き込みの接続の設定があるか
 * @param rowLabelColumns 行を見分ける値のカラム（BR2.6）
 * @param knownIdentifiers SQL の部品に渡す名前の一覧（BR3.3）
 * @param writeRules カラムの名前ごとの書き込みの検証の材料（NONE のカラムは含めない。U2 の BR3・BR4・BR5・BR14.2）
 * @param foreignKeys 違反の後の確かめに使う外部キー（参照先の名前が分かるものだけ。U2 の BR6.2）
 */
public record TableAccessView(
        String schema,
        String table,
        DisplayName displayName,
        DefinitionId definitionId,
        boolean view,
        List<String> primaryKey,
        MainPermission tableMain,
        boolean canCreate,
        boolean canDelete,
        List<ColumnAccess> columns,
        Set<AllowedOperation> operations,
        RestrictionReason restrictionReason,
        boolean writeConfigured,
        List<String> rowLabelColumns,
        KnownIdentifiers knownIdentifiers,
        Map<String, ColumnWriteRules> writeRules,
        List<ForeignKeyRef> foreignKeys) {

    /** 必須の値を確かめ、一覧と集合を変更できないものにする。 */
    public TableAccessView {
        Objects.requireNonNull(schema, "schema は必須です");
        Objects.requireNonNull(table, "table は必須です");
        Objects.requireNonNull(displayName, "displayName は必須です");
        Objects.requireNonNull(definitionId, "definitionId は必須です");
        Objects.requireNonNull(tableMain, "tableMain は必須です");
        Objects.requireNonNull(knownIdentifiers, "knownIdentifiers は必須です");
        primaryKey = List.copyOf(primaryKey);
        columns = List.copyOf(columns);
        operations = operations.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(AllowedOperation.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(operations));
        rowLabelColumns = List.copyOf(rowLabelColumns);
        writeRules = Collections.unmodifiableMap(new LinkedHashMap<>(writeRules));
        foreignKeys = List.copyOf(foreignKeys);
    }

    /**
     * 名前でカラムの書き込みの検証の材料を引く（NONE のカラムと DSL に無いカラムは無い）。
     *
     * @param name カラムの名前
     * @return 検証の材料（無ければ空）
     */
    public Optional<ColumnWriteRules> writeRules(String name) {
        return Optional.ofNullable(writeRules.get(name));
    }

    /**
     * 操作が許されるかを返す。
     *
     * @param operation 操作
     * @return 許されるなら true
     */
    public boolean allows(AllowedOperation operation) {
        return operations.contains(operation);
    }

    /**
     * 名前でカラムを引く（NONE のカラムと DSL に無いカラムは無い）。
     *
     * @param name カラムの名前
     * @return カラム（無ければ空）
     */
    public Optional<ColumnAccess> column(String name) {
        return columns.stream().filter(c -> c.column().equals(name)).findFirst();
    }

    /**
     * 一覧の列（{@code list.visible} のカラムを {@code list.order} の順。同じ順は DSL の順）を返す（BR2.2）。
     *
     * @return 一覧の列
     */
    public List<ColumnAccess> listColumns() {
        return columns.stream()
                .filter(ColumnAccess::listVisible)
                .sorted(Comparator.comparing(ColumnAccess::listOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * 詳細の列（{@code detail.visible} のカラムを DSL の順）を返す（BR2.2）。
     *
     * @return 詳細の列
     */
    public List<ColumnAccess> detailColumns() {
        return columns.stream().filter(ColumnAccess::detailVisible).toList();
    }

    /**
     * 検索の項目に出すカラム（DSL の順）を返す（BR4.10）。
     *
     * @return 検索の項目
     */
    public List<ColumnAccess> searchColumns() {
        return columns.stream().filter(c -> c.search() != null).toList();
    }

    /**
     * 行を指す主キーのカラムを返す（DETAIL が許されるときだけ。DSL の主キーの順）。
     *
     * @return 主キーのカラム（DETAIL が許されなければ空）
     */
    public List<ColumnAccess> keyColumns() {
        if (!allows(AllowedOperation.DETAIL)) {
            return List.of();
        }
        return primaryKey.stream().map(name -> column(name).orElseThrow()).toList();
    }

    /**
     * 既定の並び（{@code defaultSort} のカラムを {@code list.order} の順）を返す（BR5.3）。
     *
     * @return 既定の並びのキー
     */
    public List<SortKey> defaultSortKeys() {
        return columns.stream()
                .filter(c -> c.defaultSort() != null)
                .sorted(Comparator.comparing(ColumnAccess::listOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(c -> new SortKey(c.column(), c.defaultSort()))
                .toList();
    }

    /**
     * 一覧の並びのキーを返す（BR5.3・BR5.4）。要求の並べ替え → 既定の並び → 主キーの順で決め、主キーがあれば、まだキーに無い
     * 主キーのカラムを昇順で足す。主キーの無いテーブルでは順を約束しない（空になりうる）。
     *
     * @param requested 要求の並べ替え（確かめた後）
     * @return 並びのキー
     */
    public List<SortKey> orderKeys(Optional<SortKey> requested) {
        List<SortKey> keys = new ArrayList<>(requested.map(List::of).orElseGet(this::defaultSortKeys));
        for (String key : primaryKey) {
            if (keys.stream().noneMatch(k -> k.column().equals(key))) {
                keys.add(new SortKey(key, SortDirection.ASC));
            }
        }
        return List.copyOf(keys);
    }

    /** テーブルの名前・カラムの数・許す操作だけを出す（表示名と設定を出さない）。 */
    @Override
    public String toString() {
        return "TableAccessView[schema=" + schema + ", table=" + table + ", columns=" + columns.size() + ", operations="
                + operations + "]";
    }
}
