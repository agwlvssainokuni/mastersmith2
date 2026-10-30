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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 参照先の検索 API の本文の形の確かめ（Intent 261029-reference-lookup-cleanup の U1、BR1.4、NFR1.4）の単体テスト。 */
class ReferenceRowsRequestValidatorTest {

    private static RawCondition condition(String column, String value) {
        return new RawCondition(column, value, null, null, null);
    }

    private static Optional<ReferenceRowsQuery> search(List<RawCondition> conditions, BigDecimal page) {
        return ReferenceRowsRequestValidator.validate("s", "t", "c", conditions, page, null);
    }

    private static Optional<ReferenceRowsQuery> label(String value) {
        return ReferenceRowsRequestValidator.validate("s", "t", "c", null, null, value);
    }

    @Test
    @DisplayName("a search with one non-empty condition and page 1 is read as SEARCH with offset 0")
    void validSearch() {
        Optional<ReferenceRowsQuery> query =
                search(List.of(condition("name", "山田"), condition("code", "")), BigDecimal.ONE);

        assertThat(query).hasValueSatisfying(q -> {
            assertThat(q.mode()).isEqualTo(ReferenceRowsQuery.Mode.SEARCH);
            assertThat(q.conditions())
                    .extracting(ReferenceCondition::column, ReferenceCondition::isEmpty)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("name", false),
                            org.assertj.core.groups.Tuple.tuple("code", true));
            assertThat(q.page()).isEqualTo(1L);
            assertThat(q.offset()).isZero();
            assertThat(q.value()).isNull();
        });
    }

    @Test
    @DisplayName("a label read keeps the value and ignores the page")
    void validLabel() {
        Optional<ReferenceRowsQuery> query =
                ReferenceRowsRequestValidator.validate("s", "t", "c", null, BigDecimal.valueOf(-5), "42");

        assertThat(query).hasValueSatisfying(q -> {
            assertThat(q.mode()).isEqualTo(ReferenceRowsQuery.Mode.VALUE);
            assertThat(q.value()).isEqualTo(new ReferenceValue("42"));
            assertThat(q.page()).isNull();
            assertThat(q.conditions()).isEmpty();
        });
    }

    @Test
    @DisplayName("missing schema, table or column is a format error")
    void missingNames() {
        List<RawCondition> one = List.of(condition("name", "x"));

        assertThat(ReferenceRowsRequestValidator.validate(null, "t", "c", one, BigDecimal.ONE, null))
                .isEmpty();
        assertThat(ReferenceRowsRequestValidator.validate("s", null, "c", one, BigDecimal.ONE, null))
                .isEmpty();
        assertThat(ReferenceRowsRequestValidator.validate("s", "t", null, null, null, "x"))
                .isEmpty();
    }

    @Test
    @DisplayName("both conditions and value, or neither, is a format error")
    void bothOrNeither() {
        assertThat(ReferenceRowsRequestValidator.validate(
                        "s", "t", "c", List.of(condition("name", "x")), BigDecimal.ONE, "x"))
                .isEmpty();
        assertThat(ReferenceRowsRequestValidator.validate("s", "t", "c", null, BigDecimal.ONE, null))
                .isEmpty();
    }

    @Test
    @DisplayName("an empty list, only empty values, a null element, a missing column or a repeated column is an error")
    void conditionShapes() {
        assertThat(search(List.of(), BigDecimal.ONE)).isEmpty();
        assertThat(search(List.of(condition("name", ""), condition("code", null)), BigDecimal.ONE))
                .isEmpty();
        assertThat(search(Arrays.asList(condition("name", "x"), null), BigDecimal.ONE))
                .isEmpty();
        assertThat(search(List.of(condition(null, "x")), BigDecimal.ONE)).isEmpty();
        assertThat(search(List.of(condition("name", "x"), condition("name", "")), BigDecimal.ONE))
                .isEmpty();
    }

    @Test
    @DisplayName("values of 1,000 code points are accepted and 1,001 are rejected, also with supplementary characters")
    void valueLength() {
        String supplementary = "𠮷";
        assertThat(search(List.of(condition("name", "あ".repeat(1_000))), BigDecimal.ONE))
                .isPresent();
        assertThat(search(List.of(condition("name", "あ".repeat(1_001))), BigDecimal.ONE))
                .isEmpty();
        assertThat(search(List.of(condition("name", supplementary.repeat(1_000))), BigDecimal.ONE))
                .as("UTF-16 では 2,000 文字でもコードポイントは 1,000")
                .isPresent();
        assertThat(search(List.of(condition("name", supplementary.repeat(1_001))), BigDecimal.ONE))
                .isEmpty();
        assertThat(label("1".repeat(1_000))).isPresent();
        assertThat(label("1".repeat(1_001))).isEmpty();
        assertThat(search(List.of(condition("name", "x"), condition("code", "あ".repeat(1_001))), BigDecimal.ONE))
                .as("空でない条件があっても長すぎる値は誤り")
                .isEmpty();
    }

    @ParameterizedTest(name = "page {0}")
    @ValueSource(strings = {"1", "1000000", "1.0", "2E+1"})
    @DisplayName("pages 1 to 1,000,000 written as integers are accepted")
    void acceptedPages(String page) {
        assertThat(search(List.of(condition("name", "x")), new BigDecimal(page)))
                .isPresent();
    }

    @ParameterizedTest(name = "page {0}")
    @ValueSource(strings = {"0", "-1", "1000001", "1.5", "1E+7"})
    @DisplayName("pages out of 1 to 1,000,000 or with a fraction are rejected")
    void rejectedPages(String page) {
        assertThat(search(List.of(condition("name", "x")), new BigDecimal(page)))
                .isEmpty();
    }

    @Test
    @DisplayName("a search without a page is rejected")
    void missingPage() {
        assertThat(search(List.of(condition("name", "x")), null)).isEmpty();
    }

    @Test
    @DisplayName("the offset of the last page fits in an int")
    void lastPageOffset() {
        assertThat(search(List.of(condition("name", "x")), BigDecimal.valueOf(1_000_000)))
                .hasValueSatisfying(q -> assertThat(q.offset()).isEqualTo(19_999_980));
    }

    @Test
    @DisplayName("the query and its parts hide values and keep names and counts in their text")
    void textHidesValues() {
        String secret = "社外秘の値";
        ReferenceRowsQuery search = search(
                        new ArrayList<>(List.of(condition("name", secret), condition("code", ""))), BigDecimal.TWO)
                .orElseThrow();
        ReferenceRowsQuery label = label(secret).orElseThrow();
        ReferenceRow row = new ReferenceRow(secret, secret, Arrays.asList(secret, null));
        ReferenceRows rows = ReferenceRows.search(List.of(row), 2, true);

        for (Object text : List.of(search, label, row, rows, new ReferenceValue(secret), search.conditions())) {
            assertThat(text.toString()).doesNotContain(secret);
        }
        assertThat(search.toString()).contains("column=c", "conditions=2", "page=2");
        assertThat(search.conditions().get(1).toString()).contains("empty");
        assertThat(row.toString()).contains("cells=2");
        assertThat(rows.toString()).contains("rows=1", "hasNext=true");
        assertThat(new ReferenceRow("v", null, null).toString()).contains("label=null", "cells=0");
    }

    @Test
    @DisplayName("the result types reject inconsistent combinations")
    void inconsistentCombinations() {
        ReferenceRow row = new ReferenceRow("v", "l", List.of());
        assertThat(ReferenceRows.label(row).page()).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ReferenceRows(List.of(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ReferenceRows(java.util.Collections.nCopies(21, row), 1L, true))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ReferenceRowsQuery(
                        "s", "t", "c", ReferenceRowsQuery.Mode.VALUE, List.of(), 1L, new ReferenceValue("x")))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> ReferenceRowsQuery.search("s", "t", "c", List.of(new ReferenceCondition("a", "")), 1))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ReferenceCheckResult.Failed(null))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ReferenceCountState.Failed(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the failed results carry the failure value and print no message")
    void failedResults() {
        ReferenceReadFailure failure = ReferenceReadFailure.ofBudgetExceeded();

        assertThat(new ReferenceCheckResult.Failed(failure).failure().reason())
                .isEqualTo(ReferenceFailureReason.TIMEOUT);
        assertThat(new ReferenceCountState.Failed(failure).failure().budgetExceeded())
                .isTrue();
        assertThat(new ReferenceCheckResult.Found()).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(new ReferenceCountState.TooMany()).isNotEqualTo(new ReferenceCountState.Available());
    }
}
