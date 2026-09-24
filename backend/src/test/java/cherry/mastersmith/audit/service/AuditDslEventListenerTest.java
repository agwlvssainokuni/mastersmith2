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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.audit.domain.AuditEvent;
import cherry.mastersmith.audit.domain.AuditEventType;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.dslmanage.domain.DslOperationEvent;
import cherry.mastersmith.dslmanage.domain.DslOperationType;
import cherry.mastersmith.dslmanage.domain.DslSource;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** DSL の操作の出来事の受け取り（Intent 260923-dsl-schema-loader の U4、契約 C7、BR7.3）の単体テスト。 */
class AuditDslEventListenerTest {

    private final AuditEventRecorder recorder = mock(AuditEventRecorder.class);

    private final AuditEventListener listener = new AuditEventListener(recorder, () -> 0L);

    private static DslOperationEvent event() {
        return new DslOperationEvent(
                DslOperationType.DSL_APPLIED,
                5L,
                Instant.parse("2026-09-24T01:00:00Z"),
                "b".repeat(64),
                DslSource.PASTE,
                null,
                "192.0.2.9",
                null,
                "trace-9");
    }

    @Test
    @DisplayName("a DSL event is recorded as its audit event")
    void recorded() {
        listener.onDslOperationEvent(event());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder).record(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuditEventType.DSL_APPLIED);
        assertThat(captor.getValue().getActorUserId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("a failing write logs one error carrying the DSL fields and nothing escapes")
    void writeFailure() {
        doThrow(new IllegalStateException("書き込みの失敗")).when(recorder).record(any());

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener.onDslOperationEvent(event());

            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                assertThat(String.valueOf(error.getKeyValuePairs()))
                        .contains("actorUserId=\"5\"")
                        .contains("dslSource=\"PASTE\"")
                        .contains("dslHash=\"" + "b".repeat(64) + "\"");
            });
        }
    }

    @Test
    @DisplayName("an event that cannot be turned into an audit event still logs one error with the known fields")
    void buildFailure() {
        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            listener.onDslOperationEvent(null);

            assertThat(logs.list())
                    .singleElement()
                    .satisfies(error -> assertThat(error.getLevel()).isEqualTo(Level.ERROR));
        }
    }
}
