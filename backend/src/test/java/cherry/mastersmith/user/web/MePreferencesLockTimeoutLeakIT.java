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
package cherry.mastersmith.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 表示の設定の保存の書き込み（{@code UserRepository#updatePreferences}）で、利用者の行の排他の待ちの上限切れが起きても、排他されて
 * いた利用者の行の値と H2 の内部の例外の文がログに出ないことの結合テスト（Intent 260930-user-admin の U3、
 * {@code infrastructure-specification.md} 6.2 の書き込みの問い合わせの中央の手当て、NFR3.1・NFR3.4）。
 *
 * <p>別の接続（{@link RowLockHolder}）で利用者の行を持ち続けて上限切れを起こし、1つの Spring の文脈の中で、{@code cherry.mastersmith}
 * のロガーを TRACE にした場合と既定の INFO の場合の両方で確かめる。応答は今までどおり 500 {@code INTERNAL_ERROR}。利用者の行の値
 * （メールアドレス・氏名・パスワードのハッシュ値）と、保存しようとした氏名が出力のどの行にも無いことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class MePreferencesLockTimeoutLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
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
    private record Run(String logs, HttpResponse<String> response, String[] secrets) {}

    private Run run(CapturedOutput output) throws Exception {
        String email = "lock-leak-me-" + TestDatabase.randomSecret() + "@example.com";
        String password = TestDatabase.randomSecret();
        long userId = TestUserAccounts.create(userAccountService, email, password, false);
        String displayName = "Held " + TestDatabase.randomSecret();
        jdbc.update("UPDATE users SET display_name = ? WHERE user_id = ?", displayName, userId);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, userId);
        String token = AuthApi.accessToken(new AuthApi(port).login(email, password));
        String newName = "Saving " + TestDatabase.randomSecret();
        int offset = output.getOut().length();

        HttpResponse<String> response;
        try (RowLockHolder holder = RowLockHolder.hold(
                TestDatabase.url(tempDir), "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE", userId)) {
            assertThat(holder.lockedRows()).isEqualTo(1);
            response = new MeApi(port).putPreferences(token, newName, "en", "dark", "lg");
        }
        String logs = output.getOut().substring(offset) + output.getErr();
        return new Run(logs, response, new String[] {email, displayName, hash, newName, password});
    }

    private static void assertNoLeak(Run run) {
        assertThat(run.response().statusCode()).isEqualTo(500);
        assertThat(HttpTestClient.json(run.response())).containsEntry("code", "INTERNAL_ERROR");
        assertThat(run.response().body()).doesNotContain(run.secrets());
        JsonLogRecords.assertContainsNoSecret(run.logs(), run.secrets());
        assertThat(run.logs()).doesNotContain("MVStoreException").doesNotContain("$2a$");
        List<Map<String, Object>> errors = JsonLogRecords.parse(run.logs()).stream()
                .filter(r -> "ERROR".equals(r.get("level")))
                .filter(r -> String.valueOf(r.get("logger")).endsWith("GlobalExceptionHandler"))
                .toList();
        assertThat(errors)
                .as("想定外の誤りの ERROR は1件で、原因をつながずクラスの名前だけ")
                .singleElement()
                .satisfies(r -> assertThat(r).containsKey("exceptionClass").doesNotContainKey("exception"));
    }

    @Test
    @DisplayName("with TRACE, a lock timeout of the preferences update leaks no user row value")
    void traceLevel(CapturedOutput output) throws Exception {
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        Run run;
        try {
            run = run(output);
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }

        assertNoLeak(run);
        assertThat(run.logs()).as("メソッドの呼び出しの追跡が有効").contains("ENTER UserPreferencesService#");
        assertThat(run.logs()).contains("EXCEPTION UserPreferencesService#");
    }

    @Test
    @DisplayName("with the default INFO level, a lock timeout of the preferences update leaks no user row value")
    void infoLevel(CapturedOutput output) throws Exception {
        Run run = run(output);

        assertNoLeak(run);
        assertThat(run.logs()).doesNotContain("ENTER ");
    }
}
