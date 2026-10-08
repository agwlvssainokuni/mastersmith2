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
import cherry.mastersmith.group.testsupport.GroupApi;
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
 * ロールを割り当てたグループの削除の結合テスト（BR10.1〜BR10.3、group の BR4.1・BR6.1、AC2.1.3、group の読み直しの R-04 (2)、計画の
 * 8.3）。テスト用の問う口（{@code TestGroupDeletionGuard}）を使わず、本番の問う口と本物の割り当ての表で確かめる。B3 の仮の実装（常に削除して
 * よい・数 0）が残っていれば、このテストは落ちる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class GroupDeletionWithRoleIT {

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
    @DisplayName(
            "a group with an assigned role is GROUP_IN_USE with assignedRoles, and can be deleted after unassigning")
    void deleteBlockedByRole() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("グループの削除"));
        long groupId = groups.group(GroupFixtures.uniqueName("ロールあり"));
        assertThat(api.assignGroup(admin.token(), roleId, groupId).statusCode()).isEqualTo(204);
        GroupApi groupApi = new GroupApi(port);

        HttpResponse<String> refused = groupApi.delete(admin.token(), groupId);

        assertCode(refused, 409, "GROUP_IN_USE");
        assertThat(HttpTestClient.json(refused))
                .containsEntry("assignedRoles", 1)
                .containsEntry("members", 0);
        assertThat(groups.groupRows(groupId)).isOne();
        assertThat(api.unassignGroup(admin.token(), roleId, groupId).statusCode())
                .isEqualTo(204);
        assertThat(groupApi.delete(admin.token(), groupId).statusCode()).isEqualTo(204);
        assertThat(groups.groupRows(groupId)).isZero();
    }

    @Test
    @DisplayName("the group list shows the assigned role counts from the real table")
    void listCounts() {
        long first = fixtures.role(RoleFixtures.uniqueName("一覧 一"));
        long second = fixtures.role(RoleFixtures.uniqueName("一覧 二"));
        long groupId = groups.group(GroupFixtures.uniqueName("一覧の数"));
        fixtures.assignGroup(first, groupId);
        fixtures.assignGroup(second, groupId);

        HttpResponse<String> list = new GroupApi(port).list(admin.token(), "?page=1");

        assertThat(list.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) HttpTestClient.json(list).get("items");
        assertThat(items)
                .filteredOn(item -> ((Number) item.get("groupId")).longValue() == groupId)
                .singleElement()
                .satisfies(item -> assertThat(item).containsEntry("assignedRoleCount", 2));
    }
}
