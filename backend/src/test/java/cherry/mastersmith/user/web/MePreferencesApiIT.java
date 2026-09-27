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
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.AuthTestTokens;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.OffsetDateTime;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * プリファレンスの API（契約 C4 の GET・PUT {@code /api/me/preferences}、BR3.1〜BR3.5、BR6.1、BR8.1、BR9.1、NFR4.1〜NFR4.3）の
 * 結合テスト。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class MePreferencesApiIT {

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
    PasswordEncoder passwordEncoder;

    private MeApi me;

    private AuthApi auth;

    private AdminTestUsers users;

    @BeforeEach
    void setUp() {
        me = new MeApi(port);
        auth = new AuthApi(port);
        users = new AdminTestUsers(userAccountService, port);
    }

    private Map<String, Object> row(long userId) {
        return jdbc.queryForMap(
                "SELECT email, display_name, language, theme, font_size, password_hash, admin_flag FROM users"
                        + " WHERE user_id = ?",
                userId);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
    }

    private static void assertAuthenticationRequired(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("without a token or with a tampered token all three APIs are 401 and nothing changes")
    void unauthenticated() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String tampered = AuthTestTokens.tamper(users.accessToken(member));
        Map<String, Object> before = row(member.userId());

        for (String token : new String[] {null, tampered}) {
            assertAuthenticationRequired(me.getPreferences(token));
            assertAuthenticationRequired(me.putPreferences(token, "変える 名前", "en", "dark", "lg"));
            assertAuthenticationRequired(
                    me.changePassword(token, AdminTestUsers.PASSWORD, "新しいパスワード-000000", "新しいパスワード-000000"));
        }

        assertThat(row(member.userId())).isEqualTo(before);
    }

    @Test
    @DisplayName(
            "a member reads the stored four values with theme system kept, and without the email or the admin flag")
    void memberReadsPreferences() {
        String token = users.accessToken(users.createNonAdmin());

        HttpResponse<String> response = me.getPreferences(token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(response))
                .isEqualTo(Map.of(
                        "displayName", TestUserAccounts.DISPLAY_NAME,
                        "language", "ja",
                        "theme", "system",
                        "fontSize", "md"));
    }

    @Test
    @DisplayName("a member saves the four values, and later reads, logins and refreshes return the saved values")
    void memberSavesPreferences() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);

        HttpResponse<String> saved = me.putPreferences(token, " 山田 花子　", "en", "dark", "lg");

        Map<String, Object> expected =
                Map.of("displayName", "山田 花子", "language", "en", "theme", "dark", "fontSize", "lg");
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(saved)).isEqualTo(expected);
        assertThat(HttpTestClient.json(me.getPreferences(token))).isEqualTo(expected);
        HttpResponse<String> login = auth.login(member.email(), AdminTestUsers.PASSWORD);
        assertThat(HttpTestClient.json(login).get("user"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsAllEntriesOf(expected);
        HttpResponse<String> refreshed = auth.refresh(AuthApi.cookieValue(login), auth.origin());
        assertThat(HttpTestClient.json(refreshed).get("user"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsAllEntriesOf(expected);
    }

    @Test
    @DisplayName("a user stored before V7 (name is the email, defaults for the rest) reads those initial values")
    void userFromBeforeV7() {
        String email = "before-v7-" + UUID.randomUUID() + "@example.com";
        String password = "V7の前からいる利用者-01";
        jdbc.update(
                "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name) VALUES (?, ?, FALSE, ?, ?)",
                email,
                passwordEncoder.encode(password),
                OffsetDateTime.now(),
                email);
        String token = AuthApi.accessToken(auth.login(email, password));

        assertThat(HttpTestClient.json(me.getPreferences(token)))
                .isEqualTo(Map.of("displayName", email, "language", "ja", "theme", "system", "fontSize", "md"));
    }

    @Test
    @DisplayName("four invalid values give four field errors in order without the entered values, and nothing changes")
    void everyErrorIsReported() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        Map<String, Object> before = row(member.userId());
        String name = "入れた名前​";

        HttpResponse<String> response = me.putPreferences(token, name, "EN", "Dark", "xl");

        assertThat(response.statusCode()).isEqualTo(400);
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsEntry("code", "VALIDATION_FAILED");
        assertThat(body.get("fieldErrors"))
                .isEqualTo(List.of(
                        Map.of("field", "displayName", "reason", "INVALID_CHARACTER"),
                        Map.of("field", "language", "reason", "INVALID_VALUE"),
                        Map.of("field", "theme", "reason", "INVALID_VALUE"),
                        Map.of("field", "fontSize", "reason", "INVALID_VALUE")));
        assertThat(response.body())
                .doesNotContain("入れた名前")
                .doesNotContain("\"EN\"")
                .doesNotContain("Dark");
        assertThat(row(member.userId())).isEqualTo(before);
    }

    @Test
    @DisplayName("a name of 254 code points is saved and 255 is TOO_LONG")
    void nameLengthBoundary() {
        String token = users.accessToken(users.createNonAdmin());

        assertThat(me.putPreferences(token, "😀".repeat(254), "ja", "system", "md")
                        .statusCode())
                .isEqualTo(200);
        HttpResponse<String> tooLong = me.putPreferences(token, "a".repeat(255), "ja", "system", "md");

        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(tooLong).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "displayName", "reason", "TOO_LONG")));
    }

    @Test
    @DisplayName("another user's id or email in the body is ignored and only the caller's values change")
    void onlyTheCallerChanges() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        AdminTestUsers.TestUser other = users.createNonAdmin();
        Map<String, Object> otherBefore = row(other.userId());
        String json = JsonMapper.builder()
                .build()
                .writeValueAsString(Map.of(
                        "userId", other.userId(),
                        "email", other.email(),
                        "displayName", "本人だけ",
                        "language", "en",
                        "theme", "light",
                        "fontSize", "sm"));

        HttpResponse<String> response = me.putPreferencesRaw(users.accessToken(member), json);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(row(member.userId())).containsEntry("DISPLAY_NAME", "本人だけ");
        assertThat(row(other.userId())).isEqualTo(otherBefore);
    }

    @Test
    @DisplayName("saving, successfully or not, never adds an audit event")
    void savingIsNotAudited() {
        String token = users.accessToken(users.createNonAdmin());
        int before = auditCount();

        me.putPreferences(token, "監査しない", "en", "dark", "lg");
        me.putPreferences(token, "", "en", "dark", "lg");

        assertThat(auditCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("a body that is not JSON is MALFORMED_REQUEST without field errors")
    void malformedBody() {
        String token = users.accessToken(users.createNonAdmin());

        HttpResponse<String> response = me.putPreferencesRaw(token, "{ not json");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(response))
                .containsEntry("code", "MALFORMED_REQUEST")
                .doesNotContainKey("fieldErrors");
    }

    @Test
    @DisplayName("an admin can use the API as well")
    void adminCanUseIt() {
        String token = users.accessToken(users.createAdmin());

        assertThat(me.getPreferences(token).statusCode()).isEqualTo(200);
        assertThat(me.putPreferences(token, "管理者", "ja", "light", "md").statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("when the caller's row is deleted while the token is still valid, the APIs are 401")
    void deletedUserIs401() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", member.userId());
        jdbc.update("DELETE FROM users WHERE user_id = ?", member.userId());

        assertAuthenticationRequired(me.getPreferences(token));
        assertAuthenticationRequired(me.putPreferences(token, "消えた人", "ja", "system", "md"));
    }
}
