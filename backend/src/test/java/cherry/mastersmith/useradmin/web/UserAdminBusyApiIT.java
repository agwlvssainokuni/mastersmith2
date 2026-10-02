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
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.testsupport.UserAdminApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 5つの操作の行の排他の待ちの上限切れ（Intent 260930-user-admin の U3、BR3.5、AC4.1.11、NFR4.3・NFR4.4）の結合テスト。
 *
 * <p>別の接続（{@link RowLockHolder}）で対象の行を {@code FOR UPDATE} で持ち続けて上限切れを起こし、409 {@code USER_ADMIN_BUSY}
 * （500 にならない）、かかった時間が上限 3000 ミリ秒以上、状態と監査が変わらない、応答に対象の値が無いことを確かめる。排他の待ちの上限は
 * 変えない（NFR4.4）。止める操作は、対象のリフレッシュトークンの行を持ち続けて、トークンの無効化の書き込みの待ちが上限切れになる場合も
 * 409 になることを確かめる（コード生成のレビューの R-01）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class UserAdminBusyApiIT {

    private static final String HOLD_USER = "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE";

    private static final String HOLD_LOCK_STATE =
            "SELECT subject_id FROM login_attempt_states WHERE subject_id = ? FOR UPDATE";

    private static final String HOLD_REFRESH_TOKENS =
            "SELECT token_id FROM refresh_tokens WHERE user_id = ? FOR UPDATE";

    private static final long LOCK_TIMEOUT_MILLIS = 3000;

    /**
     * 書き込みの問い合わせ（リフレッシュトークンの無効化の更新）の待ちの下限の確かめに使う値。待ちの上限は H2 の既定（設定ではない。
     * B4 の Step 25 の実測で約 2 秒）で、行の排他の読み取りの 3000 ミリ秒より短いため、待ったことだけを確かめる。
     */
    private static final long WRITE_WAIT_MIN_MILLIS = 1000;

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
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    private UserAdminApi api;

    private UserAdminFixtures fixtures;

    private String admin;

    @BeforeEach
    void setUp() {
        api = new UserAdminApi(port);
        fixtures = new UserAdminFixtures(userAccountService, revocationService, transactionManager, jdbc, port);
        String email = "admin-" + UserAdminFixtures.marker() + "@example.com";
        fixtures.create(email, "待つ 管理者", true);
        admin = fixtures.login(email);
    }

    private long user(String displayName, boolean isAdmin) {
        return fixtures.create("busy-" + UserAdminFixtures.marker() + "@example.com", displayName, isAdmin);
    }

    private int auditCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    private String emailOf(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, userId);
    }

    /** 行を持ち続けたまま操作を送り、409 USER_ADMIN_BUSY と、状態・監査が変わらないことを確かめる。 */
    private void assertBusy(long target, String displayName, String action, String holdSql) throws SQLException {
        assertBusy(target, displayName, action, holdSql, LOCK_TIMEOUT_MILLIS);
    }

    /** 行を持ち続けたまま操作を送り、409 USER_ADMIN_BUSY と、状態・監査が変わらないことを確かめる（待つ時間の下限を指定する）。 */
    private void assertBusy(long target, String displayName, String action, String holdSql, long minWaitMillis)
            throws SQLException {
        Map<String, Object> before = fixtures.userColumns(target);
        Map<String, Object> lockBefore = fixtures.lockColumns(target);
        int audit = auditCount();

        HttpResponse<String> response;
        long elapsedMillis;
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), holdSql, target)) {
            assertThat(holder.lockedRows()).isGreaterThanOrEqualTo(1);
            long start = System.nanoTime();
            response = api.operate(admin, target, action);
            elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        }

        assertThat(response.statusCode()).as(action).isEqualTo(409);
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).as(action).containsEntry("code", "USER_ADMIN_BUSY");
        assertThat(elapsedMillis).as(action + " は上限まで待つ").isGreaterThanOrEqualTo(minWaitMillis);
        assertThat(response.body())
                .as(action)
                .doesNotContain(emailOf(target))
                .doesNotContain(displayName)
                .doesNotContain("MVStoreException")
                .doesNotContain("Timeout");
        for (String key : new String[] {"title", "detail"}) {
            assertThat((String) body.get(key)).doesNotContain(String.valueOf(target));
        }
        assertThat(fixtures.userColumns(target)).as(action).isEqualTo(before);
        assertThat(fixtures.lockColumns(target)).as(action).isEqualTo(lockBefore);
        assertThat(auditCount()).as(action + " は監査を残さない").isEqualTo(audit);
    }

    @Test
    @DisplayName("grant-admin is 409 USER_ADMIN_BUSY after the lock timeout while the target row is held")
    void grantAdmin() throws SQLException {
        assertBusy(user("待たれる 一郎", false), "待たれる 一郎", "grant-admin", HOLD_USER);
    }

    @Test
    @DisplayName("revoke-admin is 409 USER_ADMIN_BUSY after the lock timeout while the target row is held")
    void revokeAdmin() throws SQLException {
        assertBusy(user("待たれる 二郎", true), "待たれる 二郎", "revoke-admin", HOLD_USER);
    }

    @Test
    @DisplayName("suspend is 409 USER_ADMIN_BUSY after the lock timeout and no refresh token is revoked")
    void suspend() throws SQLException {
        long target = user("待たれる 三郎", false);
        fixtures.login(emailOf(target));
        assertBusy(target, "待たれる 三郎", "suspend", HOLD_USER);
        assertThat(fixtures.activeRefreshTokens(target)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "suspend is 409 USER_ADMIN_BUSY, not 500, when revoking waits too long on held refresh token rows (R-01)")
    void suspendWhileRefreshTokensAreHeld() throws SQLException {
        long target = user("待たれる 六郎", false);
        fixtures.login(emailOf(target));
        assertThat(fixtures.activeRefreshTokens(target)).isEqualTo(1);

        assertBusy(target, "待たれる 六郎", "suspend", HOLD_REFRESH_TOKENS, WRITE_WAIT_MIN_MILLIS);

        assertThat(fixtures.activeRefreshTokens(target))
                .as("停止の書き換えとトークンの無効化のどちらも巻き戻る")
                .isEqualTo(1);
        HttpResponse<String> retried = api.operate(admin, target, "suspend");
        assertThat(retried.statusCode()).as("行を放した後は止められる").isEqualTo(204);
        assertThat(fixtures.activeRefreshTokens(target)).isZero();
    }

    @Test
    @DisplayName("resume is 409 USER_ADMIN_BUSY after the lock timeout while the target row is held")
    void resume() throws SQLException {
        long target = user("待たれる 四郎", false);
        fixtures.suspend(target);
        assertBusy(target, "待たれる 四郎", "resume", HOLD_USER);
    }

    @Test
    @DisplayName("reset-login-failures is 409 USER_ADMIN_BUSY while the lock state row is held")
    void resetLoginFailures() throws SQLException {
        long target = user("待たれる 五郎", false);
        fixtures.lockState(target, 5, clock.instant().plus(Duration.ofMinutes(15)));
        assertBusy(target, "待たれる 五郎", "reset-login-failures", HOLD_LOCK_STATE);
    }
}
