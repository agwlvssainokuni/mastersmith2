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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.SortDirection;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 値を持つ型（BoundValue・RowKey・RowValues・RowRecord・SearchCondition・入力の型）の伏せ字と不変の単体テスト（BR10.1）。 */
class ValueTypesTest {

    private static final String SECRET = "ペン山田 090-0000-0000";

    @Test
    @DisplayName("a bound value hides its value and rejects the binary type")
    void boundValue() {
        BoundValue value = new BoundValue(ValueType.STRING, SECRET);

        assertThat(value.toString()).isEqualTo("BoundValue[valueType=STRING, value=***]");
        assertThat(new BoundValue(ValueType.INTEGER, null).toString()).contains("value=null");
        assertThatThrownBy(() -> new BoundValue(ValueType.BINARY, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundValue(null, "x")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a row key keeps the key order, cannot be changed and hides names and values")
    void rowKey() {
        Map<String, BoundValue> source = new LinkedHashMap<>();
        source.put("b", new BoundValue(ValueType.STRING, SECRET));
        source.put("a", new BoundValue(ValueType.INTEGER, 1L));
        RowKey key = new RowKey(source);
        source.clear();

        assertThat(key.values().keySet()).containsExactly("b", "a");
        assertThat(key.toString()).isEqualTo("RowKey[columns=2]");
        assertThatThrownBy(() -> key.values().put("c", null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("row values keep null apart from an empty string and hide the values")
    void rowValues() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("name", SECRET);
        source.put("memo", null);
        source.put("note", "");
        RowValues values = new RowValues(source, Set.of("name"));

        assertThat(values.values()).containsEntry("memo", null).containsEntry("note", "");
        assertThat(values.toString())
                .isEqualTo("RowValues[columns=3, truncated=1]")
                .doesNotContain("ペン");
        assertThat(new RowRecord(values, values).toString())
                .doesNotContain("ペン")
                .doesNotContain("name");
        assertThat(new RowRecord(null, values).toString()).contains("key=none");
        assertThatThrownBy(() -> values.truncatedColumns().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a search condition hides its values and its shape must match the operator")
    void searchCondition() {
        BoundValue text = new BoundValue(ValueType.STRING, SECRET);

        assertThat(SearchCondition.equalTo("name", text).toString())
                .isEqualTo("SearchCondition[column=name, mode=EQUALS, values=1]");
        assertThat(SearchCondition.range(
                                "price", ValueType.DECIMAL, null, new BoundValue(ValueType.DECIMAL, BigDecimal.TEN))
                        .toString())
                .contains("values=1");
        assertThatThrownBy(() -> SearchCondition.contains("price", new BoundValue(ValueType.DECIMAL, BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchCondition.choice("kind", ValueType.STRING, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchCondition.range("price", ValueType.DECIMAL, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the raw inputs hide their values in the string form")
    void rawInputs() {
        Map<String, String> key = new HashMap<>();
        key.put("code", SECRET);

        assertThat(new RawCondition("name", SECRET, null, null, null).toString())
                .isEqualTo("RawCondition[column=name, values=1]");
        assertThat(new SearchInput(List.of(new RawCondition("name", SECRET, null, null, null)), null, null, null)
                        .toString())
                .doesNotContain("ペン");
        assertThat(new KeyInput(key).toString()).isEqualTo("KeyInput[columns=1]");
        assertThat(new KeyInput(null).values()).isEmpty();
    }

    @Test
    @DisplayName("the definition id checks the hash and tells a changed definition apart")
    void definitionId() {
        DefinitionId current = BusinessDataDslFixture.DEFINITION;

        assertThat(current.changedFrom(null)).isFalse();
        assertThat(current.changedFrom(new RequestedDefinitionId(BusinessDataDslFixture.HASH, 7L)))
                .isFalse();
        assertThat(current.changedFrom(new RequestedDefinitionId(BusinessDataDslFixture.HASH, 8L)))
                .isTrue();
        assertThat(current.changedFrom(new RequestedDefinitionId(BusinessDataDslFixture.OTHER_HASH, 7L)))
                .isTrue();
        assertThat(current.changedFrom(new RequestedDefinitionId("ABC", 7L)))
                .as("形の正しくない値も違うとする")
                .isTrue();
        assertThat(current.changedFrom(new RequestedDefinitionId(null, null))).isTrue();
        assertThat(new RequestedDefinitionId(BusinessDataDslFixture.HASH, 7L).toString())
                .doesNotContain("0123");
        assertThatThrownBy(() -> new DefinitionId("ABC", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefinitionId(BusinessDataDslFixture.HASH.toUpperCase(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the page request keeps its range and computes the offset without overflow")
    void pageRequest() {
        assertThat(new PageRequest(PageRequest.MAX_PAGE, 100, null).offset()).isEqualTo(99_999_900L);
        assertThat(new PageRequest(1, 20, new SortKey("a", SortDirection.DESC)).sortKey())
                .isPresent();
        assertThatThrownBy(() -> new PageRequest(0, 20, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageRequest(PageRequest.MAX_PAGE + 1, 20, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageRequest(1, 10, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the failure keeps no message and the rule and kind types check their arguments")
    void failureAndRules() {
        RowQueryFailure failure =
                new RowQueryFailure(RowQueryFailureReason.TIMEOUT, RowQueryStage.EXECUTE, "57014", 0, "x.Y");

        assertThat(List.of(RowQueryFailure.class.getRecordComponents()))
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("reason", "stage", "sqlState", "vendorCode", "exceptionClass");
        assertThat(failure.sqlState()).isEqualTo("57014");
        assertThatThrownBy(() -> new SearchRule(SearchMode.EQUALS, SearchOperatorPolicy.BOOLEAN_CHOICES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchRule(SearchMode.CHOICE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ColumnKind(null, true)).isInstanceOf(NullPointerException.class);
        assertThat(ValueType.TIME_TZ.rangeComparable()).isTrue();
        assertThat(ValueType.OTHER.rangeComparable()).isFalse();
    }
}
