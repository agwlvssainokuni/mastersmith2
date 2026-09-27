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
package cherry.mastersmith.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** パスワードの変更の出来事（BR7.2・BR7.4、契約 C8）の単体テスト。 */
class PasswordChangedEventTest {

    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.5", "Mozilla/5.0", "trace-0005");

    @Test
    @DisplayName("a success carries no failure reason and copies the origin")
    void success() {
        PasswordChangedEvent event = PasswordChangedEvent.succeeded(7, NOW, ORIGIN);

        assertThat(event.userId()).isEqualTo(7);
        assertThat(event.result()).isEqualTo(PasswordChangeOutcome.SUCCESS);
        assertThat(event.failureReason()).isNull();
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.sourceIp()).isEqualTo("192.0.2.5");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.traceId()).isEqualTo("trace-0005");
    }

    @Test
    @DisplayName("a failure carries the current-password mismatch reason")
    void failure() {
        PasswordChangedEvent event =
                PasswordChangedEvent.failed(7, PasswordChangeFailureReason.CURRENT_PASSWORD_MISMATCH, NOW, ORIGIN);

        assertThat(event.result()).isEqualTo(PasswordChangeOutcome.FAILURE);
        assertThat(event.failureReason()).isEqualTo(PasswordChangeFailureReason.CURRENT_PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("a success with a reason, a failure without a reason and missing required values are refused")
    void invalidCombinations() {
        assertThatThrownBy(() -> new PasswordChangedEvent(
                        7,
                        PasswordChangeOutcome.SUCCESS,
                        PasswordChangeFailureReason.CURRENT_PASSWORD_MISMATCH,
                        NOW,
                        "192.0.2.5",
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PasswordChangedEvent(7, PasswordChangeOutcome.FAILURE, null, NOW, "192.0.2.5", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordChangedEvent(7, null, null, NOW, "192.0.2.5", null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new PasswordChangedEvent(7, PasswordChangeOutcome.SUCCESS, null, null, "192.0.2.5", null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new PasswordChangedEvent(7, PasswordChangeOutcome.SUCCESS, null, NOW, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PasswordChangedEvent.failed(7, null, NOW, ORIGIN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the event carries no password, hash or email address, even in its string form")
    void noSecret() {
        assertThat(Arrays.stream(PasswordChangedEvent.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .map(name -> name.toLowerCase(Locale.ROOT)))
                .noneMatch(name -> name.contains("password") || name.contains("hash") || name.contains("email"));
        assertThat(PasswordChangedEvent.succeeded(7, NOW, ORIGIN).toString())
                .doesNotContain("$2a$")
                .doesNotContain("@");
    }
}
