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
 * 管理者の自分自身への操作（AC2.2.12・AC2.2.13、BR6.10、計画の 8.3）の結合テスト。管理者は自分の作業ロールに無い権限を持つロールも自分に
 * 割り当てられ、自分のロールをすべて外せる。管理の可否と最後の管理者の保護は管理者の印のままで、ロールに左右されない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleSelfOperationIT {

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
    @DisplayName("an admin assigns a full role to itself, uses it as the work role and keeps the admin APIs")
    void assignToSelf() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("自分に全権限"));
        assertThat(api.save(
                                admin.token(),
                                roleId,
                                RoleApi.saveBody(
                                        "SALES", null, List.of(RoleApi.entry("SALES", null, null, "FULL", true, true))))
                        .statusCode())
                .isEqualTo(204);

        assertThat(api.assignUser(admin.token(), roleId, admin.userId()).statusCode())
                .isEqualTo(204);
        assertThat(api.switchWorkRole(admin.token(), roleId).statusCode()).isEqualTo(204);

        assertThat(api.mySchemas(admin.token()).body()).contains("\"main\":\"FULL\"");
        assertThat(api.list(admin.token(), "").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("an admin removes all of its own roles, the admin APIs keep working and the admin flag is unchanged")
    void removeOwnRoles() {
        long first = fixtures.role(RoleFixtures.uniqueName("自分 一"));
        long second = fixtures.role(RoleFixtures.uniqueName("自分 二"));
        long groupId = groups.group(GroupFixtures.uniqueName("自分"));
        fixtures.assignUser(first, admin.userId());
        fixtures.assignUser(second, admin.userId());
        fixtures.assignGroup(second, groupId);

        assertThat(api.unassignUser(admin.token(), first, admin.userId()).statusCode())
                .isEqualTo(204);
        assertThat(api.unassignUser(admin.token(), second, admin.userId()).statusCode())
                .isEqualTo(204);

        assertThat(HttpTestClient.json(api.workRole(admin.token()))).containsEntry("current", null);
        assertThat(api.list(admin.token(), "").statusCode()).isEqualTo(200);
        assertThat(api.assignments(admin.token(), second).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT admin_flag FROM users WHERE user_id = ?", Boolean.class, admin.userId()))
                .isTrue();
    }
}
