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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import cherry.mastersmith.dsl.testsupport.CountingHttpServer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 同梱の JSON Schema による構文の検証の単体テスト（BR1.6・BR2.2、NFR3.3・NFR5.1・NFR5.2、AC2.2.1・AC2.2.6・AC2.3.6〜AC2.3.8）。 */
class DslSchemaValidatorTest {

    private static final DslSchemaValidator VALIDATOR = new DslSchemaValidator();

    private static List<DslError> validate(String yaml) {
        return VALIDATOR.validate(document(yaml));
    }

    @Test
    @DisplayName("the valid sample passes the bundled schema")
    void validSamplePasses() {
        assertThat(validate(validYaml())).isEmpty();
    }

    @Test
    @DisplayName("a missing required item and a wrong type are both reported with line, column and path")
    void twoSyntaxErrorsWithPositions() {
        String yaml = validYamlReplacing("        detail: { visible: false }\n", "")
                .replace(
                        "    view: false\n    primaryKey: [dept_code]", "    view: maybe\n    primaryKey: [dept_code]");

        List<DslError> errors = validate(yaml);

        assertThat(errors).hasSize(2);
        assertThat(errors).allMatch(error -> error.kind() == DslErrorKind.SYNTAX);
        DslError type = errors.stream()
                .filter(error -> error.messageKey().equals(DslMessageKeys.SYNTAX_TYPE))
                .findFirst()
                .orElseThrow();
        assertThat(type.path()).isEqualTo("tables.dept_mst.view");
        assertThat(type.line()).isEqualTo(lineOf(yaml, "    view: maybe"));
        assertThat(type.column()).isEqualTo(5);
        assertThat(type.messageArgs()).containsExactly("boolean", "maybe");
        DslError required = errors.stream()
                .filter(error -> error.messageKey().equals(DslMessageKeys.SYNTAX_REQUIRED))
                .findFirst()
                .orElseThrow();
        assertThat(required.path()).isEqualTo("tables.emp_view.columns.emp_no");
        assertThat(required.messageArgs()).containsExactly("detail");
        assertThat(required.line())
                .isEqualTo(
                        lineOf(
                                yaml,
                                "      emp_no:\n        label: { ja: 社員番号, en: emp_no }\n        dbType: { name: INTEGER, nullable: false }"));
        assertThat(required.column()).isEqualTo(7);
        assertNoComponentText(errors);
    }

    @Test
    @DisplayName("connection items are unknown properties and their values are never embedded in the errors")
    void connectionItemsAreUnknownWithoutValues() {
        String yaml = validYaml()
                + "url: jdbc:mysql://sample.invalid:3306/sample_db\n"
                + "username: sample-user-name\n"
                + "password: sample-password-value\n";
        String tableLevel = yaml.replace(
                "  dept_mst:\n    label: { ja: 部署, en: dept_mst }\n",
                "  dept_mst:\n    label: { ja: 部署, en: dept_mst }\n    password: sample-password-in-table\n");

        List<DslError> errors = validate(tableLevel);

        assertThat(errors).extracting(DslError::messageKey).containsOnly(DslMessageKeys.SYNTAX_UNKNOWN_PROPERTY);
        assertThat(errors)
                .extracting(DslError::path)
                .containsExactlyInAnyOrder("url", "username", "password", "tables.dept_mst.password");
        assertThat(errors).allMatch(error -> error.line() != null);
        assertThat(errors.toString())
                .doesNotContain(
                        "sample.invalid", "sample-user-name", "sample-password-value", "sample-password-in-table");
        assertNoComponentText(errors);
    }

    @Test
    @DisplayName("a value written by the user is embedded only up to its first 100 characters")
    void userValueIsCut() {
        String yaml = validYamlReplacing(
                "        formPart: number\n        search: { enabled: false",
                "        formPart: " + "x".repeat(150) + "\n        search: { enabled: false");

        DslError error = validate(yaml).getFirst();

        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.SYNTAX_ENUM);
        assertThat(error.messageArgs().getFirst()).isEqualTo("x".repeat(100));
    }

    @Test
    @DisplayName("limits of numbers, lengths and counts have their own message keys")
    void otherKeywords() {
        String yaml = validYamlReplacing("width: 120", "width: 0")
                .replace(
                        "          lookupSearch:\n            - { column: dept_name, operator: CONTAINS }\n",
                        "          lookupSearch: []\n")
                .replace(
                        "    columns:\n      emp_no:\n        label: { ja: 社員番号, en: emp_no }\n        dbType: { name: INTEGER, nullable: false }\n        formPart: number\n        search: { enabled: false, collapsed: false }\n        list: { visible: true, order: 1, sortable: true }\n        detail: { visible: false }\n        validations: []\n",
                        "    columns: {}\n")
                .replace("{ type: unique, origin: DB }", "{ type: unique, value: 1, origin: DB }")
                .replace("dbType: { name: DATE, nullable: true }", "dbType: { name: \"\", nullable: true }")
                .replace("precision: 10, scale: 0", "precision: 10, scale: 2147483648");

        List<DslError> errors = validate(yaml);

        assertThat(errors)
                .extracting(DslError::messageKey)
                .contains(
                        DslMessageKeys.SYNTAX_MINIMUM,
                        DslMessageKeys.SYNTAX_MAXIMUM,
                        DslMessageKeys.SYNTAX_MIN_ITEMS,
                        DslMessageKeys.SYNTAX_MIN_LENGTH,
                        DslMessageKeys.SYNTAX_INVALID);
        assertThat(errors)
                .filteredOn(error -> error.messageKey().equals(DslMessageKeys.SYNTAX_MINIMUM))
                .first()
                .satisfies(error -> {
                    assertThat(error.path()).isEqualTo("tables.emp_mst.columns.emp_no.list.width");
                    assertThat(error.messageArgs()).containsExactly("1", "0");
                });
        assertNoComponentText(errors);
    }

    @Test
    @DisplayName("a $ref key in the DSL is an unknown property and nothing is fetched from its URL")
    void refInDslIsUnknownAndNotFetched() {
        try (CountingHttpServer server = new CountingHttpServer()) {
            String yaml = validYaml() + "$ref: \"" + server.url("/dsl.json") + "\"\n";
            yaml = yaml.replace(
                    "        formPart: date\n",
                    "        formPart: date\n        $ref: \"" + server.url("/column.json") + "\"\n");

            List<DslError> errors = validate(yaml);

            assertThat(errors).extracting(DslError::messageKey).containsOnly(DslMessageKeys.SYNTAX_UNKNOWN_PROPERTY);
            assertThat(errors)
                    .extracting(DslError::path)
                    .containsExactlyInAnyOrder("$ref", "tables.emp_mst.columns.joined_on.$ref");
            assertThat(errors.toString()).doesNotContain(server.url(""));
            assertThat(server.requests()).isZero();
        }
    }

    @Test
    @DisplayName("a schema that refers to an external URL fails when it is built, without fetching it")
    void externalReferenceInSchemaIsNotFetched() {
        try (CountingHttpServer server = new CountingHttpServer()) {
            String schema = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                    + "\"properties\":{\"a\":{\"$ref\":\"" + server.url("/remote.json") + "\"}}}";

            assertThatThrownBy(() -> new DslSchemaValidator(schema)).isInstanceOf(RuntimeException.class);
            assertThat(server.requests()).isZero();
        }
    }

    @Test
    @DisplayName("networknt paths become the same JSON pointers as the position map")
    void pointerOfNullPath() {
        assertThat(DslSchemaValidator.pointerOf(null)).isEmpty();
    }
}
