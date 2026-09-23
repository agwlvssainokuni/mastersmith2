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
package cherry.mastersmith.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.audit.domain.AuditEvent;
import cherry.mastersmith.audit.domain.AuditEventType;
import cherry.mastersmith.audit.domain.AuditFailureReason;
import cherry.mastersmith.audit.domain.AuditResult;
import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.domain.LoginFailureReason;
import cherry.mastersmith.common.testsupport.LogEvents;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.KeyValuePair;

/** 受け取りと記録の単体テスト（BR1.1〜BR1.3、BR3.1、BR3.2、NFR10.1、NFR10.3）。 */
class AuditEventListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-22T01:02:03Z");

    private static final String EMAIL = "user@example.com";

    private static final ClientInfo CLIENT = new ClientInfo("192.0.2.10", "Mozilla/5.0", "trace-0001");

    private final AuditEventRecorder recorder = mock(AuditEventRecorder.class);

    private final LongSupplier nanoTime = mock(LongSupplier.class);

    private AuditEventListener listener() {
        return new AuditEventListener(recorder, nanoTime);
    }

    /** 経過時間を、開始と終了の2回の呼び出しで作る。 */
    private void elapsedMillis(long millis) {
        when(nanoTime.getAsLong()).thenReturn(0L, millis * 1_000_000L);
    }

    private static AuthenticationEvent loginFailed() {
        return AuthenticationEvent.of(
                AuthenticationEventType.LOGIN_FAILED,
                OCCURRED_AT,
                EMAIL,
                7L,
                LoginFailureReason.PASSWORD_MISMATCH,
                CLIENT);
    }

    private static AdminAccessDeniedEvent accessDenied() {
        return AdminAccessDeniedEvent.of(OCCURRED_AT, AccessDeniedReason.NOT_ADMIN, EMAIL, "/api/admin/check", CLIENT);
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        return pairs == null
                ? Map.of()
                : pairs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    private AuditEvent captureRecorded() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder, times(1)).record(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("an authentication event is appended exactly once")
    void authenticationEventIsAppendedOnce() {
        elapsedMillis(1);

        listener().onAuthenticationEvent(loginFailed());

        AuditEvent recorded = captureRecorded();
        assertThat(recorded.getEventType()).isEqualTo(AuditEventType.LOGIN_FAILED);
        assertThat(recorded.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(recorded.getFailureReason()).isEqualTo(AuditFailureReason.PASSWORD_MISMATCH);
        assertThat(recorded.getEnteredEmail()).isEqualTo(EMAIL);
        assertThat(recorded.getRequestPath()).isNull();
    }

    @Test
    @DisplayName("an access denied event is appended exactly once with its request path")
    void accessDeniedEventIsAppendedOnce() {
        elapsedMillis(1);

        listener().onAdminAccessDeniedEvent(accessDenied());

        AuditEvent recorded = captureRecorded();
        assertThat(recorded.getEventType()).isEqualTo(AuditEventType.ACCESS_DENIED);
        assertThat(recorded.getFailureReason()).isEqualTo(AuditFailureReason.NOT_ADMIN);
        assertThat(recorded.getRequestPath()).isEqualTo("/api/admin/check");
    }

    @Test
    @DisplayName("a failing append never reaches the caller and is never retried")
    void failingAppendIsContainedAndNotRetried() {
        elapsedMillis(1);
        doThrow(new IllegalStateException("追記に失敗しました")).when(recorder).record(any(AuditEvent.class));

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onAuthenticationEvent(loginFailed()))
                    .doesNotThrowAnyException();
            assertThatCode(() -> listener().onAdminAccessDeniedEvent(accessDenied()))
                    .doesNotThrowAnyException();

            assertThat(logs.list())
                    .hasSize(2)
                    .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
        }
        verify(recorder, times(2)).record(any(AuditEvent.class));
    }

    @Test
    @DisplayName("the error log carries every field that was to be recorded, including the email address")
    void errorLogCarriesEveryField() {
        elapsedMillis(1);
        doThrow(new IllegalStateException("追記に失敗しました")).when(recorder).record(any(AuditEvent.class));

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onAdminAccessDeniedEvent(accessDenied());

            assertThat(logs.list()).hasSize(1);
            Map<String, Object> fields = keyValues(logs.list().getFirst());
            assertThat(fields)
                    .containsEntry("auditEventType", "ACCESS_DENIED")
                    .containsEntry("result", "FAILURE")
                    .containsEntry("occurredAt", OCCURRED_AT.toString())
                    .containsEntry("enteredEmail", EMAIL)
                    .containsEntry("failureReason", "NOT_ADMIN")
                    .containsEntry("sourceIp", "192.0.2.10")
                    .containsEntry("userAgent", "Mozilla/5.0")
                    .containsEntry("requestPath", "/api/admin/check")
                    .containsEntry("auditTraceId", "trace-0001")
                    .containsEntry("exceptionType", "java.lang.IllegalStateException");
        }
    }

    @Test
    @DisplayName("the error message is fixed and never reuses the message of the exception")
    void errorMessageIsFixed() {
        elapsedMillis(1);
        doThrow(new IllegalStateException("INSERT INTO audit_events VALUES ('秘密の値')"))
                .when(recorder)
                .record(any(AuditEvent.class));

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onAuthenticationEvent(loginFailed());

            ILoggingEvent error = logs.list().getFirst();
            assertThat(error.getMessage()).isEqualTo(AuditEventListener.FAILURE_MESSAGE);
            assertThat(error.getFormattedMessage()).doesNotContain("秘密の値");
            assertThat(error.getThrowableProxy()).isNotNull();
        }
    }

    @Test
    @DisplayName("the fixed message and the key values never carry a password or a token value")
    void logsCarryNoSecret() {
        String password = "正しいパスワード-1234";
        String token = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
        elapsedMillis(1);
        doThrow(new IllegalStateException(password + " " + token))
                .when(recorder)
                .record(any(AuditEvent.class));

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onAuthenticationEvent(loginFailed());

            String rendered = logs.list().stream()
                    .map(event -> event.getFormattedMessage() + " " + keyValues(event))
                    .collect(Collectors.joining("\n"));
            assertThat(rendered).doesNotContain(password).doesNotContain(token).doesNotContain("$2a$");
        }
    }

    @Test
    @DisplayName("a write slower than the threshold logs one warning while a fast write logs none")
    void slowWriteIsWarnedOnce() {
        elapsedMillis(AuditEventListener.SLOW_WRITE_THRESHOLD_MILLIS + 50);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onAuthenticationEvent(loginFailed());

            assertThat(logs.list()).hasSize(1).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage()).isEqualTo(AuditEventListener.SLOW_WRITE_MESSAGE);
                assertThat(keyValues(event)).containsEntry("elapsedMs", "250");
            });
        }

        elapsedMillis(AuditEventListener.SLOW_WRITE_THRESHOLD_MILLIS);
        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onAuthenticationEvent(loginFailed());

            assertThat(logs.list()).isEmpty();
        }
    }

    @Test
    @DisplayName("a successful append writes nothing to the application log")
    void successfulAppendIsNotLogged() {
        elapsedMillis(1);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onAuthenticationEvent(loginFailed());
            listener().onAdminAccessDeniedEvent(accessDenied());

            assertThat(logs.list()).isEmpty();
        }
    }

    @Test
    @DisplayName("a null event of either path is contained and the repository is never called")
    void nullEventIsContained() {
        elapsedMillis(1);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onAuthenticationEvent(null)).doesNotThrowAnyException();
            assertThatCode(() -> listener().onAdminAccessDeniedEvent(null)).doesNotThrowAnyException();

            assertThat(logs.list())
                    .hasSize(2)
                    .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
            // 組み立てに失敗したため項目の値は取れないが、キーは欠かさず出す。
            assertThat(keyValues(logs.list().getFirst()))
                    .containsKeys(
                            "auditEventType",
                            "result",
                            "occurredAt",
                            "enteredEmail",
                            "failureReason",
                            "sourceIp",
                            "userAgent",
                            "requestPath",
                            "auditTraceId",
                            "exceptionType");
        }
        verifyNoInteractions(recorder);
    }
}
