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
package cherry.mastersmith.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.TestSigningKeyEnvironmentPostProcessor;
import cherry.mastersmith.auth.testsupport.TestUserSuspension;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用停止を含む認証の操作で、メソッドの呼び出しの追跡（TRACE）を有効にしても、秘密の値がログに出ず、伏せ字にした6つの型が出る行に
 * メールアドレスと氏名が出ず、入れたメールアドレス・利用者のメールアドレスが出力のどの行にも出ないことの結合テスト（Intent
 * 260930-user-admin の U1、NFR3.1・NFR3.2、BR2.6・BR5.3、project.md の Forbidden）。
 *
 * <p>伏せ字にした型は {@code AuthenticationEvent}・{@code LoginCommand}・{@code AuthenticatedUser}・{@code CurrentUserResponse}・
 * {@code LoginRequest}・{@code PasswordVerification}。{@code UserAccountService#verifyPassword}・{@code #existsByEmail} の
 * メールアドレスの引数は、B1 のレビュー R-01 を受けた依頼者の決定で伏せ字の型（{@code RedactedText}）にしたため、起動（初期管理者の
 * 作成）から最後の要求までの出力の全体で、メールアドレスが無いことを確かめる。既存の {@code AuthSecretLeakIT}（初期管理者の起動の
 * 確かめを兼ねる）は変えない。
 */
@ExtendWith(OutputCaptureExtension.class)
class AuthSuspensionSecretLeakIT {

    private static final String WRONG = "まちがったパスワード-9999";

    @TempDir
    Path tempDir;

    /** 停止中の3つの入口の応答。 */
    private record SuspendedResponses(
            HttpResponse<String> login, HttpResponse<String> refresh, HttpResponse<String> access) {}

    @Test
    @DisplayName("login, refresh, suspension, revocation and the DSL preview with TRACE never log a secret, and the"
            + " masked types never log the email or the name")
    void noSecretsAndMaskedTypes(CapturedOutput output) {
        String signingKey = TestSigningKeyEnvironmentPostProcessor.randomKey(32);
        String adminEmail = "leak-admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = "初期管理者-" + TestDatabase.randomSecret();
        String memberEmail = "leak-member-" + UUID.randomUUID() + "@example.com";
        String memberPassword = "利用者-" + TestDatabase.randomSecret();

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        "--spring.datasource.url=" + TestDatabase.url(tempDir.resolve("leak")),
                        "--server.port=0",
                        "--mastersmith.auth.signing-key=" + signingKey,
                        "--mastersmith.auth.password.bcrypt-cost=4",
                        "--mastersmith.auth.initial-admin.email=" + adminEmail,
                        "--mastersmith.auth.initial-admin.password=" + adminPassword,
                        "--logging.level.cherry.mastersmith.auth=TRACE",
                        "--logging.level.cherry.mastersmith.user=TRACE",
                        "--logging.level.cherry.mastersmith.audit=TRACE",
                        "--logging.level.cherry.mastersmith.dslmanage=TRACE")) {
            int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
            AuthApi api = new AuthApi(port);
            HttpTestClient client = new HttpTestClient(port);
            UserAccountService users = context.getBean(UserAccountService.class);
            long memberId = TestUserAccounts.create(users, memberEmail, memberPassword, false);
            TestUserSuspension suspension = new TestUserSuspension(
                    new TransactionTemplate(context.getBean(PlatformTransactionManager.class)),
                    users,
                    context.getBean(RefreshTokenRevocationService.class));
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            int start = output.getOut().length();

            HttpResponse<String> login = api.login(memberEmail, memberPassword);
            assertThat(login.statusCode()).isEqualTo(200);
            String accessToken = AuthApi.accessToken(login);
            String cookie = AuthApi.cookieValue(login);
            assertThat(api.login(memberEmail, WRONG).statusCode()).isEqualTo(401);
            HttpResponse<String> refreshed = api.refresh(cookie, api.origin());
            assertThat(refreshed.statusCode()).isEqualTo(200);
            String newAccessToken = AuthApi.accessToken(refreshed);
            String newCookie = AuthApi.cookieValue(refreshed);
            HttpResponse<String> adminLogin = api.login(adminEmail, adminPassword);
            String adminToken = AuthApi.accessToken(adminLogin);
            String adminCookie = AuthApi.cookieValue(adminLogin);
            client.get("/api/admin/dsl/preview", "Authorization", "Bearer " + adminToken);
            List<String> tokenHashes = jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class);

            assertThat(suspension.suspend(memberId)).isEqualTo(1);
            SuspendedResponses suspended = new SuspendedResponses(
                    api.login(memberEmail, memberPassword),
                    api.refresh(newCookie, api.origin()),
                    client.get("/api/me/preferences", "Authorization", "Bearer " + newAccessToken));

            String out = output.getOut().substring(start);
            List<String> secrets = new ArrayList<>(List.of(
                    memberPassword,
                    adminPassword,
                    WRONG,
                    accessToken,
                    cookie,
                    newAccessToken,
                    newCookie,
                    adminToken,
                    adminCookie,
                    signingKey));
            secrets.addAll(jdbc.queryForList("SELECT password_hash FROM users", String.class));
            secrets.addAll(tokenHashes);

            // 確かめ1: 秘密の値が出力全体に無く、すべての行が JSON である。
            JsonLogRecords.assertAllLinesAreJson(output.getOut());
            JsonLogRecords.assertContainsNoSecret(output.getAll(), secrets.toArray(String[]::new));

            // 確かめ2: 伏せ字にした型が出る TRACE の行に、メールアドレスと氏名が無い。
            List<Map<String, Object>> records = JsonLogRecords.parse(out);
            for (String prefix : List.of(
                    "ENTER LoginService#login(",
                    "ENTER AuditEventListener#onAuthenticationEvent(",
                    "EXIT  AuthController#login(",
                    "EXIT  AuthController#refresh(",
                    "ENTER DslAdminController#preview(",
                    "ENTER RefreshTokenRevocationService#revokeAllRefreshTokens(",
                    "EXIT  RefreshTokenRevocationService#revokeAllRefreshTokens(",
                    "ENTER AuthController#login(",
                    "EXIT  UserAccountService#verifyPassword(")) {
                List<String> lines = messages(records, prefix);
                assertThat(lines).as("TRACE の行 %s", prefix).isNotEmpty();
                assertThat(lines)
                        .allSatisfy(line -> assertThat(line)
                                .doesNotContain(memberEmail)
                                .doesNotContain(adminEmail)
                                .doesNotContain(TestUserAccounts.DISPLAY_NAME));
            }
            assertThat(messages(records, "ENTER AuditEventListener#onAuthenticationEvent("))
                    .anySatisfy(line ->
                            assertThat(line).contains("ACCOUNT_SUSPENDED").contains("enteredEmail=***"));
            assertThat(messages(records, "EXIT  RefreshTokenRevocationService#revokeAllRefreshTokens("))
                    .singleElement()
                    .satisfies(line -> assertThat(line).contains("RevokeAllResult[revoked=1]"));

            // 確かめ3: まとめての無効化の DEBUG の行は、キーが userId・revoked だけ。
            Map<String, Object> enter = records.stream()
                    .filter(record -> String.valueOf(record.get("message"))
                            .startsWith("ENTER RefreshTokenRevocationService#revokeAllRefreshTokens("))
                    .findFirst()
                    .orElseThrow();
            Map<String, Object> revoked = records.stream()
                    .filter(record -> "DEBUG".equals(record.get("level"))
                            && String.valueOf(record.get("logger")).endsWith("RefreshTokenRevocationService"))
                    .findFirst()
                    .orElseThrow();
            Set<String> extraKeys = new HashSet<>(revoked.keySet());
            extraKeys.removeAll(enter.keySet());
            assertThat(extraKeys).containsExactlyInAnyOrder("userId", "revoked");
            assertThat(revoked).containsEntry("revoked", 1);
            assertThat(String.valueOf(revoked.get("userId"))).isEqualTo(Long.toString(memberId));

            // 確かめ4: 停止中の3つの入口の応答の本文に、メールアドレス・トークンの値・SUSPENDED が無い。
            for (HttpResponse<String> response : List.of(suspended.login(), suspended.refresh(), suspended.access())) {
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body())
                        .doesNotContain(memberEmail)
                        .doesNotContain(newAccessToken)
                        .doesNotContain(newCookie)
                        .doesNotContain("SUSPENDED");
            }

            // 確かめ5（R-01）: メールアドレスを伏せ字の型で受ける2つの口の TRACE の行が出ていて、入れたメールアドレス・利用者の
            // メールアドレス（一般の利用者と初期管理者）が、起動から最後の要求までの出力のどの行にも無い。
            List<Map<String, Object>> all = JsonLogRecords.parse(output.getOut());
            assertThat(messages(records, "ENTER UserAccountService#verifyPassword("))
                    .isNotEmpty()
                    .allSatisfy(line -> assertThat(line).contains("***"));
            // 起動時の初期管理者の処理は Intent 261004-safety-carryover で existsByEmail の代わりに rescueInitialAdmin を呼ぶ
            // （同じく伏せ字の型でメールアドレスを受ける口）。
            assertThat(messages(all, "ENTER UserAccountService#rescueInitialAdmin("))
                    .isNotEmpty()
                    .allSatisfy(line -> assertThat(line).contains("***"));
            List<String> linesWithEmail = output.getAll()
                    .lines()
                    .filter(line -> line.contains(memberEmail) || line.contains(adminEmail))
                    .map(line -> line.length() > 120 ? line.substring(0, 120) : line)
                    .toList();
            assertThat(linesWithEmail).as("メールアドレスを含む行").isEmpty();
        }
    }

    private static List<String> messages(List<Map<String, Object>> records, String prefix) {
        return records.stream()
                .map(record -> String.valueOf(record.get("message")))
                .filter(message -> message.startsWith(prefix))
                .toList();
    }
}
