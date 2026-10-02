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
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.invitation.domain.InvitationToken;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
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
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 招待・送り直し・取り消し・登録の完了で、招待の行の排他の待ちの上限切れが起きても、排他されていた招待の行の値と H2 の内部の例外の
 * 文がログに出ないことの結合テスト（Intent 260930-user-admin の U3、{@code security-design.md} 7.2 の E2〜E4、NFR3.1・NFR3.4）。
 *
 * <p>別の接続（{@link RowLockHolder}）で招待の行を持ち続けて上限切れを起こし、1つの Spring の文脈の中で、{@code cherry.mastersmith}
 * のロガーを TRACE にした場合と既定の INFO の場合の両方で確かめる。応答は今までどおり 500 {@code INTERNAL_ERROR}。招待の行の値
 * （メールアドレス・招待のトークンのハッシュ値の16進）と、招待のトークンそのものが出力のどの行にも無いことを確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class InvitationLockTimeoutLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    private static final String BASE_URL = "http://lock-leak-base.example.com";

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

    @Autowired
    LoggingSystem loggingSystem;

    /** 1回分の結果。 */
    private record Run(String logs, List<HttpResponse<String>> responses, String[] secrets) {}

    private Run run(CapturedOutput output) throws Exception {
        String adminEmail = "lock-leak-admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = TestDatabase.randomSecret();
        TestUserAccounts.create(userAccountService, adminEmail, adminPassword, true);
        String admin = AuthApi.accessToken(new AuthApi(port).login(adminEmail, adminPassword));
        InvitationApi api = new InvitationApi(port);
        String invitee = "lock-leak-invitee-" + TestDatabase.randomSecret() + "@example.com";
        HttpResponse<String> created = api.invite(admin, invitee, "ja");
        assertThat(created.statusCode()).isEqualTo(201);
        String token = ReceivedMails.lastToken(RECEIVER);
        long id = ((Number) HttpTestClient.json(created).get("invitationId")).longValue();
        String password = TestDatabase.randomSecret() + "Aa";
        String displayName = "Lock " + TestDatabase.randomSecret();
        int offset = output.getOut().length();

        List<HttpResponse<String>> responses = new ArrayList<>();
        try (RowLockHolder holder = RowLockHolder.hold(
                TestDatabase.url(tempDir),
                "SELECT invitation_id FROM invitations WHERE invitation_id = ? FOR UPDATE",
                id)) {
            assertThat(holder.lockedRows()).isEqualTo(1);
            responses.add(api.invite(admin, invitee, "ja"));
            responses.add(api.resend(admin, id));
            responses.add(api.cancel(admin, id));
            responses.add(api.completeWith(token, displayName, password, password, "ja"));
        }
        String logs = output.getOut().substring(offset) + output.getErr();

        String hash = HexFormat.of().formatHex(new InvitationToken(token).hash());
        String[] secrets = {
            invitee, token, hash, hash.toUpperCase(Locale.ROOT), password, displayName, adminEmail, adminPassword
        };
        return new Run(logs, responses, secrets);
    }

    private static void assertNoLeak(Run run) {
        assertThat(run.responses()).extracting(HttpResponse::statusCode).containsExactly(500, 500, 500, 500);
        for (HttpResponse<String> response : run.responses()) {
            assertThat(HttpTestClient.json(response)).containsEntry("code", "INTERNAL_ERROR");
            assertThat(response.body()).doesNotContain(run.secrets());
        }
        JsonLogRecords.assertContainsNoSecret(run.logs(), run.secrets());
        assertThat(run.logs()).doesNotContain("MVStoreException").doesNotContain("register#token");
        List<Map<String, Object>> records = JsonLogRecords.parse(run.logs());
        assertThat(records)
                .filteredOn(r -> "WARN".equals(r.get("level")) && "INVITATION_ROW".equals(r.get("lockKind")))
                .as("招待の行の排他の経路は WARN に排他の種類とクラスの名前を出す")
                .hasSize(4)
                .allSatisfy(r -> assertThat(r).containsKey("exceptionClass").doesNotContainKey("exception"));
        List<Map<String, Object>> errors = records.stream()
                .filter(r -> "ERROR".equals(r.get("level")))
                .filter(r -> String.valueOf(r.get("logger")).endsWith("GlobalExceptionHandler"))
                .toList();
        assertThat(errors).as("想定外の誤りの ERROR は要求ごとに1件").hasSize(4);
        assertThat(errors)
                .allSatisfy(r -> assertThat(String.valueOf(r.get("exception")))
                        .startsWith("cherry.mastersmith.common.persistence.RowLockUnavailableException")
                        .doesNotContain("Caused by"));
    }

    @Test
    @DisplayName("with TRACE, lock timeouts of invite, resend, cancel and registration leak no invitation row value")
    void traceLevel(CapturedOutput output) throws Exception {
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        Run run;
        try {
            run = run(output);
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }

        assertNoLeak(run);
        assertThat(run.logs()).as("メソッドの呼び出しの追跡が有効").contains("ENTER InvitationService#invite");
        assertThat(run.logs())
                .contains("EXCEPTION InvitationService#invite")
                .contains("EXCEPTION InvitationService#resend")
                .contains("EXCEPTION InvitationService#cancel")
                .contains("EXCEPTION RegistrationService#complete");
        assertThat(run.logs())
                .as("断片の実装（invitation.lock）は追跡の対象の外で、トークンのハッシュの引数が追跡に出ない")
                .doesNotContain("InvitationLockQueriesImpl#");
    }

    @Test
    @DisplayName("with the default INFO level, lock timeouts of invite, resend, cancel and registration leak no value")
    void infoLevel(CapturedOutput output) throws Exception {
        Run run = run(output);

        assertNoLeak(run);
        assertThat(run.logs()).doesNotContain("ENTER ");
    }
}
