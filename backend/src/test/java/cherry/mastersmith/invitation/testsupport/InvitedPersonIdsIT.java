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
package cherry.mastersmith.invitation.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link InvitedPersonIds} の結合テスト。InvitedPersonAuthenticationIT が招待の ID をメールアドレスのハッシュ値の 1,000 の枠で
 * 選んでいたため、同じ内部DB の別の招待と ID が重なり主キーの違反で落ちた不具合の再現のテスト。
 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
class InvitedPersonIdsIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserAccountService userAccountService;

    private long adminId;

    @BeforeEach
    void setUp() {
        adminId = TestUserAccounts.create(
                userAccountService, "admin-" + UUID.randomUUID() + "@example.com", "テスト用パスワード-0000", true);
    }

    @Test
    @DisplayName("two invitations whose emails fall into the same old slot get different ids without a key violation")
    void sameOldSlotDoesNotCollide() {
        List<String> emails = twoEmailsInTheSameOldSlot();
        insertInvitation(emails.get(0), 1);
        insertInvitation(emails.get(1), 2);

        long first = InvitedPersonIds.reassign(jdbc, emails.get(0));
        long second = InvitedPersonIds.reassign(jdbc, emails.get(1));

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isGreaterThanOrEqualTo(InvitedPersonIds.BASE);
        assertThat(second).isGreaterThanOrEqualTo(InvitedPersonIds.BASE);
        assertThat(jdbc.queryForObject(
                        "SELECT invitation_id FROM invitations WHERE email = ?", Long.class, emails.get(0)))
                .isEqualTo(first);
        assertThat(jdbc.queryForObject(
                        "SELECT invitation_id FROM invitations WHERE email = ?", Long.class, emails.get(1)))
                .isEqualTo(second);
    }

    @Test
    @DisplayName("the reassigned id is not used by any user")
    void reassignedIdIsNotAUserId() {
        String email = "invited-" + UUID.randomUUID() + "@example.com";
        insertInvitation(email, 3);

        long id = InvitedPersonIds.reassign(jdbc, email);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id = ?", Integer.class, id))
                .isZero();
    }

    /** 古い選び方（下限＋ハッシュ値の 1,000 の剰余）で同じ枠に入る2つのメールアドレスを、乱数に頼らずに探す。 */
    private static List<String> twoEmailsInTheSameOldSlot() {
        Map<Integer, String> seen = new HashMap<>();
        for (int i = 0; ; i++) {
            String email = "same-slot-" + i + "@example.com";
            String earlier = seen.putIfAbsent(Math.abs(email.hashCode() % 1000), email);
            if (earlier != null) {
                return List.of(earlier, email);
            }
        }
    }

    private void insertInvitation(String email, int seq) {
        jdbc.update(
                "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at, expires_at,"
                        + " send_result, state) VALUES (?, 'ja', ?, ?, CURRENT_TIMESTAMP,"
                        + " DATEADD('DAY', 1, CURRENT_TIMESTAMP), 'SENT', 'PENDING')",
                email,
                HexFormat.of().parseHex("%064x".formatted(0x7a000000 + seq)),
                adminId);
    }
}
