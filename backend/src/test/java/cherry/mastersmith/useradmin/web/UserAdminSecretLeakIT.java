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
package cherry.mastersmith.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.testsupport.UserAdminApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 利用者の一覧と氏名・言語の変更で、個人に関する値と秘密が漏れないことの結合テスト（Intent 260930-user-admin の U3、AC1.1.6、
 * NFR3.1〜NFR3.4、BR7.4・BR7.5、team.md の「利用者の管理の漏えい」）。
 *
 * <p>1つの Spring の文脈の中で、{@code cherry.mastersmith} のロガーを TRACE（メソッドの呼び出しの追跡が有効）にした場合と、既定の INFO
 * の場合の両方で同じ要求を送り、その間に出た出力のどの行にも、利用者のメールアドレス・氏名・検索の文字・変更した氏名・パスワードの
 * ハッシュ値が無いことを確かめる。ロガーのレベルは Spring Boot の {@link LoggingSystem} で切り替え、終わったら設定の値に戻す
 * （計画 8節の D-10）。値は ASCII の乱数にして、JSON の書き方に左右されずに探せるようにする。前準備（利用者の作成・ログイン）の
 * 出力は確かめの範囲の外。
 *
 * <p>B4（Intent 260930-user-admin の U3 後半）で、5つの操作（成功と拒否）と、5つの操作のそれぞれの行の排他の待ちの上限切れ（別の接続で
 * 行を持ち続ける）を足した。出力のどの行にも、利用者のメールアドレス・氏名・パスワードのハッシュ値・排他されていた行の値（解除の予定の
 * 時刻）・{@code MVStoreException} の文が無く、上限切れの WARN が2行（排他の種類とクラスの名前、code）で同じトレースID になる（SD-5）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class UserAdminSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /**
     * Spring Data の repository の追跡のロガー（追跡は対象の実体のクラスの名前のロガーで出るため、Spring Data の repository の
     * インターフェースのメソッドは、このパッケージのロガーを TRACE にしたときに出る）。
     */
    private static final String SPRING_DATA_LOGGER = "org.springframework.data.jpa.repository.support";

    /** Hibernate の誤りのロガー（Hibernate 7.4。誤りの番号と SQLState、値の無い SQL の文を WARN に出す）。 */
    private static final String HIBERNATE_ERROR_LOGGER = "org.hibernate.orm.jdbc.error";

    private static final String HOLD_USER = "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE";

    private static final String HOLD_REFRESH_TOKENS =
            "SELECT token_id FROM refresh_tokens WHERE user_id = ? FOR UPDATE";

    private static final String HOLD_LOCK_STATE =
            "SELECT subject_id FROM login_attempt_states WHERE subject_id = ? FOR UPDATE";

    /** 排他されていた行の値に見立てた、見分けやすい解除の予定の時刻（B4）。 */
    private static final Instant LOCKED_UNTIL = Instant.parse("2031-07-13T05:43:21Z");

    private static final String LOCKED_UNTIL_TEXT = "2031-07-13";

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
    RefreshTokenRevocationService revocationService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LoggingSystem loggingSystem;

    private UserAdminApi api;

    private UserAdminFixtures fixtures;

    private String adminEmail;

    private String admin;

    private long adminId;

    @BeforeEach
    void setUp() {
        api = new UserAdminApi(port);
        fixtures = new UserAdminFixtures(userAccountService, revocationService, transactionManager, jdbc, port);
        adminEmail = "leak-admin-" + TestDatabase.randomSecret() + "@example.com";
        adminId = fixtures.create(adminEmail, "Admin " + TestDatabase.randomSecret(), true);
        admin = fixtures.login(adminEmail);
    }

    /** 確かめの1回分の値と結果。 */
    private record Run(String logs, List<HttpResponse<String>> responses, String[] secrets, long userId) {}

    private Run run(CapturedOutput output) {
        String mark = TestDatabase.randomSecret();
        String email = "leak-check-" + mark + "@example.com";
        String name = "Leak " + TestDatabase.randomSecret();
        String newName = "Renamed " + TestDatabase.randomSecret();
        String badName = "Bad " + TestDatabase.randomSecret() + "\u0007";
        String tooLong = mark + "x".repeat(300);
        long userId = fixtures.create(email, name, false);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, userId);
        int audit = auditCount();
        int offset = output.getOut().length();

        List<HttpResponse<String>> responses = new ArrayList<>();
        responses.add(api.search(admin, mark));
        responses.add(api.search(admin, name.toUpperCase(java.util.Locale.ROOT)));
        responses.add(api.search(admin, tooLong));
        responses.add(api.putProfile(admin, String.valueOf(userId), newName, "en"));
        responses.add(api.putProfile(admin, String.valueOf(userId), badName, "ja"));
        responses.add(api.putProfile(admin, "999999999", newName, "ja"));

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(200, 200, 400, 204, 400, 404);
        assertThat(auditCount()).as("一覧と氏名・言語の変更は監査に残らない").isEqualTo(audit);
        String logs = output.getOut().substring(offset) + output.getErr();
        String[] secrets = {email, name, mark, newName, badName.strip(), hash, adminEmail};
        return new Run(logs, responses, secrets, userId);
    }

    private int auditCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    private void assertNoLeak(Run run) {
        JsonLogRecords.assertContainsNoSecret(run.logs(), run.secrets());
        assertThat(run.logs()).doesNotContain("$2a$");
        assertThat(run.responses().getFirst().body())
                .as("応答に一覧の値は出るが、ハッシュ値・失敗回数は出ない")
                .doesNotContain("$2a$")
                .doesNotContainIgnoringCase("hash")
                .doesNotContainIgnoringCase("failure");
        for (HttpResponse<String> response :
                run.responses().subList(2, run.responses().size())) {
            assertThat(response.body()).doesNotContain(Arrays.copyOf(run.secrets(), 5));
        }
    }

    /** B4 の確かめの1回分の値と結果。 */
    private record OpsRun(
            String logs, List<HttpResponse<String>> responses, List<HttpResponse<String>> busy, String[] secrets) {}

    private String hashOf(long userId) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, userId);
    }

    private HttpResponse<String> busy(long target, String action, String holdSql) throws SQLException {
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), holdSql, target)) {
            assertThat(holder.lockedRows()).isEqualTo(1);
            return api.operate(admin, target, action);
        }
    }

    private OpsRun runOperations(CapturedOutput output) throws SQLException {
        String[] emails = new String[5];
        String[] names = new String[5];
        for (int i = 0; i < emails.length; i++) {
            emails[i] = "leak-op-" + TestDatabase.randomSecret() + "@example.com";
            names[i] = "Op " + TestDatabase.randomSecret();
        }
        long member = fixtures.create(emails[0], names[0], false);
        long adminTarget = fixtures.create(emails[1], names[1], true);
        long suspended = fixtures.create(emails[2], names[2], false);
        fixtures.suspend(suspended);
        long locked = fixtures.create(emails[3], names[3], false);
        // 止める操作のリフレッシュトークンの書き込みの上限切れ（コード生成のレビューの R-01）の対象。ログインしてトークンを1件持たせる。
        long tokenOwner = fixtures.create(emails[4], names[4], false);
        fixtures.login(emails[4]);
        fixtures.lockState(member, 4, LOCKED_UNTIL);
        fixtures.lockState(locked, 4, LOCKED_UNTIL);
        List<String> secrets = new ArrayList<>(List.of(emails));
        secrets.addAll(List.of(names));
        for (long userId : new long[] {member, adminTarget, suspended, locked, tokenOwner}) {
            secrets.add(hashOf(userId));
        }
        for (byte[] tokenHash : jdbc.queryForList(
                "SELECT token_hash FROM refresh_tokens WHERE user_id = ?", byte[].class, tokenOwner)) {
            String hex = HexFormat.of().formatHex(tokenHash);
            secrets.add(hex);
            secrets.add(hex.toUpperCase(Locale.ROOT));
        }
        secrets.add(adminEmail);
        secrets.add(LOCKED_UNTIL_TEXT);
        int offset = output.getOut().length();
        int errOffset = output.getErr().length();

        List<HttpResponse<String>> responses = new ArrayList<>();
        for (String action : List.of("grant-admin", "revoke-admin", "suspend", "resume", "reset-login-failures")) {
            responses.add(api.operate(admin, member, action));
        }
        responses.add(api.operate(admin, adminTarget, "grant-admin"));
        responses.add(api.operate(admin, adminId, "revoke-admin"));
        responses.add(api.operate(admin, Long.MAX_VALUE, "suspend"));
        List<HttpResponse<String>> busy = new ArrayList<>();
        busy.add(busy(member, "grant-admin", HOLD_USER));
        busy.add(busy(adminTarget, "revoke-admin", HOLD_USER));
        busy.add(busy(member, "suspend", HOLD_USER));
        busy.add(busy(suspended, "resume", HOLD_USER));
        busy.add(busy(locked, "reset-login-failures", HOLD_LOCK_STATE));
        busy.add(busy(tokenOwner, "suspend", HOLD_REFRESH_TOKENS));

        assertThat(responses)
                .extracting(HttpResponse::statusCode)
                .containsExactly(204, 204, 204, 204, 204, 409, 409, 404);
        assertThat(busy).extracting(HttpResponse::statusCode).containsOnly(409);
        String logs = output.getOut().substring(offset) + output.getErr().substring(errOffset);
        return new OpsRun(logs, responses, busy, secrets.toArray(String[]::new));
    }

    private void assertNoLeak(OpsRun run) {
        JsonLogRecords.assertContainsNoSecret(run.logs(), run.secrets());
        assertThat(run.logs()).doesNotContain("$2a$").doesNotContain("MVStoreException");
        List<HttpResponse<String>> all = new ArrayList<>(run.responses());
        all.addAll(run.busy());
        for (HttpResponse<String> response : all) {
            assertThat(response.body())
                    .doesNotContain(run.secrets())
                    .doesNotContain("$2a$")
                    .doesNotContainIgnoringCase("hash")
                    .doesNotContainIgnoringCase("consecutive")
                    .doesNotContain("MVStoreException");
        }
        List<Map<String, Object>> records = JsonLogRecords.parse(run.logs());
        List<String> lockKinds = List.of(
                "ADMIN_ROWS", "ADMIN_ROWS", "ADMIN_ROWS", "USER_ROW", "LOGIN_ATTEMPT_ROW", "REFRESH_TOKEN_ROWS");
        for (int i = 0; i < run.busy().size(); i++) {
            Object traceId = HttpTestClient.json(run.busy().get(i)).get("traceId");
            List<Map<String, Object>> warns = records.stream()
                    .filter(record -> "WARN".equals(record.get("level")))
                    .filter(record -> traceId.equals(record.get("traceId")))
                    .toList();
            String lockKind = lockKinds.get(i);
            List<Map<String, Object>> appWarns = warns.stream()
                    .filter(record -> String.valueOf(record.get("logger")).startsWith(ROOT_LOGGER + "."))
                    .toList();
            assertThat(warns)
                    .as("アプリの外の WARN は Hibernate の誤りのロガーの行（誤りの番号と値の無い SQL の文）だけ")
                    .filteredOn(record -> !appWarns.contains(record))
                    .allSatisfy(record -> assertThat(record).containsEntry("logger", HIBERNATE_ERROR_LOGGER));
            assertThat(records)
                    .as("上限切れは想定外の誤り（500）にならず、アプリの ERROR が無い（レビューの R-01）")
                    .filteredOn(record -> "ERROR".equals(record.get("level")))
                    .filteredOn(record -> traceId.equals(record.get("traceId")))
                    .noneMatch(record -> String.valueOf(record.get("logger")).startsWith(ROOT_LOGGER + "."));
            assertThat(appWarns).as("上限切れのアプリの WARN が2行で同じトレースID（SD-5）").hasSize(2);
            assertThat(appWarns)
                    .anySatisfy(record -> assertThat(record)
                            .containsEntry("lockKind", lockKind)
                            .containsKey("exceptionClass"))
                    .anySatisfy(record -> assertThat(record).containsEntry("code", "USER_ADMIN_BUSY"));
        }
    }

    private static List<String> lines(String logs, String needle) {
        return logs.lines().filter(line -> line.contains(needle)).toList();
    }

    @Test
    @DisplayName("with TRACE the method trace runs on every layer and shows *** instead of emails, names and searches")
    void traceLevel(CapturedOutput output) {
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        loggingSystem.setLogLevel(SPRING_DATA_LOGGER, LogLevel.TRACE);
        Run run;
        try {
            run = run(output);
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
            loggingSystem.setLogLevel(SPRING_DATA_LOGGER, null);
        }

        assertNoLeak(run);
        JsonLogRecords.assertAllLinesAreJson(
                run.logs().substring(0, run.logs().length() - output.getErr().length()));
        for (String needle : new String[] {
            "ENTER UserAdminController#list",
            "ENTER UserAdminService#list",
            "ENTER UserAccountService#findAdminPage",
            "Repository#findAdminRowsBySearch",
            "Repository#countBySearch",
            "ENTER UserAdminController#updateProfile",
            "ENTER UserAccountService#updateProfile",
            "Repository#updateProfile"
        }) {
            assertThat(lines(run.logs(), needle)).as("追跡の行がある: %s", needle).isNotEmpty();
        }
        assertThat(lines(run.logs(), "ENTER UserAdminController#list"))
                .allSatisfy(line -> assertThat(line).contains("***"));
        assertThat(lines(run.logs(), "Repository#findAdminRowsBySearch"))
                .anySatisfy(line -> assertThat(line).contains("ENTER").contains("***"));
        assertThat(lines(run.logs(), "Repository#updateProfile"))
                .anySatisfy(line -> assertThat(line).contains("ENTER").contains("***"));
    }

    @Test
    @DisplayName("with the default INFO level nothing is traced and nothing personal is written either")
    void infoLevel(CapturedOutput output) {
        Run run = run(output);

        assertNoLeak(run);
        assertThat(lines(run.logs(), "ENTER ")).isEmpty();
    }

    @Test
    @DisplayName("with TRACE the five operations, rejections and lock timeouts write no personal value nor row value")
    void operationsTraceLevel(CapturedOutput output) throws SQLException {
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        loggingSystem.setLogLevel(SPRING_DATA_LOGGER, LogLevel.TRACE);
        OpsRun run;
        try {
            run = runOperations(output);
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
            loggingSystem.setLogLevel(SPRING_DATA_LOGGER, null);
        }

        assertNoLeak(run);
        for (String needle : new String[] {
            "ENTER UserAdminController#grantAdmin",
            "ENTER UserAdminService#suspend",
            "ENTER UserAccountService#lockAdminRowsInIdOrder",
            "ENTER UserRowLockRepository#lockAdminRowsAndTarget",
            "ENTER UserRowLockRepository#lockUserRow",
            "ENTER LockAdministrationService#prepareFailureReset",
            "ENTER LoginAttemptStateRepository#tryLockForUpdate"
        }) {
            assertThat(lines(run.logs(), needle)).as("追跡の行がある: %s", needle).isNotEmpty();
        }
    }

    @Test
    @DisplayName("with the default INFO level the five operations and lock timeouts write no personal value either")
    void operationsInfoLevel(CapturedOutput output) throws SQLException {
        OpsRun run = runOperations(output);

        assertNoLeak(run);
        assertThat(lines(run.logs(), "ENTER ")).isEmpty();
    }
}
