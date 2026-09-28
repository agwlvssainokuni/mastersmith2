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
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.invitation.testsupport.TestInvitationBarrier;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
 * 同じ招待への同時の操作の結合テスト（BR6.4、AC2.2.13・AC3.2.7、{@code reliability-design.md} 3節）。先の操作が行の排他を得た直後に、
 * 後の操作を別のスレッドで始めて排他の待ちに入らせ、先の操作の確定の後に後の操作が新しい状態で判定されることを確かめる。
 */
@SpringBootTest
@Import({AuthApiTestConfig.class, TestInvitationBarrier.Config.class})
class RegistrationConcurrencyIT {

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
    TestInvitationBarrier barrier;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void reset() {
        barrier.reset();
    }

    private record Invited(long admin, long invitationId, String email, String token) {}

    private Invited invite() {
        long admin = TestUserAccounts.create(
                userAccountService, "admin-" + UUID.randomUUID() + "@example.com", PASSWORD, true);
        String email = "invitee-" + UUID.randomUUID() + "@example.com";
        InviteResult.Invited result =
                (InviteResult.Invited) invitations.invite(admin, ORIGIN, new InviteCommand(email, "ja"));
        return new Invited(admin, result.summary().invitationId(), email, ReceivedMails.lastToken(RECEIVER));
    }

    private CompleteResult complete(String token) {
        return registrations.complete(
                ORIGIN, new RegistrationCommand(token, "山田 花子", PASSWORD, PASSWORD, "ja", "system", "md"));
    }

    private int users(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count == null ? 0 : count;
    }

    private String lastFailureReason() {
        return jdbc.queryForObject(
                "SELECT failure_reason FROM audit_events WHERE event_type = 'REGISTRATION_FAILED'"
                        + " ORDER BY audit_event_id DESC FETCH FIRST 1 ROWS ONLY",
                String.class);
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(TestInvitationBarrier.TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("two completions of the same invitation create one user and the later one is ALREADY_USED")
    void twoCompletions() throws Exception {
        Invited invited = invite();
        CompletableFuture<CompleteResult> later = barrier.whileLocked(() -> complete(invited.token()));

        assertThat(complete(invited.token())).isEqualTo(new CompleteResult.Completed());

        assertThat(await(later)).isEqualTo(new CompleteResult.Rejected());
        assertThat(users(invited.email())).isEqualTo(1);
        assertThat(lastFailureReason()).isEqualTo("INVITATION_ALREADY_USED");
    }

    @Test
    @DisplayName("when the cancellation comes first no user is created and the completion is CANCELLED")
    void cancelFirst() throws Exception {
        Invited invited = invite();
        CompletableFuture<CompleteResult> later = barrier.whileLocked(() -> complete(invited.token()));

        assertThat(invitations.cancel(invited.admin(), ORIGIN, invited.invitationId()))
                .isEqualTo(new CancelResult.Cancelled());

        assertThat(await(later)).isEqualTo(new CompleteResult.Rejected());
        assertThat(users(invited.email())).isZero();
        assertThat(lastFailureReason()).isEqualTo("INVITATION_CANCELLED");
    }

    @Test
    @DisplayName("when the resend comes first the completion with the previous token is refused as not found")
    void resendFirst() throws Exception {
        Invited invited = invite();
        CompletableFuture<CompleteResult> later = barrier.whileLocked(() -> complete(invited.token()));

        assertThat(invitations.resend(invited.admin(), ORIGIN, invited.invitationId()))
                .isInstanceOf(ResendResult.Resent.class);

        assertThat(await(later)).isEqualTo(new CompleteResult.Rejected());
        assertThat(users(invited.email())).isZero();
        assertThat(lastFailureReason()).isEqualTo("INVITATION_NOT_FOUND");
        assertThat(complete(ReceivedMails.lastToken(RECEIVER))).isEqualTo(new CompleteResult.Completed());
    }

    @Test
    @DisplayName("when the completion comes first a concurrent cancel and a concurrent resend are NotFound")
    void completionFirst() throws Exception {
        Invited first = invite();
        CompletableFuture<CancelResult> cancel =
                barrier.whileLocked(() -> invitations.cancel(first.admin(), ORIGIN, first.invitationId()));
        assertThat(complete(first.token())).isEqualTo(new CompleteResult.Completed());
        assertThat(await(cancel)).isEqualTo(new CancelResult.NotFound());

        Invited second = invite();
        CompletableFuture<ResendResult> resend =
                barrier.whileLocked(() -> invitations.resend(second.admin(), ORIGIN, second.invitationId()));
        assertThat(complete(second.token())).isEqualTo(new CompleteResult.Completed());
        assertThat(await(resend)).isEqualTo(new ResendResult.NotFound());
        assertThat(users(first.email()) + users(second.email())).isEqualTo(2);
    }
}
