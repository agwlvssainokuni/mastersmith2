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
package cherry.mastersmith.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.auth.domain.LoginAttemptState;
import cherry.mastersmith.auth.domain.RefreshToken;
import cherry.mastersmith.common.testsupport.TestDatabase;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
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

/** ロックの状態とリフレッシュトークンの表（V3）の結合テスト（組み込みの H2）。 */
@SpringBootTest
class AuthSchemaIT {

    private static final SecureRandom RANDOM = new SecureRandom();

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

    private long insertUser() {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        jdbc.update(
                "INSERT INTO users (email, password_hash, admin_flag, created_at) VALUES (?, 'x', FALSE, ?)",
                email,
                OffsetDateTime.now());
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
    }

    private static byte[] randomHash() {
        byte[] hash = new byte[32];
        RANDOM.nextBytes(hash);
        return hash;
    }

    private RefreshToken persistToken(long userId, byte[] hash, Instant issuedAt, Instant expiresAt) {
        return tx.execute(status -> {
            RefreshToken token = new RefreshToken(userId, hash, issuedAt, expiresAt);
            entityManager.persist(token);
            return token;
        });
    }

    @Test
    @DisplayName("eight dummy rows exist with negative ids that never overlap user ids")
    void dummyRows() {
        List<Long> ids = jdbc.queryForList(
                "SELECT subject_id FROM login_attempt_states WHERE subject_id < 0 ORDER BY subject_id DESC",
                Long.class);
        long userId = insertUser();

        assertThat(ids).containsExactly(-1L, -2L, -3L, -4L, -5L, -6L, -7L, -8L);
        assertThat(userId).isPositive();
        LoginAttemptState dummy = tx.execute(status -> entityManager.find(LoginAttemptState.class, -1L));
        assertThat(dummy.getConsecutiveFailures()).isZero();
        assertThat(dummy.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("a negative failure count is rejected by the check constraint")
    void negativeFailuresRejected() {
        long userId = insertUser();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO login_attempt_states (subject_id, consecutive_failures) VALUES (?, -1)", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the same token hash cannot be stored twice")
    void tokenHashIsUnique() {
        long userId = insertUser();
        byte[] hash = randomHash();
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        persistToken(userId, hash, now, now.plusSeconds(60));

        assertThatThrownBy(() -> persistToken(userId, hash, now, now.plusSeconds(60)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("a refresh token must belong to an existing user")
    void tokenRequiresUser() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");

        assertThatThrownBy(() -> persistToken(Long.MAX_VALUE, randomHash(), now, now.plusSeconds(60)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("token times and lock times are stored as instants regardless of the JVM time zone")
    void timesAreInstants() {
        long userId = insertUser();
        Instant issuedAt = Instant.parse("2026-09-22T15:30:00Z");
        Instant expiresAt = issuedAt.plusSeconds(86_400);
        RefreshToken saved = persistToken(userId, randomHash(), issuedAt, expiresAt);
        Instant lockedUntil = Instant.parse("2026-09-22T16:00:00Z");
        tx.executeWithoutResult(status -> entityManager.persist(new LoginAttemptState(userId, 5, lockedUntil)));

        RefreshToken found = tx.execute(status -> entityManager.find(RefreshToken.class, saved.getTokenId()));
        LoginAttemptState state = tx.execute(status -> entityManager.find(LoginAttemptState.class, userId));
        OffsetDateTime rawExpires = jdbc.queryForObject(
                "SELECT expires_at FROM refresh_tokens WHERE token_id = ?", OffsetDateTime.class, saved.getTokenId());

        assertThat(found.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(found.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(found.getRevokedAt()).isNull();
        assertThat(found.getTokenHash()).hasSize(32);
        assertThat(rawExpires.toInstant()).isEqualTo(expiresAt);
        assertThat(state.getLockedUntil()).isEqualTo(lockedUntil);
        assertThat(state.getConsecutiveFailures()).isEqualTo(5);
    }
}
