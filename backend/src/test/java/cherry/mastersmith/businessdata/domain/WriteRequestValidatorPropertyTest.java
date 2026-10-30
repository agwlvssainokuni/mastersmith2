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
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.role.domain.MainPermission;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/** 書き込みの要求の確かめの性質ベースのテスト（jqwik。Intent 261016-business-data-views の U2、NFR7.6）。 */
class WriteRequestValidatorPropertyTest {

    private static WriteValidation update(TableAccessView view, String column, String value) {
        return WriteRequestValidator.validate(
                view,
                WriteOperation.UPDATE,
                new KeyInput(Map.of("id", "1")),
                new WriteValues(Map.of(column, value)),
                new ColumnValueCodec(),
                new DeadlinePatternMatcher(Clock.systemUTC()));
    }

    @Property
    @Label("a string inside the length limits is accepted and one outside is rejected")
    void lengthLimits(@ForAll @IntRange(min = 1, max = 40) int max, @ForAll @StringLength(max = 50) String value) {
        DslTable table = BusinessDataDslFixture.table(
                "t",
                false,
                List.of("id"),
                column("id").dbType("int4").build(),
                column("s")
                        .dbType("text")
                        .validation(ValidationType.MAX_LENGTH, Integer.toString(max))
                        .build());
        TableAccessView view = BusinessDataDslFixture.view(
                table, MainPermission.FULL, Map.of("id", new ColumnKind(ValueType.INTEGER, true)));

        boolean inside = value.codePointCount(0, value.length()) <= max;

        assertThat(update(view, "s", value) instanceof WriteValidation.Valid).isEqualTo(inside);
    }

    @Property
    @Label("a decimal inside the digits and the range is accepted and one outside is rejected")
    void decimalDigits(
            @ForAll @IntRange(min = 1, max = 8) int precision,
            @ForAll @IntRange(min = 0, max = 4) int scaleRaw,
            @ForAll @IntRange(min = 0, max = 99_999) int unscaled,
            @ForAll @IntRange(min = 0, max = 6) int valueScale) {
        int scale = Math.min(scaleRaw, precision);
        DslTable table = BusinessDataDslFixture.table(
                "t",
                false,
                List.of("id"),
                column("id").dbType("int4").build(),
                column("d").dbType("numeric").decimal(precision, scale).build());
        TableAccessView view = BusinessDataDslFixture.view(
                table,
                MainPermission.FULL,
                Map.of(
                        "id", new ColumnKind(ValueType.INTEGER, true),
                        "d", new ColumnKind(ValueType.DECIMAL, true)));
        BigDecimal value = BigDecimal.valueOf(unscaled, valueScale);
        BigDecimal stripped = value.stripTrailingZeros();
        boolean inside = Math.max(0, stripped.scale()) <= scale
                && Math.max(0, stripped.precision() - stripped.scale()) <= precision - scale;

        assertThat(update(view, "d", value.toPlainString()) instanceof WriteValidation.Valid)
                .isEqualTo(inside);
    }

    @Property
    @Label("a column that cannot be written is always rejected as NOT_ALLOWED whatever the value")
    void notWritableAlwaysRejected(@ForAll @StringLength(max = 30) String value) {
        DslTable table = BusinessDataDslFixture.table(
                "t",
                false,
                List.of("id"),
                column("id").dbType("int4").build(),
                column("r").build());
        TableAccessView view = TableAccessPolicy.evaluate(BusinessDataDslFixture.input(
                table,
                MainPermission.FULL,
                Map.of("id", MainPermission.FULL, "r", MainPermission.READ),
                Map.of("id", new ColumnKind(ValueType.INTEGER, true)),
                true,
                true,
                true));

        assertThat(update(view, "r", value))
                .isEqualTo(new WriteValidation.Invalid(
                        List.of(new BusinessDataFieldError("r", BusinessDataFieldErrorReason.NOT_ALLOWED))));
    }

    @Provide
    Arbitrary<String> heldTexts() {
        return Arbitraries.of("", "abc", "1", "x", "xy", "-3").injectNull(0.2);
    }

    @Property
    @Label("holding moves only the errors of the held column and never changes the errors of other columns")
    void holdMovesOnlyHeldColumn(@ForAll("heldTexts") String qty, @ForAll("heldTexts") String memo) {
        DslTable table = BusinessDataDslFixture.table(
                "t",
                false,
                List.of("id"),
                column("id").dbType("int4").build(),
                column("qty").dbType("int4").nullable(false).build(),
                column("memo")
                        .dbType("varchar")
                        .validation(ValidationType.MIN_LENGTH, "2")
                        .build());
        TableAccessView view = TableAccessPolicy.evaluate(BusinessDataDslFixture.input(
                table,
                MainPermission.FULL,
                Map.of("id", MainPermission.FULL, "qty", MainPermission.FULL, "memo", MainPermission.FULL),
                Map.of(
                        "id", new ColumnKind(ValueType.INTEGER, true),
                        "qty", new ColumnKind(ValueType.INTEGER, true)),
                true,
                true,
                true));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("qty", qty);
        values.put("memo", memo);

        WriteValidation plain = create(view, values, WriteRequestValidator.Hold.NONE);
        WriteValidation held = create(view, values, new WriteRequestValidator.Hold(Set.of("qty"), Set.of()));

        assertThat(errorsBut(held, "qty")).isEqualTo(errorsBut(plain, "qty"));
        assertThat(errorsOf(plain, "qty")).containsAll(errorsOf(held, "qty"));
    }

    private static WriteValidation create(
            TableAccessView view, Map<String, String> values, WriteRequestValidator.Hold hold) {
        return WriteRequestValidator.validate(
                view,
                WriteOperation.CREATE,
                null,
                new WriteValues(values),
                new ColumnValueCodec(),
                new DeadlinePatternMatcher(Clock.systemUTC()),
                hold);
    }

    private static List<BusinessDataFieldError> all(WriteValidation validation) {
        return validation instanceof WriteValidation.Invalid invalid ? invalid.errors() : List.of();
    }

    private static List<BusinessDataFieldError> errorsBut(WriteValidation validation, String column) {
        return all(validation).stream().filter(e -> !e.field().equals(column)).toList();
    }

    private static List<BusinessDataFieldError> errorsOf(WriteValidation validation, String column) {
        return all(validation).stream().filter(e -> e.field().equals(column)).toList();
    }
}
