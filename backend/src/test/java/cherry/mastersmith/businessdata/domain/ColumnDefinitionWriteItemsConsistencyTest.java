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
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.OptionItem;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.role.domain.MainPermission;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 定義の API の検証の材料と、サーバーの検証の境界が同じことの単体テスト（Intent 261016-business-data-views の U2、BR14.2）。同じ DSL
 * から作った定義の項目の値をそのまま境界に使い、サーバーの検証が同じ所で分かれることを確かめる。
 */
class ColumnDefinitionWriteItemsConsistencyTest {

    private static final DslTable TABLE = BusinessDataDslFixture.table(
            "item",
            false,
            List.of("id"),
            column("id").dbType("int4").build(),
            column("name")
                    .dbType("varchar")
                    .length(40)
                    .validation(ValidationType.MIN_LENGTH, "3")
                    .validation(ValidationType.MAX_LENGTH, "12")
                    .build(),
            column("price")
                    .dbType("numeric")
                    .decimal(6, 2)
                    .validation(ValidationType.MIN, "-10")
                    .validation(ValidationType.MAX, "500")
                    .build(),
            column("kind").form(FormPart.RADIO).options(fixed("x", "y")).build(),
            column("must")
                    .validation(ValidationType.REQUIRED, ValidationOrigin.MANUAL)
                    .build());

    private static final TableAccessView VIEW = TableAccessPolicy.evaluate(BusinessDataDslFixture.input(
            TABLE,
            MainPermission.FULL,
            Map.of(
                    "id", MainPermission.FULL,
                    "name", MainPermission.FULL,
                    "price", MainPermission.FULL,
                    "kind", MainPermission.FULL,
                    "must", MainPermission.FULL),
            Map.of("id", new ColumnKind(ValueType.INTEGER, true), "price", new ColumnKind(ValueType.DECIMAL, true)),
            true,
            true,
            true));

    private static ColumnValidation item(String column) {
        return VIEW.column(column).orElseThrow().validation();
    }

    private static Map<String, String> single(String column, String value) {
        Map<String, String> values = new HashMap<>();
        values.put(column, value);
        return values;
    }

    private static boolean accepted(String column, String value) {
        return WriteRequestValidator.validate(
                        VIEW,
                        WriteOperation.UPDATE,
                        new KeyInput(Map.of("id", "1")),
                        new WriteValues(single(column, value)),
                        new ColumnValueCodec(),
                        new DeadlinePatternMatcher(Clock.systemUTC()))
                instanceof WriteValidation.Valid;
    }

    @Test
    @DisplayName("the length items are exactly where the server starts to reject")
    void lengths() {
        ColumnValidation name = item("name");

        assertThat(accepted("name", "あ".repeat(name.maxLength()))).isTrue();
        assertThat(accepted("name", "あ".repeat(name.maxLength() + 1))).isFalse();
        assertThat(accepted("name", "あ".repeat(name.minLength()))).isTrue();
        assertThat(accepted("name", "あ".repeat(name.minLength() - 1))).isFalse();
    }

    @Test
    @DisplayName("the digit and range items are exactly where the server starts to reject")
    void digitsAndRange() {
        ColumnValidation price = item("price");
        BigDecimal max = new BigDecimal(price.max());
        BigDecimal min = new BigDecimal(price.min());
        BigDecimal step = BigDecimal.ONE.movePointLeft(price.scale());

        assertThat(accepted("price", max.toPlainString())).isTrue();
        assertThat(accepted("price", max.add(step).toPlainString())).isFalse();
        assertThat(accepted("price", min.toPlainString())).isTrue();
        assertThat(accepted("price", min.subtract(step).toPlainString())).isFalse();
        assertThat(accepted("price", "1." + "1".repeat(price.scale()))).isTrue();
        assertThat(accepted("price", "1." + "1".repeat(price.scale() + 1))).isFalse();
        assertThat(price.precision()).isEqualTo(6);
    }

    @Test
    @DisplayName("the choices and required items match what the server accepts")
    void choicesAndRequired() {
        for (OptionItem choice : item("kind").choices()) {
            assertThat(accepted("kind", choice.value())).as(choice.value()).isTrue();
        }
        assertThat(accepted("kind", "z")).isFalse();
        assertThat(item("must").required()).isTrue();
        assertThat(accepted("must", "")).isFalse();
        assertThat(item("kind").required()).isFalse();
        assertThat(accepted("kind", null)).as("必須でないカラムは空を受け付ける").isTrue();
    }
}
