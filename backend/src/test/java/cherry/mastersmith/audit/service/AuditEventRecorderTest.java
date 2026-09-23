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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.audit.domain.AuditEvent;
import cherry.mastersmith.audit.domain.AuditEventType;
import cherry.mastersmith.audit.domain.AuditResult;
import cherry.mastersmith.audit.repository.AuditEventRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 追記の部品の単体テスト（{@code nfr-design/reliability-design.md} 1章）。 */
class AuditEventRecorderTest {

    private static AuditEvent auditEvent() {
        return new AuditEvent(
                Instant.parse("2026-09-22T01:00:00Z"),
                AuditEventType.LOGIN_SUCCEEDED,
                AuditResult.SUCCESS,
                "user@example.com",
                null,
                "192.0.2.10",
                null,
                null,
                "trace-0001");
    }

    @Test
    @DisplayName("the append runs in a new transaction")
    void appendRunsInANewTransaction() throws NoSuchMethodException {
        Method method = AuditEventRecorder.class.getMethod("record", AuditEvent.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("the append calls the repository exactly once")
    void appendCallsTheRepositoryOnce() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditEvent event = auditEvent();

        new AuditEventRecorder(repository).record(event);

        verify(repository, times(1)).save(event);
    }

    @Test
    @DisplayName("a failure of the repository is rethrown so the listener can catch it")
    void repositoryFailureIsRethrown() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.save(any(AuditEvent.class))).thenThrow(new IllegalStateException("追記に失敗しました"));

        assertThatThrownBy(() -> new AuditEventRecorder(repository).record(auditEvent()))
                .isInstanceOf(IllegalStateException.class);
    }
}
