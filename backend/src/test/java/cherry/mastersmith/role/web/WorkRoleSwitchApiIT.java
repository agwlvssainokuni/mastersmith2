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
 * 自分の作業ロールの読み取りと切り替えの API（契約 C8、FS の 2.10、BR7.1〜BR7.8、AC4.1.1〜AC4.1.6・AC4.1.11〜AC4.1.16・AC4.1.21、計画の
 * 8.3）の結合テスト。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class WorkRoleSwitchApiIT {

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> current(Actor actor) {
        HttpResponse<String> response = api.workRole(actor.token());
        assertThat(response.statusCode()).isEqualTo(200);
        return (Map<String, Object>) HttpTestClient.json(response).get("current");
    }

    private static long idOf(Map<String, Object> ref) {
        return ((Number) ref.get("roleId")).longValue();
    }

    @Test
    @DisplayName("the roles of direct and group assignments are listed in id order and the first one is the work role")
    void readsRolesAndFirstRole() {
        Actor member = actors.member();
        long first = fixtures.role(RoleFixtures.uniqueName("作業 一"));
        long second = fixtures.role(RoleFixtures.uniqueName("作業 二"));
        long groupId = groups.group(GroupFixtures.uniqueName("作業"));
        groups.member(groupId, member.userId(), Instant.parse("2026-10-02T00:00:00Z"));
        fixtures.assignGroup(first, groupId);
        fixtures.assignUser(second, member.userId());
        fixtures.assignUser(first, member.userId());

        HttpResponse<String> response = api.workRole(member.token());

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles =
                (List<Map<String, Object>>) HttpTestClient.json(response).get("roles");
        assertThat(roles).extracting(WorkRoleSwitchApiIT::idOf).containsExactly(first, second);
        assertThat(idOf(current(member))).isEqualTo(first);
        assertThat(fixtures.storedWorkRole(member.userId())).as("読み取りは保存を書かない").isNull();
    }

    @Test
    @DisplayName("a user without roles has no work role")
    void noRoles() {
        Actor member = actors.member();

        assertThat(HttpTestClient.json(api.workRole(member.token())))
                .containsEntry("roles", List.of())
                .containsEntry("current", null);
    }

    @Test
    @DisplayName("switching stores the role, survives a new login, and switching to the stored role writes nothing")
    void switches() {
        Actor member = actors.member();
        long first = fixtures.role(RoleFixtures.uniqueName("切り替え 一"));
        long second = fixtures.role(RoleFixtures.uniqueName("切り替え 二"));
        fixtures.assignUser(first, member.userId());
        fixtures.assignUser(second, member.userId());

        assertThat(api.switchWorkRole(member.token(), second).statusCode()).isEqualTo(204);
        assertThat(idOf(current(member))).isEqualTo(second);
        assertThat(fixtures.storedWorkRole(member.userId())).isEqualTo(second);
        int switched = auditRows("WORK_ROLE_SWITCHED", "SUCCESS");

        assertThat(api.switchWorkRole(member.token(), second).statusCode()).isEqualTo(204);
        assertThat(auditRows("WORK_ROLE_SWITCHED", "SUCCESS")).as("保存が同じなら残さない").isEqualTo(switched);
    }

    @Test
    @DisplayName("a role outside the assignments and a missing role are refused alike with ROLE_NOT_ASSIGNED")
    void notAssigned() {
        Actor member = actors.member();
        long assigned = fixtures.role(RoleFixtures.uniqueName("割り当て内"));
        long other = fixtures.role(RoleFixtures.uniqueName("割り当て外"));
        fixtures.assignUser(assigned, member.userId());

        HttpResponse<String> outside = api.switchWorkRole(member.token(), other);
        HttpResponse<String> missing = api.switchWorkRole(member.token(), Long.MAX_VALUE);

        assertCode(outside, 409, "ROLE_NOT_ASSIGNED");
        assertCode(missing, 409, "ROLE_NOT_ASSIGNED");
        assertThat(HttpTestClient.json(outside).get("title"))
                .isEqualTo(HttpTestClient.json(missing).get("title"));
        assertThat(fixtures.storedWorkRole(member.userId())).isNull();
    }

    @Test
    @DisplayName("an unassigned stored role is read as the first role and comes back when assigned again (BR7.4)")
    void readsAgainAfterReassign() {
        Actor member = actors.member();
        long first = fixtures.role(RoleFixtures.uniqueName("戻る 一"));
        long second = fixtures.role(RoleFixtures.uniqueName("戻る 二"));
        fixtures.assignUser(first, member.userId());
        fixtures.assignUser(second, member.userId());
        assertThat(api.switchWorkRole(member.token(), second).statusCode()).isEqualTo(204);

        assertThat(api.unassignUser(admin.token(), second, member.userId()).statusCode())
                .isEqualTo(204);
        assertThat(idOf(current(member))).isEqualTo(first);
        assertThat(fixtures.storedWorkRole(member.userId())).isEqualTo(second);

        assertThat(api.assignUser(admin.token(), second, member.userId()).statusCode())
                .isEqualTo(204);
        assertThat(idOf(current(member))).isEqualTo(second);
    }

    @Test
    @DisplayName(
            "choosing the effective role while the stored one is read differently stores it and keeps it after changes")
    void explicitChoiceIsKept() {
        Actor member = actors.member();
        long first = fixtures.role(RoleFixtures.uniqueName("明示 一"));
        long second = fixtures.role(RoleFixtures.uniqueName("明示 二"));
        long third = fixtures.role(RoleFixtures.uniqueName("明示 三"));
        fixtures.assignUser(second, member.userId());
        fixtures.assignUser(third, member.userId());
        fixtures.selectWorkRole(member.userId(), first);
        assertThat(idOf(current(member))).isEqualTo(second);

        assertThat(api.switchWorkRole(member.token(), second).statusCode()).isEqualTo(204);
        assertThat(fixtures.storedWorkRole(member.userId())).isEqualTo(second);
        assertThat(api.assignUser(admin.token(), first, member.userId()).statusCode())
                .isEqualTo(204);

        assertThat(idOf(current(member))).as("作業ロール以外の足し外しで変わらない（AC4.1.21）").isEqualTo(second);
    }

    @Test
    @DisplayName("the body needs an integer roleId")
    void bodyValidation() {
        Actor member = actors.member();

        HttpResponse<String> missing = api.switchWorkRoleJson(member.token(), "{}");
        assertCode(missing, 400, "VALIDATION_FAILED");
        assertThat(missing.body()).contains("\"field\":\"roleId\"");
        assertThat(api.switchWorkRoleJson(member.token(), "{\"roleId\":\"abc\"}")
                        .statusCode())
                .isEqualTo(400);
    }
}
