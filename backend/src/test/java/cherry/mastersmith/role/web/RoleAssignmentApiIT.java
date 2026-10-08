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
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
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
 * 割り当ての API とグループ・利用者の側の読み取り（契約 C7、FS の 2.8、BR6.1〜BR6.11・BR3.2・BR12.2、AC2.2.1〜AC2.2.8・AC2.2.15・
 * AC2.2.16・AC1.1.3、計画の 8.3）の結合テスト。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleAssignmentApiIT {

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
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private RoleApi api;

    private RoleActors actors;

    private RoleFixtures fixtures;

    private GroupFixtures groups;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        actors = new RoleActors(userAccountService, revocationService, transactionManager, port);
        fixtures = new RoleFixtures(jdbc);
        groups = new GroupFixtures(users, jdbc);
        admin = actors.admin();
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(HttpTestClient.json(response)).containsEntry("code", code);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> jsonList(HttpResponse<String> response) {
        return tools.jackson.databind.json.JsonMapper.shared().readValue(response.body(), List.class);
    }

    private int auditRows(String eventType, String result) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = ? AND result = ?",
                Integer.class,
                eventType,
                result);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("users and groups are assigned, listed with their sources and read from the group and the user side")
    void assignAndRead() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("割り当て"));
        long direct = groups.user("割り当て 直接");
        long both = groups.user("割り当て 両方");
        long viaGroup = groups.user("割り当て グループ");
        long groupId = groups.group(GroupFixtures.uniqueName("割り当て"));
        groups.member(groupId, both, Instant.parse("2026-10-02T00:00:00Z"));
        groups.member(groupId, viaGroup, Instant.parse("2026-10-02T00:00:00Z"));

        assertThat(api.assignUser(admin.token(), roleId, direct).statusCode()).isEqualTo(204);
        assertThat(api.assignUser(admin.token(), roleId, both).statusCode()).isEqualTo(204);
        assertThat(api.assignGroup(admin.token(), roleId, groupId).statusCode()).isEqualTo(204);

        HttpResponse<String> list = api.assignments(admin.token(), roleId);
        assertThat(list.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users =
                (List<Map<String, Object>>) HttpTestClient.json(list).get("users");
        assertThat(users)
                .extracting(user -> ((Number) user.get("userId")).longValue())
                .containsExactly(direct, both, viaGroup);
        assertThat(users.get(0)).containsEntry("displayName", "割り当て 直接").containsEntry("suspended", false);
        assertThat(users.get(0).get("sources")).isEqualTo(List.of(source("DIRECT", null, null)));
        assertThat(users.get(1).get("sources"))
                .isEqualTo(List.of(source("DIRECT", null, null), source("GROUP", groupId, groupName(groupId))));
        assertThat(users.get(2).get("sources")).isEqualTo(List.of(source("GROUP", groupId, groupName(groupId))));
        assertThat(HttpTestClient.json(list).get("groups"))
                .isEqualTo(List.of(Map.of("groupId", (int) groupId, "name", groupName(groupId))));
        assertThat(list.body())
                .doesNotContain("password")
                .doesNotContain("$2a$")
                .doesNotContain("failedCount");

        HttpResponse<String> groupRoles = api.groupRoles(admin.token(), groupId);
        assertThat(groupRoles.statusCode()).isEqualTo(200);
        assertThat(jsonList(groupRoles))
                .containsExactly(Map.of(
                        "roleId", (int) roleId, "name", fixtures.roleRow(roleId).get("NAME")));
        HttpResponse<String> userRoles = api.userRoles(admin.token(), both);
        assertThat(userRoles.statusCode()).isEqualTo(200);
        assertThat(jsonList(userRoles))
                .singleElement()
                .satisfies(role -> assertThat(role.get("sources"))
                        .isEqualTo(
                                List.of(source("DIRECT", null, null), source("GROUP", groupId, groupName(groupId)))));
    }

    @Test
    @DisplayName("unassigning removes the pair, and a second unassignment or a missing group is ROLE_NO_CHANGE")
    void unassign() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("外し"));
        long userId = groups.user("外し 一郎");
        long groupId = groups.group(GroupFixtures.uniqueName("外し"));
        fixtures.assignUser(roleId, userId);
        fixtures.assignGroup(roleId, groupId);

        assertThat(api.unassignUser(admin.token(), roleId, userId).statusCode()).isEqualTo(204);
        assertThat(api.unassignGroup(admin.token(), roleId, groupId).statusCode())
                .isEqualTo(204);
        assertThat(fixtures.assignmentRows(roleId)).isZero();
        assertCode(api.unassignUser(admin.token(), roleId, userId), 409, "ROLE_NO_CHANGE");
        assertCode(api.unassignGroup(admin.token(), roleId, Long.MAX_VALUE), 409, "ROLE_NO_CHANGE");
    }

    @Test
    @DisplayName("an existing pair is ROLE_NO_CHANGE, a missing user or group is 404, a suspended user can be assigned")
    void refusalsAndSuspendedUser() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("拒否"));
        long userId = groups.user("拒否 一郎");
        fixtures.assignUser(roleId, userId);
        Actor suspended = actors.user("停止中 一郎");
        actors.suspend(suspended.userId());

        assertCode(api.assignUser(admin.token(), roleId, userId), 409, "ROLE_NO_CHANGE");
        assertCode(api.assignUser(admin.token(), roleId, Long.MAX_VALUE), 404, "USER_NOT_FOUND");
        assertCode(api.assignGroup(admin.token(), roleId, Long.MAX_VALUE), 404, "GROUP_NOT_FOUND");
        assertThat(api.assignUser(admin.token(), roleId, suspended.userId()).statusCode())
                .isEqualTo(204);
        assertThat(fixtures.assignmentRows(roleId)).isEqualTo(2);
    }

    @Test
    @DisplayName("the body needs exactly one of userId and groupId, and the ids must be integers")
    void bodyValidation() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("本文"));
        long userId = groups.user("本文 一郎");
        long groupId = groups.group(GroupFixtures.uniqueName("本文"));

        HttpResponse<String> neither = api.assignJson(admin.token(), roleId, "{}");
        HttpResponse<String> bothIds =
                api.assignJson(admin.token(), roleId, RoleApi.json(Map.of("userId", userId, "groupId", groupId)));
        HttpResponse<String> notInteger = api.assignJson(admin.token(), roleId, "{\"userId\":\"abc\"}");

        assertCode(neither, 400, "VALIDATION_FAILED");
        assertThat(neither.body()).contains("\"field\":\"userId\"").contains("REQUIRED");
        assertCode(bothIds, 400, "VALIDATION_FAILED");
        assertThat(bothIds.body()).contains("\"field\":\"groupId\"");
        assertThat(notInteger.statusCode()).isEqualTo(400);
        assertThat(fixtures.assignmentRows(roleId)).isZero();
    }

    @Test
    @DisplayName("a role with remaining assignments cannot be deleted and the response carries the counts (AC1.1.3)")
    void deleteInUse() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("使用中"));
        fixtures.assignUser(roleId, groups.user("使用中 一郎"));
        fixtures.assignGroup(roleId, groups.group(GroupFixtures.uniqueName("使用中")));

        HttpResponse<String> response = api.delete(admin.token(), roleId);

        assertCode(response, 409, "ROLE_IN_USE");
        assertThat(HttpTestClient.json(response))
                .containsEntry("assignedUsers", 1)
                .containsEntry("assignedGroups", 1);
        assertThat(response.body()).doesNotContain("使用中 一郎");
        assertThat(fixtures.roleRows(roleId)).isOne();
    }

    @Test
    @DisplayName("the readings of a missing role, group or user are 404 without changing anything")
    void missingReadings() {
        assertCode(api.assignments(admin.token(), Long.MAX_VALUE), 404, "ROLE_NOT_FOUND");
        assertCode(api.groupRoles(admin.token(), Long.MAX_VALUE), 404, "GROUP_NOT_FOUND");
        assertCode(api.userRoles(admin.token(), Long.MAX_VALUE), 404, "USER_NOT_FOUND");
        long noRoles = groups.user("ロールなし 一郎");
        assertThat(jsonList(api.userRoles(admin.token(), noRoles))).isEmpty();
    }

    private String groupName(long groupId) {
        return jdbc.queryForObject("SELECT name FROM groups WHERE group_id = ?", String.class, groupId);
    }

    private static Map<String, Object> source(String type, Long groupId, String name) {
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("type", type);
        source.put("groupId", groupId == null ? null : (int) (long) groupId);
        source.put("name", name);
        return source;
    }
}
