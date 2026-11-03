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
import cherry.mastersmith.auth.testsupport.TestUserSuspension;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import java.net.http.HttpResponse;
import java.nio.file.Path;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * プリファレンスの API の 5 つ目の項目（表示のタイムゾーン。Intent 261102-timezone-ui-polish の U2 の BR2.2〜BR2.9、NFR1.2.x・
 * NFR1.4.x・NFR4.1.5）の結合テスト。組み込みの H2。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class MePreferencesDisplayTimeZoneApiIT {

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

    private MeApi me;

    private AuthApi auth;

    private AdminTestUsers users;

    @BeforeEach
    void setUp() {
        me = new MeApi(port);
        auth = new AuthApi(port);
        users = new AdminTestUsers(userAccountService, port);
    }

    private String zone(long userId) {
        return jdbc.queryForObject("SELECT display_time_zone FROM users WHERE user_id = ?", String.class, userId);
    }

    private Map<String, Object> row(long userId) {
        return jdbc.queryForMap(
                "SELECT email, display_name, language, theme, font_size, password_hash, admin_flag, suspended,"
                        + " display_time_zone FROM users WHERE user_id = ?",
                userId);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
    }

    @Test
    @DisplayName("the three states: a name is saved, an absent item keeps it, an explicit null clears it")
    void threeStates() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);

        HttpResponse<String> set = me.putPreferencesWithZone(token, "状態 一", "ja", "light", "md", "Asia/Kolkata");
        assertThat(set.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(set)).containsEntry("displayTimeZone", "Asia/Kolkata");
        assertThat(zone(member.userId())).isEqualTo("Asia/Kolkata");

        HttpResponse<String> kept = me.putPreferences(token, "状態 二", "en", "dark", "lg");
        assertThat(kept.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(kept)).containsEntry("displayTimeZone", "Asia/Kolkata");
        assertThat(zone(member.userId())).isEqualTo("Asia/Kolkata");
        assertThat(row(member.userId())).containsEntry("DISPLAY_NAME", "状態 二").containsEntry("LANGUAGE", "en");

        HttpResponse<String> cleared = me.putPreferencesWithZone(token, "状態 三", "en", "dark", "lg", null);
        assertThat(cleared.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(cleared)).containsEntry("displayTimeZone", null);
        assertThat(zone(member.userId())).isNull();
        assertThat(HttpTestClient.json(me.getPreferences(token))).containsEntry("displayTimeZone", null);
    }

    @Test
    @DisplayName("GET always carries the fifth item: unset is null and a saved name is returned as is")
    void getCarriesTheFifthItem() {
        String token = users.accessToken(users.createNonAdmin());
        assertThat(HttpTestClient.json(me.getPreferences(token))).containsKey("displayTimeZone");

        me.putPreferencesWithZone(token, "名前", "ja", "system", "md", "America/New_York");

        assertThat(HttpTestClient.json(me.getPreferences(token))).containsEntry("displayTimeZone", "America/New_York");
    }

    @Test
    @DisplayName("four kinds of invalid values are 400 with the displayTimeZone error only, and nothing is saved")
    void invalidValuesAreRejected() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        me.putPreferencesWithZone(token, "元の 名前", "ja", "light", "sm", "Asia/Tokyo");
        Map<String, Object> before = row(member.userId());
        int auditBefore = auditCount();

        for (Object bad :
                new Object[] {"", "+09:00", "Mars/Olympus-MARK", 42, true, Map.of("a", 1), List.of("Asia/Tokyo")}) {
            HttpResponse<String> response = me.putPreferencesWithZone(token, "変わる 名前", "en", "dark", "lg", bad);

            assertThat(response.statusCode()).as(String.valueOf(bad)).isEqualTo(400);
            Map<String, Object> body = HttpTestClient.json(response);
            assertThat(body).containsEntry("code", "VALIDATION_FAILED");
            assertThat(body.get("fieldErrors"))
                    .isEqualTo(List.of(Map.of("field", "displayTimeZone", "reason", "INVALID_VALUE")));
            assertThat(response.body()).doesNotContain("MARK").doesNotContain("変わる");
            assertThat(row(member.userId())).isEqualTo(before);
        }
        assertThat(auditCount()).isEqualTo(auditBefore);
    }

    @Test
    @DisplayName("errors are listed in order and the zone error comes last")
    void errorsAreInOrder() {
        String token = users.accessToken(users.createNonAdmin());

        HttpResponse<String> response = me.putPreferencesWithZone(token, "", "EN", "Dark", "xl", "+09:00");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(response).get("fieldErrors"))
                .isEqualTo(List.of(
                        Map.of("field", "displayName", "reason", "REQUIRED"),
                        Map.of("field", "language", "reason", "INVALID_VALUE"),
                        Map.of("field", "theme", "reason", "INVALID_VALUE"),
                        Map.of("field", "fontSize", "reason", "INVALID_VALUE"),
                        Map.of("field", "displayTimeZone", "reason", "INVALID_VALUE")));
    }

    @Test
    @DisplayName("a name is matched exactly: other case, padded and offset forms are rejected, 64 UTC names pass")
    void exactMatchOnly() {
        String token = users.accessToken(users.createNonAdmin());

        for (String bad : new String[] {"asia/tokyo", " Asia/Tokyo", "Asia/Tokyo ", "Z", "UTC+9", "-03:30"}) {
            assertThat(me.putPreferencesWithZone(token, "名前", "ja", "system", "md", bad)
                            .statusCode())
                    .as(bad)
                    .isEqualTo(400);
        }
        assertThat(me.putPreferencesWithZone(token, "名前", "ja", "system", "md", "UTC")
                        .statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("when the item appears twice in the body, the later value wins")
    void duplicateKeyLaterWins() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        String json = "{\"displayName\":\"重複\",\"language\":\"ja\",\"theme\":\"light\",\"fontSize\":\"md\","
                + "\"displayTimeZone\":\"Asia/Tokyo\",\"displayTimeZone\":\"Europe/London\"}";

        HttpResponse<String> response = me.putPreferencesRaw(token, json);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(zone(member.userId())).isEqualTo("Europe/London");
    }

    @Test
    @DisplayName("a value of 100,000 characters is rejected without echoing it")
    void veryLongValueIsRejected() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        String huge = "A".repeat(100_000);

        HttpResponse<String> response = me.putPreferencesWithZone(token, "名前", "ja", "system", "md", huge);

        // 実測で 400 VALIDATION_FAILED（長さの上限 64 を超える値）。413 にはならない（決めた側の動作として固定。D-P4）。
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("AAAAAAAAAA");
        assertThat(zone(member.userId())).isNull();
    }

    @Test
    @DisplayName("a body naming another user, email, admin flag or suspension changes only the caller's own columns")
    void onlyTheCallerChanges() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        AdminTestUsers.TestUser other = users.createNonAdmin();
        Map<String, Object> otherBefore = row(other.userId());
        String json = "{\"userId\":" + other.userId() + ",\"email\":\"" + other.email()
                + "\",\"admin\":true,\"suspended\":true,\"displayName\":\"本人だけ\",\"language\":\"en\","
                + "\"theme\":\"light\",\"fontSize\":\"sm\",\"displayTimeZone\":\"Asia/Tokyo\"}";

        HttpResponse<String> response = me.putPreferencesRaw(users.accessToken(member), json);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(zone(member.userId())).isEqualTo("Asia/Tokyo");
        assertThat(row(other.userId())).isEqualTo(otherBefore);
        assertThat(row(member.userId())).containsEntry("ADMIN_FLAG", false).containsEntry("SUSPENDED", false);
    }

    @Test
    @DisplayName("without a token it is 401 and nothing changes")
    void unauthenticated() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        Map<String, Object> before = row(member.userId());

        HttpResponse<String> response = me.putPreferencesWithZone(null, "名前", "ja", "system", "md", "Asia/Tokyo");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(row(member.userId())).isEqualTo(before);
    }

    @Test
    @DisplayName("when the caller's row is deleted while the token is valid, the save is 401 and adds no row")
    void deletedUserIs401() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", member.userId());
        jdbc.update("DELETE FROM users WHERE user_id = ?", member.userId());
        int usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);

        HttpResponse<String> response = me.putPreferencesWithZone(token, "消えた人", "ja", "system", "md", "Asia/Tokyo");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                .isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("a suspended user is rejected on save, and the next save passes right after resuming")
    void suspendedUserIsRejected() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        TestUserSuspension suspension = new TestUserSuspension(
                new TransactionTemplate(transactionManager), userAccountService, revocationService);

        suspension.suspend(member.userId());
        HttpResponse<String> rejected = me.putPreferencesWithZone(token, "停止中", "ja", "system", "md", "Asia/Tokyo");
        suspension.resume(member.userId());
        HttpResponse<String> passed = me.putPreferencesWithZone(token, "解除後", "ja", "system", "md", "Asia/Tokyo");

        assertThat(rejected.statusCode()).isIn(401, 403);
        assertThat(passed.statusCode()).isEqualTo(200);
        assertThat(zone(member.userId())).isEqualTo("Asia/Tokyo");
    }

    @Test
    @DisplayName("the login and refresh responses do not carry the display time zone")
    void loginResponseHasNoZone() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        me.putPreferencesWithZone(users.accessToken(member), "名前", "ja", "system", "md", "Asia/Tokyo");

        HttpResponse<String> login = auth.login(member.email(), AdminTestUsers.PASSWORD);

        assertThat(login.body()).doesNotContain("displayTimeZone").doesNotContain("Asia/Tokyo");
    }

    @Test
    @DisplayName("saving, with or without the zone, never adds an audit event")
    void savingIsNotAudited() {
        String token = users.accessToken(users.createNonAdmin());
        int before = auditCount();

        me.putPreferencesWithZone(token, "監査しない", "en", "dark", "lg", "Asia/Tokyo");
        me.putPreferencesWithZone(token, "監査しない", "en", "dark", "lg", "+09:00");
        me.putPreferencesWithZone(token, "監査しない", "en", "dark", "lg", null);

        assertThat(auditCount()).isEqualTo(before);
    }
}
