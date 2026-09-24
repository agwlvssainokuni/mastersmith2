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
package cherry.mastersmith.dsl.service;

import static cherry.mastersmith.dsl.testsupport.DslSamples.lineOf;
import static cherry.mastersmith.dsl.testsupport.DslSamples.validYaml;
import static cherry.mastersmith.dsl.testsupport.DslSamples.validYamlReplacing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.ListFormat;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.dsl.parse.SafeYamlParser;
import cherry.mastersmith.dsl.validate.DslSchemaValidator;
import cherry.mastersmith.dsl.validate.DslSemanticValidator;
import cherry.mastersmith.dsl.validate.PatternChecker;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 読み込みの口の単体テスト（BR1.1・BR2.1・BR2.3・BR5.1・BR5.2、NFR2.1、AC2.2.5・AC2.2.7・AC2.2.8・AC2.3.1・AC2.3.8）。 */
class DefaultDslReaderTest {

    private static final PatternChecker PATTERNS = new PatternChecker();

    static final DslReader READER =
            new DefaultDslReader(new SafeYamlParser(), new DslSchemaValidator(), new DslSemanticValidator(PATTERNS));

    @AfterAll
    static void close() {
        PATTERNS.close();
    }

    static DslReadResult read(String yaml) {
        return READER.read(yaml.getBytes(StandardCharsets.UTF_8));
    }

    static DslModel model(String yaml) {
        DslReadResult result = read(yaml);
        assertThat(result).isInstanceOf(DslReadResult.Valid.class);
        return ((DslReadResult.Valid) result).model();
    }

    static List<DslError> errors(String yaml) {
        DslReadResult result = read(yaml);
        assertThat(result).isInstanceOf(DslReadResult.Invalid.class);
        List<DslError> errors = ((DslReadResult.Invalid) result).errors();
        for (DslError error : errors) {
            assertThat(DslMessageKeys.all()).contains(error.messageKey());
            assertThat(String.join(" ", error.messageArgs()))
                    .doesNotContain("Exception", "snakeyaml", "networknt", "must be", "$.");
        }
        return errors;
    }

    /** 見本の後ろにコメントの行を足して、ちょうど size バイトにする。 */
    private static byte[] paddedTo(int size) {
        byte[] sample = validYaml().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[size];
        System.arraycopy(sample, 0, bytes, 0, sample.length);
        int position = sample.length;
        while (position < size) {
            int lineLength = Math.min(1000, size - position);
            bytes[position] = '#';
            Arrays.fill(bytes, position + 1, position + lineLength, (byte) 'x');
            bytes[position + lineLength - 1] = '\n';
            position += lineLength;
        }
        return bytes;
    }

    @Test
    @DisplayName("the valid sample becomes a model with the same content as the DSL")
    void validSampleBecomesModel() {
        String yaml = validYaml();
        DslReadResult result = read(yaml);

        assertThat(result).isInstanceOf(DslReadResult.Valid.class);
        DslReadResult.Valid valid = (DslReadResult.Valid) result;
        DslModel model = valid.model();
        assertThat(valid.dslHash()).isEqualTo(READER.hash(yaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(model.formatVersion()).isEqualTo(1);
        assertThat(model.menus()).hasSize(2);
        assertThat(model.menus().getFirst().icon()).isEqualTo("folder");
        assertThat(model.menus().getFirst().items().getFirst().items())
                .extracting(item -> item.table())
                .containsExactly("dept_mst", "emp_mst");
        assertThat(model.menus().get(1).label().ja()).isEqualTo("社員の一覧");
        assertThat(model.tables().keySet()).containsExactly("dept_mst", "emp_mst", "emp_view");
        assertThat(model.tables().get("emp_view").view()).isTrue();

        DslTable dept = model.tables().get("dept_mst");
        assertThat(dept.primaryKey()).containsExactly("dept_code");
        assertThat(dept.columns().keySet()).containsExactly("dept_code", "dept_name", "kind");
        DslColumn code = dept.columns().get("dept_code");
        assertThat(code.dbType().length()).isEqualTo(10);
        assertThat(code.dbType().precision()).isNull();
        assertThat(code.search().operator()).isEqualTo(SearchOperator.EQUALS);
        assertThat(code.list().defaultSort()).isEqualTo(SortDirection.ASC);
        assertThat(code.options()).isNull();
        assertThat(code.validations())
                .extracting(v -> v.type(), v -> v.number(), v -> v.text(), v -> v.origin())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ValidationType.REQUIRED, null, null, ValidationOrigin.DB),
                        org.assertj.core.groups.Tuple.tuple(
                                ValidationType.MAX_LENGTH, BigDecimal.TEN, null, ValidationOrigin.DB),
                        org.assertj.core.groups.Tuple.tuple(
                                ValidationType.PATTERN, null, "^[A-Z0-9]+$", ValidationOrigin.MANUAL));
        assertThat(code.validations().get(2).message().en()).isEqualTo("Upper case and digits");
        assertThat(dept.columns().get("kind").options().items())
                .extracting(item -> item.value())
                .containsExactly("1", "2");

        DslTable emp = model.tables().get("emp_mst");
        assertThat(emp.foreignKeys().getFirst().referencedTable()).isEqualTo("dept_mst");
        assertThat(emp.columns().get("emp_no").list().format()).isEqualTo(ListFormat.NUMBER_GROUPED);
        assertThat(emp.columns().get("emp_no").list().width()).isEqualTo(120);
        assertThat(emp.columns().get("dept_code").options().source()).isEqualTo(OptionSourceKind.REFERENCE);
        assertThat(emp.columns().get("dept_code").options().labelColumn()).isEqualTo("dept_name");
        DslColumn boss = emp.columns().get("boss_dept");
        assertThat(boss.formPart()).isEqualTo(FormPart.LOOKUP);
        assertThat(boss.options().lookupList()).extracting(item -> item.order()).containsExactly(1, 2);
        assertThat(boss.options().lookupSearch().getFirst().operator()).isEqualTo(SearchOperator.CONTAINS);
        assertThat(emp.columns().get("joined_on").search().defaultValue()).isEqualTo("2026-04-01");
        assertThat(emp.columns().get("joined_on").label().en()).isEmpty();
    }

    @Test
    @DisplayName("exactly 10MB is not stopped by the size limit and one more byte is SIZE_LIMIT without position")
    void sizeLimitBoundary() {
        DslReadResult atLimit = READER.read(paddedTo(DslFormat.MAX_BYTES));
        assertThat(atLimit).isInstanceOf(DslReadResult.Valid.class);

        DslReadResult overLimit = READER.read(paddedTo(DslFormat.MAX_BYTES + 1));
        assertThat(overLimit).isInstanceOf(DslReadResult.Invalid.class);
        List<DslError> errors = ((DslReadResult.Invalid) overLimit).errors();
        assertThat(errors).singleElement().satisfies(error -> {
            assertThat(error.kind()).isEqualTo(DslErrorKind.SIZE_LIMIT);
            assertThat(error.messageKey()).isEqualTo(DslMessageKeys.SIZE_LIMIT);
            assertThat(error.messageArgs()).containsExactly("10485760");
            assertThat(error.line()).isNull();
            assertThat(error.path()).isNull();
        });
    }

    @Test
    @DisplayName(
            "the hash is the same for the same bytes, different for one different byte, and 64 lower case hex digits")
    void hashOfBytes() {
        byte[] bytes = validYaml().getBytes(StandardCharsets.UTF_8);
        byte[] changed = bytes.clone();
        changed[changed.length - 1] = ' ';

        assertThat(READER.hash(bytes)).isEqualTo(READER.hash(bytes.clone())).matches("[0-9a-f]{64}");
        assertThat(READER.hash(changed)).isNotEqualTo(READER.hash(bytes));
        assertThat(READER.hash(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("a missing version is the only error, without syntax or semantic checks")
    void missingVersion() {
        String yaml = validYamlReplacing("version: 1\n", "").replace("    table: emp_view", "    table: no_such")
                + "unknown: 1\n";

        assertThat(errors(yaml)).singleElement().satisfies(error -> {
            assertThat(error.kind()).isEqualTo(DslErrorKind.UNSUPPORTED_VERSION);
            assertThat(error.messageKey()).isEqualTo(DslMessageKeys.VERSION_MISSING);
            assertThat(error.path()).isEqualTo("version");
            assertThat(error.line()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "\"1\"", "1.0", "123456789012345678901234567890", "[1]", "{v: 1}"})
    @DisplayName("an unsupported version is the only error, located at the version")
    void unsupportedVersion(String version) {
        String yaml = validYamlReplacing("version: 1\n", "version: " + version + "\n") + "unknown: 1\n";

        assertThat(errors(yaml)).singleElement().satisfies(error -> {
            assertThat(error.kind()).isEqualTo(DslErrorKind.UNSUPPORTED_VERSION);
            assertThat(error.messageKey()).isEqualTo(DslMessageKeys.VERSION_UNSUPPORTED);
            assertThat(error.messageArgs()).hasSize(2).last().isEqualTo("1");
            assertThat(error.line()).isEqualTo(lineOf(yaml, "version: "));
            assertThat(error.column()).isEqualTo(1);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "# only a comment\n", "- 1\n- 2\n", "just text\n", "version: null\n"})
    @DisplayName("an empty body or a body without a version mapping is a single UNSUPPORTED_VERSION error")
    void emptyBody(String yaml) {
        assertThat(errors(yaml)).singleElement().satisfies(error -> {
            assertThat(error.kind()).isEqualTo(DslErrorKind.UNSUPPORTED_VERSION);
            assertThat(error.messageKey()).isEqualTo(DslMessageKeys.VERSION_MISSING);
        });
    }

    @Test
    @DisplayName("a DSL with both syntax and semantic errors returns only the syntax errors")
    void syntaxBeforeSemantic() {
        String yaml = validYamlReplacing("    table: emp_view", "    table: no_such_table")
                .replace("width: 120", "width: 0");

        assertThat(errors(yaml)).singleElement().satisfies(error -> {
            assertThat(error.kind()).isEqualTo(DslErrorKind.SYNTAX);
            assertThat(error.messageKey()).isEqualTo(DslMessageKeys.SYNTAX_MINIMUM);
        });
    }

    @Test
    @DisplayName("a reading error stops before the version, syntax and semantic checks")
    void readingErrorStopsEarly() {
        String yaml = validYamlReplacing("version: 1\n", "version: 2\nversion: 3\n") + "unknown: 1\n";

        assertThat(errors(yaml)).singleElement().satisfies(error -> {
            assertThat(error.kind()).isEqualTo(DslErrorKind.DUPLICATE_KEY);
            assertThat(error.path()).isEqualTo("version");
        });
        assertThat(errors(validYaml() + "extra: !!java.io.File /tmp\n"))
                .singleElement()
                .extracting(DslError::kind)
                .isEqualTo(DslErrorKind.FORBIDDEN_TAG);
    }

    @Test
    @DisplayName("semantic errors are returned when the syntax is valid")
    void semanticErrors() {
        List<DslError> errors = errors(validYamlReplacing("    table: emp_view", "    table: no_such_table"));

        assertThat(errors).singleElement().extracting(DslError::kind).isEqualTo(DslErrorKind.SEMANTIC);
    }

    @Test
    @DisplayName("a null body is a programming error")
    void nullBodyIsProgrammingError() {
        assertThatThrownBy(() -> READER.read(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> READER.hash(null)).isInstanceOf(NullPointerException.class);
    }
}
