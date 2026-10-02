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
package cherry.mastersmith.common.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 管理の一覧のページ送りの計算の単体テスト（BR1.1〜BR1.5、性質ベースのテストを含む）。
 *
 * <p>性質ベースのテスト（jqwik）が失敗したときの再現の仕方: 失敗の報告に出る {@code seed} を、そのテストの
 * {@code @Property(tries = 500, seed = "<出た seed>")} に一時的に与えて流す（原因を直した後に外し、指定を残してコミットしない）。
 * 失敗した例の記録は {@code build/jqwik-database} に置かれ、リポジトリに残らない（{@code junit-platform.properties}）。
 * Gradle のテストの出力は失敗の詳細をすべて出す設定（{@code exceptionFormat = FULL}）のため、CI の記録にも種が残る。
 */
class PagingTest {

    @ParameterizedTest
    @CsvSource({"1, 1", "20, 1", "21, 2", "40, 2", "41, 3"})
    @DisplayName("the page of a position is the position divided by 20 rounded up")
    void pageOfPosition(long position, int page) {
        assertThat(Paging.pageOf(position)).isEqualTo(page);
    }

    @Test
    @DisplayName("a missing page is 1 and positive integers are accepted")
    void parsesPage() {
        assertThat(Paging.parsePage(null)).hasValue(1);
        assertThat(Paging.parsePage("1")).hasValue(1);
        assertThat(Paging.parsePage("3")).hasValue(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "abc", "", " 1", "+1", "9999999999"})
    @DisplayName("zero, negative, non-integer, empty and oversized pages are rejected")
    void rejectsPage(String raw) {
        assertThat(Paging.parsePage(raw)).isEmpty();
    }

    @Test
    @DisplayName("the offset of a page starts at zero and moves by 20")
    void offsets() {
        assertThat(Paging.offsetOf(1)).isZero();
        assertThat(Paging.offsetOf(2)).isEqualTo(20);
        assertThat(Paging.offsetOf(Integer.MAX_VALUE)).isEqualTo((Integer.MAX_VALUE - 1L) * 20);
    }

    @Test
    @DisplayName("positions and pages below 1 are unexpected errors")
    void rejectsBelowOne() {
        assertThatThrownBy(() -> Paging.pageOf(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Paging.offsetOf(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the page size is 20, the same value as PAGE_SIZE of the screen")
    void pageSizeIsTwenty() {
        // 画面の frontend/src/shared/paging/paging.ts の PAGE_SIZE も 20 で固定している（片方だけの変更をここで止める）。
        assertThat(Paging.PAGE_SIZE).isEqualTo(20);
    }

    @Property(tries = 500)
    @Label("the page of a position equals (p-1)/20+1 and the position falls within that page")
    void pageContainsPosition(@ForAll @LongRange(min = 1, max = 1_000_000) long position) {
        int page = Paging.pageOf(position);
        long offset = Paging.offsetOf(page);

        assertThat(page).isEqualTo((position - 1) / 20 + 1);
        assertThat(position - 1).isGreaterThanOrEqualTo(offset).isLessThan(offset + Paging.PAGE_SIZE);
    }

    @Property(tries = 500)
    @Label("every integer from 1 to 999,999,999 written in decimal is accepted as that page")
    void acceptsDecimalPages(@ForAll @IntRange(min = 1, max = 999_999_999) int n) {
        assertThat(Paging.parsePage(Integer.toString(n))).hasValue(n);
    }

    @Property(tries = 500)
    @Label("strings with a non-digit, digit strings of 10 or more characters, and zero are rejected")
    void rejectsMalformedPages(@ForAll("malformedPages") String raw) {
        assertThat(Paging.parsePage(raw)).isEmpty();
    }

    @Property(tries = 500)
    @Label("consecutive offsets differ by 20 and the page of the first position of a page is that page")
    void offsetsAndPagesRoundTrip(@ForAll @IntRange(min = 1, max = Integer.MAX_VALUE - 1) int page) {
        assertThat(Paging.offsetOf(page + 1) - Paging.offsetOf(page)).isEqualTo(Paging.PAGE_SIZE);
        assertThat(Paging.pageOf(Paging.offsetOf(page) + 1)).isEqualTo(page);
    }

    /** 数字の列に数字以外の文字を差し込んだ列、10 文字以上の数字だけの列、値が 0 の数字の列（"0"・"00" など）。 */
    @Provide
    Arbitrary<String> malformedPages() {
        Arbitrary<String> digits = Arbitraries.strings().numeric().ofMaxLength(8);
        Arbitrary<Character> nonDigit = Arbitraries.chars().filter(c -> c < '0' || c > '9');
        Arbitrary<String> withNonDigit =
                Combinators.combine(digits, nonDigit, digits).as((before, c, after) -> before + c + after);
        Arbitrary<String> tooLong =
                Arbitraries.strings().numeric().ofMinLength(10).ofMaxLength(30);
        Arbitrary<String> zeros =
                Arbitraries.strings().withChars('0').ofMinLength(1).ofMaxLength(9);
        return Arbitraries.oneOf(withNonDigit, tooLong, zeros);
    }
}
