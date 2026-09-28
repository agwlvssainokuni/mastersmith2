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
package cherry.mastersmith.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 招待の定期の削除の結合テスト（BR11.1・BR11.2、NFR9.10、{@code reliability-design.md} 4節）。注入した時計で境目を決め、cron は
 * テストの設定で止めて削除をテストの中から直接呼ぶ。
 */
@SpringBootTest
@Import(AuthApiTestConfig.class)
class InvitationCleanupIT {

    private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");

    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(90));

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        InvitationTestProperties.register(registry, tempDir, 25, null);
    }

    @Autowired
    InvitationCleanupJob job;

    @Autowired
    InvitationService service;

    @Autowired
    MutableClock clock;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    private long admin;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM invitations");
        clock.set(NOW);
        admin = TestUserAccounts.create(
                userAccountService, "admin-" + UUID.randomUUID() + "@example.com", "テスト用パスワード-0000", true);
    }

    private long insert(String state, Instant invitedAt, Instant expiresAt, Instant endedAt) {
        String email = "row-" + SEQ.incrementAndGet() + "@example.com";
        jdbc.update(
                "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at, expires_at,"
                        + " send_result, state, ended_at) VALUES (?, 'ja', ?, ?, ?, ?, 'SENT', ?, ?)",
                email,
                HexFormat.of().parseHex("%064x".formatted(SEQ.get())),
                admin,
                Timestamp.from(invitedAt),
                Timestamp.from(expiresAt),
                state,
                endedAt == null ? null : Timestamp.from(endedAt));
        return jdbc.queryForObject("SELECT invitation_id FROM invitations WHERE email = ?", Long.class, email);
    }

    private boolean exists(long id) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM invitations WHERE invitation_id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    @Test
    @DisplayName("ended invitations exactly at the cutoff are deleted and one microsecond after are kept")
    void endedBoundary() {
        long at = insert("COMPLETED", CUTOFF.minus(Duration.ofDays(1)), CUTOFF, CUTOFF);
        long cancelled = insert("CANCELLED", CUTOFF.minus(Duration.ofDays(1)), CUTOFF, CUTOFF.minusSeconds(1));
        long after = insert("REPLACED", CUTOFF.minus(Duration.ofDays(1)), CUTOFF, CUTOFF.plusNanos(1000));

        assertThat(job.run()).isEqualTo(2);
        assertThat(exists(at)).isFalse();
        assertThat(exists(cancelled)).isFalse();
        assertThat(exists(after)).isTrue();
    }

    @Test
    @DisplayName("expired pending invitations are deleted by the expiry and valid ones are kept")
    void expiredPendingBoundary() {
        long at = insert("PENDING", CUTOFF.minus(Duration.ofDays(1)), CUTOFF, null);
        long after = insert("PENDING", CUTOFF.minus(Duration.ofDays(1)), CUTOFF.plusNanos(1000), null);
        long valid = insert("PENDING", NOW.minusSeconds(60), NOW.plus(Duration.ofHours(23)), null);

        assertThat(job.run()).isEqualTo(1);
        assertThat(exists(at)).isFalse();
        assertThat(exists(after)).isTrue();
        assertThat(exists(valid)).isTrue();
    }

    @Test
    @DisplayName("more rows than one batch are all deleted in several transactions")
    void moreThanOneBatch() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            int seq = SEQ.incrementAndGet();
            rows.add(new Object[] {
                "bulk-" + seq + "@example.com",
                HexFormat.of().parseHex("%064x".formatted(seq)),
                admin,
                Timestamp.from(CUTOFF.minus(Duration.ofDays(2))),
                Timestamp.from(CUTOFF.minus(Duration.ofDays(1))),
                Timestamp.from(CUTOFF.minusSeconds(1))
            });
        }
        jdbc.batchUpdate(
                "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at, expires_at,"
                        + " send_result, state, ended_at) VALUES (?, 'ja', ?, ?, ?, ?, 'SENT', 'CANCELLED', ?)",
                rows);

        assertThat(job.run()).isEqualTo(1001);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invitations", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("a resent invitation whose new expiry is after the cutoff is kept")
    void resentRowIsKept() {
        long expired = insert("PENDING", CUTOFF.minus(Duration.ofDays(3)), CUTOFF.minus(Duration.ofDays(2)), null);
        jdbc.update(
                "UPDATE invitations SET expires_at = ? WHERE invitation_id = ?",
                Timestamp.from(NOW.plus(Duration.ofHours(24))),
                expired);

        assertThat(job.run()).isZero();
        assertThat(exists(expired)).isTrue();
    }

    @Test
    @DisplayName("audit records pointing to a deleted invitation are kept, and the deleted id is NotFound")
    void auditIsKept() {
        long ended = insert("CANCELLED", CUTOFF.minus(Duration.ofDays(2)), CUTOFF, CUTOFF.minusSeconds(1));
        jdbc.update(
                "INSERT INTO audit_events (occurred_at, event_type, result, source_ip, actor_user_id,"
                        + " target_invitation_id) VALUES (?, 'INVITATION_CANCELLED', 'SUCCESS', '192.0.2.1', ?, ?)",
                Timestamp.from(CUTOFF.minus(Duration.ofDays(1))),
                admin,
                ended);

        assertThat(job.run()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE target_invitation_id = ?", Integer.class, ended))
                .isEqualTo(1);
        RequestOrigin origin = new RequestOrigin("192.0.2.1", null, null);
        assertThat(service.cancel(admin, origin, ended)).isEqualTo(new CancelResult.NotFound());
    }
}
