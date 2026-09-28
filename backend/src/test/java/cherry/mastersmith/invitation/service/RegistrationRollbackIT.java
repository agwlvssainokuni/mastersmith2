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
import static org.assertj.core.api.Assertions.assertThatCode;

import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
 * 登録の完了の時点で同じメールアドレスの利用者がいるときの巻き戻しの結合テスト（BR7.4・BR8.3、{@code reliability-design.md} 3節）。
 * この経路の性能は Unverified のまま（正しさだけを確かめる。{@code performance-design.md} 4節）。
 */
@SpringBootTest
@Import(AuthApiTestConfig.class)
class RegistrationRollbackIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.9", "agent", null);

    private static final String PASSWORD = "テスト用パスワード-0000";

    private static final SmtpTestServer RECEIVER = SmtpTestServer.builder().start();

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        InvitationTestProperties.register(registry, tempDir, RECEIVER.port(), "http://localhost:8080");
    }

    @AfterAll
    static void stop() {
        RECEIVER.close();
    }

    @Autowired
    InvitationService invitations;

    @Autowired
    RegistrationService registrations;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    private record Invited(long invitationId, String email, String token) {}

    private Invited invite() {
        long admin = TestUserAccounts.create(
                userAccountService, "admin-" + UUID.randomUUID() + "@example.com", PASSWORD, true);
        String email = "invitee-" + UUID.randomUUID() + "@example.com";
        InviteResult.Invited result =
                (InviteResult.Invited) invitations.invite(admin, ORIGIN, new InviteCommand(email, "ja"));
        return new Invited(result.summary().invitationId(), email, ReceivedMails.lastToken(RECEIVER));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    @Test
    @DisplayName(
            "a user registered in the meantime rolls back without UnexpectedRollbackException and audits the reason")
    void rollsBack() {
        Invited invited = invite();
        TestUserAccounts.create(userAccountService, invited.email(), PASSWORD, false);
        int users = count("SELECT COUNT(*) FROM users");

        assertThatCode(() -> assertThat(registrations.complete(
                                ORIGIN,
                                new RegistrationCommand(
                                        invited.token(), "山田 花子", PASSWORD, PASSWORD, "ja", "system", "md")))
                        .isEqualTo(new CompleteResult.Rejected()))
                .doesNotThrowAnyException();

        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(users);
        assertThat(jdbc.queryForMap(
                        "SELECT state, completed_user_id FROM invitations WHERE invitation_id = ?",
                        invited.invitationId()))
                .containsEntry("STATE", "PENDING")
                .containsEntry("COMPLETED_USER_ID", null);
        assertThat(count(
                        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'REGISTRATION_COMPLETED'"
                                + " AND target_invitation_id = ?",
                        invited.invitationId()))
                .isZero();
        Map<String, Object> failed = jdbc.queryForMap(
                "SELECT result, failure_reason, actor_user_id, target_user_id FROM audit_events"
                        + " WHERE event_type = 'REGISTRATION_FAILED' AND target_invitation_id = ?",
                invited.invitationId());
        assertThat(failed)
                .containsEntry("RESULT", "FAILURE")
                .containsEntry("FAILURE_REASON", "EMAIL_ALREADY_REGISTERED")
                .containsEntry("ACTOR_USER_ID", null)
                .containsEntry("TARGET_USER_ID", null);
    }

    @Test
    @DisplayName("the link of an email registered in the meantime is also refused by verify without auditing")
    void verifyAlsoRefuses() {
        Invited invited = invite();
        TestUserAccounts.create(userAccountService, invited.email(), PASSWORD, false);
        int audits = count("SELECT COUNT(*) FROM audit_events");

        assertThat(registrations.verify(new TokenCommand(invited.token()))).isEqualTo(new VerifyResult.Rejected());
        assertThat(count("SELECT COUNT(*) FROM audit_events")).isEqualTo(audits);
    }
}
