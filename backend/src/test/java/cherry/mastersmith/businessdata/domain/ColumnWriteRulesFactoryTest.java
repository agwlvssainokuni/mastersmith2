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
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.reference;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.OptionItem;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 書き込みの検証の材料を作る関数の単体テスト（Intent 261016-business-data-views の U2、BR3.6・BR14.2、NFR4.9）。 */
class ColumnWriteRulesFactoryTest {

    private static final ColumnKind STRING = new ColumnKind(ValueType.STRING, true);

    @Test
    @DisplayName("string columns get the length limits and the pattern, and no numeric items")
    void stringItems() {
        ColumnWriteRules rules = ColumnWriteRulesFactory.create(
                column("c")
                        .dbType("varchar")
                        .length(50)
                        .validation(ValidationType.MIN_LENGTH, "2")
                        .validation(ValidationType.MAX_LENGTH, "30")
                        .validation(ValidationType.MIN, "1")
                        .pattern("[a-z]+", null)
                        .build(),
                STRING);

        assertThat(rules.minLength()).isEqualTo(2);
        assertThat(rules.maxLength()).as("DSL と DB の長さの小さい方").isEqualTo(30);
        assertThat(rules.pattern()).isEqualTo("[a-z]+");
        assertThat(rules.min()).as("文字の型に min は当てない").isNull();
        assertThat(rules.precision()).isNull();
        assertThat(rules.toString()).doesNotContain("[a-z]+");
    }

    @Test
    @DisplayName("the maximum length is the smallest of the DSL limit, the DB length and 10,000")
    void maxLengthIsTheSmallest() {
        assertThat(ColumnWriteRulesFactory.create(column("c").dbType("text").build(), STRING)
                        .maxLength())
                .isEqualTo(10_000);
        assertThat(ColumnWriteRulesFactory.create(
                                column("c").dbType("varchar").length(20).build(), STRING)
                        .maxLength())
                .isEqualTo(20);
        assertThat(ColumnWriteRulesFactory.create(
                                column("c")
                                        .dbType("text")
                                        .validation(ValidationType.MAX_LENGTH, "20000")
                                        .build(),
                                STRING)
                        .maxLength())
                .isEqualTo(10_000);
    }

    @Test
    @DisplayName("numbers get min and max, decimals get precision and scale, and strings items stay empty")
    void numericItems() {
        ColumnWriteRules decimal = ColumnWriteRulesFactory.create(
                column("p")
                        .dbType("numeric")
                        .decimal(10, 2)
                        .validation(ValidationType.MIN, "0")
                        .validation(ValidationType.MAX, "99.5")
                        .validation(ValidationType.MAX_LENGTH, "3")
                        .build(),
                new ColumnKind(ValueType.DECIMAL, true));
        ColumnWriteRules integer = ColumnWriteRulesFactory.create(
                column("i").dbType("int4").decimal(32, 0).build(), new ColumnKind(ValueType.INTEGER, true));

        assertThat(decimal.precision()).isEqualTo(10);
        assertThat(decimal.scale()).isEqualTo(2);
        assertThat(decimal.min()).isEqualTo("0");
        assertThat(decimal.max()).isEqualTo("99.5");
        assertThat(decimal.maxLength()).as("数の型に maxLength は当てない").isNull();
        assertThat(integer.precision()).as("precision・scale は DECIMAL だけ").isNull();
    }

    @Test
    @DisplayName("date and time types get the fractional digits of the kind and no min or max")
    void temporalItems() {
        ColumnWriteRules rules = ColumnWriteRulesFactory.create(
                column("t")
                        .dbType("timestamptz")
                        .validation(ValidationType.MIN, "1")
                        .build(),
                new ColumnKind(ValueType.DATETIME_TZ, true, 6));
        ColumnWriteRules date = ColumnWriteRulesFactory.create(
                column("d").dbType("date").build(), new ColumnKind(ValueType.DATE, true, 3));

        assertThat(rules.fractionDigits()).isEqualTo(6);
        assertThat(rules.min()).as("DSL の min は数で、日時には当てない").isNull();
        assertThat(date.fractionDigits()).as("日付は秒を持たない").isNull();
    }

    @Test
    @DisplayName("lookup becomes text, fixed select and radio keep choices, and references have none")
    void formPartsAndChoices() {
        assertThat(ColumnWriteRulesFactory.create(
                                column("l").form(FormPart.LOOKUP).build(), STRING)
                        .formPart())
                .isEqualTo(FormPart.TEXT);
        assertThat(ColumnWriteRulesFactory.create(
                                column("s")
                                        .form(FormPart.SELECT)
                                        .options(fixed("a", "b"))
                                        .build(),
                                STRING)
                        .choices())
                .extracting(OptionItem::value)
                .containsExactly("a", "b");
        assertThat(ColumnWriteRulesFactory.create(
                                column("r")
                                        .form(FormPart.SELECT)
                                        .options(reference())
                                        .build(),
                                STRING)
                        .choices())
                .isEmpty();
        assertThat(ColumnWriteRulesFactory.create(
                                column("t")
                                        .form(FormPart.TEXT)
                                        .options(fixed("a"))
                                        .build(),
                                STRING)
                        .choices())
                .as("text の部品は選択肢と照らさない")
                .isEmpty();
    }

    @Test
    @DisplayName("required, its origin, unique, nullable and masked are taken for every type")
    void flagsForEveryType() {
        ColumnWriteRules rules = ColumnWriteRulesFactory.create(
                column("pw")
                        .form(FormPart.PASSWORD)
                        .nullable(false)
                        .validation(ValidationType.REQUIRED, ValidationOrigin.DB)
                        .validation(ValidationType.UNIQUE, ValidationOrigin.MANUAL)
                        .build(),
                new ColumnKind(ValueType.INTEGER, true));

        assertThat(rules.required()).isTrue();
        assertThat(rules.requiredOrigin()).isEqualTo(ValidationOrigin.DB);
        assertThat(rules.unique()).isTrue();
        assertThat(rules.nullable()).isFalse();
        assertThat(rules.masked()).isTrue();
        assertThat(ColumnWriteRulesFactory.create(column("x").build(), STRING).requiredOrigin())
                .isNull();
    }

    @Test
    @DisplayName("the definition items copy the rules for writable columns and fall back for others")
    void validationCopy() {
        ColumnWriteRules rules = ColumnWriteRulesFactory.create(
                column("s")
                        .form(FormPart.SELECT)
                        .options(fixed("a"))
                        .nullable(false)
                        .validation(ValidationType.REQUIRED, ValidationOrigin.MANUAL)
                        .pattern("a", null)
                        .build(),
                STRING);

        ColumnValidation writable = ColumnValidation.from(rules, true);
        ColumnValidation readOnly = ColumnValidation.from(rules, false);

        assertThat(writable.required()).isTrue();
        assertThat(writable.hasPattern()).isTrue();
        assertThat(writable.choices()).hasSize(1);
        assertThat(writable.maxLength()).isEqualTo(10_000);
        assertThat(readOnly.formPart()).isEqualTo(FormPart.SELECT);
        assertThat(readOnly.required()).isFalse();
        assertThat(readOnly.nullable()).as("DB の値のまま").isFalse();
        assertThat(readOnly.choices()).isEmpty();
        assertThat(readOnly.hasPattern()).isFalse();
        assertThat(readOnly.maxLength()).isNull();
    }
}
