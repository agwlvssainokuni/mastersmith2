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
package cherry.mastersmith.mail.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 送信の結果の値の型の単体テスト（BR6.1）。 */
class MailSendResultTest {

    @Test
    @DisplayName("a sent result has no failure kind")
    void sentHasNoFailureKind() {
        MailSendResult result = MailSendResult.sent();

        assertThat(result.outcome()).isEqualTo(MailOutcome.SENT);
        assertThat(result.failureKind()).isNull();
        assertThat(result.isSent()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(MailFailureKind.class)
    @DisplayName("a failed result carries its kind and prints only the outcome and the kind")
    void failedCarriesKind(MailFailureKind kind) {
        MailSendResult result = MailSendResult.failed(kind);

        assertThat(result.outcome()).isEqualTo(MailOutcome.FAILED);
        assertThat(result.failureKind()).isEqualTo(kind);
        assertThat(result.isSent()).isFalse();
        assertThat(result.toString()).isEqualTo("MailSendResult[outcome=FAILED, failureKind=" + kind + "]");
    }

    @Test
    @DisplayName("a failed result without a kind and a sent result with a kind are rejected")
    void inconsistentCombinationsAreRejected() {
        assertThatThrownBy(() -> MailSendResult.failed(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MailSendResult(MailOutcome.FAILED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailSendResult(MailOutcome.SENT, MailFailureKind.TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailSendResult(null, null)).isInstanceOf(NullPointerException.class);
    }
}
