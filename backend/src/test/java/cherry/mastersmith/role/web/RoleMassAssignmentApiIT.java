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
package cherry.mastersmith.role.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
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
 * 一括代入の防止（BR2.3、NFR1.4、AC1.1.10・AC1.2.10、{@code security-design.md} 3節）の結合テスト。作成・名前の変更・保存の本文に許して
 * いない項目（ID・作成者・時刻・組み込みの印・名前の鍵）を足しても反映されず、決めた項目だけで処理されることを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleMassAssignmentApiIT {

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
    ActiveDslModelHolder holder;

    @Autowired
    JdbcTemplate jdbc;

    private RoleApi api;

    private RoleFixtures fixtures;

    private Actor admin;

    private RoleActors actors;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        fixtures = new RoleFixtures(jdbc);
        actors = new RoleActors(userAccountService, revocationService, transactionManager, port);
        admin = actors.admin();
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private Map<String, Object> row(long roleId) {
        return jdbc.queryForMap("SELECT role_id, name, name_key, created_at FROM roles WHERE role_id = ?", roleId);
    }

    @Test
    @DisplayName(
            "extra fields in a creation are ignored: the id, the key, the times and the built-in flag come from the server")
    void createIgnoresExtraFields() {
        String name = RoleFixtures.uniqueName("一括代入 作成");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("roleId", 999_999);
        body.put("createdAt", "2000-01-01T00:00:00Z");
        body.put("nameKey", "forged");
        body.put("createdBy", admin.userId() + 1);
        body.put("builtIn", true);

        HttpResponse<String> response = api.createJson(admin.token(), RoleApi.json(body));

        assertThat(response.statusCode()).isEqualTo(201);
        long roleId = ((Number) HttpTestClient.json(response).get("roleId")).longValue();
        assertThat(roleId).isNotEqualTo(999_999L);
        assertThat(HttpTestClient.json(response)).doesNotContainKeys("builtIn", "createdBy", "nameKey");
        assertThat(String.valueOf(HttpTestClient.json(response).get("createdAt")))
                .doesNotStartWith("2000");
        assertThat(row(roleId)).containsEntry("NAME_KEY", name.toLowerCase(java.util.Locale.ROOT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE role_id = 999999", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("extra fields in a rename are ignored: only the name changes")
    void renameIgnoresExtraFields() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("一括代入 改名"));
        Map<String, Object> before = row(roleId);
        String renamed = RoleFixtures.uniqueName("一括代入 新名");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", renamed);
        body.put("roleId", roleId + 1000);
        body.put("createdAt", "2000-01-01T00:00:00Z");

        assertThat(api.renameJson(admin.token(), roleId, RoleApi.json(body)).statusCode())
                .isEqualTo(204);

        Map<String, Object> after = row(roleId);
        assertThat(after.get("NAME")).isEqualTo(renamed);
        assertThat(after.get("ROLE_ID")).isEqualTo(before.get("ROLE_ID"));
        assertThat(after.get("CREATED_AT")).isEqualTo(before.get("CREATED_AT"));
    }

    @Test
    @DisplayName("extra fields in a save are ignored: the settings of the role in the path change and nothing else")
    void saveIgnoresExtraFields() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("一括代入 保存"));
        long other = fixtures.role(RoleFixtures.uniqueName("一括代入 ほか"));
        Map<String, Object> entry = RoleApi.entry("SALES", null, null, "READ", null, null);
        entry.put("roleId", other);
        entry.put("updatedAt", "2000-01-01T00:00:00Z");
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("schemaName", "SALES");
        scope.put("roleId", other);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scope", scope);
        body.put("entries", List.of(entry));
        body.put("roleId", other);
        body.put("admin", true);

        assertThat(api.save(admin.token(), roleId, RoleApi.json(body)).statusCode())
                .isEqualTo(204);

        assertThat(fixtures.settingRows(roleId)).isEqualTo(1);
        assertThat(fixtures.settingRows(other)).isZero();
        assertThat(String.valueOf(jdbc.queryForObject(
                        "SELECT updated_at FROM permission_settings WHERE role_id = ?", Object.class, roleId)))
                .doesNotStartWith("2000");
        assertThat(jdbc.queryForObject("SELECT admin_flag FROM users WHERE user_id = ?", Boolean.class, admin.userId()))
                .isTrue();
    }

    @Test
    @DisplayName("extra fields in an assignment are ignored: only the pair in the path and the body is written (B5)")
    void assignIgnoresExtraFields() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("一括代入 割り当て"));
        long other = fixtures.role(RoleFixtures.uniqueName("一括代入 割り当て ほか"));
        Actor target = actors.user("一括代入 対象");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", target.userId());
        body.put("roleId", other);
        body.put("assignedAt", "2000-01-01T00:00:00Z");
        body.put("admin", true);
        body.put("workRoleId", other);

        assertThat(api.assignJson(admin.token(), roleId, RoleApi.json(body)).statusCode())
                .isEqualTo(204);

        assertThat(fixtures.assignmentRows(roleId)).isOne();
        assertThat(fixtures.assignmentRows(other)).isZero();
        assertThat(String.valueOf(jdbc.queryForObject(
                        "SELECT assigned_at FROM user_role_assignments WHERE role_id = ?", Object.class, roleId)))
                .doesNotStartWith("2000");
        assertThat(jdbc.queryForObject(
                        "SELECT admin_flag FROM users WHERE user_id = ?", Boolean.class, target.userId()))
                .isFalse();
        assertThat(fixtures.storedWorkRole(target.userId())).isNull();
    }

    @Test
    @DisplayName("a userId in a work role switch is ignored: only the caller's own selection changes (B5)")
    void switchIgnoresOtherUser() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("一括代入 切り替え"));
        Actor caller = actors.member();
        Actor other = actors.user("一括代入 他人");
        fixtures.assignUser(roleId, caller.userId());
        fixtures.assignUser(roleId, other.userId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roleId", roleId);
        body.put("userId", other.userId());

        assertThat(api.switchWorkRoleJson(caller.token(), RoleApi.json(body)).statusCode())
                .isEqualTo(204);

        assertThat(fixtures.storedWorkRole(caller.userId())).isEqualTo(roleId);
        assertThat(fixtures.storedWorkRole(other.userId())).isNull();
    }

    @Test
    @DisplayName("role fields in PUT /api/me/preferences change neither the roles nor the work role (B5)")
    void preferencesIgnoreRoleFields() {
        long assigned = fixtures.role(RoleFixtures.uniqueName("一括代入 設定"));
        long notAssigned = fixtures.role(RoleFixtures.uniqueName("一括代入 設定 外"));
        Actor caller = actors.member();
        fixtures.assignUser(assigned, caller.userId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", "一括代入 表示名");
        body.put("language", "ja");
        body.put("theme", "system");
        body.put("fontSize", "md");
        body.put("roleId", notAssigned);
        body.put("workRoleId", notAssigned);
        body.put("roles", List.of(notAssigned));
        body.put("admin", true);

        assertThat(api.send("PUT", "/api/me/preferences", caller.token(), RoleApi.json(body))
                        .statusCode())
                .isEqualTo(200);

        assertThat(fixtures.assignmentRows(notAssigned)).isZero();
        assertThat(fixtures.assignmentRows(assigned)).isOne();
        assertThat(fixtures.storedWorkRole(caller.userId())).isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT admin_flag FROM users WHERE user_id = ?", Boolean.class, caller.userId()))
                .isFalse();
    }
}
