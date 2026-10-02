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
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.testsupport.UserAdminApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
 * 管理者による氏名と言語の変更の API（{@code PUT /api/admin/users/{userId}/profile}、契約 C3、BR2.7・BR5.1〜BR5.4・BR7.1、NFR1.1・
 * NFR1.3・NFR1.5・NFR8.1）の結合テスト。team.md の必須のテスト（管理の API の認可、要求の改ざん）を含む。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class UserAdminProfileApiIT {

    private static final String COLUMNS = "SELECT email, display_name, language, theme, font_size, password_hash,"
            + " admin_flag, suspended, created_at FROM users WHERE user_id = ?";

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

    private UserAdminApi api;

    private UserAdminFixtures fixtures;

    private long adminId;

    private String admin;

    @BeforeEach
    void setUp() {
        api = new UserAdminApi(port);
        fixtures = new UserAdminFixtures(userAccountService, revocationService, transactionManager, jdbc, port);
        String email = "admin-" + UserAdminFixtures.marker() + "@example.com";
        adminId = fixtures.create(email, "管理 太郎", true);
        admin = fixtures.login(email);
    }

    private long target() {
        return fixtures.create("target-" + UserAdminFixtures.marker() + "@example.com", "元の 氏名", false);
    }

    private Map<String, Object> row(long userId) {
        return jdbc.queryForMap(COLUMNS, userId);
    }

    private int auditCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    private static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    private static Map<String, Object> without(Map<String, Object> row, String... keys) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        for (String key : keys) {
            copy.remove(key);
        }
        return copy;
    }

    @Test
    @DisplayName("204 without a body changes only the name and the language, and nothing is audited")
    void updatesNameAndLanguage() {
        long userId = target();
        Map<String, Object> before = row(userId);
        int audit = auditCount();

        HttpResponse<String> response = api.putProfile(admin, String.valueOf(userId), "  新しい 氏名　", "en");

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        Map<String, Object> after = row(userId);
        assertThat(after).containsEntry("DISPLAY_NAME", "新しい 氏名").containsEntry("LANGUAGE", "en");
        assertThat(without(after, "DISPLAY_NAME", "LANGUAGE")).isEqualTo(without(before, "DISPLAY_NAME", "LANGUAGE"));
        assertThat(auditCount()).isEqualTo(audit);
    }

    @Test
    @DisplayName("an admin may change their own name and language, and the same values again are still 204")
    void selfAndSameValues() {
        HttpResponse<String> self = api.putProfile(admin, String.valueOf(adminId), "自分の 氏名", "en");
        HttpResponse<String> again = api.putProfile(admin, String.valueOf(adminId), "自分の 氏名", "en");

        assertThat(self.statusCode()).isEqualTo(204);
        assertThat(again.statusCode()).isEqualTo(204);
        assertThat(row(adminId)).containsEntry("DISPLAY_NAME", "自分の 氏名").containsEntry("LANGUAGE", "en");
    }

    @Test
    @DisplayName("input errors are 400 with only field names and reasons, change nothing and are not audited")
    void inputErrors() {
        long userId = target();
        Map<String, Object> before = row(userId);
        int audit = auditCount();
        String target = String.valueOf(userId);

        HttpResponse<String> empty = api.putProfile(admin, target, " 　 ", "ja");
        HttpResponse<String> tooLong = api.putProfile(admin, target, "長".repeat(255), "ja");
        HttpResponse<String> control = api.putProfile(admin, target, "制御\u0007文字", "JA");
        HttpResponse<String> french = api.putProfile(admin, target, "254 文字以内", "fr");
        HttpResponse<String> missing = api.putProfileJson(admin, target, "{}");

        assertThat(json(empty).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "displayName", "reason", "REQUIRED")));
        assertThat(json(tooLong).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "displayName", "reason", "TOO_LONG")));
        assertThat(json(control).get("fieldErrors"))
                .isEqualTo(List.of(
                        Map.of("field", "displayName", "reason", "INVALID_CHARACTER"),
                        Map.of("field", "language", "reason", "INVALID_VALUE")));
        assertThat(json(french).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "language", "reason", "INVALID_VALUE")));
        assertThat(json(missing).get("fieldErrors"))
                .isEqualTo(List.of(
                        Map.of("field", "displayName", "reason", "REQUIRED"),
                        Map.of("field", "language", "reason", "REQUIRED")));
        for (HttpResponse<String> response : List.of(empty, tooLong, control, french, missing)) {
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json(response)).containsEntry("code", "VALIDATION_FAILED");
            assertThat(response.body())
                    .doesNotContain("長長長")
                    .doesNotContain("制御")
                    .doesNotContain("fr\"");
        }
        assertThat(api.putProfile(admin, target, "長".repeat(254), "ja").statusCode())
                .isEqualTo(204);
        assertThat(without(row(userId), "DISPLAY_NAME")).isEqualTo(without(before, "DISPLAY_NAME"));
        assertThat(auditCount()).isEqualTo(audit);
    }

    @Test
    @DisplayName("an unknown user is 404 USER_NOT_FOUND in Japanese and English, after input errors, without audit")
    void unknownUser() {
        int audit = auditCount();

        HttpResponse<String> ja = api.putProfile(admin, String.valueOf(Long.MAX_VALUE), "だれか", "ja");
        HttpResponse<String> en = api.putProfile(admin, "0", "だれか", "ja", "Accept-Language", "en-US,en;q=0.9");
        HttpResponse<String> both = api.putProfile(admin, "-5", "", "ja");
        HttpResponse<String> notNumber = api.putProfile(admin, "abc", "だれか", "ja");
        HttpResponse<String> overflow = api.putProfile(admin, "99999999999999999999", "だれか", "ja");

        assertThat(ja.statusCode()).isEqualTo(404);
        assertThat(json(ja)).containsEntry("code", "USER_NOT_FOUND").containsEntry("title", "利用者が見つかりません");
        assertThat(en.statusCode()).isEqualTo(404);
        assertThat(json(en)).containsEntry("code", "USER_NOT_FOUND").containsEntry("title", "User not found");
        for (String key : new String[] {"title", "detail"}) {
            assertThat((String) json(ja).get(key))
                    .as("説明文に対象の利用者 ID を載せない（instance は要求のパスのまま）")
                    .doesNotContain(String.valueOf(Long.MAX_VALUE));
        }
        assertThat(both.statusCode()).isEqualTo(400);
        assertThat(json(both).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "displayName", "reason", "REQUIRED")));
        for (HttpResponse<String> response : List.of(notNumber, overflow)) {
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json(response)).containsEntry("code", "VALIDATION_FAILED");
        }
        assertThat(auditCount()).isEqualTo(audit);
    }

    @Test
    @DisplayName("extra fields for the flag, suspension, email, theme or failures are ignored (mass assignment)")
    void ignoresExtraFields() {
        long userId = target();
        Map<String, Object> before = row(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", "改ざん 確認");
        body.put("language", "en");
        body.put("admin", true);
        body.put("adminFlag", true);
        body.put("suspended", true);
        body.put("email", "changed@example.com");
        body.put("theme", "dark");
        body.put("fontSize", "lg");
        body.put("consecutiveFailures", 0);
        body.put("userId", adminId);

        HttpResponse<String> response = api.putProfileJson(admin, String.valueOf(userId), UserAdminApi.json(body));

        assertThat(response.statusCode()).isEqualTo(204);
        Map<String, Object> after = row(userId);
        assertThat(after).containsEntry("DISPLAY_NAME", "改ざん 確認").containsEntry("LANGUAGE", "en");
        assertThat(without(after, "DISPLAY_NAME", "LANGUAGE")).isEqualTo(without(before, "DISPLAY_NAME", "LANGUAGE"));
        assertThat(row(adminId)).containsEntry("DISPLAY_NAME", "管理 太郎");
    }

    @Test
    @DisplayName("401 without a token, 403 ACCESS_DENIED for a member, 401 for a suspended admin, nothing changes")
    void authorization() {
        long userId = target();
        String mark = UserAdminFixtures.marker();
        String memberEmail = "member-" + mark + "@example.com";
        fixtures.create(memberEmail, "一般 三郎", false);
        String member = fixtures.login(memberEmail);
        String suspendedEmail = "suspended-admin-" + mark + "@example.com";
        long suspendedAdmin = fixtures.create(suspendedEmail, "停止 管理者", true);
        String suspendedToken = fixtures.login(suspendedEmail);
        fixtures.suspend(suspendedAdmin);
        Map<String, Object> before = row(userId);
        int audit = auditCount();
        String target = String.valueOf(userId);

        HttpResponse<String> anonymous = api.putProfile(null, target, "変える", "en");
        HttpResponse<String> forbidden = api.putProfile(member, target, "変える", "en");
        HttpResponse<String> suspended = api.putProfile(suspendedToken, target, "変える", "en");

        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(json(anonymous)).containsEntry("code", "AUTHENTICATION_REQUIRED");
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(json(forbidden)).containsEntry("code", "ACCESS_DENIED");
        assertThat(suspended.statusCode()).isEqualTo(401);
        assertThat(json(suspended)).containsEntry("code", "AUTHENTICATION_REQUIRED");
        assertThat(row(userId)).isEqualTo(before);
        List<String> added = jdbc.queryForList(
                "SELECT event_type || ' ' || failure_reason FROM audit_events ORDER BY audit_event_id OFFSET ? ROWS",
                String.class,
                audit);
        assertThat(added)
                .as("既存のアクセスの拒否だけが残る（停止中の管理者は残らない）")
                .containsExactly("ACCESS_DENIED TOKEN_MISSING", "ACCESS_DENIED NOT_ADMIN");
        assertThat(api.putProfile(admin, target, "変える", "en").statusCode()).isEqualTo(204);
    }
}
