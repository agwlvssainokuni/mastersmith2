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

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** 参照先の読み取りの失敗の値の単体テスト（T-S1、reliability-design.md 5.2、BR9.5・BR10.2）。 */
class ReferenceReadFailureTest {

    private static RowQueryFailure failure(RowQueryFailureReason reason) {
        return new RowQueryFailure(reason, RowQueryStage.EXECUTE, "57014", 1969, "org.postgresql.util.PSQLException");
    }

    @ParameterizedTest
    @CsvSource({"TIMEOUT,TIMEOUT", "UNAVAILABLE,UNAVAILABLE", "MISMATCH,MISMATCH", "READ_CONVERSION,MISMATCH"})
    @DisplayName("a row query failure maps to one of the three reasons and keeps the diagnostic values")
    void mapsTheReason(RowQueryFailureReason from, ReferenceFailureReason to) {
        ReferenceReadFailure mapped = ReferenceReadFailure.of(failure(from));

        assertThat(mapped.reason()).isEqualTo(to);
        assertThat(mapped.sqlState()).isEqualTo("57014");
        assertThat(mapped.vendorCode()).isEqualTo(1969);
        assertThat(mapped.exceptionClass()).isEqualTo("org.postgresql.util.PSQLException");
        assertThat(mapped.budgetExceeded()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = RowQueryFailureReason.class,
            names = {"INVALID_VALUE", "UNEXPECTED"})
    @DisplayName("INVALID_VALUE and UNEXPECTED are not mapped and stay unexpected")
    void refusesUnexpectedReasons(RowQueryFailureReason reason) {
        assertThatThrownBy(() -> ReferenceReadFailure.of(failure(reason))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("every row query failure reason is either mapped or refused")
    void allReasonsCovered() {
        long mapped = Arrays.stream(RowQueryFailureReason.values())
                .filter(r -> r != RowQueryFailureReason.INVALID_VALUE && r != RowQueryFailureReason.UNEXPECTED)
                .map(r -> ReferenceReadFailure.of(failure(r)))
                .count();

        assertThat(mapped).isEqualTo(RowQueryFailureReason.values().length - 2);
        assertThat(ReferenceFailureReason.values())
                .containsExactly(
                        ReferenceFailureReason.TIMEOUT,
                        ReferenceFailureReason.UNAVAILABLE,
                        ReferenceFailureReason.MISMATCH);
    }

    @Test
    @DisplayName("a budget stop is a TIMEOUT without any exception values")
    void budgetExceeded() {
        ReferenceReadFailure stop = ReferenceReadFailure.ofBudgetExceeded();

        assertThat(stop.reason()).isEqualTo(ReferenceFailureReason.TIMEOUT);
        assertThat(stop.budgetExceeded()).isTrue();
        assertThat(stop.sqlState()).isNull();
        assertThat(stop.vendorCode()).isNull();
        assertThat(stop.exceptionClass()).isNull();
    }

    @Test
    @DisplayName("an inconsistent combination of budget stop, reason and exception class is refused")
    void refusesInconsistentValues() {
        assertThatThrownBy(() -> new ReferenceReadFailure(ReferenceFailureReason.UNAVAILABLE, null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferenceReadFailure(ReferenceFailureReason.TIMEOUT, null, null, "X", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferenceReadFailure(ReferenceFailureReason.TIMEOUT, null, null, null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the text form holds only the reason and diagnostic values and no message")
    void toStringHasNoMessage() {
        String text =
                ReferenceReadFailure.of(failure(RowQueryFailureReason.TIMEOUT)).toString();

        assertThat(text)
                .contains("TIMEOUT", "57014", "1969", "PSQLException")
                .doesNotContain("message")
                .doesNotContain("canceling statement");
    }
}
