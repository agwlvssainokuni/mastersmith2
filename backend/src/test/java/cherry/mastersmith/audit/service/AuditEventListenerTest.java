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
import cherry.mastersmith.invitation.domain.InvitationCancelledEvent;
import cherry.mastersmith.invitation.domain.InvitationIssuedEvent;
import cherry.mastersmith.invitation.domain.InvitationResentEvent;
import cherry.mastersmith.invitation.domain.LinkRejection;
import cherry.mastersmith.invitation.domain.RegistrationCompletedEvent;
import cherry.mastersmith.invitation.domain.RegistrationFailedEvent;
import cherry.mastersmith.user.domain.PasswordChangeFailureReason;
import cherry.mastersmith.user.domain.PasswordChangedEvent;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.useradmin.domain.AdminOperation;
import cherry.mastersmith.useradmin.domain.UserAdminAuditEvent;
import cherry.mastersmith.useradmin.domain.UserAdminAuditFailure;
import java.time.Instant;
import java.util.HashMap;
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

    /** 文字列にせずに値のまま並べる（列挙の型まで確かめるため。値は null でもよい）。 */
    private static Map<String, Object> rawKeyValues(ILoggingEvent event) {
        Map<String, Object> values = new HashMap<>();
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            pairs.forEach(pair -> values.put(pair.key, pair.value));
        }
        return values;
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

    private static PasswordChangedEvent passwordChanged() {
        return PasswordChangedEvent.succeeded(
                21, OCCURRED_AT, new RequestOrigin("192.0.2.30", "Mozilla/5.0", "trace-0030"));
    }

    private static PasswordChangedEvent passwordMismatch() {
        return PasswordChangedEvent.failed(
                21,
                PasswordChangeFailureReason.CURRENT_PASSWORD_MISMATCH,
                OCCURRED_AT,
                new RequestOrigin("192.0.2.30", "Mozilla/5.0", "trace-0030"));
    }

    @Test
    @DisplayName("a password change event is appended exactly once with the user as actor and target")
    void passwordChangedEventIsAppendedOnce() {
        elapsedMillis(1);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onPasswordChangedEvent(passwordMismatch());

            assertThat(logs.list()).isEmpty();
        }
        AuditEvent recorded = captureRecorded();
        assertThat(recorded.getEventType()).isEqualTo(AuditEventType.PASSWORD_CHANGED);
        assertThat(recorded.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(recorded.getFailureReason()).isEqualTo(AuditFailureReason.CURRENT_PASSWORD_MISMATCH);
        assertThat(recorded.getActorUserId()).isEqualTo(21L);
        assertThat(recorded.getTargetUserId()).isEqualTo(21L);
    }

    @Test
    @DisplayName("a failing append of a password change logs one error with the target fields and no email or password")
    void passwordChangedFailureLogsTargets() {
        String password = "正しいパスワード-1234";
        elapsedMillis(1);
        doThrow(new IllegalStateException(password)).when(recorder).record(any(AuditEvent.class));

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onPasswordChangedEvent(passwordChanged()))
                    .doesNotThrowAnyException();

            assertThat(logs.list()).hasSize(1);
            ILoggingEvent error = logs.list().getFirst();
            assertThat(error.getLevel()).isEqualTo(Level.ERROR);
            assertThat(error.getMessage()).isEqualTo(AuditEventListener.FAILURE_MESSAGE);
            Map<String, Object> fields = keyValues(error);
            assertThat(fields)
                    .containsEntry("auditEventType", "PASSWORD_CHANGED")
                    .containsEntry("result", "SUCCESS")
                    .containsEntry("actorUserId", "21")
                    .containsEntry("targetUserId", "21")
                    .containsEntry("targetInvitationId", "null")
                    .containsEntry("enteredEmail", "null")
                    .containsEntry("sourceIp", "192.0.2.30")
                    .containsEntry("auditTraceId", "trace-0030")
                    .doesNotContainKeys("dslHash", "dslSource", "rejectionKind");
            assertThat(fields.keySet())
                    .noneMatch(key -> key.toLowerCase(java.util.Locale.ROOT).contains("password"));
            assertThat(error.getFormattedMessage() + " " + fields)
                    .doesNotContain(password)
                    .doesNotContain("@");
        }
        verify(recorder, times(1)).record(any(AuditEvent.class));
    }

    @Test
    @DisplayName("a slow password change write logs one warning")
    void slowPasswordChangedWriteIsWarned() {
        elapsedMillis(AuditEventListener.SLOW_WRITE_THRESHOLD_MILLIS + 1);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onPasswordChangedEvent(passwordChanged());

            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(keyValues(event)).containsEntry("auditEventType", "PASSWORD_CHANGED");
            });
        }
    }

    @Test
    @DisplayName("a null password change event is contained and logs every key including the targets")
    void nullPasswordChangedEventIsContained() {
        elapsedMillis(1);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onPasswordChangedEvent(null)).doesNotThrowAnyException();

            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(keyValues(event))
                        .containsKeys(
                                "auditEventType", "actorUserId", "targetUserId", "targetInvitationId", "exceptionType");
            });
        }
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("the error fields of the existing events carry no target keys")
    void existingEventsCarryNoTargetKeys() {
        elapsedMillis(1);
        doThrow(new IllegalStateException("追記に失敗しました")).when(recorder).record(any(AuditEvent.class));

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onAuthenticationEvent(loginFailed());

            assertThat(keyValues(logs.list().getFirst()))
                    .doesNotContainKeys("targetUserId", "targetInvitationId", "actorUserId");
        }
    }

    @Test
    @DisplayName("an event whose audit record cannot be built still logs the fields it carries")
    void unbuildableEventsLogTheirFields() {
        // 組み立ての部品が受け付けない形の出来事（必須の値が無い）を作り、組み立てに失敗した経路の項目の作り方を確かめる。
        AuthenticationEvent authentication = mock(AuthenticationEvent.class);
        when(authentication.enteredEmail()).thenReturn(EMAIL);
        when(authentication.sourceIp()).thenReturn("192.0.2.40");
        AdminAccessDeniedEvent denied = mock(AdminAccessDeniedEvent.class);
        when(denied.requestPath()).thenReturn("/api/admin/check");
        PasswordChangedEvent password = mock(PasswordChangedEvent.class);
        when(password.userId()).thenReturn(21L);
        when(password.sourceIp()).thenReturn("192.0.2.41");

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener().onAuthenticationEvent(authentication);
            listener().onAdminAccessDeniedEvent(denied);
            listener().onPasswordChangedEvent(password);

            assertThat(logs.list())
                    .hasSize(3)
                    .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
            assertThat(keyValues(logs.list().get(0)))
                    .containsEntry("enteredEmail", EMAIL)
                    .containsEntry("sourceIp", "192.0.2.40");
            assertThat(keyValues(logs.list().get(1))).containsEntry("requestPath", "/api/admin/check");
            assertThat(keyValues(logs.list().get(2)))
                    .containsEntry("auditEventType", "PASSWORD_CHANGED")
                    .containsEntry("actorUserId", "21")
                    .containsEntry("targetUserId", "21")
                    .containsEntry("sourceIp", "192.0.2.41")
                    .containsEntry("enteredEmail", "null");
        }
        verifyNoInteractions(recorder);
    }

    private static final RequestOrigin INVITE_ORIGIN = new RequestOrigin("192.0.2.50", "Agent/2", "trace-0050");

    @Test
    @DisplayName("each of the five invitation events is appended exactly once with its type")
    void invitationEventsAreAppended() {
        when(nanoTime.getAsLong()).thenReturn(0L);
        AuditEventListener listener = listener();

        listener.onInvitationIssuedEvent(InvitationIssuedEvent.of(5, 9, OCCURRED_AT, INVITE_ORIGIN));
        listener.onInvitationResentEvent(InvitationResentEvent.of(5, 9, OCCURRED_AT, INVITE_ORIGIN));
        listener.onInvitationCancelledEvent(InvitationCancelledEvent.of(5, 9, OCCURRED_AT, INVITE_ORIGIN));
        listener.onRegistrationCompletedEvent(RegistrationCompletedEvent.of(5, 31, OCCURRED_AT, INVITE_ORIGIN));
        listener.onRegistrationFailedEvent(
                RegistrationFailedEvent.of(5L, LinkRejection.INVITATION_EXPIRED, OCCURRED_AT, INVITE_ORIGIN));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder, times(5)).record(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AuditEvent::getEventType)
                .containsExactly(
                        AuditEventType.INVITATION_ISSUED,
                        AuditEventType.INVITATION_RESENT,
                        AuditEventType.INVITATION_CANCELLED,
                        AuditEventType.REGISTRATION_COMPLETED,
                        AuditEventType.REGISTRATION_FAILED);
        assertThat(captor.getAllValues())
                .allSatisfy(audit -> assertThat(audit.getTargetInvitationId()).isEqualTo(5L));
    }

    @Test
    @DisplayName("a failing append of an invitation event logs one error with the actor and the targets only")
    void invitationFailureLogsTargets() {
        elapsedMillis(1);
        doThrow(new IllegalStateException("追記に失敗しました")).when(recorder).record(any(AuditEvent.class));

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener()
                            .onInvitationIssuedEvent(InvitationIssuedEvent.of(5, 9, OCCURRED_AT, INVITE_ORIGIN)))
                    .doesNotThrowAnyException();

            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(keyValues(error))
                        .containsEntry("auditEventType", "INVITATION_ISSUED")
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("actorUserId", "9")
                        .containsEntry("targetUserId", "null")
                        .containsEntry("targetInvitationId", "5")
                        .containsEntry("enteredEmail", "null")
                        .doesNotContainKeys("dslHash", "dslSource", "rejectionKind");
                assertThat(error.getFormattedMessage() + keyValues(error))
                        .doesNotContain("@")
                        .doesNotContain("token");
            });
        }
    }

    @Test
    @DisplayName("a failing append of a failed registration logs the reason and the found invitation")
    void registrationFailureLogsReason() {
        elapsedMillis(1);
        doThrow(new IllegalStateException("追記に失敗しました")).when(recorder).record(any(AuditEvent.class));

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener()
                    .onRegistrationFailedEvent(RegistrationFailedEvent.of(
                            5L, LinkRejection.EMAIL_ALREADY_REGISTERED, OCCURRED_AT, INVITE_ORIGIN));
            listener().onRegistrationCompletedEvent(RegistrationCompletedEvent.of(6, 31, OCCURRED_AT, INVITE_ORIGIN));

            assertThat(logs.list()).hasSize(2);
            assertThat(keyValues(logs.list().get(0)))
                    .containsEntry("auditEventType", "REGISTRATION_FAILED")
                    .containsEntry("result", "FAILURE")
                    .containsEntry("failureReason", "EMAIL_ALREADY_REGISTERED")
                    .containsEntry("targetInvitationId", "5")
                    .doesNotContainKey("actorUserId");
            assertThat(keyValues(logs.list().get(1)))
                    .containsEntry("auditEventType", "REGISTRATION_COMPLETED")
                    .containsEntry("targetUserId", "31")
                    .containsEntry("targetInvitationId", "6");
        }
    }

    @Test
    @DisplayName("null invitation events are contained and logged with the type and no target")
    void nullInvitationEventsAreContained() {
        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            AuditEventListener listener = listener();
            assertThatCode(() -> {
                        listener.onInvitationIssuedEvent(null);
                        listener.onInvitationResentEvent(null);
                        listener.onInvitationCancelledEvent(null);
                        listener.onRegistrationCompletedEvent(null);
                        listener.onRegistrationFailedEvent(null);
                    })
                    .doesNotThrowAnyException();

            assertThat(logs.list())
                    .hasSize(5)
                    .allSatisfy(error -> assertThat(keyValues(error)).containsEntry("targetInvitationId", "null"));
            assertThat(logs.list())
                    .extracting(error -> keyValues(error).get("auditEventType"))
                    .containsExactly(
                            "INVITATION_ISSUED",
                            "INVITATION_RESENT",
                            "INVITATION_CANCELLED",
                            "REGISTRATION_COMPLETED",
                            "REGISTRATION_FAILED");
        }
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("a user admin event is appended exactly once with the actor, the target and the reason (U3 B4)")
    void userAdminEventIsAppendedOnce() {
        elapsedMillis(1);
        UserAdminAuditEvent event = UserAdminAuditEvent.failed(
                AdminOperation.SUSPEND,
                3,
                44,
                UserAdminAuditFailure.LAST_ACTIVE_ADMIN,
                OCCURRED_AT,
                new RequestOrigin("192.0.2.60", "Agent/60", "trace-0060"));

        listener().onUserAdminAuditEvent(event);

        AuditEvent recorded = captureRecorded();
        assertThat(recorded.getEventType()).isEqualTo(AuditEventType.USER_SUSPENDED);
        assertThat(recorded.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(recorded.getFailureReason()).isEqualTo(AuditFailureReason.LAST_ACTIVE_ADMIN);
        assertThat(recorded.getActorUserId()).isEqualTo(3L);
        assertThat(recorded.getTargetUserId()).isEqualTo(44L);
        assertThat(recorded.getEnteredEmail()).isNull();
    }

    @Test
    @DisplayName(
            "a failing append of a user admin event is contained and logs the actor and target without email (U3 B4)")
    void userAdminEventFailureIsContained() {
        elapsedMillis(1);
        doThrow(new IllegalStateException("追記に失敗しました")).when(recorder).record(any(AuditEvent.class));
        UserAdminAuditEvent event = UserAdminAuditEvent.succeeded(
                AdminOperation.GRANT_ADMIN, 3, 44, OCCURRED_AT, new RequestOrigin("192.0.2.60", null, null));

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onUserAdminAuditEvent(event)).doesNotThrowAnyException();
            assertThatCode(() -> listener().onUserAdminAuditEvent(null)).doesNotThrowAnyException();

            assertThat(logs.list())
                    .hasSize(2)
                    .allSatisfy(logged -> assertThat(logged.getLevel()).isEqualTo(Level.ERROR));
            Map<String, Object> fields = keyValues(logs.list().getFirst());
            assertThat(fields)
                    .containsEntry("auditEventType", "USER_ADMIN_GRANTED")
                    .containsEntry("actorUserId", "3")
                    .containsEntry("targetUserId", "44")
                    .containsEntry("enteredEmail", "null");
            assertThat(keyValues(logs.list().get(1))).containsEntry("actorUserId", "null");
        }
    }

    @Test
    @DisplayName("a user admin event whose audit record cannot be built logs the actor and target it carries (U3 B4)")
    void unbuildableUserAdminEventLogsItsFields() {
        // 組み立ての部品が受け付けない形の出来事（操作の区分が無い）を作り、組み立てに失敗した経路の項目の作り方を確かめる
        // （コード生成のレビューの R-03。出来事の側の確かめで本番では起きない経路）。
        UserAdminAuditEvent event = mock(UserAdminAuditEvent.class);
        when(event.actorUserId()).thenReturn(3L);
        when(event.targetUserId()).thenReturn(44L);
        when(event.failure()).thenReturn(UserAdminAuditFailure.NOT_ADMIN);
        when(event.occurredAt()).thenReturn(OCCURRED_AT);
        when(event.sourceIp()).thenReturn("192.0.2.61");
        when(event.userAgent()).thenReturn("Agent/61");
        when(event.traceId()).thenReturn("trace-0061");

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onUserAdminAuditEvent(event)).doesNotThrowAnyException();

            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(error.getMessage()).isEqualTo(AuditEventListener.FAILURE_MESSAGE);
                assertThat(keyValues(error))
                        .containsEntry("actorUserId", "3")
                        .containsEntry("targetUserId", "44")
                        .containsEntry("targetInvitationId", "null")
                        .containsEntry("failureReason", "NOT_ADMIN")
                        .containsEntry("sourceIp", "192.0.2.61")
                        .containsEntry("auditTraceId", "trace-0061")
                        .containsEntry("enteredEmail", "null")
                        .doesNotContainKeys("dslHash", "dslSource", "rejectionKind");
            });
        }
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("unbuildable failed user-admin event logs audit event type, result and failure reason as audit values")
    void unbuildableFailedUserAdminEventLogsAuditValues() {
        // 組み立ての部品が受け付けない形の出来事（日時が無い）で組み立てを失敗させ、操作の区分を持つ出来事の ERROR の項目が、
        // 組み立てに成功したときと同じ値の形（監査の種類・結果・理由）になることを確かめる。
        UserAdminAuditEvent event = mock(UserAdminAuditEvent.class);
        when(event.operation()).thenReturn(AdminOperation.SUSPEND);
        when(event.succeeded()).thenReturn(false);
        when(event.failure()).thenReturn(UserAdminAuditFailure.LAST_ACTIVE_ADMIN);
        when(event.actorUserId()).thenReturn(3L);
        when(event.targetUserId()).thenReturn(44L);
        when(event.sourceIp()).thenReturn("192.0.2.62");
        when(event.userAgent()).thenReturn("Agent/62");
        when(event.traceId()).thenReturn("trace-0062");

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onUserAdminAuditEvent(event)).doesNotThrowAnyException();

            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(error.getMessage()).isEqualTo(AuditEventListener.FAILURE_MESSAGE);
                Map<String, Object> values = rawKeyValues(error);
                assertThat(values.get("auditEventType")).isEqualTo(AuditEventType.USER_SUSPENDED);
                assertThat(values.get("result")).isEqualTo(AuditResult.FAILURE);
                assertThat(values.get("failureReason")).isEqualTo(AuditFailureReason.LAST_ACTIVE_ADMIN);
                assertThat(values)
                        .containsEntry("actorUserId", 3L)
                        .containsEntry("targetUserId", 44L)
                        .containsEntry("enteredEmail", null)
                        .doesNotContainKeys("dslHash", "dslSource", "rejectionKind");
            });
        }
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("unbuildable succeeded user-admin event logs audit event type and SUCCESS without failure reason")
    void unbuildableSucceededUserAdminEventLogsAuditValues() {
        UserAdminAuditEvent event = mock(UserAdminAuditEvent.class);
        when(event.operation()).thenReturn(AdminOperation.RESET_LOGIN_FAILURES);
        when(event.succeeded()).thenReturn(true);
        when(event.actorUserId()).thenReturn(3L);
        when(event.targetUserId()).thenReturn(45L);
        when(event.sourceIp()).thenReturn("192.0.2.63");

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onUserAdminAuditEvent(event)).doesNotThrowAnyException();

            assertThat(logs.list()).singleElement().satisfies(error -> {
                Map<String, Object> values = rawKeyValues(error);
                assertThat(values.get("auditEventType")).isEqualTo(AuditEventType.LOGIN_FAILURES_RESET);
                assertThat(values.get("result")).isEqualTo(AuditResult.SUCCESS);
                assertThat(values).containsEntry("failureReason", null);
            });
        }
        verifyNoInteractions(recorder);
    }
}
