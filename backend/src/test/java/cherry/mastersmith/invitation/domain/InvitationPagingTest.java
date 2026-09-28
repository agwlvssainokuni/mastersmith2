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
package cherry.mastersmith.invitation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 一覧のページの計算の単体テスト（BR2.3・BR5.2、NFR9.7、性質ベースのテストを含む）。 */
class InvitationPagingTest {

    @ParameterizedTest
    @CsvSource({"1, 1", "20, 1", "21, 2", "40, 2", "41, 3"})
    @DisplayName("the page of a position is the position divided by 20 rounded up")
    void pageOfPosition(long position, int page) {
        assertThat(InvitationPaging.pageOf(position)).isEqualTo(page);
    }

    @Test
    @DisplayName("a missing page is 1 and positive integers are accepted")
    void parsesPage() {
        assertThat(InvitationPaging.parsePage(null)).hasValue(1);
        assertThat(InvitationPaging.parsePage("1")).hasValue(1);
        assertThat(InvitationPaging.parsePage("3")).hasValue(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "abc", "", " 1", "+1", "9999999999"})
    @DisplayName("zero, negative, non-integer, empty and oversized pages are rejected")
    void rejectsPage(String raw) {
        assertThat(InvitationPaging.parsePage(raw)).isEmpty();
    }

    @Test
    @DisplayName("the offset of a page starts at zero and moves by 20")
    void offsets() {
        assertThat(InvitationPaging.offsetOf(1)).isZero();
        assertThat(InvitationPaging.offsetOf(2)).isEqualTo(20);
        assertThat(InvitationPaging.offsetOf(Integer.MAX_VALUE)).isEqualTo((Integer.MAX_VALUE - 1L) * 20);
    }

    @Test
    @DisplayName("positions and pages below 1 are unexpected errors")
    void rejectsBelowOne() {
        assertThatThrownBy(() -> InvitationPaging.pageOf(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvitationPaging.offsetOf(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Property(tries = 500)
    @Label("the page of a position equals (p-1)/20+1 and the position falls within that page")
    void pageContainsPosition(@ForAll @LongRange(min = 1, max = 1_000_000) long position) {
        int page = InvitationPaging.pageOf(position);
        long offset = InvitationPaging.offsetOf(page);

        assertThat(page).isEqualTo((position - 1) / 20 + 1);
        assertThat(position - 1).isGreaterThanOrEqualTo(offset).isLessThan(offset + InvitationPaging.PAGE_SIZE);
    }
}
