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

import cherry.mastersmith.auth.domain.RefreshTokenValue;
import cherry.mastersmith.auth.domain.RefreshTokenValues;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
 * ログインとトークンの更新で、行の排他の待ちの上限切れが起きても、排他されていた行の値と H2 の内部の例外の文がログに出ないことの
 * 結合テスト（Intent 260930-user-admin の U3、{@code security-design.md} 7.2 の E1、{@code infrastructure-specification.md} 6.2、
 * NFR3.1・NFR3.4）。
 *
 * <p>別の接続（{@link RowLockHolder}）で行を持ち続けて上限切れを起こし、1つの Spring の文脈の中で、{@code cherry.mastersmith} の
 * ロガーを TRACE（メソッドの呼び出しの追跡が有効）にした場合と既定の INFO の場合の両方で確かめる。
 *
 * <ul>
 *   <li>実在の利用者のログイン（ロックの状態の行の排他の読み取り E1）
 *   <li>ダミーの行の8つをすべて持ったときの存在しないメールアドレスのログイン（E1 の代わりの道。計画 9節の Q-G）
 *   <li>トークンの更新の書き込み（リフレッシュトークンの行を持ったとき。書き込みの問い合わせの中央の手当て）
 * </ul>
 *
 * <p>どれも応答は今までどおり 500 {@code INTERNAL_ERROR}。行の値は見分けやすい値（解除の予定の時刻・トークンのハッシュ）で入れ、
 * 出力のどの行にも無いことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthLockTimeoutLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    private static final String PASSWORD = "正しいパスワード-1234";

    /** 利用者の行に入れる見分けやすい解除の予定の時刻（過去のためロックは効かない）。 */
    private static final Instant USER_LOCKED_UNTIL = Instant.parse("2001-02-03T04:05:06Z");

    /** ダミーの行に入れる見分けやすい解除の予定の時刻。 */
    private static final Instant DUMMY_LOCKED_UNTIL = Instant.parse("2002-03-04T05:06:07Z");

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
    private record Run(String logs, List<HttpResponse<String>> responses, String[] secrets) {}

    private Run run(CapturedOutput output) throws Exception {
        AuthApi api = new AuthApi(port);
        String email = "lock-leak-" + UUID.randomUUID() + "@example.com";
        long userId = TestUserAccounts.create(userAccountService, email, PASSWORD, false);
        String cookie = AuthApi.cookieValue(api.login(email, PASSWORD));
        jdbc.update(
                "UPDATE login_attempt_states SET consecutive_failures = 3, locked_until = ? WHERE subject_id = ?",
                Timestamp.from(USER_LOCKED_UNTIL),
                userId);
        List<Map<String, Object>> dummies =
                jdbc.queryForList("SELECT subject_id, consecutive_failures, locked_until FROM login_attempt_states"
                        + " WHERE subject_id < 0");
        jdbc.update(
                "UPDATE login_attempt_states SET locked_until = ? WHERE subject_id < 0",
                Timestamp.from(DUMMY_LOCKED_UNTIL));
        byte[] tokenHash = RefreshTokenValues.hash(new RefreshTokenValue(cookie));
        String url = TestDatabase.url(tempDir);
        int offset = output.getOut().length();

        List<HttpResponse<String>> responses = new ArrayList<>();
        try (RowLockHolder holder = RowLockHolder.hold(
                url, "SELECT subject_id FROM login_attempt_states WHERE subject_id = ? FOR UPDATE", userId)) {
            assertThat(holder.lockedRows()).isEqualTo(1);
            responses.add(api.login(email, PASSWORD));
        }
        try (RowLockHolder holder = RowLockHolder.hold(
                url, "SELECT subject_id FROM login_attempt_states WHERE subject_id < 0 FOR UPDATE")) {
            assertThat(holder.lockedRows()).isEqualTo(8);
            responses.add(api.login("lock-leak-nobody-" + UUID.randomUUID() + "@example.com", PASSWORD));
        }
        try (RowLockHolder holder = RowLockHolder.hold(
                url, "SELECT token_id FROM refresh_tokens WHERE token_hash = ? FOR UPDATE", tokenHash)) {
            assertThat(holder.lockedRows()).isEqualTo(1);
            responses.add(api.refresh(cookie, api.origin()));
        }
        String logs = output.getOut().substring(offset) + output.getErr();

        for (Map<String, Object> dummy : dummies) {
            jdbc.update(
                    "UPDATE login_attempt_states SET consecutive_failures = ?, locked_until = ? WHERE subject_id = ?",
                    dummy.get("consecutive_failures"),
                    dummy.get("locked_until"),
                    dummy.get("subject_id"));
        }
        String hex = HexFormat.of().formatHex(tokenHash);
        String[] secrets = {"2001-02-03", "2002-03-04", hex, hex.toUpperCase(Locale.ROOT), email, cookie};
        return new Run(logs, responses, secrets);
    }

    private static void assertNoLeak(Run run) {
        assertThat(run.responses()).extracting(HttpResponse::statusCode).containsExactly(500, 500, 500);
        for (HttpResponse<String> response : run.responses()) {
            assertThat(HttpTestClient.json(response)).containsEntry("code", "INTERNAL_ERROR");
            assertThat(response.body()).doesNotContain(run.secrets());
        }
        JsonLogRecords.assertContainsNoSecret(run.logs(), run.secrets());
        assertThat(run.logs()).doesNotContain("MVStoreException");
        List<Map<String, Object>> records = JsonLogRecords.parse(run.logs());
        assertThat(records)
                .filteredOn(r -> "WARN".equals(r.get("level")) && "LOGIN_ATTEMPT_ROW".equals(r.get("lockKind")))
                .as("読み取りの排他の経路は WARN に排他の種類とクラスの名前を出す")
                .hasSize(2)
                .allSatisfy(r -> assertThat(r).containsKey("exceptionClass").doesNotContainKey("exception"));
        List<Map<String, Object>> errors = records.stream()
                .filter(r -> "ERROR".equals(r.get("level")))
                .filter(r -> String.valueOf(r.get("logger")).endsWith("GlobalExceptionHandler"))
                .toList();
        assertThat(errors).as("想定外の誤りの ERROR は要求ごとに1件").hasSize(3);
        assertThat(errors)
                .allSatisfy(r -> assertThat(String.valueOf(r.get("exception"))).doesNotContain("Caused by"));
        assertThat(errors.subList(0, 2))
                .allSatisfy(r -> assertThat(String.valueOf(r.get("exception")))
                        .startsWith("cherry.mastersmith.common.persistence.RowLockUnavailableException"));
        assertThat(errors.get(2)).containsKey("exceptionClass").doesNotContainKey("exception");
    }

    @Test
    @DisplayName("with TRACE, lock timeouts of login, dummy-row login and token refresh leak no row value")
    void traceLevel(CapturedOutput output) throws Exception {
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        Run run;
        try {
            run = run(output);
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }

        assertNoLeak(run);
        assertThat(run.logs()).as("メソッドの呼び出しの追跡が有効").contains("ENTER LoginService#login");
        assertThat(run.logs()).contains("EXCEPTION LoginAttemptStateRepository#lockForUpdate");
    }

    @Test
    @DisplayName(
            "with the default INFO level, lock timeouts of login, dummy-row login and token refresh leak no row value")
    void infoLevel(CapturedOutput output) throws Exception {
        Run run = run(output);

        assertNoLeak(run);
        assertThat(run.logs()).doesNotContain("ENTER ");
    }
}
