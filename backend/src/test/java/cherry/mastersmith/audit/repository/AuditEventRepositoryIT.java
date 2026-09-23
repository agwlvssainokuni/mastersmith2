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
package cherry.mastersmith.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.audit.domain.AuditEvent;
import cherry.mastersmith.audit.domain.AuditEventType;
import cherry.mastersmith.audit.domain.AuditFailureReason;
import cherry.mastersmith.audit.domain.AuditResult;
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** 監査イベントの保存の部品の結合テスト（BR4.1、NFR1.3、NFR3.2）。 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class AuditEventRepositoryIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    AuditEventRepository repository;

    @Autowired
    TransactionTemplate tx;

    private static AuditEvent event(Instant occurredAt, AuditEventType eventType) {
        return new AuditEvent(
                occurredAt,
                eventType,
                eventType == AuditEventType.LOGIN_FAILED ? AuditResult.FAILURE : AuditResult.SUCCESS,
                "user@example.com",
                eventType == AuditEventType.LOGIN_FAILED ? AuditFailureReason.PASSWORD_MISMATCH : null,
                "192.0.2.10",
                "Mozilla/5.0",
                null,
                "trace-0001");
    }

    private AuditEvent append(AuditEvent auditEvent) {
        return tx.execute(status -> repository.save(auditEvent));
    }

    @Test
    @DisplayName("an appended audit event is read back by its id")
    void appendedEventIsReadBack() {
        AuditEvent saved = append(event(Instant.parse("2026-09-22T01:00:00Z"), AuditEventType.LOGIN_SUCCEEDED));

        AuditEvent found = tx.execute(
                status -> repository.findById(saved.getAuditEventId()).orElseThrow());

        assertThat(saved.getAuditEventId()).isNotNull();
        assertThat(found.getEventType()).isEqualTo(AuditEventType.LOGIN_SUCCEEDED);
        assertThat(found.getEnteredEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("several audit events are read back in the order of the time of the event")
    void eventsAreReadInTimeOrder() {
        append(event(Instant.parse("2026-09-22T05:00:00Z"), AuditEventType.LOGGED_OUT));
        append(event(Instant.parse("2026-09-22T04:00:00Z"), AuditEventType.LOGIN_FAILED));
        append(event(Instant.parse("2026-09-22T06:00:00Z"), AuditEventType.LOGIN_SUCCEEDED));

        List<Instant> times = tx.execute(status -> repository.findAllByOrderByOccurredAtAsc().stream()
                .map(AuditEvent::getOccurredAt)
                .toList());

        assertThat(times).isSorted();
        assertThat(times).contains(Instant.parse("2026-09-22T04:00:00Z"), Instant.parse("2026-09-22T06:00:00Z"));
    }

    @Test
    @DisplayName("appending one audit event issues exactly one insert and reads no other table")
    void appendIssuesOneInsertOnly() {
        SqlStatementCounter.start();
        append(event(Instant.parse("2026-09-22T07:00:00Z"), AuditEventType.LOGIN_FAILED));
        Map<String, List<String>> byThread = SqlStatementCounter.stop();

        assertThat(byThread).hasSize(1);
        assertThat(byThread.values().iterator().next()).containsExactly("insert audit_events");
    }

    @Test
    @DisplayName("saving the same audit event again never issues an update")
    void savedRowIsNeverUpdated() {
        AuditEvent saved = append(event(Instant.parse("2026-09-22T08:00:00Z"), AuditEventType.LOGGED_OUT));

        SqlStatementCounter.start();
        tx.execute(status -> repository.save(saved));
        Map<String, List<String>> byThread = SqlStatementCounter.stop();

        assertThat(byThread.values().stream().flatMap(List::stream))
                .as("追記だけの表であり、更新の SQL は出ない")
                .noneMatch(kind -> kind.startsWith("update"));
    }
}
