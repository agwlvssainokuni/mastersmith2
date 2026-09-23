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
package cherry.mastersmith.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 監査イベントの形（BR2.3、BR4.1、NFR3.1、NFR3.2）のテスト。 */
class AuditEventTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-22T01:02:03Z");

    private static final String EMAIL = "user@example.com";

    private static AuditEvent loginFailed() {
        return new AuditEvent(
                OCCURRED_AT,
                AuditEventType.LOGIN_FAILED,
                AuditResult.FAILURE,
                EMAIL,
                AuditFailureReason.PASSWORD_MISMATCH,
                "192.0.2.10",
                "Mozilla/5.0",
                null,
                "trace-0001");
    }

    @Test
    @DisplayName("every recorded field is available after construction")
    void everyFieldIsAvailable() {
        AuditEvent event = loginFailed();

        assertThat(event.getAuditEventId()).isNull();
        assertThat(event.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.getEventType()).isEqualTo(AuditEventType.LOGIN_FAILED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(event.getEnteredEmail()).isEqualTo(EMAIL);
        assertThat(event.getFailureReason()).isEqualTo(AuditFailureReason.PASSWORD_MISMATCH);
        assertThat(event.getSourceIp()).isEqualTo("192.0.2.10");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.getRequestPath()).isNull();
        assertThat(event.getTraceId()).isEqualTo("trace-0001");
    }

    @Test
    @DisplayName("the optional fields may be empty")
    void optionalFieldsMayBeEmpty() {
        AuditEvent event = new AuditEvent(
                OCCURRED_AT,
                AuditEventType.LOGIN_SUCCEEDED,
                AuditResult.SUCCESS,
                null,
                null,
                "192.0.2.10",
                null,
                null,
                null);

        assertThat(event.getEnteredEmail()).isNull();
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getUserAgent()).isNull();
        assertThat(event.getRequestPath()).isNull();
        assertThat(event.getTraceId()).isNull();
    }

    @Test
    @DisplayName("the required fields are rejected when missing")
    void requiredFieldsAreRejected() {
        assertThatThrownBy(() -> new AuditEvent(
                        null,
                        AuditEventType.LOGGED_OUT,
                        AuditResult.SUCCESS,
                        EMAIL,
                        null,
                        "192.0.2.10",
                        null,
                        null,
                        null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditEvent(
                        OCCURRED_AT, null, AuditResult.SUCCESS, EMAIL, null, "192.0.2.10", null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditEvent(
                        OCCURRED_AT, AuditEventType.LOGGED_OUT, null, EMAIL, null, "192.0.2.10", null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditEvent(
                        OCCURRED_AT,
                        AuditEventType.LOGGED_OUT,
                        AuditResult.SUCCESS,
                        EMAIL,
                        null,
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the string form masks the email address and carries no secret")
    void stringFormMasksTheEmail() {
        AuditEvent event = loginFailed();

        assertThat(event.toString()).doesNotContain(EMAIL).contains("enteredEmail=***");
        assertThat(event.toString()).doesNotContain("$2a$").doesNotContain("Bearer ");
    }

    @Test
    @DisplayName("the entity exposes no password, token or setter")
    void noSecretFieldsAndNoSetters() {
        Method[] methods = AuditEvent.class.getDeclaredMethods();

        assertThat(Arrays.stream(methods).map(Method::getName))
                .as("値を変える手段を持たない")
                .noneMatch(name -> name.startsWith("set"));
        assertThat(Arrays.stream(AuditEvent.class.getDeclaredFields())
                        .map(field -> field.getName().toLowerCase(Locale.ROOT)))
                .as("パスワード・トークン・ハッシュの項目を持たない")
                .noneMatch(name -> name.contains("password") || name.contains("token") || name.contains("hash"));
    }
}
