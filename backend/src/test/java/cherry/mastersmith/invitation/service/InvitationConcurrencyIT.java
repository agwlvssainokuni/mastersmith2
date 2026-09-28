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
import cherry.mastersmith.invitation.testsupport.TestInvitationBarrier;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
 * 同じメールアドレスへの同時の招待の結合テスト（BR2.5、NFR9.8、AC1.1.13、{@code reliability-design.md} 2.2・2.3）。待ち合わせの口で
 * 2つの要求を招待の追記の直前にそろえ、生成列の一意の制約の上で1件だけ作られることを確かめる。
 */
@SpringBootTest
@Import({AuthApiTestConfig.class, TestInvitationBarrier.Config.class})
class InvitationConcurrencyIT {

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
    TestInvitationBarrier barrier;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("two simultaneous invitations to the same email create one, the other is AlreadyPending with its id")
    void onlyOneIsCreated() throws Exception {
        long admin = TestUserAccounts.create(
                userAccountService, "admin-" + UUID.randomUUID() + "@example.com", "テスト用パスワード-0000", true);
        String email = "invitee-" + UUID.randomUUID() + "@example.com";
        int mailsBefore = RECEIVER.messages().size();
        barrier.insertTogether(2);

        CompletableFuture<InviteResult> first =
                CompletableFuture.supplyAsync(() -> service.invite(admin, ORIGIN, new InviteCommand(email, "ja")));
        CompletableFuture<InviteResult> second =
                CompletableFuture.supplyAsync(() -> service.invite(admin, ORIGIN, new InviteCommand(email, "en")));
        List<InviteResult> results = List.of(
                first.get(TestInvitationBarrier.TIMEOUT_SECONDS, TimeUnit.SECONDS),
                second.get(TestInvitationBarrier.TIMEOUT_SECONDS, TimeUnit.SECONDS));

        InviteResult.Invited invited = results.stream()
                .filter(InviteResult.Invited.class::isInstance)
                .map(InviteResult.Invited.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(results)
                .filteredOn(InviteResult.AlreadyPending.class::isInstance)
                .singleElement()
                .isEqualTo(new InviteResult.AlreadyPending(invited.summary().invitationId(), 1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invitations WHERE email = ?", Integer.class, email))
                .isEqualTo(1);
        assertThat(RECEIVER.messages().size() - mailsBefore).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'INVITATION_ISSUED' AND target_invitation_id = ?",
                        Integer.class,
                        invited.summary().invitationId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'INVITATION_ISSUED'", Integer.class))
                .isEqualTo(1);
    }
}
