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

import cherry.mastersmith.common.testsupport.TestDatabase;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** 監査イベントの表（V4）の結合テスト（組み込みの H2）。 */
@SpringBootTest
class AuditSchemaIT {

    /** サロゲートペアで表される文字（絵文字）。 */
    private static final String EMOJI = "😀";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    EntityManager entityManager;

    private AuditEvent persist(AuditEvent event) {
        return tx.execute(status -> {
            entityManager.persist(event);
            return event;
        });
    }

    private AuditEvent find(Long id) {
        return tx.execute(status -> entityManager.find(AuditEvent.class, id));
    }

    @Test
    @DisplayName("the U4 migration is applied successfully at startup")
    void migrationApplied() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT \"version\", \"success\" FROM \"flyway_schema_history\" WHERE \"version\" = '4'");

        assertThat(rows).hasSize(1).allSatisfy(row -> assertThat(row).containsEntry("success", true));
    }

    @Test
    @DisplayName("an audit event is saved and read back with every column")
    void savedAndReadBack() {
        AuditEvent saved = persist(new AuditEvent(
                Instant.parse("2026-09-22T01:02:03Z"),
                AuditEventType.ACCESS_DENIED,
                AuditResult.FAILURE,
                "member@example.com",
                AuditFailureReason.NOT_ADMIN,
                "192.0.2.10",
                "Mozilla/5.0",
                "/api/admin/check",
                "0123456789abcdef"));

        AuditEvent found = find(saved.getAuditEventId());

        assertThat(saved.getAuditEventId()).isPositive();
        assertThat(found.getEventType()).isEqualTo(AuditEventType.ACCESS_DENIED);
        assertThat(found.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(found.getEnteredEmail()).isEqualTo("member@example.com");
        assertThat(found.getFailureReason()).isEqualTo(AuditFailureReason.NOT_ADMIN);
        assertThat(found.getSourceIp()).isEqualTo("192.0.2.10");
        assertThat(found.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(found.getRequestPath()).isEqualTo("/api/admin/check");
        assertThat(found.getTraceId()).isEqualTo("0123456789abcdef");
    }

    @Test
    @DisplayName("the optional columns may stay empty")
    void optionalColumnsMayStayEmpty() {
        AuditEvent saved = persist(new AuditEvent(
                Instant.parse("2026-09-22T02:00:00Z"),
                AuditEventType.LOGIN_SUCCEEDED,
                AuditResult.SUCCESS,
                null,
                null,
                "192.0.2.11",
                null,
                null,
                null));

        AuditEvent found = find(saved.getAuditEventId());

        assertThat(found.getEnteredEmail()).isNull();
        assertThat(found.getFailureReason()).isNull();
        assertThat(found.getUserAgent()).isNull();
        assertThat(found.getRequestPath()).isNull();
        assertThat(found.getTraceId()).isNull();
    }

    @Test
    @DisplayName("the required columns are rejected when missing")
    void requiredColumnsAreRejected() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO audit_events (occurred_at, event_type, result, source_ip)"
                                + " VALUES (?, 'LOGIN_FAILED', 'FAILURE', NULL)",
                        OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("occurred_at is stored as an instant and does not shift with the JVM time zone")
    void occurredAtIsAnInstant() {
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Tokyo"));
        Instant occurredAt = Instant.parse("2026-09-22T15:30:00Z");

        AuditEvent saved = persist(new AuditEvent(
                occurredAt,
                AuditEventType.LOGGED_OUT,
                AuditResult.SUCCESS,
                "user@example.com",
                null,
                "192.0.2.12",
                null,
                null,
                null));

        OffsetDateTime stored = jdbc.queryForObject(
                "SELECT occurred_at FROM audit_events WHERE audit_event_id = ?",
                OffsetDateTime.class,
                saved.getAuditEventId());

        assertThat(stored.toInstant()).isEqualTo(occurredAt);
        assertThat(find(saved.getAuditEventId()).getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("the table carries exactly one index on the time of the event")
    void oneIndexOnOccurredAt() {
        List<String> columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS"
                        + " WHERE TABLE_NAME = 'AUDIT_EVENTS' AND INDEX_NAME = 'IX_AUDIT_EVENTS_OCCURRED_AT'",
                String.class);

        assertThat(columns).containsExactly("OCCURRED_AT");
    }

    @Test
    @DisplayName("values at the truncation limit, including surrogate pairs, fit into the columns")
    void truncatedValuesFitIntoTheColumns() {
        String email = AuditText.enteredEmail(EMOJI.repeat(400));
        String userAgent = AuditText.userAgent(EMOJI.repeat(700));
        String requestPath = AuditText.requestPath(EMOJI.repeat(700));

        AuditEvent saved = persist(new AuditEvent(
                Instant.parse("2026-09-22T03:00:00Z"),
                AuditEventType.ACCESS_DENIED,
                AuditResult.FAILURE,
                email,
                AuditFailureReason.TOKEN_INVALID,
                "2001:db8:0000:0000:0000:0000:0000:0001",
                userAgent,
                requestPath,
                null));

        AuditEvent found = find(saved.getAuditEventId());

        assertThat(email).hasSize(508);
        assertThat(userAgent).hasSize(1024);
        assertThat(found.getEnteredEmail()).isEqualTo(email);
        assertThat(found.getUserAgent()).isEqualTo(userAgent);
        assertThat(found.getRequestPath()).isEqualTo(requestPath);
    }
}
