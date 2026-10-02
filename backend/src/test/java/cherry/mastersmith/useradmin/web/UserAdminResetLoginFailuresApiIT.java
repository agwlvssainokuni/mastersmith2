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
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.testsupport.UserAdminApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
 * ログインの失敗回数を戻す API（{@code POST /api/admin/users/{userId}/reset-login-failures}、Intent 260930-user-admin の U3、BR4.5・
 * BR3.4、AC4.1.1〜AC4.1.4・AC4.1.7・AC4.1.8・AC4.1.10）の結合テスト。team.md の必須のテスト（ロックの解除）にあたる。
 *
 * <p>しきい値は設定の既定 5。時刻は注入した時計（{@link MutableClock}）で決め、実時刻と sleep に頼らない。決めた側の動作として、戻した後は
 * 失敗回数を 0 から数え、ロックの状態の行が無い利用者は 409 {@code USER_ADMIN_NO_CHANGE}（行を作らない）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class UserAdminResetLoginFailuresApiIT {

    private static final int THRESHOLD = 5;

    private static final String WRONG = "まちがったパスワード-9999";

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

    private AuthApi auth;

    private UserAdminFixtures fixtures;

    private long adminId;

    private String admin;

    @BeforeEach
    void setUp() {
        api = new UserAdminApi(port);
        auth = new AuthApi(port);
        fixtures = new UserAdminFixtures(userAccountService, revocationService, transactionManager, jdbc, port);
        String email = email("admin");
        adminId = fixtures.create(email, "戻す 管理者", true);
        admin = fixtures.login(email);
    }

    private static String email(String label) {
        return label + "-" + UserAdminFixtures.marker() + "@example.com";
    }

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(auth.login(email, WRONG).statusCode()).isEqualTo(401);
        }
    }

    private boolean locked(long userId) {
        Map<String, Object> row = fixtures.lockColumns(userId);
        return !row.isEmpty() && row.get("LOCKED_UNTIL") != null;
    }

    private HttpResponse<String> reset(long userId) {
        return api.operate(admin, userId, "reset-login-failures");
    }

    @Test
    @DisplayName("a locked user can log in with the right password right after the failures are reset")
    void lockedUserLogsInAfterReset() {
        String targetEmail = email("locked");
        long target = fixtures.create(targetEmail, "ロック 太郎", false);
        fail(targetEmail, THRESHOLD);
        assertThat(locked(target)).isTrue();
        assertThat(auth.login(targetEmail, UserAdminFixtures.PASSWORD).statusCode())
                .as("ロック中は正しいパスワードでも拒否")
                .isEqualTo(401);

        assertThat(reset(target).statusCode()).isEqualTo(204);

        assertThat(fixtures.lockColumns(target))
                .containsEntry("CONSECUTIVE_FAILURES", 0)
                .containsEntry("LOCKED_UNTIL", null);
        assertThat(auth.login(targetEmail, UserAdminFixtures.PASSWORD).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("after a reset, threshold - 1 failures do not lock and the threshold-th failure locks again")
    void countsFromZeroAfterReset() {
        String targetEmail = email("boundary");
        long target = fixtures.create(targetEmail, "境界 次郎", false);
        fail(targetEmail, THRESHOLD);
        assertThat(reset(target).statusCode()).isEqualTo(204);

        fail(targetEmail, THRESHOLD - 1);
        assertThat(fixtures.lockColumns(target)).containsEntry("CONSECUTIVE_FAILURES", THRESHOLD - 1);
        assertThat(locked(target)).isFalse();

        fail(targetEmail, 1);
        assertThat(locked(target)).isTrue();
        assertThat(auth.login(targetEmail, UserAdminFixtures.PASSWORD).statusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("a user whose lock has already expired can still be reset, and a lock ending exactly now is reset")
    void expiredLockIsResettable() {
        long expired = fixtures.create(email("expired"), "期限切れ 三郎", false);
        fixtures.lockState(expired, THRESHOLD, clock.instant().minus(Duration.ofMinutes(1)));
        long endingNow = fixtures.create(email("ending"), "ちょうど 四郎", false);
        fixtures.lockState(endingNow, 0, clock.instant());

        assertThat(reset(expired).statusCode()).isEqualTo(204);
        assertThat(reset(endingNow).statusCode()).isEqualTo(204);

        for (long userId : new long[] {expired, endingNow}) {
            assertThat(fixtures.lockColumns(userId))
                    .containsEntry("CONSECUTIVE_FAILURES", 0)
                    .containsEntry("LOCKED_UNTIL", null);
        }
    }

    @Test
    @DisplayName("a user without a lock state row, or with nothing to reset, is 409 USER_ADMIN_NO_CHANGE")
    void nothingToReset() {
        long noRow = fixtures.create(email("norow"), "行なし 五郎", false);
        fixtures.removeLockState(noRow);
        long zero = fixtures.create(email("zero"), "零 六郎", false);
        fixtures.lockState(zero, 0, null);
        int audit = auditCount();

        HttpResponse<String> noRowResponse = reset(noRow);
        HttpResponse<String> zeroResponse = reset(zero);

        for (HttpResponse<String> response : List.of(noRowResponse, zeroResponse)) {
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(HttpTestClient.json(response)).containsEntry("code", "USER_ADMIN_NO_CHANGE");
        }
        assertThat(fixtures.lockColumns(noRow)).as("行を作らない").isEmpty();
        assertThat(fixtures.lockColumns(zero))
                .containsEntry("CONSECUTIVE_FAILURES", 0)
                .containsEntry("LOCKED_UNTIL", null);
        assertThat(auditCount()).as("業務の拒否は失敗の監査を残す").isEqualTo(audit + 2);
    }

    @Test
    @DisplayName("an admin may reset their own failures and a suspended user's, and the suspension stays")
    void selfAndSuspendedUsers() {
        fixtures.lockState(adminId, 2, null);
        long suspended = fixtures.create(email("suspended"), "停止 七郎", false);
        fixtures.lockState(suspended, THRESHOLD, clock.instant().plus(Duration.ofMinutes(15)));
        fixtures.suspend(suspended);

        assertThat(reset(adminId).statusCode()).isEqualTo(204);
        assertThat(reset(suspended).statusCode()).isEqualTo(204);

        assertThat(fixtures.lockColumns(adminId)).containsEntry("CONSECUTIVE_FAILURES", 0);
        assertThat(fixtures.lockColumns(suspended))
                .containsEntry("CONSECUTIVE_FAILURES", 0)
                .containsEntry("LOCKED_UNTIL", null);
        assertThat(fixtures.userColumns(suspended)).containsEntry("SUSPENDED", true);
    }

    private int auditCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }
}
