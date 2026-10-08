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
 * 作業ロールの切り替えの監査の線引きの結合テスト（BR7.6・BR11.3・BR11.4、AC4.1.13・AC4.1.15・AC4.1.21、計画の 8.3）。保存がすでに選んだ
 * ロールを指すときは行が増えず、読み替え中に有効な作業ロールと同じロールを選ぶと1行増えて {@code storedBeforeRoleId} が書く前の保存になる。
 * 読み替えだけでは行が増えない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class WorkRoleSwitchAuditIT {

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

    private List<Map<String, Object>> switchRows(long userId) {
        return jdbc.queryForList(
                "SELECT result, target_role_id, detail FROM audit_events WHERE event_type = 'WORK_ROLE_SWITCHED'"
                        + " AND target_user_id = ? ORDER BY audit_event_id",
                userId);
    }

    @Test
    @DisplayName("switching to the stored role adds no row, and reading the work role adds no row")
    void sameStoredRole() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("同じ保存"));
        Actor member = actors.member();
        fixtures.assignUser(roleId, member.userId());
        fixtures.selectWorkRole(member.userId(), roleId);

        assertThat(api.switchWorkRole(member.token(), roleId).statusCode()).isEqualTo(204);
        assertThat(api.workRole(member.token()).statusCode()).isEqualTo(200);

        assertThat(switchRows(member.userId())).isEmpty();
    }

    @Test
    @DisplayName("choosing the effective role while reinterpreted adds one row with the stored role before writing")
    void reinterpretedChoice() {
        String firstName = RoleFixtures.uniqueName("読み替え 一");
        long first = fixtures.role(firstName);
        long unassigned = fixtures.role(RoleFixtures.uniqueName("読み替え 外"));
        Actor member = actors.member();
        fixtures.assignUser(first, member.userId());
        fixtures.selectWorkRole(member.userId(), unassigned);

        api.workRole(member.token());
        assertThat(switchRows(member.userId())).as("読み替えだけでは残さない").isEmpty();

        assertThat(api.switchWorkRole(member.token(), first).statusCode()).isEqualTo(204);

        assertThat(switchRows(member.userId())).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("RESULT", "SUCCESS").containsEntry("TARGET_ROLE_ID", first);
            assertThat(row.get("DETAIL"))
                    .isEqualTo("{\"fromRoleId\":" + first + ",\"fromRoleName\":\"" + firstName + "\",\"toRoleName\":\""
                            + firstName + "\",\"storedBeforeRoleId\":" + unassigned + "}");
        });
        assertThat(fixtures.storedWorkRole(member.userId())).isEqualTo(first);
    }
}
