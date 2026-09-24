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
package cherry.mastersmith.dslmanage.generate;

import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.targetdb.domain.TargetColumn;
import cherry.mastersmith.targetdb.domain.TargetDbType;
import cherry.mastersmith.targetdb.domain.TargetForeignKey;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetTable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * スキーマの写しから DSL の値の木を組み立てる（BR1.2〜BR1.5・BR2.1〜BR2.5・BR3.1・BR3.2・BR4.1、functional-spec.md の 1節）。
 *
 * <p>木は順序を持つ対応表（{@link LinkedHashMap}）と一覧で作り、項目の並びを U2 の書式の例の順に固定する（BR5.1）。写しの外の
 * 値（接続先・ユーザー名・パスワード・スキーマ名）は入れない（BR5.3）。
 *
 * <p>外部キーは、参照先のテーブルとカラムが写しにあるものだけを写す（読める権限が無いなどで参照先が写しに無い外部キーは、DSL
 * の意味の検証を通らないため入れない）。{@code longtext} のように長さが DSL の整数の範囲（2147483647 まで）を超える型は、長さを
 * 無し（null）として書き、長さの {@code maxLength} を作らない。
 */
@Component
public class DslTreeBuilder {

    /** 表示するカラムの並び順の始まり（BR2.5）。 */
    private static final int FIRST_ORDER = 1;

    /** 名前を大文字・小文字を区別せずに Unicode の符号の順で比べ、同じなら元の名前の符号の順で比べる（BR1.3・BR1.4）。 */
    static final Comparator<String> NAME_ORDER = DslTreeBuilder::compareNames;

    /**
     * 写しから DSL の値の木を作る。
     *
     * @param schema スキーマの写し
     * @return DSL の値の木（{@code version}・{@code menus}・{@code tables} の順）
     */
    public Map<String, Object> build(TargetSchema schema) {
        List<TargetTable> tables = schema.tables().stream()
                .sorted(Comparator.comparing(TargetTable::name, NAME_ORDER))
                .toList();
        List<Object> menus = new ArrayList<>();
        Map<String, Object> tableMap = new LinkedHashMap<>();
        for (TargetTable table : tables) {
            Map<String, Object> menu = new LinkedHashMap<>();
            menu.put("label", label(table.name(), table.comment()));
            menu.put("table", table.name());
            menus.add(menu);
            tableMap.put(table.name(), table(schema, table));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", DslFormat.CURRENT_VERSION);
        root.put("menus", menus);
        root.put("tables", tableMap);
        return root;
    }

    private static Map<String, Object> table(TargetSchema schema, TargetTable table) {
        List<TargetForeignKey> foreignKeys =
                table.view() ? List.of() : resolvableForeignKeys(schema, table.foreignKeys());
        List<String> primaryKey = table.view() ? List.of() : table.primaryKey();
        Map<String, TargetForeignKey> singleColumnKeys = new HashMap<>();
        for (TargetForeignKey foreignKey : foreignKeys) {
            if (foreignKey.columns().size() == 1) {
                singleColumnKeys.putIfAbsent(foreignKey.columns().getFirst(), foreignKey);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("label", label(table.name(), table.comment()));
        result.put("view", table.view());
        result.put("primaryKey", new ArrayList<>(primaryKey));
        result.put(
                "foreignKeys",
                foreignKeys.stream().map(DslTreeBuilder::foreignKey).toList());
        Map<String, Object> columns = new LinkedHashMap<>();
        Set<String> primaryKeyColumns = new HashSet<>(primaryKey);
        String sortColumn = primaryKey.isEmpty() ? null : primaryKey.getFirst();
        int order = FIRST_ORDER;
        for (TargetColumn column : table.columns()) {
            ColumnPlan plan = ColumnPlan.of(
                    column, primaryKeyColumns.contains(column.name()), singleColumnKeys.get(column.name()));
            Integer listOrder = plan.listed() ? order++ : null;
            columns.put(
                    column.name(), column(column, plan, listOrder, column.name().equals(sortColumn)));
        }
        result.put("columns", columns);
        return result;
    }

    /** 参照先のテーブルとカラムが写しにある外部キーだけを返す（BR3.2。写しに無い参照先は DSL で指せない）。 */
    private static List<TargetForeignKey> resolvableForeignKeys(TargetSchema schema, List<TargetForeignKey> keys) {
        List<TargetForeignKey> resolvable = new ArrayList<>();
        for (TargetForeignKey key : keys) {
            Optional<TargetTable> referenced = schema.table(key.referencedTable());
            if (referenced.isPresent()) {
                Set<String> names = new HashSet<>();
                referenced.get().columns().forEach(column -> names.add(column.name()));
                if (names.containsAll(key.referencedColumns())) {
                    resolvable.add(key);
                }
            }
        }
        return resolvable;
    }

    private static Map<String, Object> foreignKey(TargetForeignKey key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", new ArrayList<>(key.columns()));
        result.put("referencedTable", key.referencedTable());
        result.put("referencedColumns", new ArrayList<>(key.referencedColumns()));
        return result;
    }

    private static Map<String, Object> column(
            TargetColumn column, ColumnPlan plan, Integer listOrder, boolean sortColumn) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("label", label(column.name(), column.comment()));
        result.put("dbType", dbType(column));
        result.put("formPart", plan.formPart().dslName());
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("enabled", plan.searchOperator() != null);
        search.put(
                "operator",
                plan.searchOperator() == null ? null : plan.searchOperator().name());
        search.put("default", null);
        search.put("collapsed", false);
        result.put("search", search);
        Map<String, Object> list = new LinkedHashMap<>();
        list.put("visible", plan.listed());
        list.put("order", listOrder);
        list.put("width", null);
        list.put("sortable", plan.listed());
        list.put("defaultSort", sortColumn && plan.listed() ? SortDirection.ASC.name() : null);
        list.put(
                "format",
                plan.defaults().format() == null
                        ? null
                        : plan.defaults().format().name());
        result.put("list", list);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("visible", plan.defaults().detailVisible());
        result.put("detail", detail);
        result.put("validations", validations(column, plan.category()));
        result.put("options", plan.reference() == null ? null : reference(plan.reference()));
        return result;
    }

    /** DB 上の型を変換せずに写す（BR2.1）。長さが DSL の整数の範囲を超えるときだけ、長さを無しにする。 */
    private static Map<String, Object> dbType(TargetColumn column) {
        TargetDbType type = column.dbType();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", type.typeName());
        result.put("length", dslLength(type));
        result.put("precision", type.precision());
        result.put("scale", type.scale());
        result.put("nullable", column.nullable());
        return result;
    }

    /** DB から導けるバリデーション（BR4.1）。必須（NOT NULL で既定値が無い）、文字列の長さの上限。 */
    private static List<Object> validations(TargetColumn column, TypeCategory category) {
        List<Object> result = new ArrayList<>();
        if (!column.nullable() && column.defaultValue() == null) {
            Map<String, Object> required = new LinkedHashMap<>();
            required.put("type", ValidationType.REQUIRED.dslName());
            required.put("origin", ValidationOrigin.DB.name());
            result.add(required);
        }
        Integer length = dslLength(column.dbType());
        if (TypeCategoryMapping.isText(category) && length != null) {
            Map<String, Object> maxLength = new LinkedHashMap<>();
            maxLength.put("type", ValidationType.MAX_LENGTH.dslName());
            maxLength.put("value", length);
            maxLength.put("origin", ValidationOrigin.DB.name());
            result.add(maxLength);
        }
        return result;
    }

    /** 1カラムの外部キーの参照の選択肢（BR3.2。値と表示は参照先のカラム）。 */
    private static Map<String, Object> reference(TargetForeignKey key) {
        String referencedColumn = key.referencedColumns().getFirst();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", OptionSourceKind.REFERENCE.name());
        result.put("table", key.referencedTable());
        result.put("valueColumn", referencedColumn);
        result.put("labelColumn", referencedColumn);
        return result;
    }

    /** 表示名（BR1.2・NFR9.2）。ja はコメント（制御文字を除いたもの。NFR4.8）があればコメント、無ければ物理名。en は物理名。 */
    private static Map<String, Object> label(String name, String comment) {
        String cleaned = DslYamlWriter.stripControlCharacters(comment);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ja", cleaned == null ? name : cleaned);
        result.put("en", name);
        return result;
    }

    private static Integer dslLength(TargetDbType type) {
        Long length = type.length();
        return length == null || length > Integer.MAX_VALUE ? null : length.intValue();
    }

    private static int compareNames(String left, String right) {
        int folded = compareCodePoints(left, right, true);
        return folded != 0 ? folded : compareCodePoints(left, right, false);
    }

    private static int compareCodePoints(String left, String right, boolean ignoreCase) {
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            int a = left.codePointAt(i);
            int b = right.codePointAt(j);
            int compared = ignoreCase ? Integer.compare(fold(a), fold(b)) : Integer.compare(a, b);
            if (compared != 0) {
                return compared;
            }
            i += Character.charCount(a);
            j += Character.charCount(b);
        }
        return Boolean.compare(i < left.length(), j < right.length());
    }

    private static int fold(int codePoint) {
        return Character.toLowerCase(Character.toUpperCase(codePoint));
    }

    /**
     * カラム1つの初期値の決め方（BR2.3〜BR2.5・BR3.2）。
     *
     * @param category 型の分類
     * @param defaults 分類ごとの初期値
     * @param formPart フォーム部品（1カラムの外部キーは select）
     * @param searchOperator 検索の演算子（null は検索しない）
     * @param reference 1カラムの外部キー（無ければ null）
     */
    private record ColumnPlan(
            TypeCategory category,
            TypeCategoryMapping.Defaults defaults,
            FormPart formPart,
            SearchOperator searchOperator,
            TargetForeignKey reference) {

        static ColumnPlan of(TargetColumn column, boolean primaryKey, TargetForeignKey reference) {
            TypeCategory category = TypeCategoryMapping.categorize(column.dbType());
            TypeCategoryMapping.Defaults defaults = TypeCategoryMapping.defaults(category);
            if (reference != null) {
                // 外部キーの select は、検索を選択肢（CHOICE）にする。主キーを兼ねるときも select に合わせる。
                return new ColumnPlan(category, defaults, FormPart.SELECT, SearchOperator.CHOICE, reference);
            }
            SearchOperator operator = defaults.searchOperator();
            if (primaryKey && operator != null) {
                operator = SearchOperator.EQUALS;
            }
            return new ColumnPlan(category, defaults, defaults.formPart(), operator, null);
        }

        boolean listed() {
            return defaults.listed();
        }
    }
}
