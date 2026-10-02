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
package cherry.mastersmith.invitation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.invitation.domain.Invitation;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationState;
import cherry.mastersmith.invitation.domain.SendResult;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
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

/**
 * 招待の表（V8）とエンティティの結合テスト（組み込みの H2、Hibernate の {@code validate} の上。{@code reliability-design.md} 2.3）。
 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
class InvitationSchemaIT {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

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

    @Autowired
    UserAccountService userAccountService;

    private long admin() {
        return TestUserAccounts.create(
                userAccountService, "admin-" + UUID.randomUUID() + "@example.com", "テスト用パスワード-0000", true);
    }

    /** 招待を JDBC で1行追記する（トークンのハッシュは連番から作る）。 */
    private void insert(long adminId, String email, String state, int seq) {
        jdbc.update(
                "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at, expires_at,"
                        + " send_result, state) VALUES (?, 'ja', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PENDING', ?)",
                email,
                HexFormat.of().parseHex("%064x".formatted(seq)),
                adminId,
                state);
    }

    /** 原因の連なりのうち、最初の {@link SQLException} を返す（無ければ null）。 */
    private static SQLException sqlException(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql;
            }
        }
        return null;
    }

    private Invitation persist(String email, long adminId, int seq) {
        return tx.execute(status -> {
            Invitation invitation = new Invitation(
                    new InvitationEmail(email),
                    Language.EN,
                    HexFormat.of().parseHex("%064x".formatted(seq)),
                    adminId,
                    NOW,
                    NOW.plus(Duration.ofHours(24)));
            entityManager.persist(invitation);
            return invitation;
        });
    }

    @Test
    @DisplayName("the application starts with Hibernate validate after V8 and V8 is recorded as applied")
    void migrationApplied() {
        assertThat(jdbc.queryForObject(
                        "SELECT \"success\" FROM \"flyway_schema_history\" WHERE \"version\" = '8'", Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("an invitation entity is saved and read back and the pending email is computed by the database")
    void entityMatchesSchema() {
        long adminId = admin();
        String email = "invitee-" + UUID.randomUUID() + "@example.com";
        Invitation saved = persist(email, adminId, 1);

        Invitation read = tx.execute(status -> entityManager.find(Invitation.class, saved.getInvitationId()));

        assertThat(read.getEmail().value()).isEqualTo(email);
        assertThat(read.getLanguage()).isEqualTo(Language.EN);
        assertThat(read.getState()).isEqualTo(InvitationState.PENDING);
        assertThat(read.getSendResult()).isEqualTo(SendResult.PENDING);
        assertThat(read.getInvitedByUserId()).isEqualTo(adminId);
        assertThat(read.getInvitedAt()).isEqualTo(NOW);
        assertThat(read.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(read.getTokenHash()).hasSize(32);
        assertThat(read.getEndedAt()).isNull();
        assertThat(read.getCompletedUserId()).isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT pending_email FROM invitations WHERE invitation_id = ?",
                        String.class,
                        saved.getInvitationId()))
                .isEqualTo(email);
    }

    @Test
    @DisplayName("state changes through the entity update the pending email, and a second pending row is rejected")
    void stateChangesAndUniqueness() {
        long adminId = admin();
        String email = "invitee-" + UUID.randomUUID() + "@example.com";
        Invitation first = persist(email, adminId, 2);

        // EntityManager を直に使うため、DB アクセスの層の例外の読み替えを通らない（層の中では一意の違反の例外になる）。
        assertThatThrownBy(() -> persist(email, adminId, 3))
                .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class)
                .satisfies(e -> {
                    // SQLException は Iterable でもあるため、assertThat に直に渡さず項目を確かめる。
                    SQLException sql = sqlException(e);
                    assertThat((Object) sql).as("原因の連なりの SQLException").isNotNull();
                    assertThat(sql.getSQLState()).isEqualTo("23505");
                    assertThat(sql.getMessage()).contains("UK_INVITATIONS_PENDING_EMAIL");
                });

        tx.executeWithoutResult(status ->
                entityManager.find(Invitation.class, first.getInvitationId()).cancel(NOW.plusSeconds(1)));
        assertThat(jdbc.queryForMap(
                        "SELECT state, pending_email, ended_at FROM invitations WHERE invitation_id = ?",
                        first.getInvitationId()))
                .containsEntry("STATE", "CANCELLED")
                .containsEntry("PENDING_EMAIL", null);
        Invitation second = persist(email, adminId, 4);
        tx.executeWithoutResult(status ->
                entityManager.find(Invitation.class, second.getInvitationId()).complete(adminId, NOW));
        Invitation completed = tx.execute(status -> entityManager.find(Invitation.class, second.getInvitationId()));
        assertThat(completed.getState()).isEqualTo(InvitationState.COMPLETED);
        assertThat(completed.getCompletedUserId()).isEqualTo(adminId);
        assertThatThrownBy(() -> completed.cancel(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName(
            "rows that are not PENDING may share the email, have no pending email, and an unknown state is rejected")
    void endedRowsMayRepeat() {
        long adminId = admin();
        String email = "ended-" + UUID.randomUUID() + "@example.com";

        insert(adminId, email, "CANCELLED", 101);
        insert(adminId, email, "REPLACED", 102);
        insert(adminId, email, "COMPLETED", 103);
        insert(adminId, email, "PENDING", 104);

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM invitations WHERE email = ? AND pending_email IS NULL",
                        Integer.class,
                        email))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM invitations WHERE pending_email = ?", Integer.class, email))
                .isEqualTo(1);
        String other = "unknown-state-" + UUID.randomUUID() + "@example.com";
        assertThatThrownBy(() -> insert(adminId, other, "EXPIRED", 105)).satisfies(e -> {
            SQLException sql = sqlException(e);
            assertThat((Object) sql).as("原因の連なりの SQLException").isNotNull();
            assertThat(sql.getSQLState()).isEqualTo("23513");
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invitations WHERE email = ?", Integer.class, other))
                .isZero();
    }
}
