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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.fixed;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.businessdata.testsupport.SteppingClock;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.role.domain.MainPermission;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 書き込みの要求の確かめの単体テスト（Intent 261016-business-data-views の U2、BR2・BR3・BR4、NFR4.9）。値はすべて架空。 */
class WriteRequestValidatorTest {

    private static final cherry.mastersmith.dsl.domain.DisplayName CODE_MESSAGE =
            new cherry.mastersmith.dsl.domain.DisplayName("英大文字3つ", "Three capital letters");

    private static final DslTable ITEM = table(
            "item",
            false,
            List.of("id"),
            column("id").dbType("int4").build(),
            column("name")
                    .dbType("varchar")
                    .length(20)
                    .validation(ValidationType.REQUIRED, ValidationOrigin.DB)
                    .validation(ValidationType.MAX_LENGTH, "10")
                    .build(),
            column("memo")
                    .dbType("varchar")
                    .validation(ValidationType.MIN_LENGTH, "2")
                    .build(),
            column("text").dbType("text").build(),
            column("code").dbType("varchar").pattern("[A-Z]{3}", CODE_MESSAGE).build(),
            column("price")
                    .dbType("numeric")
                    .decimal(5, 2)
                    .validation(ValidationType.MIN, "0")
                    .validation(ValidationType.MAX, "999.99")
                    .build(),
            column("qty").dbType("int4").build(),
            column("big").dbType("int8").build(),
            column("flag").dbType("bool").form(FormPart.CHECKBOX).build(),
            column("kind")
                    .dbType("varchar")
                    .form(FormPart.SELECT)
                    .options(fixed("a", "b"))
                    .build(),
            column("born").dbType("date").build(),
            column("at").dbType("timestamp").build(),
            column("atz").dbType("timestamptz").build(),
            column("strict").dbType("varchar").nullable(false).build(),
            column("ro").dbType("varchar").build(),
            column("manual")
                    .dbType("varchar")
                    .validation(ValidationType.REQUIRED, ValidationOrigin.MANUAL)
                    .build());

    private static final ColumnValueCodec CODEC = new ColumnValueCodec();

    private static TableAccessView view() {
        Map<String, MainPermission> main = new HashMap<>();
        ITEM.columns().keySet().forEach(name -> main.put(name, MainPermission.FULL));
        main.put("ro", MainPermission.READ);
        Map<String, ColumnKind> kinds = new HashMap<>();
        kinds.put("id", new ColumnKind(ValueType.INTEGER, true));
        kinds.put("price", new ColumnKind(ValueType.DECIMAL, true));
        kinds.put("qty", new ColumnKind(ValueType.INTEGER, true));
        kinds.put("big", new ColumnKind(ValueType.BIG_INTEGER, true));
        kinds.put("flag", new ColumnKind(ValueType.BOOLEAN, true));
        kinds.put("born", new ColumnKind(ValueType.DATE, true));
        kinds.put("at", new ColumnKind(ValueType.DATETIME, true, 0));
        kinds.put("atz", new ColumnKind(ValueType.DATETIME_TZ, true, 6));
        return TableAccessPolicy.evaluate(
                BusinessDataDslFixture.input(ITEM, MainPermission.FULL, main, kinds, true, true, true));
    }

    private static WriteValidation validate(
            WriteOperation operation, Map<String, String> key, Map<String, String> values) {
        return validate(operation, key, values, Clock.systemUTC());
    }

    private static WriteValidation validate(
            WriteOperation operation, Map<String, String> key, Map<String, String> values, Clock clock) {
        return WriteRequestValidator.validate(
                view(),
                operation,
                new KeyInput(key),
                new WriteValues(values),
                CODEC,
                new DeadlinePatternMatcher(clock));
    }

    private static Map<String, String> required(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "見本");
        values.put("manual", "x");
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    private static Map<String, String> one(String column, String value) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(column, value);
        return values;
    }

    private static List<BusinessDataFieldError> errors(WriteValidation validation) {
        assertThat(validation).isInstanceOf(WriteValidation.Invalid.class);
        return ((WriteValidation.Invalid) validation).errors();
    }

    private static BusinessDataFieldErrorReason updateError(String column, String value) {
        return errors(validate(WriteOperation.UPDATE, Map.of("id", "1"), one(column, value)))
                .getFirst()
                .reason();
    }

    private static TypedValue updateValue(String column, String value) {
        WriteValidation validation = validate(WriteOperation.UPDATE, Map.of("id", "1"), one(column, value));
        assertThat(validation).isInstanceOf(WriteValidation.Valid.class);
        return ((WriteValidation.Valid) validation).values().getFirst();
    }

    @Test
    @DisplayName("a valid create returns typed values in the sent order and no key")
    void validCreate() {
        WriteValidation validation = validate(
                WriteOperation.CREATE,
                Map.of(),
                required(
                        "price",
                        "12.30",
                        "qty",
                        "7",
                        "big",
                        "9223372036854775807",
                        "flag",
                        "true",
                        "kind",
                        "a",
                        "born",
                        "2026-02-28",
                        "at",
                        "2026-03-01T10:00:00",
                        "atz",
                        "2026-03-01T10:00:00+09:00"));

        assertThat(validation).isInstanceOfSatisfying(WriteValidation.Valid.class, valid -> {
            assertThat(valid.key()).isNull();
            assertThat(valid.values())
                    .extracting(TypedValue::column)
                    .containsExactly("name", "manual", "price", "qty", "big", "flag", "kind", "born", "at", "atz");
            Map<String, Object> raw = new HashMap<>();
            valid.values().forEach(v -> raw.put(v.column(), v.value().value()));
            assertThat(raw)
                    .containsEntry("price", new BigDecimal("12.30"))
                    .containsEntry("qty", 7L)
                    .containsEntry("big", new BigDecimal("9223372036854775807"))
                    .containsEntry("flag", Boolean.TRUE)
                    .containsEntry("born", LocalDate.of(2026, 2, 28))
                    .containsEntry("at", LocalDateTime.of(2026, 3, 1, 10, 0))
                    .containsEntry("atz", OffsetDateTime.of(2026, 3, 1, 1, 0, 0, 0, ZoneOffset.UTC));
        });
    }

    @Test
    @DisplayName("required columns that are missing, empty or null are REQUIRED on create, including DB origin")
    void requiredOnCreate() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("manual", "");

        assertThat(errors(validate(WriteOperation.CREATE, Map.of(), values)))
                .containsExactlyInAnyOrder(
                        new BusinessDataFieldError("manual", BusinessDataFieldErrorReason.REQUIRED),
                        new BusinessDataFieldError("name", BusinessDataFieldErrorReason.REQUIRED));
        assertThat(updateError("name", null)).isEqualTo(BusinessDataFieldErrorReason.REQUIRED);
    }

    @Test
    @DisplayName("columns that cannot be written are NOT_ALLOWED with the same reason and the key cannot be updated")
    void notAllowed() {
        Map<String, String> values = required("ro", "x", "unknown", "x", "id", "2");

        assertThat(errors(validate(WriteOperation.CREATE, Map.of(), values)))
                .containsExactly(
                        new BusinessDataFieldError("ro", BusinessDataFieldErrorReason.NOT_ALLOWED),
                        new BusinessDataFieldError("unknown", BusinessDataFieldErrorReason.NOT_ALLOWED));
        assertThat(updateError("id", "2")).isEqualTo(BusinessDataFieldErrorReason.NOT_ALLOWED);
    }

    @Test
    @DisplayName("string length counts code points; the DSL limit and the 10,000 cap accept the boundary")
    void stringLength() {
        assertThat(updateValue("name", "あ".repeat(10)).value().value()).isEqualTo("あ".repeat(10));
        assertThat(updateError("name", "あ".repeat(11))).isEqualTo(BusinessDataFieldErrorReason.TOO_LONG);
        assertThat(updateValue("name", "😀".repeat(10)).column())
                .as("サロゲートペアは1文字")
                .isEqualTo("name");
        assertThat(updateValue("text", "x".repeat(10_000)).column()).isEqualTo("text");
        assertThat(updateError("text", "x".repeat(10_001))).isEqualTo(BusinessDataFieldErrorReason.TOO_LONG);
        assertThat(updateError("memo", "x")).isEqualTo(BusinessDataFieldErrorReason.TOO_SHORT);
        assertThat(updateValue("memo", "").value().value()).as("空の文字には下限を当てない").isEqualTo("");
    }

    @Test
    @DisplayName("decimal digits and DSL ranges are checked without rounding")
    void decimalDigitsAndRange() {
        assertThat(updateValue("price", "999.99").value().value()).isEqualTo(new BigDecimal("999.99"));
        assertThat(updateValue("price", "1.230").column()).as("末尾の 0 は桁に数えない").isEqualTo("price");
        assertThat(updateError("price", "1.234")).isEqualTo(BusinessDataFieldErrorReason.TOO_MANY_DIGITS);
        assertThat(updateError("price", "1000.00")).isEqualTo(BusinessDataFieldErrorReason.TOO_MANY_DIGITS);
        assertThat(updateError("price", "1234.5")).isEqualTo(BusinessDataFieldErrorReason.TOO_MANY_DIGITS);
        assertThat(updateError("price", "-0.01")).isEqualTo(BusinessDataFieldErrorReason.OUT_OF_RANGE);
        assertThat(updateError("price", "1e3")).isEqualTo(BusinessDataFieldErrorReason.INVALID_VALUE);
    }

    @Test
    @DisplayName("integers outside the type range are OUT_OF_RANGE and the BIGINT limits are accepted")
    void integerRange() {
        assertThat(updateValue("qty", "2147483647").value().value()).isEqualTo(2147483647L);
        assertThat(updateError("qty", "9999999999")).isEqualTo(BusinessDataFieldErrorReason.OUT_OF_RANGE);
        assertThat(updateValue("big", "-9223372036854775808").value().value())
                .isEqualTo(new BigDecimal("-9223372036854775808"));
        assertThat(updateError("big", "99999999999999999999")).isEqualTo(BusinessDataFieldErrorReason.OUT_OF_RANGE);
        assertThat(updateError("qty", "1.5")).isEqualTo(BusinessDataFieldErrorReason.INVALID_VALUE);
        assertThat(updateError("qty", "")).as("文字の型でない空の文字").isEqualTo(BusinessDataFieldErrorReason.INVALID_VALUE);
    }

    @Test
    @DisplayName("choices, booleans and real dates are checked")
    void choicesBooleansAndDates() {
        assertThat(updateError("kind", "c")).isEqualTo(BusinessDataFieldErrorReason.NOT_A_CHOICE);
        assertThat(updateError("flag", "yes")).isEqualTo(BusinessDataFieldErrorReason.INVALID_VALUE);
        assertThat(updateValue("flag", "false").value().value()).isEqualTo(Boolean.FALSE);
        assertThat(updateError("born", "2026-02-30")).isEqualTo(BusinessDataFieldErrorReason.INVALID_VALUE);
    }

    @Test
    @DisplayName("fractional seconds are accepted up to the precision of the type and rejected one digit beyond")
    void fractionalSeconds() {
        assertThat(updateValue("at", "2026-03-01T10:00:00").column()).isEqualTo("at");
        assertThat(updateError("at", "2026-03-01T10:00:00.1")).isEqualTo(BusinessDataFieldErrorReason.TOO_MANY_DIGITS);
        assertThat(updateValue("atz", "2026-03-01T10:00:00.123456Z").column()).isEqualTo("atz");
        assertThat(updateError("atz", "2026-03-01T10:00:00.1234567Z"))
                .isEqualTo(BusinessDataFieldErrorReason.TOO_MANY_DIGITS);
    }

    @Test
    @DisplayName("time zone values without an offset, with Z and with an offset are the same instant in UTC")
    void timeZoneForms() {
        Object plain = updateValue("atz", "2026-03-01T01:00:00").value().value();
        Object zulu = updateValue("atz", "2026-03-01T01:00:00Z").value().value();
        Object tokyo = updateValue("atz", "2026-03-01T10:00:00+09:00").value().value();

        assertThat(plain).isEqualTo(zulu).isEqualTo(tokyo);
        assertThat(((OffsetDateTime) plain).getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("null is written to nullable columns and NOT_NULL otherwise")
    void nulls() {
        assertThat(updateValue("text", null).value().value()).isNull();
        assertThat(updateValue("qty", null).value().value()).isNull();
        assertThat(updateError("strict", null)).isEqualTo(BusinessDataFieldErrorReason.NOT_NULL);
        assertThat(updateValue("strict", "").value().value())
                .as("空の文字は NULL ではない")
                .isEqualTo("");
    }

    @Test
    @DisplayName("an update without values is NO_CHANGE and the key must match the primary key exactly")
    void updateKeyAndNoChange() {
        assertThat(errors(validate(WriteOperation.UPDATE, Map.of("id", "1"), Map.of())))
                .containsExactly(new BusinessDataFieldError("", BusinessDataFieldErrorReason.NO_CHANGE));
        assertThat(errors(validate(WriteOperation.UPDATE, Map.of("other", "1"), one("qty", "1"))))
                .containsExactly(
                        new BusinessDataFieldError("id", BusinessDataFieldErrorReason.KEY_MISMATCH),
                        new BusinessDataFieldError("other", BusinessDataFieldErrorReason.KEY_MISMATCH));
        assertThat(validate(WriteOperation.DELETE, Map.of("id", "1"), one("qty", "x")))
                .as("削除は値を見ない")
                .isInstanceOfSatisfying(WriteValidation.Valid.class, valid -> {
                    assertThat(valid.values()).isEmpty();
                    assertThat(valid.key().values()).containsOnlyKeys("id");
                });
    }

    @Test
    @DisplayName("pattern mismatches carry the DSL message and a heavy pattern ends with PATTERN_TIMEOUT")
    void patterns() {
        assertThat(updateValue("code", "ABC").column()).isEqualTo("code");
        assertThat(errors(validate(WriteOperation.UPDATE, Map.of("id", "1"), one("code", "ABCD"))))
                .containsExactly(new BusinessDataFieldError(
                        "code", BusinessDataFieldErrorReason.PATTERN_MISMATCH, CODE_MESSAGE));
        assertThat(updateValue("code", "").column()).as("空の文字には当てない").isEqualTo("code");

        DslTable heavy = table(
                "heavy",
                false,
                List.of("id"),
                column("id").dbType("int4").build(),
                column("v").dbType("varchar").pattern("((a+)+)+c", null).build());
        TableAccessView view = BusinessDataDslFixture.view(heavy, MainPermission.FULL, Map.of());
        WriteValidation timeout = WriteRequestValidator.validate(
                view,
                WriteOperation.UPDATE,
                new KeyInput(Map.of("id", "1")),
                new WriteValues(one("v", "a".repeat(30) + "!")),
                CODEC,
                new DeadlinePatternMatcher(new SteppingClock(0, 1)));
        assertThat(errors(timeout))
                .containsExactly(new BusinessDataFieldError("v", BusinessDataFieldErrorReason.PATTERN_TIMEOUT));
    }

    @Test
    @DisplayName("only the first error of each column is returned and errors of all columns are collected")
    void firstErrorPerColumn() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "x".repeat(11));
        values.put("qty", "abc");
        values.put("kind", "z");

        assertThat(errors(validate(WriteOperation.UPDATE, Map.of("id", "1"), values)))
                .containsExactly(
                        new BusinessDataFieldError("name", BusinessDataFieldErrorReason.TOO_LONG),
                        new BusinessDataFieldError("qty", BusinessDataFieldErrorReason.INVALID_VALUE),
                        new BusinessDataFieldError("kind", BusinessDataFieldErrorReason.NOT_A_CHOICE));
    }

    @Test
    @DisplayName("primary key values accept 1,000 characters and reject 1,001 as INVALID_VALUE")
    void keyLength() {
        DslTable keyed = table(
                "keyed",
                false,
                List.of("k"),
                column("k").dbType("varchar").build(),
                column("v").dbType("varchar").build());
        TableAccessView view = BusinessDataDslFixture.view(keyed, MainPermission.FULL, Map.of());

        assertThat(WriteRequestValidator.validate(
                        view,
                        WriteOperation.DELETE,
                        new KeyInput(Map.of("k", "あ".repeat(1_000))),
                        new WriteValues(null),
                        CODEC,
                        new DeadlinePatternMatcher(Clock.systemUTC())))
                .isInstanceOf(WriteValidation.Valid.class);
        assertThat(errors(WriteRequestValidator.validate(
                        view,
                        WriteOperation.DELETE,
                        new KeyInput(Map.of("k", "あ".repeat(1_001))),
                        new WriteValues(null),
                        CODEC,
                        new DeadlinePatternMatcher(Clock.systemUTC()))))
                .containsExactly(new BusinessDataFieldError("k", BusinessDataFieldErrorReason.INVALID_VALUE));
    }
}
