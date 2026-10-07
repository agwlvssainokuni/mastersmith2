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
package cherry.mastersmith.dsl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** モデルの値の単体テスト（BR5.2、契約 C4・C8。書式の版 2 は U2 dsl-v2 の BR1.2・BR1.6・BR8.2、契約 C3）。 */
class DslModelTest {

    static final String HASH = "0123456789abcdef".repeat(4);

    static DslColumn column(String name) {
        return new DslColumn(
                name,
                new DisplayName("名前", "name"),
                new DbType("VARCHAR", 10, null, null, false),
                FormPart.TEXT,
                new SearchSetting(true, SearchOperator.EQUALS, null, false),
                new ListSetting(true, 1, null, true, null, null),
                new DetailSetting(true),
                List.of(new Validation(ValidationType.MAX_LENGTH, BigDecimal.TEN, null, ValidationOrigin.DB, null)),
                null);
    }

    static DslTable table(String name, String... columnNames) {
        Map<String, DslColumn> columns = new LinkedHashMap<>();
        for (String columnName : columnNames) {
            columns.put(columnName, column(columnName));
        }
        return new DslTable(name, new DisplayName("表", name), false, List.of(columnNames[0]), List.of(), columns);
    }

    static DslSchema schema(String name, DslTable... tables) {
        Map<String, DslTable> map = new LinkedHashMap<>();
        for (DslTable table : tables) {
            map.put(table.name(), table);
        }
        return new DslSchema(name, new DisplayName("スキーマ", name), map);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("lists and maps are immutable copies and keep the DSL order")
    void immutableAndOrdered() {
        Map<String, DslTable> tables = new LinkedHashMap<>();
        for (String name : List.of("z_table", "a_table", "m_table")) {
            tables.put(name, table(name, "c3", "c1", "c2"));
        }
        List<DslSchema> schemas =
                new ArrayList<>(List.of(new DslSchema("sales", new DisplayName("販売", "Sales"), tables)));
        List<DslMenuItem> menus = new ArrayList<>(List.of(
                new DslMenuItem(new DisplayName("部署", "Dept"), null, new TableRef("sales", "a_table"), List.of())));

        DslModel model = new DslModel(HASH, 2, schemas, menus);
        tables.clear();
        schemas.clear();
        menus.clear();

        assertThat(model.formatVersion()).isEqualTo(2);
        assertThat(model.schemas()).extracting(DslSchema::name).containsExactly("sales");
        assertThat(model.schema().tables().keySet()).containsExactly("z_table", "a_table", "m_table");
        assertThat(model.schema().tables().get("a_table").columns().keySet()).containsExactly("c3", "c1", "c2");
        assertThat(model.menus()).hasSize(1);
        assertThat(model.menus().getFirst().table()).isEqualTo(new TableRef("sales", "a_table"));
        assertThatThrownBy(() -> model.schema().tables().put("x", table("x", "c")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.schemas().add(model.schema())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.menus().add(model.menus().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(
                        () -> model.schema().tables().get("a_table").columns().remove("c1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "the model rejects a malformed hash, an unsupported version, duplicate schema names and a key that differs"
                    + " from the name")
    void rejectsInvalidModel() {
        List<DslSchema> schemas = List.of(schema("sales", table("dept", "code")));

        assertThatThrownBy(() -> new DslModel(HASH.toUpperCase(), 2, schemas, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslModel("abc", 2, schemas, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslModel(null, 2, schemas, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslModel(HASH, 1, schemas, List.of()))
                .as("version 1 is not read as version 2")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslModel(
                        HASH,
                        2,
                        List.of(schema("sales", table("a", "c")), schema("sales", table("b", "c"))),
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new DslSchema("sales", new DisplayName("", ""), Map.of("other", table("dept", "code"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a table is found by the pair of schema and table names, case-sensitively")
    void findTableByRef() {
        DslTable dept = table("dept_mst", "code");
        DslModel model = new DslModel(HASH, 2, List.of(schema("sales", dept, table("emp_mst", "no"))), List.of());

        assertThat(model.findTable(new TableRef("sales", "dept_mst"))).contains(dept);
        assertThat(model.findTable(new TableRef("sales", "no_such"))).isEmpty();
        assertThat(model.findTable(new TableRef("other", "dept_mst"))).isEmpty();
        assertThat(model.findTable(new TableRef("Sales", "dept_mst"))).isEmpty();
        assertThat(model.findTable(new TableRef("sales", "DEPT_MST"))).isEmpty();
        assertThatThrownBy(() -> model.findTable(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("schema() returns the only schema and fails when there is not exactly one")
    void onlySchema() {
        DslSchema sales = schema("sales");

        assertThat(new DslModel(HASH, 2, List.of(sales), List.of()).schema()).isEqualTo(sales);
        assertThat(sales.tables()).as("a schema may have no tables").isEmpty();
        assertThatThrownBy(() -> new DslModel(HASH, 2, List.of(), List.of()).schema())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DslModel(HASH, 2, List.of(sales, schema("hr")), List.of()).schema())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a table reference and a schema require non-empty names and a label")
    void tableRefAndSchemaRequireValues() {
        assertThatThrownBy(() -> new TableRef("", "t")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TableRef("s", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TableRef(null, "t")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslSchema("", new DisplayName("", ""), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslSchema("s", null, Map.of())).isInstanceOf(NullPointerException.class);
        assertThat(new TableRef("s", "t")).isEqualTo(new TableRef("s", "t"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("required values of each part are checked")
    void rejectsMissingValues() {
        DisplayName label = new DisplayName("", "");
        assertThatThrownBy(() -> new DisplayName(null, "en")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DslMenuItem(label, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslTable("t", label, false, List.of(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DbType("INT", -1, null, null, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DbType("", null, null, null, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchSetting(true, null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListSetting(true, null, null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListSetting(false, 0, null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListSetting(false, null, 0, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DslForeignKey(List.of(), "t", List.of("c")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LookupListItem("c", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LookupSearchItem("c", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OptionItem(null, label)).isInstanceOf(NullPointerException.class);
        assertThat(new ListSetting(false, null, null, false, SortDirection.ASC, ListFormat.DATE).order())
                .isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a validation holds a value matching its type")
    void validationValueMatchesType() {
        assertThat(new Validation(ValidationType.PATTERN, null, "^[0-9]+$", ValidationOrigin.MANUAL, null).text())
                .isEqualTo("^[0-9]+$");
        assertThat(new Validation(ValidationType.REQUIRED, null, null, ValidationOrigin.DB, null).number())
                .isNull();
        assertThatThrownBy(() -> new Validation(ValidationType.MIN, null, null, ValidationOrigin.DB, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new Validation(ValidationType.PATTERN, BigDecimal.ONE, null, ValidationOrigin.DB, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Validation(ValidationType.UNIQUE, BigDecimal.ONE, null, ValidationOrigin.DB, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ValidationType.fromDslName("minLength")).isEqualTo(ValidationType.MIN_LENGTH);
        assertThat(FormPart.fromDslName("lookup")).isEqualTo(FormPart.LOOKUP);
        assertThat(FormPart.SWITCH.dslName()).isEqualTo("switch");
        assertThat(ValidationType.UNIQUE.dslName()).isEqualTo("unique");
        assertThatThrownBy(() -> FormPart.fromDslName("TEXT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationType.fromDslName("x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("an option source requires the values of its kind")
    void optionSourceRequiresValuesOfItsKind() {
        DisplayName label = new DisplayName("はい", "Yes");
        OptionSource fixed = new OptionSource(
                OptionSourceKind.FIXED, List.of(new OptionItem("1", label)), null, null, null, List.of(), List.of());
        assertThat(fixed.items()).hasSize(1);
        OptionSource lookup = new OptionSource(
                OptionSourceKind.LOOKUP,
                List.of(),
                "dept",
                "code",
                null,
                List.of(new LookupSearchItem("name", SearchOperator.CONTAINS)),
                List.of(new LookupListItem("name", 1)));
        assertThat(lookup.lookupList()).hasSize(1);

        assertThatThrownBy(() ->
                        new OptionSource(OptionSourceKind.FIXED, List.of(), null, null, null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OptionSource(
                        OptionSourceKind.REFERENCE, List.of(), "dept", "code", null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OptionSource(
                        OptionSourceKind.LOOKUP, List.of(), "dept", "code", null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OptionSource(
                        OptionSourceKind.REFERENCE, List.of(), null, "code", "name", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
