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
package cherry.mastersmith.invitation.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * B3 の見せ方（{@code bolt-plan.md}）の結合テスト: 管理者が招待 → 受け手でメールを受ける → リンクのトークンで確かめ → 登録の完了 →
 * 新しい利用者でログイン。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvitationFlowIT {

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

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Test
    @DisplayName("an admin invites, the invitee receives the mail, verifies, completes and logs in")
    void endToEnd() {
        AdminTestUsers users = new AdminTestUsers(userAccountService, port);
        String admin = users.accessToken(users.createAdmin());
        InvitationApi api = new InvitationApi(port);
        String email = "flow-" + UUID.randomUUID() + "@example.com";

        assertThat(api.invite(admin, email, "ja").statusCode()).isEqualTo(201);
        String token = ReceivedMails.lastToken(RECEIVER);
        HttpResponse<String> verified = api.verify(token);
        assertThat(HttpTestClient.json(verified)).isEqualTo(Map.of("email", email, "language", "ja"));
        assertThat(api.complete(token, "招待 された人").statusCode()).isEqualTo(204);
        HttpResponse<String> login = new AuthApi(port).login(email, InvitationApi.PASSWORD);

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(AuthApi.accessToken(login)).isNotBlank();
        assertThat(HttpTestClient.json(api.list(admin, ""))).containsEntry("total", 0);
    }
}
