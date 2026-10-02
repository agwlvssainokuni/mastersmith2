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

import cherry.mastersmith.access.web.AdminCheckController;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.AuthTestTokens;
import cherry.mastersmith.auth.testsupport.CountingPasswordEncoder;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.auth.testsupport.TestUserSuspension;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用停止中の利用者の、認証の3つの入口（ログインの照合・トークンの更新・アクセストークンの認証）の結合テスト（Intent 260930-user-admin の
 * U1、NFR1.1〜NFR1.4・NFR2.1〜NFR2.3・NFR5.1・NFR9.1〜NFR9.3、BR3.1・BR3.3・BR4.1〜BR4.4・BR6.1・BR6.2、AC3.2.1〜AC3.2.8）。
 *
 * <p>{@code team.md} の「利用停止」の必須のテスト（入口ごとの拒否、解いた直後の受け付け、停止の前に出したトークンの扱い、停止中の管理者）を
 * 含む。利用者はテストごとに作り、止める・解くは {@link TestUserSuspension}（契約 C1 の口）で行う。時刻は {@link MutableClock} で進める。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.test-fixture.auth-protected=true",
            "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                    + "cherry.mastersmith.auth.testsupport.SqlStatementCounter"
        })
@Import(AuthApiTestConfig.class)
class SuspendedUserAuthenticationIT {

    private static final String PASSWORD = "正しいパスワード-1234";

    private static final String WRONG = "まちがったパスワード";

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
    CountingPasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    private AuthApi api;

    private TestUserSuspension suspension;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        api = new AuthApi(port);
        clock.set(AuthApiTestConfig.START);
        transaction = new TransactionTemplate(transactionManager);
        suspension = new TestUserSuspension(transaction, userAccountService, revocationService);
        encoder.takeMatchCount();
    }

    /** テストの利用者（メールアドレスと利用者 ID）。 */
    private record TestUser(String email, long userId) {}

    private TestUser createUser(boolean admin) {
        String email = "suspended-" + UUID.randomUUID() + "@example.com";
        return new TestUser(email, TestUserAccounts.create(userAccountService, email, PASSWORD, admin));
    }

    /** ログインに成功した結果（アクセストークンとリフレッシュトークンの Cookie の値）。 */
    private record Session(String accessToken, String refreshCookie) {}

    private Session login(TestUser user) {
        HttpResponse<String> response = api.login(user.email(), PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        return new Session(AuthApi.accessToken(response), AuthApi.cookieValue(response));
    }

    private HttpResponse<String> refresh(String cookie) {
        return api.refresh(cookie, api.origin());
    }

    private int failures(long userId) {
        return jdbc.queryForObject(
                "SELECT consecutive_failures FROM login_attempt_states WHERE subject_id = ?", Integer.class, userId);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
    }

    private int activeRefreshTokens(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class, userId);
    }

    private static Map<String, Object> comparable(HttpResponse<String> response) {
        Map<String, Object> body = new HashMap<>(HttpTestClient.json(response));
        body.remove("traceId");
        body.remove("instance");
        body.put("statusCode", response.statusCode());
        return body;
    }

    private static void assertProblem(HttpResponse<String> response, String code) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(HttpTestClient.json(response)).containsEntry("code", code);
        assertThat(response.body()).doesNotContain("SUSPENDED").doesNotContain("accessToken");
        // 新しいリフレッシュトークンを渡さない（更新の失敗は、今までどおり Cookie を消す指示だけを返す）。
        AuthApi.setCookie(response).ifPresent(cookie -> assertThat(cookie).startsWith(AuthApi.COOKIE + "=;"));
    }

    private static long count(Map<String, List<String>> byThread, String kind) {
        return byThread.values().stream()
                .flatMap(List::stream)
                .filter(kind::equals)
                .count();
    }

    @Test
    @DisplayName("after suspension every entry rejects: access token 401, refresh 401 and login 401 without tokens")
    void everyEntryRejects() {
        TestUser user = createUser(false);
        Session before = login(user);
        for (int i = 0; i < 5; i++) {
            assertThat(api.login(user.email(), WRONG).statusCode()).isEqualTo(401);
        }
        clock.advance(Duration.ofSeconds(1));
        assertThat(suspension.suspend(user.userId())).isEqualTo(1);
        int audits = auditCount();

        assertProblem(api.me(before.accessToken()), "AUTHENTICATION_REQUIRED");
        assertProblem(refresh(before.refreshCookie()), "REFRESH_FAILED");
        assertThat(auditCount()).as("停止中の更新とアクセストークンの認証は監査に残らない").isEqualTo(audits);
        assertProblem(api.login(user.email(), PASSWORD), "AUTHENTICATION_FAILED");
        assertProblem(api.login(user.email(), WRONG), "AUTHENTICATION_FAILED");
        // ロック中（停止の前にしきい値まで失敗させた）でも、停止中は同じ拒否になる。
        assertThat(failures(user.userId())).isEqualTo(5);
        assertThat(auditCount()).isEqualTo(audits + 2);
    }

    @Test
    @DisplayName("each entry answers a suspended user exactly like another failure of the same entry")
    void responsesAreIndistinguishable() {
        TestUser suspended = createUser(false);
        Session suspendedSession = login(suspended);
        TestUser active = createUser(false);
        Session activeSession = login(active);
        HttpResponse<String> rotated = refresh(activeSession.refreshCookie());
        assertThat(rotated.statusCode()).isEqualTo(200);
        suspension.suspend(suspended.userId());

        HttpResponse<String> loginSuspended = api.login(suspended.email(), PASSWORD);
        HttpResponse<String> loginWrong = api.login(active.email(), WRONG);
        HttpResponse<String> refreshSuspended = refresh(suspendedSession.refreshCookie());
        HttpResponse<String> refreshRevoked = refresh(activeSession.refreshCookie());
        HttpResponse<String> accessSuspended = api.me(suspendedSession.accessToken());
        HttpResponse<String> accessTampered = api.me(AuthTestTokens.tamper(AuthApi.accessToken(rotated)));

        assertThat(comparable(loginSuspended)).isEqualTo(comparable(loginWrong));
        assertThat(comparable(refreshSuspended)).isEqualTo(comparable(refreshRevoked));
        assertThat(comparable(accessSuspended)).isEqualTo(comparable(accessTampered));
        assertThat(List.of(loginSuspended, refreshSuspended, accessSuspended))
                .allSatisfy(response ->
                        assertThat(response.body()).doesNotContain("SUSPENDED").doesNotContain(suspended.email()));
        assertThat(comparable(loginSuspended)).containsEntry("code", "AUTHENTICATION_FAILED");
        assertThat(comparable(refreshSuspended)).containsEntry("code", "REFRESH_FAILED");
        assertThat(comparable(accessSuspended)).containsEntry("code", "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("a refresh of a user suspended without revocation is 401 and the refresh token stays unrevoked")
    void refreshOfSuspendedUserRollsBack() {
        TestUser user = createUser(false);
        Session session = login(user);
        // M8 B の隙の形: 停止の列だけを確定し、リフレッシュトークンを無効にしない。
        transaction.executeWithoutResult(status -> userAccountService.setSuspended(user.userId(), true));

        assertProblem(refresh(session.refreshCookie()), "REFRESH_FAILED");

        assertThat(activeRefreshTokens(user.userId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, user.userId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("right after resume all three entries accept new credentials, the old refresh token stays rejected,"
            + " and suspended attempts did not count as failures")
    void resumeAcceptsAgain() {
        TestUser user = createUser(false);
        Session before = login(user);
        for (int i = 0; i < 2; i++) {
            api.login(user.email(), WRONG);
        }
        assertThat(failures(user.userId())).isEqualTo(2);
        suspension.suspend(user.userId());
        for (int i = 0; i < 3; i++) {
            assertThat(api.login(user.email(), WRONG).statusCode()).isEqualTo(401);
        }
        assertThat(api.login(user.email(), PASSWORD).statusCode()).isEqualTo(401);
        assertThat(failures(user.userId())).isEqualTo(2);

        suspension.resume(user.userId());

        assertThat(failures(user.userId())).isEqualTo(2);
        assertThat(api.login(user.email(), WRONG).statusCode()).isEqualTo(401);
        assertThat(failures(user.userId())).as("止める前の回数から続けて数える").isEqualTo(3);
        Session after = login(user);
        assertThat(failures(user.userId())).isZero();
        HttpResponse<String> refreshed = refresh(after.refreshCookie());
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(api.me(AuthApi.accessToken(refreshed)).statusCode()).isEqualTo(200);
        assertThat(api.me(after.accessToken()).statusCode()).isEqualTo(200);
        assertProblem(refresh(before.refreshCookie()), "REFRESH_FAILED");
    }

    @Test
    @DisplayName(
            "an access token issued before suspension is 401 while suspended and usable after resume until it expires")
    void accessTokenTimeline() {
        Instant t0 = AuthApiTestConfig.START;
        TestUser user = createUser(false);
        String accessToken = login(user).accessToken();

        clock.set(t0.plus(Duration.ofMinutes(1)));
        suspension.suspend(user.userId());
        assertThat(api.me(accessToken).statusCode()).isEqualTo(401);
        clock.set(t0.plus(Duration.ofMinutes(2)).minusMillis(1));
        assertThat(api.me(accessToken).statusCode()).isEqualTo(401);

        clock.set(t0.plus(Duration.ofMinutes(2)));
        suspension.resume(user.userId());
        assertThat(api.me(accessToken).statusCode()).isEqualTo(200);
        clock.set(t0.plus(Duration.ofMinutes(5)).minusMillis(1));
        assertThat(api.me(accessToken).statusCode()).isEqualTo(200);
        clock.set(t0.plus(Duration.ofMinutes(5)));
        assertThat(api.me(accessToken).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("a suspended administrator gets 401 from the admin APIs, nothing changes and nothing is audited")
    void suspendedAdministrator() {
        TestUser admin = createUser(true);
        String accessToken = login(admin).accessToken();
        HttpTestClient client = new HttpTestClient(port);
        assertThat(client.get(AdminCheckController.PATH, "Authorization", "Bearer " + accessToken)
                        .statusCode())
                .isEqualTo(204);
        suspension.suspend(admin.userId());
        int audits = auditCount();
        int invitations = jdbc.queryForObject("SELECT COUNT(*) FROM invitations", Integer.class);

        HttpResponse<String> check = client.get(AdminCheckController.PATH, "Authorization", "Bearer " + accessToken);
        HttpResponse<String> list = client.get("/api/admin/invitations", "Authorization", "Bearer " + accessToken);
        HttpResponse<String> invite = client.send(client.request("/api/admin/invitations")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"invitee-" + UUID.randomUUID() + "@example.com\",\"language\":\"ja\"}"))
                .build());

        for (HttpResponse<String> response : List.of(check, list, invite)) {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(HttpTestClient.json(response)).containsEntry("code", "AUTHENTICATION_REQUIRED");
        }
        assertThat(auditCount()).as("業務の行もアクセスの拒否の行も増えない").isEqualTo(audits);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invitations", Integer.class))
                .isEqualTo(invitations);
        assertThat(userAccountService.isSuspended(admin.userId())).isTrue();
        assertThat(userAccountService.findById(admin.userId()).orElseThrow().admin())
                .isTrue();
    }

    @Test
    @DisplayName("the suspension check adds no query: one select of users per access token request and per refresh")
    void noExtraQueries() {
        TestUser active = createUser(false);
        Session activeSession = login(active);
        TestUser suspended = createUser(false);
        Session suspendedSession = login(suspended);
        transaction.executeWithoutResult(status -> userAccountService.setSuspended(suspended.userId(), true));

        SqlStatementCounter.start();
        assertThat(api.me(activeSession.accessToken()).statusCode()).isEqualTo(200);
        Map<String, List<String>> activeAccess = SqlStatementCounter.stop();
        SqlStatementCounter.start();
        assertThat(api.me(suspendedSession.accessToken()).statusCode()).isEqualTo(401);
        Map<String, List<String>> suspendedAccess = SqlStatementCounter.stop();
        SqlStatementCounter.start();
        assertThat(refresh(activeSession.refreshCookie()).statusCode()).isEqualTo(200);
        Map<String, List<String>> activeRefresh = SqlStatementCounter.stop();
        SqlStatementCounter.start();
        assertThat(refresh(suspendedSession.refreshCookie()).statusCode()).isEqualTo(401);
        Map<String, List<String>> suspendedRefresh = SqlStatementCounter.stop();

        assertThat(count(activeAccess, "select users")).isEqualTo(1);
        assertThat(count(suspendedAccess, "select users")).isEqualTo(1);
        assertThat(count(suspendedRefresh, "select users"))
                .isEqualTo(count(activeRefresh, "select users"))
                .isEqualTo(1);
    }
}
