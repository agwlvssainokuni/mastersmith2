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

import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.auth.testsupport.TestUserSuspension;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.UserProblemTypes;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * パスワードの変更の API（契約 C4 の POST {@code /api/me/password}、BR4.1〜BR4.5、BR8.2・BR8.3、NFR4.2・NFR4.5・NFR8.1、
 * AC5.1.1〜AC5.1.6）の結合テスト。team.md の必須のテスト（今のパスワードの確かめ、規則の境界、変更の後のトークンの扱い）を含む。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class MePasswordApiIT {

    private static final String NEW_PASSWORD = "新しいパスワード-000001";

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
    RefreshTokenRevocationService revocationService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    MutableClock clock;

    private MeApi me;

    private AuthApi auth;

    private AdminTestUsers users;

    @BeforeEach
    void setUp() {
        clock.set(AuthApiTestConfig.START);
        me = new MeApi(port);
        auth = new AuthApi(port);
        users = new AdminTestUsers(userAccountService, port);
    }

    private String hash(long userId) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, userId);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
    }

    @Test
    @DisplayName("a member changes the password with 204, then logs in only with the new password (AC5.1.1)")
    void changes() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);

        HttpResponse<String> response = me.changePassword(token, AdminTestUsers.PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().allValues("Set-Cookie"))
                .as("トークンやクッキーを出し直さない")
                .isEmpty();
        assertThat(auth.login(member.email(), NEW_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(auth.login(member.email(), AdminTestUsers.PASSWORD).statusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName(
            "a wrong current password is 400 PASSWORD_CURRENT_MISMATCH and the user stays logged in (AC5.1.2, AC5.1.6)")
    void wrongCurrentPassword() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        HttpResponse<String> login = auth.login(member.email(), AdminTestUsers.PASSWORD);
        String token = AuthApi.accessToken(login);
        String before = hash(member.userId());

        HttpResponse<String> response = me.changePassword(token, "まちがったパスワード-9", NEW_PASSWORD, NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(response))
                .containsEntry("code", "PASSWORD_CURRENT_MISMATCH")
                .doesNotContainKey("fieldErrors");
        assertThat(hash(member.userId())).isEqualTo(before);
        assertThat(me.getPreferences(token).statusCode()).as("アクセストークンは使える").isEqualTo(200);
        assertThat(auth.refresh(AuthApi.cookieValue(login), auth.origin()).statusCode())
                .as("リフレッシュトークンも使える")
                .isEqualTo(200);
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(
            delimiter = '|',
            value = {
                "11|a|newPassword|TOO_SHORT",
                "0|a|newPassword|REQUIRED",
                "73|a|newPassword|TOO_LONG",
                "11|😀|newPassword|TOO_SHORT"
            })
    @DisplayName("new passwords breaking the rule are refused with field errors, unchanged and unaudited (AC5.1.3)")
    void ruleBoundariesRejected(int count, String unit, String field, String reason) {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        String before = hash(member.userId());
        int audits = auditCount();
        String candidate = unit.repeat(count);

        HttpResponse<String> response = me.changePassword(token, AdminTestUsers.PASSWORD, candidate, candidate);

        assertThat(response.statusCode()).isEqualTo(400);
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsEntry("code", "VALIDATION_FAILED");
        assertThat(body.get("fieldErrors"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains(Map.of("field", field, "reason", reason));
        assertThat(hash(member.userId())).isEqualTo(before);
        assertThat(auditCount()).isEqualTo(audits);
    }

    @ParameterizedTest(name = "[{index}] {0} x {1}")
    @CsvSource(
            delimiter = '|',
            value = {"12|a", "72|a", "12|😀"})
    @DisplayName("new passwords at the accepted boundaries are accepted (AC5.1.3)")
    void ruleBoundariesAccepted(int count, String unit) {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String candidate = unit.repeat(count);

        assertThat(me.changePassword(users.accessToken(member), AdminTestUsers.PASSWORD, candidate, candidate)
                        .statusCode())
                .isEqualTo(204);
        assertThat(auth.login(member.email(), candidate).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("two different entries of the new password are MISMATCH and nothing changes (AC5.1.3)")
    void confirmationMismatch() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String before = hash(member.userId());

        HttpResponse<String> response =
                me.changePassword(users.accessToken(member), AdminTestUsers.PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x");

        assertThat(HttpTestClient.json(response).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "newPasswordConfirmation", "reason", "MISMATCH")));
        assertThat(hash(member.userId())).isEqualTo(before);
    }

    @Test
    @DisplayName(
            "after changing on device A, both A and another device B can still refresh (AC5.1.4, the decided behavior)")
    void otherDevicesKeepTheirRefreshTokens() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        HttpResponse<String> deviceA = auth.login(member.email(), AdminTestUsers.PASSWORD);
        HttpResponse<String> deviceB = auth.login(member.email(), AdminTestUsers.PASSWORD);

        assertThat(me.changePassword(AuthApi.accessToken(deviceA), AdminTestUsers.PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                        .statusCode())
                .isEqualTo(204);

        assertThat(auth.refresh(AuthApi.cookieValue(deviceA), auth.origin()).statusCode())
                .isEqualTo(200);
        assertThat(auth.refresh(AuthApi.cookieValue(deviceB), auth.origin()).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName(
            "an access token issued before the change keeps working until it expires (AC5.1.5, the decided behavior)")
    void issuedAccessTokenWorksUntilExpiry() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String before = users.accessToken(member);

        assertThat(me.changePassword(before, AdminTestUsers.PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                        .statusCode())
                .isEqualTo(204);

        assertThat(me.getPreferences(before).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofSeconds(299));
        assertThat(me.getPreferences(before).statusCode()).as("有効期限の直前").isEqualTo(200);
        clock.advance(Duration.ofSeconds(1));
        assertThat(me.getPreferences(before).statusCode()).as("有効期限の時点").isEqualTo(401);
    }

    @Test
    @DisplayName(
            "six wrong current passwords do not block the seventh correct one and never touch the login lock (NFR4.5)")
    void mismatchesAreNotLimited() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);

        for (int i = 0; i < 6; i++) {
            assertThat(me.changePassword(token, "まちがい-" + i + "-パスワード", NEW_PASSWORD, NEW_PASSWORD)
                            .statusCode())
                    .isEqualTo(400);
        }
        HttpResponse<String> seventh = me.changePassword(token, AdminTestUsers.PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        assertThat(seventh.statusCode()).isEqualTo(204);
        Map<String, Object> lock = jdbc.queryForMap(
                "SELECT consecutive_failures, locked_until FROM login_attempt_states WHERE subject_id = ?",
                member.userId());
        assertThat(lock.get("CONSECUTIVE_FAILURES")).isEqualTo(0);
        assertThat(lock.get("LOCKED_UNTIL")).isNull();
        assertThat(auth.login(member.email(), NEW_PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("the error texts follow Accept-Language for the mismatch and the validation failure (NFR8.1)")
    void errorTextsFollowAcceptLanguage() {
        String token = users.accessToken(users.createNonAdmin());

        Map<String, Object> mismatchJa = HttpTestClient.json(
                me.changePassword(token, "まちがったパスワード-9", NEW_PASSWORD, NEW_PASSWORD, "Accept-Language", "ja"));
        Map<String, Object> mismatchEn = HttpTestClient.json(
                me.changePassword(token, "まちがったパスワード-9", NEW_PASSWORD, NEW_PASSWORD, "Accept-Language", "en"));
        Map<String, Object> invalidJa =
                HttpTestClient.json(me.changePassword(token, "", NEW_PASSWORD, NEW_PASSWORD, "Accept-Language", "ja"));
        Map<String, Object> invalidEn =
                HttpTestClient.json(me.changePassword(token, "", NEW_PASSWORD, NEW_PASSWORD, "Accept-Language", "en"));

        assertThat(mismatchJa)
                .containsEntry(
                        "title",
                        UserProblemTypes.PASSWORD_CURRENT_MISMATCH.title().ja());
        assertThat(mismatchEn)
                .containsEntry(
                        "title",
                        UserProblemTypes.PASSWORD_CURRENT_MISMATCH.title().en());
        assertThat(invalidJa)
                .containsEntry(
                        "title", CommonProblemTypes.VALIDATION_FAILED.title().ja());
        assertThat(invalidEn)
                .containsEntry(
                        "title", CommonProblemTypes.VALIDATION_FAILED.title().en());
        assertThat(mismatchJa.get("detail")).isNotEqualTo(mismatchEn.get("detail"));
        assertThat(invalidJa.get("detail")).isNotEqualTo(invalidEn.get("detail"));
    }

    @Test
    @DisplayName("an admin can change the password as well")
    void adminCanChange() {
        AdminTestUsers.TestUser admin = users.createAdmin();

        assertThat(me.changePassword(users.accessToken(admin), AdminTestUsers.PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                        .statusCode())
                .isEqualTo(204);
    }

    private boolean suspended(long userId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT suspended FROM users WHERE user_id = ?", Boolean.class, userId));
    }

    private boolean admin(long userId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT admin_flag FROM users WHERE user_id = ?", Boolean.class, userId));
    }

    /** パスワードの変更に、項目を足した本文をそのまま送る。 */
    private HttpResponse<String> changePasswordWith(String token, Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentPassword", AdminTestUsers.PASSWORD);
        body.put("newPassword", NEW_PASSWORD);
        body.put("newPasswordConfirmation", NEW_PASSWORD);
        body.putAll(extra);
        HttpTestClient client = new HttpTestClient(port);
        return client.send(client.request(MeApi.PASSWORD)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        JsonMapper.builder().build().writeValueAsString(body)))
                .build());
    }

    @Test
    @DisplayName(
            "suspended and admin in the body are ignored: the state does not change and the next request still passes")
    void suspensionAndAdminCannotBeSetThroughPasswordChange() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);

        HttpResponse<String> response = changePasswordWith(token, Map.of("suspended", true, "admin", true));

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(suspended(member.userId())).isFalse();
        assertThat(admin(member.userId())).isFalse();
        assertThat(me.getPreferences(token).statusCode()).isEqualTo(200);
        assertThat(auth.login(member.email(), NEW_PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("a suspended user sending suspended false stays 401, keeps the password and stays suspended")
    void suspendedUserCannotResumeThroughPasswordChange() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        new TestUserSuspension(new TransactionTemplate(transactionManager), userAccountService, revocationService)
                .suspend(member.userId());
        String hashBefore = hash(member.userId());

        HttpResponse<String> response = changePasswordWith(token, Map.of("suspended", false));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "AUTHENTICATION_REQUIRED");
        assertThat(suspended(member.userId())).isTrue();
        assertThat(hash(member.userId())).isEqualTo(hashBefore);
        assertThat(me.getPreferences(token).statusCode()).isEqualTo(401);
    }
}
