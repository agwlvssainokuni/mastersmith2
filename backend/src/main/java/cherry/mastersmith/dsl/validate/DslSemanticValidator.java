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
package cherry.mastersmith.dsl.validate;

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import cherry.mastersmith.dsl.domain.MenuDepth;
import cherry.mastersmith.dsl.parse.JsonPointers;
import cherry.mastersmith.dsl.parse.PositionMap;
import cherry.mastersmith.dsl.parse.YamlDocument;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 意味の検証（BR3.1〜BR3.7）。構文（JSON Schema）の検証を通った形だけを受け取る。誤りは見つかったものをすべて返し（件数の上限を
 * 設けない）、場所から位置の対応表で行・列を引く（BR4.1）。
 *
 * <ul>
 *   <li>BR3.1: メニューは DSL にあるテーブルだけを指す
 *   <li>BR3.2: メニューの項目はテーブルか子の少なくとも一方を持つ
 *   <li>BR3.3: 主キー・外部キー・選択肢（REFERENCE・LOOKUP）・参照ピッカーの検索と一覧の参照の先のテーブルとカラムがある
 *   <li>BR3.4: 一覧の並び順はテーブルの中で重ならない（後の方を誤りにする）
 *   <li>BR3.5: フォーム部品と選択肢の出どころが合う（あわせて、固定の選択肢の値が重ならないことも確かめる。entities.md の
 *       OptionSource.items の制約）
 *   <li>BR3.6: {@code min ≦ max}・{@code minLength ≦ maxLength}・{@code pattern} が正しい正規表現（{@link PatternChecker}）
 * </ul>
 *
 * <p>書式の版 2（Intent 261004-role-menu の U2 dsl-v2）で、次を足し・変えた。誤りは スキーマの数 → メニュー（組・空）→ メニューの
 * 深さ → スキーマごとのテーブル（参照・既存の決まり）の順に並べる。場所はスキーマの下の道（例 {@code schemas.sales.tables.dept}）。
 *
 * <ul>
 *   <li>BR1.3: スキーマはちょうど1つ（0 か 2 以上なら場所 {@code schemas} に1件）
 *   <li>BR1.5: 外部キーの参照先と選択肢の出どころは、同じスキーマの中のテーブル名として照らす
 *   <li>BR1.6: メニューの {@code table} の組が指すスキーマとテーブルが DSL にある
 *   <li>BR2.1・BR2.2: メニューの深さは {@link DslFormat#MAX_MENU_DEPTH} 段まで（上限を超えた最初の段の項目ごとに1件）。起動時の
 *       読み直しだけは深さを確かめない（{@link #validate(YamlDocument, boolean)}）
 * </ul>
 */
@Component
public class DslSemanticValidator {

    private static final String FIXED = "FIXED";

    private static final String REFERENCE = "REFERENCE";

    private static final String LOOKUP = "LOOKUP";

    private final PatternChecker patternChecker;

    /**
     * 作る。
     *
     * @param patternChecker 正規表現の確かめ
     */
    public DslSemanticValidator(PatternChecker patternChecker) {
        this.patternChecker = patternChecker;
    }

    /**
     * 意味を検証する（メニューの深さを含むすべての決まり。投入・復元・プレビューの読み直しの読み方。BR2.2）。
     *
     * @param document 構文の検証を通った JSON の形と位置の対応表
     * @return 意味の誤り（無ければ空）
     */
    public List<DslError> validate(YamlDocument document) {
        return validate(document, true);
    }

    /**
     * 意味を検証する。
     *
     * @param document 構文の検証を通った JSON の形と位置の対応表
     * @param checkMenuDepth メニューの深さを確かめるなら true（起動時の読み直しだけが false。BR2.3）
     * @return 意味の誤り（無ければ空）
     */
    public List<DslError> validate(YamlDocument document, boolean checkMenuDepth) {
        JsonNode schemas = document.json().path("schemas");
        Check check = new Check(schemas, document.positions());
        String schemasPointer = JsonPointers.child(JsonPointers.ROOT, "schemas");
        if (schemas.size() != 1) {
            check.error(schemasPointer, DslMessageKeys.SEMANTIC_SCHEMA_COUNT, String.valueOf(schemas.size()));
        }
        JsonNode menus = document.json().path("menus");
        String menusPointer = JsonPointers.child(JsonPointers.ROOT, "menus");
        for (int i = 0; i < menus.size(); i++) {
            check.menu(menus.get(i), JsonPointers.child(menusPointer, i));
        }
        if (checkMenuDepth) {
            for (List<Integer> path : MenuDepth.overDepth(
                    elements(menus), item -> elements(item.path("items")), DslFormat.MAX_MENU_DEPTH)) {
                check.error(
                        menuPointer(menusPointer, path),
                        DslMessageKeys.SEMANTIC_MENU_DEPTH,
                        String.valueOf(DslFormat.MAX_MENU_DEPTH));
            }
        }
        for (Map.Entry<String, JsonNode> schema : schemas.properties()) {
            JsonNode tables = schema.getValue().path("tables");
            String tablesPointer = JsonPointers.child(JsonPointers.child(schemasPointer, schema.getKey()), "tables");
            for (Map.Entry<String, JsonNode> table : tables.properties()) {
                check.table(
                        tables, JsonPointers.child(tablesPointer, table.getKey()), table.getKey(), table.getValue());
            }
        }
        return List.copyOf(check.errors);
    }

    private static List<JsonNode> elements(JsonNode array) {
        List<JsonNode> list = new ArrayList<>();
        Iterator<JsonNode> iterator = array.isArray() ? array.iterator() : Collections.emptyIterator();
        iterator.forEachRemaining(list::add);
        return list;
    }

    private static String menuPointer(String menusPointer, List<Integer> path) {
        String pointer = JsonPointers.child(menusPointer, path.getFirst());
        for (int index : path.subList(1, path.size())) {
            pointer = JsonPointers.child(JsonPointers.child(pointer, "items"), index);
        }
        return pointer;
    }

    /** 1回の検証の状態。 */
    private final class Check {

        private final JsonNode schemas;

        private final PositionMap positions;

        private final List<DslError> errors = new ArrayList<>();

        /** 今確かめているテーブルのスキーマの {@code tables}（外部キーと選択肢の参照はこの中で照らす。BR1.5）。 */
        private JsonNode tables;

        Check(JsonNode schemas, PositionMap positions) {
            this.schemas = schemas;
            this.positions = positions;
        }

        void menu(JsonNode item, String pointer) {
            JsonNode table = item.path("table");
            JsonNode items = item.path("items");
            if (table.isObject()) {
                String schemaName = table.path("schema").stringValue();
                String tableName = table.path("name").stringValue();
                if (!schemas.path(schemaName).path("tables").has(tableName)) {
                    error(
                            JsonPointers.child(pointer, "table"),
                            DslMessageKeys.SEMANTIC_MENU_UNKNOWN_TABLE,
                            DslError.excerpt(schemaName + "." + tableName));
                }
            }
            if (!table.isObject() && items.size() == 0) {
                error(pointer, DslMessageKeys.SEMANTIC_MENU_EMPTY);
            }
            for (int i = 0; i < items.size(); i++) {
                menu(items.get(i), JsonPointers.child(JsonPointers.child(pointer, "items"), i));
            }
        }

        void table(JsonNode schemaTables, String pointer, String tableName, JsonNode table) {
            this.tables = schemaTables;
            JsonNode columns = table.path("columns");
            columnNames(tableName, columns, table.path("primaryKey"), JsonPointers.child(pointer, "primaryKey"));
            JsonNode foreignKeys = table.path("foreignKeys");
            for (int i = 0; i < foreignKeys.size(); i++) {
                foreignKey(
                        tableName,
                        columns,
                        foreignKeys.get(i),
                        JsonPointers.child(JsonPointers.child(pointer, "foreignKeys"), i));
            }
            Map<Integer, String> orders = new HashMap<>();
            for (Map.Entry<String, JsonNode> column : columns.properties()) {
                String columnPointer = JsonPointers.child(JsonPointers.child(pointer, "columns"), column.getKey());
                listOrder(orders, column.getKey(), column.getValue().path("list"), columnPointer);
                options(column.getValue(), columnPointer);
                validations(column.getValue().path("validations"), JsonPointers.child(columnPointer, "validations"));
            }
        }

        private void foreignKey(String tableName, JsonNode columns, JsonNode foreignKey, String pointer) {
            columnNames(tableName, columns, foreignKey.path("columns"), JsonPointers.child(pointer, "columns"));
            String referenced = foreignKey.path("referencedTable").stringValue();
            JsonNode referencedTable = referencedTable(referenced, JsonPointers.child(pointer, "referencedTable"));
            if (referencedTable != null) {
                columnNames(
                        referenced,
                        referencedTable.path("columns"),
                        foreignKey.path("referencedColumns"),
                        JsonPointers.child(pointer, "referencedColumns"));
            }
        }

        private void listOrder(Map<Integer, String> orders, String columnName, JsonNode list, String columnPointer) {
            JsonNode order = list.path("order");
            if (order.isIntegralNumber() && orders.putIfAbsent(order.intValue(), columnName) != null) {
                error(
                        JsonPointers.child(JsonPointers.child(columnPointer, "list"), "order"),
                        DslMessageKeys.SEMANTIC_DUPLICATE_ORDER,
                        String.valueOf(order.intValue()));
            }
        }

        private void options(JsonNode column, String columnPointer) {
            String formPart = column.path("formPart").stringValue();
            JsonNode options = column.path("options");
            String source = options.isObject() ? options.path("source").stringValue() : null;
            if (!matches(formPart, source)) {
                String pointer = JsonPointers.child(columnPointer, options.isObject() ? "options" : "formPart");
                error(
                        pointer,
                        DslMessageKeys.SEMANTIC_OPTIONS_MISMATCH,
                        formPart,
                        Objects.requireNonNullElse(source, "none"));
            }
            if (source == null) {
                return;
            }
            String optionsPointer = JsonPointers.child(columnPointer, "options");
            if (FIXED.equals(source)) {
                fixedItems(options.path("items"), JsonPointers.child(optionsPointer, "items"));
                return;
            }
            String referenced = options.path("table").stringValue();
            JsonNode referencedTable = referencedTable(referenced, JsonPointers.child(optionsPointer, "table"));
            if (referencedTable == null) {
                return;
            }
            JsonNode columns = referencedTable.path("columns");
            columnName(
                    referenced,
                    columns,
                    options.path("valueColumn"),
                    JsonPointers.child(optionsPointer, "valueColumn"));
            columnName(
                    referenced,
                    columns,
                    options.path("labelColumn"),
                    JsonPointers.child(optionsPointer, "labelColumn"));
            for (String listName : List.of("lookupSearch", "lookupList")) {
                JsonNode entries = options.path(listName);
                for (int i = 0; i < entries.size(); i++) {
                    String entryPointer = JsonPointers.child(JsonPointers.child(optionsPointer, listName), i);
                    columnName(
                            referenced,
                            columns,
                            entries.get(i).path("column"),
                            JsonPointers.child(entryPointer, "column"));
                }
            }
        }

        private void fixedItems(JsonNode items, String itemsPointer) {
            Set<String> values = new HashSet<>();
            for (int i = 0; i < items.size(); i++) {
                String value = items.get(i).path("value").stringValue();
                if (!values.add(value)) {
                    error(
                            JsonPointers.child(JsonPointers.child(itemsPointer, i), "value"),
                            DslMessageKeys.SEMANTIC_OPTIONS_DUPLICATE_VALUE,
                            DslError.excerpt(value));
                }
            }
        }

        private void validations(JsonNode validations, String pointer) {
            Bound min = null;
            Bound max = null;
            Bound minLength = null;
            Bound maxLength = null;
            for (int i = 0; i < validations.size(); i++) {
                JsonNode validation = validations.get(i);
                String entryPointer = JsonPointers.child(pointer, i);
                JsonNode value = validation.path("value");
                switch (validation.path("type").stringValue()) {
                    case "min" -> min = first(min, value, entryPointer, i);
                    case "max" -> max = first(max, value, entryPointer, i);
                    case "minLength" -> minLength = first(minLength, value, entryPointer, i);
                    case "maxLength" -> maxLength = first(maxLength, value, entryPointer, i);
                    case "pattern" -> pattern(value.stringValue(), JsonPointers.child(entryPointer, "value"));
                    default -> {
                        // required・unique は値を持たないため、矛盾を確かめることが無い。
                    }
                }
            }
            compare(min, max, DslMessageKeys.SEMANTIC_MIN_GREATER_THAN_MAX);
            compare(minLength, maxLength, DslMessageKeys.SEMANTIC_MIN_LENGTH_GREATER_THAN_MAX_LENGTH);
        }

        private void pattern(String pattern, String pointer) {
            switch (patternChecker.check(pattern)) {
                case TOO_LONG ->
                    error(
                            pointer,
                            DslMessageKeys.SEMANTIC_PATTERN_TOO_LONG,
                            String.valueOf(DslFormat.MAX_PATTERN_LENGTH));
                case INVALID -> error(pointer, DslMessageKeys.SEMANTIC_INVALID_PATTERN);
                case VALID -> {
                    // 正しい正規表現。
                }
            }
        }

        private void compare(Bound lower, Bound upper, String messageKey) {
            if (lower != null && upper != null && lower.value().compareTo(upper.value()) > 0) {
                String later = lower.index() > upper.index() ? lower.pointer() : upper.pointer();
                error(
                        later,
                        messageKey,
                        lower.value().toPlainString(),
                        upper.value().toPlainString());
            }
        }

        private JsonNode referencedTable(String tableName, String pointer) {
            JsonNode table = tables.get(tableName);
            if (table == null) {
                error(pointer, DslMessageKeys.SEMANTIC_UNKNOWN_TABLE, DslError.excerpt(tableName));
            }
            return table;
        }

        private void columnNames(String tableName, JsonNode columns, JsonNode names, String pointer) {
            for (int i = 0; i < names.size(); i++) {
                columnName(tableName, columns, names.get(i), JsonPointers.child(pointer, i));
            }
        }

        private void columnName(String tableName, JsonNode columns, JsonNode name, String pointer) {
            if (isText(name) && !columns.has(name.stringValue())) {
                error(
                        pointer,
                        DslMessageKeys.SEMANTIC_UNKNOWN_COLUMN,
                        DslError.excerpt(tableName),
                        DslError.excerpt(name.stringValue()));
            }
        }

        void error(String pointer, String messageKey, String... messageArgs) {
            errors.add(DslErrors.at(DslErrorKind.SEMANTIC, positions, pointer, messageKey, messageArgs));
        }
    }

    /**
     * フォーム部品と選択肢の出どころが合うかを判定する（BR3.5）。
     *
     * @param formPart フォーム部品
     * @param source 選択肢の出どころ（無ければ null）
     * @return 合えば true
     */
    static boolean matches(String formPart, String source) {
        return switch (formPart) {
            case "select", "radio" -> FIXED.equals(source) || REFERENCE.equals(source);
            case "lookup" -> LOOKUP.equals(source);
            default -> source == null;
        };
    }

    private static boolean isText(JsonNode node) {
        return node != null && node.isString();
    }

    private static Bound first(Bound current, JsonNode value, String pointer, int index) {
        if (current != null || !value.isNumber()) {
            return current;
        }
        return new Bound(value.decimalValue(), pointer, index);
    }

    /**
     * 最初に見つかった上限・下限の値と、その場所。
     *
     * @param value 値
     * @param pointer 場所
     * @param index バリデーションの並びの中の位置（0 から。後の方を誤りの場所にするため）
     */
    private record Bound(BigDecimal value, String pointer, int index) {}
}
