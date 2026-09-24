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

import static cherry.mastersmith.dsl.testsupport.DslSamples.lineOf;
import static cherry.mastersmith.dsl.testsupport.DslSamples.validYaml;
import static cherry.mastersmith.dsl.testsupport.DslSamples.validYamlReplacing;
import static cherry.mastersmith.dsl.validate.ValidationTestSupport.assertNoComponentText;
import static cherry.mastersmith.dsl.validate.ValidationTestSupport.document;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 意味の検証の単体テスト（BR3.1〜BR3.7、AC2.2.2・AC2.2.4、NFR3.6）。 */
class DslSemanticValidatorTest {

    private static final PatternChecker PATTERNS = new PatternChecker();

    private static final DslSemanticValidator VALIDATOR = new DslSemanticValidator(PATTERNS);

    /** 構文の検証も通ることを確かめてから、意味の検証をする。 */
    private static List<DslError> validate(String yaml) {
        assertThat(new DslSchemaValidator().validate(document(yaml)))
                .as("構文の検証は通る")
                .isEmpty();
        List<DslError> errors = VALIDATOR.validate(document(yaml));
        assertThat(errors).allMatch(error -> error.kind() == DslErrorKind.SEMANTIC);
        assertNoComponentText(errors);
        return errors;
    }

    @AfterAll
    static void close() {
        PATTERNS.close();
    }

    @Test
    @DisplayName("the valid sample has no semantic errors")
    void validSamplePasses() {
        assertThat(validate(validYaml())).isEmpty();
    }

    @Test
    @DisplayName("a menu item pointing to a table that is not in the DSL is reported at the item's table")
    void menuUnknownTable() {
        String yaml = validYamlReplacing("    table: emp_view", "    table: no_such_table");

        List<DslError> errors = validate(yaml);

        assertThat(errors)
                .extracting(
                        DslError::messageKey, DslError::path, DslError::line, DslError::column, DslError::messageArgs)
                .containsExactly(tuple(
                        DslMessageKeys.SEMANTIC_MENU_UNKNOWN_TABLE,
                        "menus.1.table",
                        lineOf(yaml, "    table: no_such_table"),
                        5,
                        List.of("no_such_table")));
    }

    @Test
    @DisplayName("a menu item with neither a table nor children is reported, also when nested")
    void menuWithoutTableOrItems() {
        String yaml = validYamlReplacing("    table: emp_view", "    items: []")
                .replace("            table: emp_mst", "            items: null");

        assertThat(validate(yaml))
                .extracting(DslError::messageKey, DslError::path)
                .containsExactly(
                        tuple(DslMessageKeys.SEMANTIC_MENU_EMPTY, "menus.0.items.0.items.1"),
                        tuple(DslMessageKeys.SEMANTIC_MENU_EMPTY, "menus.1"));
    }

    @Test
    @DisplayName("references to missing tables of foreign keys and lookups are reported at the reference")
    void unknownReferencedTables() {
        String yaml = validYamlReplacing("referencedTable: dept_mst", "referencedTable: no_dept")
                .replace(
                        "          source: LOOKUP\n          table: dept_mst",
                        "          source: LOOKUP\n          table: nowhere")
                .replace(
                        "options: { source: REFERENCE, table: dept_mst,", "options: { source: REFERENCE, table: gone,");

        assertThat(validate(yaml))
                .extracting(DslError::messageKey, DslError::path, DslError::messageArgs)
                .containsExactly(
                        tuple(
                                DslMessageKeys.SEMANTIC_UNKNOWN_TABLE,
                                "tables.emp_mst.foreignKeys.0.referencedTable",
                                List.of("no_dept")),
                        tuple(
                                DslMessageKeys.SEMANTIC_UNKNOWN_TABLE,
                                "tables.emp_mst.columns.dept_code.options.table",
                                List.of("gone")),
                        tuple(
                                DslMessageKeys.SEMANTIC_UNKNOWN_TABLE,
                                "tables.emp_mst.columns.boss_dept.options.table",
                                List.of("nowhere")));
    }

    @Test
    @DisplayName("columns that are not in the own or referenced table are reported for keys, references and lookups")
    void unknownColumns() {
        String yaml = validYamlReplacing("    primaryKey: [emp_no]\n", "    primaryKey: [emp_number]\n")
                .replace(
                        "{ columns: [dept_code], referencedTable: dept_mst, referencedColumns: [dept_code] }",
                        "{ columns: [dept_cd], referencedTable: dept_mst, referencedColumns: [code] }")
                .replace(
                        "valueColumn: dept_code, labelColumn: dept_name }",
                        "valueColumn: dept_code, labelColumn: dept_nm }")
                .replace(
                        "            - { column: dept_name, operator: CONTAINS }",
                        "            - { column: name, operator: CONTAINS }")
                .replace("            - { column: dept_name, order: 2 }", "            - { column: title, order: 2 }");

        assertThat(validate(yaml))
                .extracting(DslError::path, DslError::messageArgs)
                .containsExactly(
                        tuple("tables.emp_mst.primaryKey.0", List.of("emp_mst", "emp_number")),
                        tuple("tables.emp_mst.foreignKeys.0.columns.0", List.of("emp_mst", "dept_cd")),
                        tuple("tables.emp_mst.foreignKeys.0.referencedColumns.0", List.of("dept_mst", "code")),
                        tuple("tables.emp_mst.columns.dept_code.options.labelColumn", List.of("dept_mst", "dept_nm")),
                        tuple(
                                "tables.emp_mst.columns.boss_dept.options.lookupSearch.0.column",
                                List.of("dept_mst", "name")),
                        tuple(
                                "tables.emp_mst.columns.boss_dept.options.lookupList.1.column",
                                List.of("dept_mst", "title")));
    }

    @Test
    @DisplayName("a list order that is already used in the table is reported at the later column")
    void duplicateListOrder() {
        String yaml = validYamlReplacing(
                "list: { visible: true, order: 3, sortable: true, format: DATE }",
                "list: { visible: true, order: 2, sortable: true, format: DATE }");

        List<DslError> errors = validate(yaml);

        assertThat(errors)
                .extracting(DslError::messageKey, DslError::path, DslError::messageArgs)
                .containsExactly(tuple(
                        DslMessageKeys.SEMANTIC_DUPLICATE_ORDER,
                        "tables.emp_mst.columns.joined_on.list.order",
                        List.of("2")));
        assertThat(errors.getFirst().line())
                .isEqualTo(lineOf(yaml, "list: { visible: true, order: 2, sortable: true, format: DATE }"));
    }

    @Test
    @DisplayName("form parts and option sources must match")
    void formPartAndOptions() {
        String yaml = validYamlReplacing(
                        "        formPart: select\n        search: { enabled: true, operator: CHOICE",
                        "        formPart: text\n        search: { enabled: true, operator: CHOICE")
                .replace(
                        "        formPart: select\n        search: { enabled: false",
                        "        formPart: lookup\n        search: { enabled: false")
                .replace(
                        "        formPart: number\n        search: { enabled: false",
                        "        formPart: radio\n        search: { enabled: false");

        assertThat(validate(yaml))
                .extracting(DslError::path, DslError::messageArgs)
                .containsExactly(
                        tuple("tables.dept_mst.columns.kind.options", List.of("text", "FIXED")),
                        tuple("tables.emp_mst.columns.dept_code.options", List.of("lookup", "REFERENCE")),
                        tuple("tables.emp_view.columns.emp_no.formPart", List.of("radio", "none")));
        assertThat(DslSemanticValidator.matches("select", "REFERENCE")).isTrue();
        assertThat(DslSemanticValidator.matches("radio", "LOOKUP")).isFalse();
        assertThat(DslSemanticValidator.matches("checkbox", null)).isTrue();
    }

    @Test
    @DisplayName("fixed option values must not repeat")
    void duplicateFixedValue() {
        String yaml = validYamlReplacing("{ value: \"2\", label: { ja: 支社", "{ value: \"1\", label: { ja: 支社");

        assertThat(validate(yaml))
                .extracting(DslError::messageKey, DslError::path)
                .containsExactly(tuple(
                        DslMessageKeys.SEMANTIC_OPTIONS_DUPLICATE_VALUE,
                        "tables.dept_mst.columns.kind.options.items.1.value"));
    }

    @Test
    @DisplayName("min greater than max and minLength greater than maxLength are reported at the later validation")
    void contradictingBounds() {
        String yaml = validYamlReplacing(
                        "{ type: min, value: 1, origin: MANUAL }", "{ type: min, value: 100000, origin: MANUAL }")
                .replace(
                        "{ type: minLength, value: 1, origin: MANUAL }",
                        "{ type: minLength, value: 51, origin: MANUAL }");

        assertThat(validate(yaml))
                .extracting(DslError::messageKey, DslError::path, DslError::messageArgs)
                .containsExactly(
                        tuple(
                                DslMessageKeys.SEMANTIC_MIN_LENGTH_GREATER_THAN_MAX_LENGTH,
                                "tables.dept_mst.columns.dept_name.validations.1",
                                List.of("51", "50")),
                        tuple(
                                DslMessageKeys.SEMANTIC_MIN_GREATER_THAN_MAX,
                                "tables.emp_mst.columns.emp_no.validations.2",
                                List.of("100000", "99999")));
    }

    @Test
    @DisplayName("an invalid pattern is reported, and 1,000 characters pass while 1,001 are too long")
    void patterns() {
        String invalid = validYamlReplacing("\"^[A-Z0-9]+$\"", "\"^[A-Z0-9+$\"");
        assertThat(validate(invalid))
                .extracting(DslError::messageKey, DslError::path)
                .containsExactly(tuple(
                        DslMessageKeys.SEMANTIC_INVALID_PATTERN,
                        "tables.dept_mst.columns.dept_code.validations.2.value"));

        assertThat(validate(validYamlReplacing("\"^[A-Z0-9]+$\"", "\"" + "a".repeat(1000) + "\"")))
                .isEmpty();

        assertThat(validate(validYamlReplacing("\"^[A-Z0-9]+$\"", "\"" + "a".repeat(1001) + "\"")))
                .extracting(DslError::messageKey, DslError::messageArgs)
                .containsExactly(tuple(DslMessageKeys.SEMANTIC_PATTERN_TOO_LONG, List.of("1000")));
    }

    @Test
    @DisplayName("all semantic errors found are returned together")
    void allErrorsAreReturned() {
        String yaml = validYamlReplacing("    table: emp_view", "    table: no_such_table")
                .replace("referencedTable: dept_mst", "referencedTable: no_dept")
                .replace("\"^[A-Z0-9]+$\"", "\"(\"")
                .replace("list: { visible: true, order: 3,", "list: { visible: true, order: 1,");

        assertThat(validate(yaml)).hasSize(4);
    }
}
