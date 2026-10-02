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

import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.invitation.domain.InvitationToken;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationApps;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.mail.testsupport.MailTestApplication;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 招待と登録の完了で、トークン・ハッシュ・招待の URL・パスワード・メールアドレスが漏れないことの結合テスト（BR3.1・BR8.6・BR9.4、
 * NFR1.5・NFR2.1・NFR2.2、team.md のトークンと URL の漏えい、計画の決定 3）。
 *
 * <p>{@code cherry.mastersmith} のロガーを TRACE にしてメソッドの呼び出しの追跡を有効にし、招待・送り直し・リンクの確かめ・完了の成功と
 * 拒否・送信の失敗の間に出たログ・監査の行（全列）・エラー応答を確かめる。前準備のログインと初期管理者の作成は範囲の外（どちらの
 * メールアドレスの TRACE の確かめも、Intent 260930-user-admin の B1 の {@code AuthSuspensionSecretLeakIT} が受け持つ）。値は ASCII の乱数にして JSON の書き方に左右されずに探せるようにする。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.cherry.mastersmith=TRACE")
@ExtendWith(OutputCaptureExtension.class)
class InvitationSecretLeakIT {

    private static final String BASE_URL = "http://leak-base.example.com";

    private static final SmtpTestServer RECEIVER = SmtpTestServer.builder().start();

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        InvitationTestProperties.register(registry, tempDir, RECEIVER.port(), BASE_URL);
    }

    @AfterAll
    static void stop() {
        RECEIVER.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    private static String[] secretsOf(String token, String... others) {
        byte[] hash = new InvitationToken(token).hash();
        List<String> secrets = new ArrayList<>(List.of(others));
        secrets.add(token);
        secrets.add(HexFormat.of().formatHex(hash));
        secrets.add(HexFormat.of().formatHex(hash).toUpperCase(Locale.ROOT));
        secrets.add(BASE_URL + "/register#token=" + token);
        return secrets.toArray(String[]::new);
    }

    @Test
    @DisplayName("invite, resend, verify and complete (success and refusal) leak no secret to logs, audit or errors")
    void noSecretLeaks(CapturedOutput output) {
        String adminEmail = "leak-check-admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = TestDatabase.randomSecret();
        String invitee = "leak-check-invitee-" + UUID.randomUUID() + "@example.com";
        String password = TestDatabase.randomSecret() + "Aa";
        TestUserAccounts.create(userAccountService, adminEmail, adminPassword, true);
        String admin = AuthApi.accessToken(new AuthApi(port).login(adminEmail, adminPassword));
        InvitationApi api = new InvitationApi(port);
        int offset = output.getOut().length();

        HttpResponse<String> created = api.invite(admin, invitee, "ja");
        String first = ReceivedMails.lastToken(RECEIVER);
        long id = ((Number) HttpTestClient.json(created).get("invitationId")).longValue();
        HttpResponse<String> resent = api.resend(admin, id);
        String token = ReceivedMails.lastToken(RECEIVER);
        List<HttpResponse<String>> errors = new ArrayList<>();
        errors.add(api.invite(admin, invitee, "ja"));
        errors.add(api.invite(admin, invitee + "\r\n", "ja"));
        HttpResponse<String> verified = api.verify(token);
        errors.add(api.verify(first));
        errors.add(api.completeWith(first, "漏れの 確かめ", password, password, "ja"));
        errors.add(api.completeWith(token, "漏れの 確かめ", password, password + "x", "ja"));
        HttpResponse<String> completed = api.completeWith(token, "漏れの 確かめ", password, password, "ja");
        errors.add(api.completeWith(token, "漏れの 確かめ", password, password, "ja"));

        assertThat(List.of(created.statusCode(), resent.statusCode(), verified.statusCode(), completed.statusCode()))
                .containsExactly(201, 200, 200, 204);
        assertThat(errors).extracting(HttpResponse::statusCode).containsExactly(409, 400, 404, 404, 400, 404);
        String logs = output.getOut().substring(offset);
        assertThat(logs).as("メソッドの呼び出しの追跡が有効").contains("ENTER InvitationService#invite");
        assertThat(logs).contains("ENTER UserAccountService#createUser").contains("ENTER RegistrationService#complete");
        JsonLogRecords.assertAllLinesAreJson(logs);
        String[] secrets = secretsOf(token, password, invitee, adminEmail, adminPassword);
        JsonLogRecords.assertContainsNoSecret(logs, secrets);
        JsonLogRecords.assertContainsNoSecret(logs, secretsOf(first));
        assertThat(logs).doesNotContain("register#token").doesNotContain("$2a$");
        for (HttpResponse<String> response : errors) {
            assertThat(response.body()).doesNotContain(secrets).doesNotContain(first);
        }
        for (HttpResponse<String> response : List.of(created, resent)) {
            assertThat(response.body())
                    .doesNotContain(token)
                    .doesNotContain(first)
                    .doesNotContain("register#token");
        }
        List<Map<String, Object>> auditRows = jdbc.queryForList(
                "SELECT * FROM audit_events WHERE event_type LIKE 'INVITATION_%' OR event_type LIKE 'REGISTRATION_%'");
        assertThat(auditRows).hasSize(5);
        assertThat(auditRows.toString())
                .doesNotContain(secrets)
                .doesNotContain(first)
                .doesNotContain("$2a$");
    }

    @Test
    @DisplayName("a send failure leaks neither the recipient nor the SMTP reply to logs or responses")
    void sendFailureLeaksNothing(@TempDir Path dir, CapturedOutput output) throws Exception {
        Map<String, String> settings =
                new java.util.LinkedHashMap<>(InvitationApps.mail(MailTestApplication.closedPort(), BASE_URL));
        settings.put("logging.level.cherry.mastersmith", "TRACE");
        try (ConfigurableApplicationContext context = InvitationApps.start(dir, settings)) {
            InvitationApi api = new InvitationApi(InvitationApps.port(context));
            String admin = InvitationApps.adminToken(context);
            String invitee = "leak-check-failure-" + UUID.randomUUID() + "@example.com";
            int offset = output.getOut().length();

            HttpResponse<String> created = api.invite(admin, invitee, "en");

            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(HttpTestClient.json(created)).containsEntry("sendResult", "FAILED");
            String logs = output.getOut().substring(offset);
            assertThat(logs).contains("ENTER InvitationMailDispatcher#dispatch");
            JsonLogRecords.assertContainsNoSecret(logs, invitee, BASE_URL + "/register#token=");
            assertThat(created.body()).doesNotContain("CONNECTION_FAILED").doesNotContain("register#token");
        }
    }
}
