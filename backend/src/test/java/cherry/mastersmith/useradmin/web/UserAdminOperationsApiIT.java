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
import cherry.mastersmith.user.testsupport.MeApi;
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
 * 5つの管理の操作の API（{@code POST /api/admin/users/{userId}/grant-admin}・{@code revoke-admin}・{@code suspend}・{@code resume}・
 * {@code reset-login-failures}、Intent 260930-user-admin の U3、契約 C3、BR2.3・BR2.7・BR4.1〜BR4.6・BR7.1、NFR1.1〜NFR1.3・
 * NFR3.3・NFR8.1）の結合テスト。team.md の必須のテスト（管理者の印の変更、管理の API の認可）を含む。
 *
 * <p>API の1件ずつの操作では、操作する管理者自身が有効な管理者のため、最後の有効な管理者の拒否は起きない（業務処理を直接呼ぶ
 * {@code UserAdminOperationsIT}・同時の重なりの {@code UserAdminConcurrencyIT} で確かめる）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class UserAdminOperationsApiIT {

    private static final List<String> ACTIONS =
            List.of("grant-admin", "revoke-admin", "suspend", "resume", "reset-login-failures");

    private static final String USER_ADMIN_EVENTS = "SELECT COUNT(*) FROM audit_events WHERE event_type IN"
            + " ('USER_ADMIN_GRANTED', 'USER_ADMIN_REVOKED', 'USER_SUSPENDED', 'USER_RESUMED', 'LOGIN_FAILURES_RESET')";

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
        adminId = fixtures.create(email, "操作 管理者", true);
        admin = fixtures.login(email);
    }

    private static String email(String label) {
        return label + "-" + UserAdminFixtures.marker() + "@example.com";
    }

    private long user(String label, boolean isAdmin) {
        return fixtures.create(email(label), "対象 " + label, isAdmin);
    }

    private String emailOf(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, userId);
    }

    private int userAdminEvents() {
        Integer count = jdbc.queryForObject(USER_ADMIN_EVENTS, Integer.class);
        return count == null ? 0 : count;
    }

    private static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.uri().getPath()).isEqualTo(status);
        assertThat(json(response)).as(response.uri().getPath()).containsEntry("code", code);
    }

    @Test
    @DisplayName("each of the five operations answers 204 without a body and changes only its own column")
    void successes() {
        long target = user("target", false);
        int tokens = fixtures.activeRefreshTokens(target);
        Map<String, Object> before = fixtures.userColumns(target);

        HttpResponse<String> grant = api.operate(admin, target, "grant-admin");
        assertThat(fixtures.userColumns(target)).containsEntry("ADMIN_FLAG", true);
        HttpResponse<String> revoke = api.operate(admin, target, "revoke-admin");
        assertThat(fixtures.userColumns(target)).isEqualTo(before);
        assertThat(fixtures.activeRefreshTokens(target))
                .as("印の操作はトークンに触れない（AC2.1.2）")
                .isEqualTo(tokens);
        HttpResponse<String> suspend = api.operate(admin, target, "suspend");
        assertThat(fixtures.userColumns(target)).containsEntry("SUSPENDED", true);
        HttpResponse<String> resume = api.operate(admin, target, "resume");
        assertThat(fixtures.userColumns(target)).isEqualTo(before);
        fixtures.lockState(target, 5, clock.instant().plus(Duration.ofMinutes(15)));
        HttpResponse<String> reset = api.operate(admin, target, "reset-login-failures");

        for (HttpResponse<String> response : List.of(grant, revoke, suspend, resume, reset)) {
            assertThat(response.statusCode()).as(response.uri().getPath()).isEqualTo(204);
            assertThat(response.body()).isEmpty();
        }
        assertThat(fixtures.lockColumns(target))
                .containsEntry("CONSECUTIVE_FAILURES", 0)
                .containsEntry("LOCKED_UNTIL", null);
        assertThat(fixtures.userColumns(target)).isEqualTo(before);
    }

    @Test
    @DisplayName("business rejections answer the code of the reason, change nothing and carry no user values")
    void rejections() {
        String self = String.valueOf(adminId);
        long suspendedMember = user("suspended-member", false);
        long suspendedAdmin = user("suspended-admin", true);
        fixtures.suspend(suspendedMember);
        fixtures.suspend(suspendedAdmin);
        long member = user("member", false);
        long otherAdmin = user("other-admin", true);
        Map<String, Object> memberBefore = fixtures.userColumns(member);
        Map<String, Object> adminBefore = fixtures.userColumns(otherAdmin);
        Map<String, Object> suspendedBefore = fixtures.userColumns(suspendedMember);

        for (String action : List.of("grant-admin", "revoke-admin", "suspend", "resume")) {
            assertCode(api.operate(admin, self, action), 409, "USER_ADMIN_SELF_OPERATION");
        }
        assertCode(api.operate(admin, self, "reset-login-failures"), 409, "USER_ADMIN_NO_CHANGE");
        assertCode(api.operate(admin, suspendedMember, "grant-admin"), 409, "USER_ADMIN_TARGET_SUSPENDED");
        assertCode(api.operate(admin, suspendedAdmin, "revoke-admin"), 409, "USER_ADMIN_TARGET_SUSPENDED");
        assertCode(api.operate(admin, otherAdmin, "grant-admin"), 409, "USER_ADMIN_NO_CHANGE");
        assertCode(api.operate(admin, member, "revoke-admin"), 409, "USER_ADMIN_NO_CHANGE");
        assertCode(api.operate(admin, suspendedMember, "suspend"), 409, "USER_ADMIN_NO_CHANGE");
        assertCode(api.operate(admin, member, "resume"), 409, "USER_ADMIN_NO_CHANGE");
        assertCode(api.operate(admin, member, "reset-login-failures"), 409, "USER_ADMIN_NO_CHANGE");
        assertThat(fixtures.lockColumns(member))
                .containsEntry("CONSECUTIVE_FAILURES", 0)
                .containsEntry("LOCKED_UNTIL", null);

        assertThat(fixtures.userColumns(member)).isEqualTo(memberBefore);
        assertThat(fixtures.userColumns(otherAdmin)).isEqualTo(adminBefore);
        assertThat(fixtures.userColumns(suspendedMember)).isEqualTo(suspendedBefore);
        assertThat(fixtures.userColumns(adminId))
                .containsEntry("ADMIN_FLAG", true)
                .containsEntry("SUSPENDED", false);

        HttpResponse<String> named = api.operate(admin, member, "revoke-admin");
        assertThat(named.body()).doesNotContain(emailOf(member)).doesNotContain("対象 member");
        for (String key : new String[] {"title", "detail"}) {
            assertThat((String) json(named).get(key)).doesNotContain(String.valueOf(member));
        }
    }

    @Test
    @DisplayName("an unknown user is 404 USER_NOT_FOUND for every operation, a non number is 400, in ja and en")
    void unknownAndMalformedUsers() {
        for (String action : ACTIONS) {
            for (String userId : List.of(String.valueOf(Long.MAX_VALUE), "0", "-5")) {
                assertCode(api.operate(admin, userId, action), 404, "USER_NOT_FOUND");
            }
            for (String userId : List.of("abc", "99999999999999999999", "1.5")) {
                assertCode(api.operate(admin, userId, action), 400, "VALIDATION_FAILED");
            }
        }

        HttpResponse<String> ja = api.operate(admin, Long.MAX_VALUE, "suspend");
        HttpResponse<String> en = api.operate(admin, Long.MAX_VALUE, "suspend", "Accept-Language", "en-US,en;q=0.9");
        HttpResponse<String> conflictEn = api.operate(admin, adminId, "suspend", "Accept-Language", "en-US,en;q=0.9");
        assertThat(json(ja)).containsEntry("title", "利用者が見つかりません");
        assertThat(json(en)).containsEntry("title", "User not found");
        assertThat(json(conflictEn)).containsEntry("title", "This operation cannot be applied to yourself");
        assertThat(json(api.operate(admin, adminId, "suspend"))).containsEntry("title", "自分自身には行えない操作です");
    }

    @Test
    @DisplayName("401 without a token, 403 ACCESS_DENIED for a member, 401 for a suspended admin, nothing changes")
    void authorization() {
        long target = user("target", false);
        fixtures.lockState(target, 5, clock.instant().plus(Duration.ofMinutes(15)));
        String memberEmail = email("member");
        fixtures.create(memberEmail, "一般 三郎", false);
        String member = fixtures.login(memberEmail);
        String suspendedEmail = email("suspended-admin");
        long suspendedAdmin = fixtures.create(suspendedEmail, "停止 管理者", true);
        String suspendedToken = fixtures.login(suspendedEmail);
        fixtures.suspend(suspendedAdmin);
        Map<String, Object> before = fixtures.userColumns(target);
        Map<String, Object> lockBefore = fixtures.lockColumns(target);
        int events = userAdminEvents();

        for (String action : ACTIONS) {
            assertCode(api.operate(null, target, action), 401, "AUTHENTICATION_REQUIRED");
            assertCode(api.operate(member, target, action), 403, "ACCESS_DENIED");
            assertCode(api.operate(suspendedToken, target, action), 401, "AUTHENTICATION_REQUIRED");
        }

        assertThat(fixtures.userColumns(target)).isEqualTo(before);
        assertThat(fixtures.lockColumns(target)).isEqualTo(lockBefore);
        assertThat(userAdminEvents()).as("管理の操作の監査は残らない（NFR1.1・NFR1.3）").isEqualTo(events);
        assertThat(api.operate(admin, target, "grant-admin").statusCode()).isEqualTo(204);
        assertThat(userAdminEvents()).isEqualTo(events + 1);
    }

    @Test
    @DisplayName("granting and revoking switch the next admin request between 200 and 403 without revoking tokens")
    void flagChangeTakesEffectOnTheNextRequest() {
        String targetEmail = email("promoted");
        long target = fixtures.create(targetEmail, "昇格 花子", false);
        HttpResponse<String> login = auth.login(targetEmail, UserAdminFixtures.PASSWORD);
        String token = AuthApi.accessToken(login);
        String cookie = AuthApi.cookieValue(login);
        MeApi me = new MeApi(port);

        assertCode(api.list(token, ""), 403, "ACCESS_DENIED");
        assertThat(api.operate(admin, target, "grant-admin").statusCode()).isEqualTo(204);
        assertThat(api.list(token, "").statusCode()).as("印を付けた直後の次の要求").isEqualTo(200);
        assertThat(api.operate(admin, target, "revoke-admin").statusCode()).isEqualTo(204);
        assertCode(api.list(token, ""), 403, "ACCESS_DENIED");

        assertThat(me.getPreferences(token).statusCode())
                .as("外す前に出したアクセストークンは無効にならない（管理の API だけが 403）")
                .isEqualTo(200);
        assertThat(fixtures.activeRefreshTokens(target)).isEqualTo(1);
        assertThat(auth.refresh(cookie, auth.origin()).statusCode()).isEqualTo(200);
        assertCode(api.operate(admin, adminId, "revoke-admin"), 409, "USER_ADMIN_SELF_OPERATION");
        assertThat(api.list(admin, "").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("suspending revokes every refresh token of the user and resuming does not bring them back")
    void suspendRevokesRefreshTokens() {
        String targetEmail = email("suspended");
        long target = fixtures.create(targetEmail, "停止 次郎", false);
        String first = AuthApi.cookieValue(auth.login(targetEmail, UserAdminFixtures.PASSWORD));
        String second = AuthApi.cookieValue(auth.login(targetEmail, UserAdminFixtures.PASSWORD));
        assertThat(fixtures.activeRefreshTokens(target)).isEqualTo(2);

        assertThat(api.operate(admin, target, "suspend").statusCode()).isEqualTo(204);
        assertThat(fixtures.activeRefreshTokens(target)).isZero();
        for (String cookie : List.of(first, second)) {
            assertCode(auth.refresh(cookie, auth.origin()), 401, "REFRESH_FAILED");
        }

        assertThat(api.operate(admin, target, "resume").statusCode()).isEqualTo(204);
        assertThat(fixtures.activeRefreshTokens(target)).isZero();
        for (String cookie : List.of(first, second)) {
            assertCode(auth.refresh(cookie, auth.origin()), 401, "REFRESH_FAILED");
        }
        assertThat(auth.login(targetEmail, UserAdminFixtures.PASSWORD).statusCode())
                .as("停止を解いた後はログインし直せる")
                .isEqualTo(200);
    }
}
