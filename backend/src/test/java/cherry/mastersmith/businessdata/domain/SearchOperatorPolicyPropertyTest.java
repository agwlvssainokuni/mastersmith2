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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SearchSetting;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** 検索の演算子と型の表の性質ベースのテスト（jqwik。BR4.9・BR4.10、team.md の「性質ベースのテスト」）。 */
class SearchOperatorPolicyPropertyTest {

    @Provide
    Arbitrary<OptionSource> options() {
        return Arbitraries.of(
                        BusinessDataDslFixture.fixed("A", "B"),
                        BusinessDataDslFixture.reference(),
                        BusinessDataDslFixture.fixed("1"))
                .injectNull(0.5);
    }

    @Property
    @Label("a rule that is produced is always in the table, and password or binary columns never get one")
    void rulesAreInTheTable(
            @ForAll SearchOperator operator,
            @ForAll("options") OptionSource options,
            @ForAll ValueType type,
            @ForAll boolean equalsSupported,
            @ForAll boolean masked) {
        Optional<SearchRule> rule = SearchOperatorPolicy.rule(
                new SearchSetting(true, operator, null, false), options, new ColumnKind(type, equalsSupported), masked);

        if (masked || type == ValueType.BINARY) {
            assertThat(rule).isEmpty();
        }
        rule.ifPresent(r -> {
            assertThat(SearchOperatorPolicy.allowed(r.mode(), type, equalsSupported))
                    .isTrue();
            if (r.mode() == SearchMode.CONTAINS) {
                assertThat(type).isEqualTo(ValueType.STRING);
            }
            if (r.mode() == SearchMode.RANGE) {
                assertThat(type.rangeComparable()).isTrue();
            }
            if (type == ValueType.OTHER) {
                assertThat(equalsSupported).isTrue();
            }
            assertThat(r.choices()).doesNotHaveDuplicates();
        });
    }
}
