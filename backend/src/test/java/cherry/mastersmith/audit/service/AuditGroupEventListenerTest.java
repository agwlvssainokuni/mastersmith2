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
import cherry.mastersmith.group.domain.GroupAuditDetail;
import cherry.mastersmith.group.domain.GroupAuditEvent;
import cherry.mastersmith.group.domain.GroupAuditFailure;
import cherry.mastersmith.group.domain.GroupOperation;
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

/** グループの操作の出来事の受け取り（BR8.1・BR8.2、NFR3.5、計画の D-10）の単体テスト。 */
class AuditGroupEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.95", "Mozilla/5.0", "trace-0095");

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
        Method method = AuditEventListener.class.getMethod("onGroupAuditEvent", GroupAuditEvent.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }

    @Test
    @DisplayName("a group event is appended exactly once with its type, result, reason and targets")
    void appendedOnce() {
        listener()
                .onGroupAuditEvent(GroupAuditEvent.failed(
                        GroupOperation.RENAME,
                        1L,
                        70L,
                        null,
                        GroupAuditFailure.GROUP_NAME_DUPLICATE,
                        new GroupAuditDetail.Rename("Sales", "総務部"),
                        NOW,
                        ORIGIN));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder, times(1)).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.getEventType()).isEqualTo(AuditEventType.GROUP_RENAMED);
        assertThat(recorded.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(recorded.getFailureReason()).isEqualTo(AuditFailureReason.GROUP_NAME_DUPLICATE);
        assertThat(recorded.getTargetGroupId()).isEqualTo(70L);
        assertThat(recorded.getDetail()).isEqualTo("{\"before\":\"Sales\",\"after\":\"総務部\"}");
    }

    @Test
    @DisplayName("a write failure is absorbed and logged once with the group target and the detail")
    void writeFailureCarriesGroupFields() {
        doThrow(new IllegalStateException("write failed")).when(recorder).record(any());

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener()
                            .onGroupAuditEvent(GroupAuditEvent.succeeded(
                                    GroupOperation.ADD_MEMBER,
                                    1L,
                                    70L,
                                    80L,
                                    new GroupAuditDetail.Membership("営業部"),
                                    NOW,
                                    ORIGIN)))
                    .doesNotThrowAnyException();

            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(keyValues(error))
                        .containsEntry("auditEventType", AuditEventType.GROUP_MEMBER_ADDED)
                        .containsEntry("result", AuditResult.SUCCESS)
                        .containsEntry("actorUserId", 1L)
                        .containsEntry("targetUserId", 80L)
                        .containsEntry("targetGroupId", 70L)
                        .containsEntry("detail", "{\"groupName\":\"営業部\"}")
                        .doesNotContainKey("targetRoleId");
            });
        }
    }

    @Test
    @DisplayName("the failure log leaves out the group target and detail when they are empty")
    void emptyGroupFieldsAreLeftOut() {
        doThrow(new IllegalStateException("write failed")).when(recorder).record(any());

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener()
                    .onGroupAuditEvent(GroupAuditEvent.failed(
                            GroupOperation.CREATE,
                            1L,
                            null,
                            null,
                            GroupAuditFailure.GROUP_NAME_DUPLICATE,
                            new GroupAuditDetail.Name("Sales"),
                            NOW,
                            ORIGIN));

            assertThat(logs.list())
                    .singleElement()
                    .satisfies(error -> assertThat(keyValues(error))
                            .containsEntry("failureReason", AuditFailureReason.GROUP_NAME_DUPLICATE)
                            .doesNotContainKeys("targetGroupId", "targetRoleId", "targetUserId")
                            .containsEntry("detail", "{\"name\":\"Sales\"}"));
        }
    }

    @Test
    @DisplayName("a missing event is absorbed and logged once with empty fields, nothing is appended")
    void missingEventIsLoggedWithEmptyFields() {
        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onGroupAuditEvent(null)).doesNotThrowAnyException();

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
    @DisplayName("a member event whose detail is too long is not appended and the log carries the event's own fields")
    void tooLongDetailOfMemberEventFallsBackToEventFields() {
        String longName = "あ".repeat(AuditEvent.MAX_DETAIL_LENGTH);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener()
                            .onGroupAuditEvent(GroupAuditEvent.succeeded(
                                    GroupOperation.REMOVE_MEMBER,
                                    1L,
                                    70L,
                                    80L,
                                    new GroupAuditDetail.Membership(longName),
                                    NOW,
                                    ORIGIN)))
                    .doesNotThrowAnyException();

            verify(recorder, never()).record(any());
            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(keyValues(error))
                        .containsEntry("auditEventType", AuditEventType.GROUP_MEMBER_REMOVED)
                        .containsEntry("result", AuditResult.SUCCESS)
                        .containsEntry("actorUserId", 1L)
                        .containsEntry("targetUserId", 80L)
                        .containsEntry("targetGroupId", 70L)
                        .containsEntry("exceptionType", IllegalArgumentException.class.getName())
                        .doesNotContainKey("targetRoleId");
                assertThat((String) keyValues(error).get("detail")).hasSizeGreaterThan(AuditEvent.MAX_DETAIL_LENGTH);
            });
        }
    }

    @Test
    @DisplayName("a rejected event whose detail is too long logs the failure reason and no member target")
    void tooLongDetailOfRejectedEventFallsBackToEventFields() {
        String longName = "a".repeat(AuditEvent.MAX_DETAIL_LENGTH);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener()
                    .onGroupAuditEvent(GroupAuditEvent.failed(
                            GroupOperation.DELETE,
                            1L,
                            70L,
                            null,
                            GroupAuditFailure.GROUP_IN_USE,
                            new GroupAuditDetail.InUse(longName, 2, 0),
                            NOW,
                            ORIGIN));

            verify(recorder, never()).record(any());
            assertThat(logs.list())
                    .singleElement()
                    .satisfies(error -> assertThat(keyValues(error))
                            .containsEntry("auditEventType", AuditEventType.GROUP_DELETED)
                            .containsEntry("result", AuditResult.FAILURE)
                            .containsEntry("failureReason", AuditFailureReason.GROUP_IN_USE)
                            .containsEntry("targetGroupId", 70L)
                            .doesNotContainKeys("targetUserId", "targetRoleId"));
        }
    }
}
