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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.audit.domain.AuditEvent;
import cherry.mastersmith.audit.domain.AuditEventType;
import cherry.mastersmith.audit.domain.AuditFailureReason;
import cherry.mastersmith.audit.domain.AuditResult;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleAuditFailure;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.user.domain.RequestOrigin;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** ロールの操作の出来事の受け取り（BR11.1・BR11.2、NFR3.5、計画の D-10、group の B3 からの引き継ぎ）の単体テスト。 */
class AuditRoleEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:30:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.96", "Mozilla/5.0", "trace-0096");

    private final AuditEventRecorder recorder = mock(AuditEventRecorder.class);

    private final LongSupplier nanoTime = mock(LongSupplier.class);

    private AuditEventListener listener() {
        when(nanoTime.getAsLong()).thenReturn(0L, 1_000_000L);
        return new AuditEventListener(recorder, nanoTime);
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> values = new HashMap<>();
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
        }
        return values;
    }

    @Test
    @DisplayName("the receiver runs after commit, also without a transaction")
    void receivesAfterCommit() throws NoSuchMethodException {
        Method method = AuditEventListener.class.getMethod("onRoleAuditEvent", RoleAuditEvent.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }

    @Test
    @DisplayName("a role event is appended exactly once with its type, result, reason, role and detail")
    void appendedOnce() {
        listener()
                .onRoleAuditEvent(RoleAuditEvent.failed(
                        RoleOperation.RENAME,
                        1L,
                        50L,
                        RoleAuditFailure.ROLE_NAME_DUPLICATE,
                        new RoleAuditDetail.Rename("Sales", "営業"),
                        NOW,
                        ORIGIN));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder, times(1)).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.getEventType()).isEqualTo(AuditEventType.ROLE_RENAMED);
        assertThat(recorded.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(recorded.getFailureReason()).isEqualTo(AuditFailureReason.ROLE_NAME_DUPLICATE);
        assertThat(recorded.getTargetRoleId()).isEqualTo(50L);
        assertThat(recorded.getDetail()).isEqualTo("{\"before\":\"Sales\",\"after\":\"営業\"}");
    }

    @Test
    @DisplayName("a write failure is absorbed and logged once with the role target and the detail")
    void writeFailureCarriesRoleFields() {
        doThrow(new IllegalStateException("write failed")).when(recorder).record(any());

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener()
                            .onRoleAuditEvent(RoleAuditEvent.succeeded(
                                    RoleOperation.CREATE, 1L, 50L, new RoleAuditDetail.Name("営業"), NOW, ORIGIN)))
                    .doesNotThrowAnyException();

            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(keyValues(error))
                        .containsEntry("auditEventType", AuditEventType.ROLE_CREATED)
                        .containsEntry("result", AuditResult.SUCCESS)
                        .containsEntry("actorUserId", 1L)
                        .containsEntry("targetRoleId", 50L)
                        .containsEntry("detail", "{\"name\":\"営業\"}")
                        .doesNotContainKeys("targetGroupId", "targetUserId");
            });
        }
    }

    @Test
    @DisplayName("an event whose detail is too long is not appended and the log carries the role target (B3 hand-over)")
    void buildFailureFallsBackToEventFields() {
        String longName = "あ".repeat(AuditEvent.MAX_DETAIL_LENGTH);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener()
                            .onRoleAuditEvent(RoleAuditEvent.succeeded(
                                    RoleOperation.DELETE, 1L, 51L, new RoleAuditDetail.Name(longName), NOW, ORIGIN)))
                    .doesNotThrowAnyException();

            verify(recorder, never()).record(any());
            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(keyValues(error))
                        .containsEntry("auditEventType", AuditEventType.ROLE_DELETED)
                        .containsEntry("result", AuditResult.SUCCESS)
                        .containsEntry("actorUserId", 1L)
                        .containsEntry("targetRoleId", 51L)
                        .containsEntry("exceptionType", IllegalArgumentException.class.getName())
                        .doesNotContainKeys("targetGroupId", "targetUserId");
                assertThat((String) keyValues(error).get("detail")).hasSizeGreaterThan(AuditEvent.MAX_DETAIL_LENGTH);
            });
        }
    }

    @Test
    @DisplayName("a missing event is absorbed and logged once with empty fields, nothing is appended")
    void missingEventIsLoggedWithEmptyFields() {
        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onRoleAuditEvent(null)).doesNotThrowAnyException();

            verify(recorder, never()).record(any());
            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(keyValues(error))
                        .containsEntry("actorUserId", null)
                        .containsEntry("auditEventType", null)
                        .doesNotContainKeys("targetGroupId", "targetRoleId", "targetUserId", "detail");
            });
        }
    }

    @Test
    @DisplayName("a write failure of a work role switch is logged once with the user target and the switch detail")
    void writeFailureOfASwitchCarriesTheUser() {
        doThrow(new IllegalStateException("write failed")).when(recorder).record(any());

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener()
                            .onRoleAuditEvent(RoleAuditEvent.succeeded(
                                    RoleOperation.SWITCH_WORK_ROLE,
                                    8L,
                                    50L,
                                    8L,
                                    null,
                                    new RoleAuditDetail.WorkRoleSwitch(null, null, "営業", null),
                                    NOW,
                                    ORIGIN)))
                    .doesNotThrowAnyException();

            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(keyValues(error))
                        .containsEntry("auditEventType", AuditEventType.WORK_ROLE_SWITCHED)
                        .containsEntry("actorUserId", 8L)
                        .containsEntry("targetUserId", 8L)
                        .containsEntry("targetRoleId", 50L)
                        .doesNotContainKey("targetGroupId");
                assertThat((String) keyValues(error).get("detail")).contains("\"toRoleName\":\"営業\"");
            });
        }
    }

    @Test
    @DisplayName("an assignment to a group is appended with the group target and no user target")
    void groupAssignmentAppended() {
        listener()
                .onRoleAuditEvent(RoleAuditEvent.failed(
                        RoleOperation.ASSIGN,
                        1L,
                        50L,
                        null,
                        70L,
                        RoleAuditFailure.GROUP_NOT_FOUND,
                        new RoleAuditDetail.Assignment("営業", null),
                        NOW,
                        ORIGIN));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder, times(1)).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.getEventType()).isEqualTo(AuditEventType.ROLE_ASSIGNED);
        assertThat(recorded.getFailureReason()).isEqualTo(AuditFailureReason.GROUP_NOT_FOUND);
        assertThat(recorded.getTargetGroupId()).isEqualTo(70L);
        assertThat(recorded.getTargetUserId()).isNull();
    }
}
