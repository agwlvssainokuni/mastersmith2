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
import cherry.mastersmith.invitation.testsupport.HookedMailSender;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
 * 送信の結果の記録の結合テスト（BR4.3・BR4.4、{@code reliability-design.md} 6節）。送信の最中にもう一度送り直された招待では古い結果で
 * 書き換えないこと、記録の前に止まった形（PENDING のまま）が一覧で FAILED に見えることを確かめる。
 *
 * <p>送信の最中の送り直しは、本物の送信の部品の包み（{@link HookedMailSender}）で1回目の送信の代わりに送り直しを別のスレッドで確定させ、
 * 1回目の送信の結果を FAILED として返して作る（2回目の送り直しは本物の送信の部品で送る）。
 */
@SpringBootTest
@Import({AuthApiTestConfig.class, HookedMailSender.Config.class})
class InvitationSendResultIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.1", "agent", null);

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
    InvitationService service;

    @Autowired
    HookedMailSender mailSender;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    private long admin() {
        return TestUserAccounts.create(
                userAccountService, "admin-" + UUID.randomUUID() + "@example.com", "テスト用パスワード-0000", true);
    }

    @Test
    @DisplayName("a result of a send that was overtaken by another resend does not overwrite the newer result")
    void olderResultDoesNotOverwrite() throws Exception {
        long admin = admin();
        String email = "invitee-" + UUID.randomUUID() + "@example.com";
        InviteResult.Invited invited =
                (InviteResult.Invited) service.invite(admin, ORIGIN, new InviteCommand(email, "ja"));
        long id = invited.summary().invitationId();
        AtomicReference<ResendResult> inner = new AtomicReference<>();
        mailSender.onNextSend(() -> {
            try {
                inner.set(CompletableFuture.supplyAsync(() -> service.resend(admin, ORIGIN, id))
                        .get(20, TimeUnit.SECONDS));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return MailSendResult.failed(MailFailureKind.CONNECTION_FAILED);
        });

        ResendResult outer = service.resend(admin, ORIGIN, id);

        assertThat(outer)
                .isInstanceOfSatisfying(
                        ResendResult.Resent.class,
                        r -> assertThat(r.summary().sendResult()).isEqualTo("FAILED"));
        assertThat(inner.get())
                .isInstanceOfSatisfying(
                        ResendResult.Resent.class,
                        r -> assertThat(r.summary().sendResult()).isEqualTo("SENT"));
        assertThat(jdbc.queryForObject("SELECT send_result FROM invitations WHERE invitation_id = ?", String.class, id))
                .as("新しい送り直しの結果（SENT）が古い結果（FAILED）で上書きされない")
                .isEqualTo("SENT");
    }

    @Test
    @DisplayName("an invitation stopped before recording (send result PENDING) is shown as FAILED in the list")
    void pendingIsShownAsFailed() {
        long admin = admin();
        String email = "stopped-" + UUID.randomUUID() + "@example.com";
        jdbc.update(
                "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at, expires_at,"
                        + " send_result, state) VALUES (?, 'ja', ?, ?, CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP),"
                        + " 'PENDING', 'PENDING')",
                email,
                HexFormat.of().parseHex("%064x".formatted(Math.abs(email.hashCode()))),
                admin);

        ListResult result = service.list(null);

        assertThat(result)
                .isInstanceOfSatisfying(
                        ListResult.Listed.class,
                        listed -> assertThat(listed.page().items())
                                .filteredOn(item -> item.email().equals(email))
                                .singleElement()
                                .satisfies(item -> assertThat(item.sendResult()).isEqualTo("FAILED")));
        assertThat(jdbc.queryForObject("SELECT send_result FROM invitations WHERE email = ?", String.class, email))
                .isEqualTo("PENDING");
    }
}
