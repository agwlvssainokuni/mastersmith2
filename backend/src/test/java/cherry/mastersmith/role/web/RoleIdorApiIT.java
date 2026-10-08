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
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
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
 * ID の差し替え（IDOR）と 0 以下の ID（BR2.4・NFR1.10、AC1.1.12、計画の D-13）の結合テスト。存在しない・0・負のロールの ID を指した要求は
 * 404 {@code ROLE_NOT_FOUND} で拒否され、ほかのロールの状態が変わらないことを、読み直して確かめる。変える操作の拒否は要求の ID のまま
 * 監査に FAILURE が残り、読み取りは残らない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleIdorApiIT {

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

    @Autowired
    UserRepository users;

    private RoleApi api;

    private RoleFixtures fixtures;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        fixtures = new RoleFixtures(jdbc);
        admin = new RoleActors(userAccountService, revocationService, transactionManager, port).admin();
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("roles", jdbc.queryForList("SELECT role_id, name, updated_at FROM roles ORDER BY role_id"));
        state.put("settings", jdbc.queryForList("SELECT * FROM permission_settings ORDER BY role_id, schema_name"));
        return state;
    }

    private int failures(long roleId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE result = 'FAILURE' AND failure_reason = 'ROLE_NOT_FOUND'"
                        + " AND target_role_id = ?",
                Integer.class,
                roleId);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("every API refuses a missing, zero or negative role id with 404 and changes no other role")
    void missingIds() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("IDOR"));
        fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        String save = RoleApi.saveBody("SALES", null, List.of(RoleApi.entry("SALES", null, null, "FULL", null, null)));
        String clear = RoleApi.json(Map.of("targets", List.of(Map.of("schemaName", "OLD"))));
        Map<String, Object> before = state();

        for (long missing : new long[] {Long.MAX_VALUE, 0L, -1L}) {
            int failuresBefore = failures(missing);
            List<HttpResponse<String>> responses = List.of(
                    api.detail(admin.token(), missing),
                    api.rename(admin.token(), missing, RoleFixtures.uniqueName("IDOR 改名")),
                    api.delete(admin.token(), missing),
                    api.schemas(admin.token(), missing),
                    api.tables(admin.token(), missing, "SALES"),
                    api.columns(admin.token(), missing, "SALES", "ORDER_LINE"),
                    api.save(admin.token(), missing, save),
                    api.clear(admin.token(), missing, clear));
            for (HttpResponse<String> response : responses) {
                assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
                assertThat(HttpTestClient.json(response)).containsEntry("code", "ROLE_NOT_FOUND");
            }
            assertThat(failures(missing) - failuresBefore)
                    .as("変える操作の4つだけが監査に残る")
                    .isEqualTo(4);
        }
        assertThat(state()).isEqualTo(before);
    }

    @Test
    @DisplayName("an id that is not an integer is a 400 and changes nothing")
    void notAnInteger() {
        Map<String, Object> before = state();

        assertThat(api.detail(admin.token(), "abc").statusCode()).isEqualTo(400);
        assertThat(api.delete(admin.token(), "1.5").statusCode()).isEqualTo(400);
        assertThat(api.schemas(admin.token(), "x").statusCode()).isEqualTo(400);
        assertThat(state()).isEqualTo(before);
    }

    @Test
    @DisplayName("assignments and lookups refuse missing, zero or negative ids with the code of the missing side (B5)")
    void missingIdsOfB5() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("IDOR 割り当て"));
        GroupFixtures groups = new GroupFixtures(users, jdbc);
        long userId = groups.user("IDOR 利用者");
        long groupId = groups.group(GroupFixtures.uniqueName("IDOR"));
        Map<String, Object> before = assignmentState();

        for (long missing : new long[] {Long.MAX_VALUE, 0L, -1L}) {
            assertCode(api.assignments(admin.token(), missing), 404, "ROLE_NOT_FOUND");
            assertCode(api.assignUser(admin.token(), missing, userId), 404, "ROLE_NOT_FOUND");
            assertCode(api.unassignGroup(admin.token(), missing, groupId), 404, "ROLE_NOT_FOUND");
            assertCode(api.assignUser(admin.token(), roleId, missing), 404, "USER_NOT_FOUND");
            assertCode(api.assignGroup(admin.token(), roleId, missing), 404, "GROUP_NOT_FOUND");
            assertCode(api.unassignUser(admin.token(), roleId, missing), 409, "ROLE_NO_CHANGE");
            assertCode(api.unassignGroup(admin.token(), roleId, missing), 409, "ROLE_NO_CHANGE");
            assertCode(api.groupRoles(admin.token(), missing), 404, "GROUP_NOT_FOUND");
            assertCode(api.userRoles(admin.token(), missing), 404, "USER_NOT_FOUND");
            assertCode(api.switchWorkRole(admin.token(), missing), 409, "ROLE_NOT_ASSIGNED");
        }
        assertThat(assignmentState()).isEqualTo(before);
    }

    @Test
    @DisplayName("a pair of another role cannot be removed through a different role id (B5)")
    void otherRolesPair() {
        long owner = fixtures.role(RoleFixtures.uniqueName("IDOR 持ち主"));
        long other = fixtures.role(RoleFixtures.uniqueName("IDOR ほか"));
        long userId = new GroupFixtures(users, jdbc).user("IDOR 割り当て済み");
        fixtures.assignUser(owner, userId);

        assertCode(api.unassignUser(admin.token(), other, userId), 409, "ROLE_NO_CHANGE");
        assertThat(fixtures.assignmentRows(owner)).isOne();
        assertCode(api.switchWorkRole(admin.token(), owner), 409, "ROLE_NOT_ASSIGNED");
        assertThat(fixtures.storedWorkRole(admin.userId())).isNull();
    }

    private Map<String, Object> assignmentState() {
        Map<String, Object> state = state();
        state.put("users", jdbc.queryForList("SELECT * FROM user_role_assignments ORDER BY role_id, user_id"));
        state.put("groups", jdbc.queryForList("SELECT * FROM group_role_assignments ORDER BY role_id, group_id"));
        state.put("selections", jdbc.queryForList("SELECT * FROM work_role_selections ORDER BY user_id"));
        return state;
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(HttpTestClient.json(response)).containsEntry("code", code);
    }
}
