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
import cherry.mastersmith.audit.domain.AuditEventFactory;
import cherry.mastersmith.audit.domain.AuditEventType;
import cherry.mastersmith.audit.domain.AuditFailureReason;
import cherry.mastersmith.audit.domain.AuditResult;
import cherry.mastersmith.businessdata.domain.AuditDetailBuilder;
import cherry.mastersmith.businessdata.domain.BusinessDataAuditReason;
import cherry.mastersmith.businessdata.domain.BusinessDataChangedEvent;
import cherry.mastersmith.businessdata.domain.WriteOperation;
import cherry.mastersmith.businessdata.domain.WriteOrigin;
import cherry.mastersmith.common.testsupport.LogEvents;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 業務データの操作の出来事の受け取りの単体テスト（Intent 261016-business-data-views の U2、BR11.1・BR11.5・BR11.6、NFR8.4）。値は
 * すべて架空。
 */
class AuditBusinessDataEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-10-20T01:02:03Z");

    private static final String KEY_VALUE = "主キーの値-秘密";

    private static final WriteOrigin ORIGIN =
            new WriteOrigin("192.0.2.41", "Mozilla/5.0", "/api/tables/rows", "trace-0041");

    private final AuditEventRecorder recorder = mock(AuditEventRecorder.class);

    private final LongSupplier nanoTime = mock(LongSupplier.class);

    private AuditEventListener listener() {
        when(nanoTime.getAsLong()).thenReturn(0L, 1_000_000L);
        return new AuditEventListener(recorder, nanoTime);
    }

    private static BusinessDataChangedEvent event(BusinessDataAuditReason reason) {
        return BusinessDataChangedEvent.of(
                3L,
                7L,
                "sample",
                "item",
                WriteOperation.UPDATE,
                reason,
                AuditDetailBuilder.build(Map.of("id", KEY_VALUE)),
                NOW,
                ORIGIN);
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
        Method method = AuditEventListener.class.getMethod("onBusinessDataChanged", BusinessDataChangedEvent.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }

    @Test
    @DisplayName("a business data event is appended exactly once with its columns and detail")
    void appendedOnce() {
        listener().onBusinessDataChanged(event(BusinessDataAuditReason.BUSINESS_DUPLICATE_VALUE));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder, times(1)).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.getEventType()).isEqualTo(AuditEventType.BUSINESS_ROW_UPDATED);
        assertThat(recorded.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(recorded.getFailureReason()).isEqualTo(AuditFailureReason.BUSINESS_DUPLICATE_VALUE);
        assertThat(recorded.getTargetTable()).isEqualTo("item");
        assertThat(recorded.getDetail()).contains(KEY_VALUE);
    }

    @Test
    @DisplayName("every business data reason maps to an audit reason, and all reasons and types fit in 32 characters")
    void mappingIsCompleteAndShort() {
        for (BusinessDataAuditReason reason : BusinessDataAuditReason.values()) {
            assertThat(AuditEventFactory.businessDataFailureReasonOf(reason))
                    .as(reason.name())
                    .isNotNull();
        }
        assertThat(AuditEventFactory.businessDataFailureReasonOf(null)).isNull();
        assertThat(Arrays.stream(AuditFailureReason.values()).map(Enum::name))
                .allSatisfy(name -> assertThat(name.length()).as(name).isLessThanOrEqualTo(32));
        assertThat(Arrays.stream(AuditEventType.values()).map(Enum::name))
                .allSatisfy(name -> assertThat(name.length()).as(name).isLessThanOrEqualTo(32));
    }

    @Test
    @DisplayName("a write failure logs once without the detail, the key values or the cause")
    void writeFailureHidesTheKey() {
        doThrow(new IllegalStateException("書き込みに失敗 " + KEY_VALUE))
                .when(recorder)
                .record(any());

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onBusinessDataChanged(event(null))).doesNotThrowAnyException();

            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(error.getThrowableProxy()).as("原因を載せない").isNull();
                assertThat(keyValues(error))
                        .containsEntry("auditEventType", AuditEventType.BUSINESS_ROW_UPDATED)
                        .containsEntry("result", AuditResult.SUCCESS)
                        .containsEntry("actorUserId", 3L)
                        .containsEntry("targetSchema", "sample")
                        .containsEntry("targetTable", "item")
                        .containsEntry("workRoleId", 7L)
                        .containsEntry("keyColumnCount", 1)
                        .containsEntry("keyTruncated", false)
                        .containsEntry("exceptionType", IllegalStateException.class.getName())
                        .doesNotContainKey("detail");
                assertThat(error.getFormattedMessage() + keyValues(error)).doesNotContain(KEY_VALUE);
            });
        }
    }

    @Test
    @DisplayName("a missing event is absorbed and logged once with empty fields, nothing is appended")
    void missingEvent() {
        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThatCode(() -> listener().onBusinessDataChanged(null)).doesNotThrowAnyException();

            verify(recorder, never()).record(any());
            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(error.getThrowableProxy()).isNull();
            });
        }
    }
}
