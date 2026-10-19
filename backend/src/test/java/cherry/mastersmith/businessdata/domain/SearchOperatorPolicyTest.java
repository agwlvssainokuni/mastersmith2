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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.fixed;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.kind;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.reference;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SearchSetting;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 検索の演算子と型の表（BR4.6・BR4.9・BR4.10）の単体テスト。 */
class SearchOperatorPolicyTest {

    private static SearchSetting on(SearchOperator operator) {
        return new SearchSetting(true, operator, null, false);
    }

    @Test
    @DisplayName("IN is read as CHOICE and a fixed choice list is kept without duplicates")
    void inIsChoice() {
        assertThat(SearchOperatorPolicy.rule(
                        on(SearchOperator.IN), fixed("a", "b", "a"), kind(ValueType.STRING), false))
                .map(SearchRule::choices)
                .contains(List.of("a", "b"));
        assertThat(SearchOperatorPolicy.rule(
                        on(SearchOperator.CHOICE), fixed("1", "2"), kind(ValueType.INTEGER), false))
                .map(SearchRule::choices)
                .contains(List.of("1", "2"));
    }

    @Test
    @DisplayName("a boolean column without choices offers true and false")
    void booleanChoices() {
        assertThat(SearchOperatorPolicy.rule(on(SearchOperator.CHOICE), null, kind(ValueType.BOOLEAN), false))
                .map(SearchRule::choices)
                .contains(List.of("true", "false"));
    }

    @Test
    @DisplayName("CHOICE and IN with reference choices or without choices on a non-boolean type become EQUALS")
    void choiceBecomesEquals() {
        assertThat(SearchOperatorPolicy.rule(on(SearchOperator.CHOICE), reference(), kind(ValueType.INTEGER), false))
                .map(SearchRule::mode)
                .contains(SearchMode.EQUALS);
        assertThat(SearchOperatorPolicy.rule(on(SearchOperator.IN), null, kind(ValueType.STRING), false))
                .map(SearchRule::mode)
                .contains(SearchMode.EQUALS);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource({
        "EQUALS, STRING, EQUALS",
        "EQUALS, DATETIME_TZ, EQUALS",
        "EQUALS, OTHER, EQUALS",
        "CONTAINS, STRING, CONTAINS",
        "CONTAINS, INTEGER, NONE",
        "CONTAINS, DATE, NONE",
        "CONTAINS, OTHER, NONE",
        "RANGE, INTEGER, RANGE",
        "RANGE, BIG_INTEGER, RANGE",
        "RANGE, DECIMAL, RANGE",
        "RANGE, TIME_TZ, RANGE",
        "RANGE, STRING, NONE",
        "RANGE, BOOLEAN, NONE",
        "RANGE, OTHER, NONE",
        "EQUALS, BINARY, NONE",
        "CHOICE, BINARY, NONE"
    })
    @DisplayName("the operator and value type pairs follow the BR4.9 table")
    void theTable(SearchOperator operator, ValueType type, String expected) {
        var rule = SearchOperatorPolicy.rule(on(operator), null, kind(type), false);

        if (expected.equals("NONE")) {
            assertThat(rule).isEmpty();
        } else {
            assertThat(rule).map(SearchRule::mode).contains(SearchMode.valueOf(expected));
        }
    }

    @Test
    @DisplayName("password columns are never searchable")
    void passwordIsNeverSearchable() {
        for (SearchOperator operator : SearchOperator.values()) {
            assertThat(SearchOperatorPolicy.rule(on(operator), fixed("x"), kind(ValueType.STRING), true))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("OTHER types without equality (BR7.9) are left out of EQUALS and CHOICE")
    void otherWithoutEquality() {
        ColumnKind json = new ColumnKind(ValueType.OTHER, false);

        assertThat(SearchOperatorPolicy.rule(on(SearchOperator.EQUALS), null, json, false))
                .isEmpty();
        assertThat(SearchOperatorPolicy.rule(on(SearchOperator.CHOICE), fixed("a"), json, false))
                .isEmpty();
    }

    @Test
    @DisplayName("a column without an enabled search setting is not a search field")
    void disabledSearch() {
        assertThat(SearchOperatorPolicy.rule(
                        new SearchSetting(false, null, null, false), null, kind(ValueType.STRING), false))
                .isEmpty();
        assertThat(SearchOperatorPolicy.rule(null, null, kind(ValueType.STRING), false))
                .isEmpty();
    }
}
