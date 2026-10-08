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
import cherry.mastersmith.role.domain.EffectivePermission;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.service.EffectivePermissionResolver;
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
 * 割り当ての変更の反映の結合テスト（BR5.4・BR6.8、AC1.2.5・AC2.1.9・AC2.1.10・AC2.2.3・AC2.2.4・AC4.1.1、{@code team.md} の必須のテスト
 * 「割り当ての変更の反映」、計画の 8.3）。
 *
 * <p>変更の前にログインして出したアクセストークンのまま、割り当て・外し・切り替え・権限の保存・グループのメンバーの外しの直後の次の要求で、
 * 解決の口・自分の権限の API・作業ロールの API の結果が変わることを確かめる（トークンや画面の値を使わず、要求ごとに内部DB から求める）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleChangeVisibilityIT {

    private static final PermissionTarget HR_EMPLOYEE = PermissionTarget.table("HR", "EMPLOYEE");

    @Autowired
    EffectivePermissionResolver resolver;

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

    private String hrMain(Actor actor) {
        String body = api.myTables(actor.token(), "HR").body();
        return body.contains("\"main\":\"FULL\"") ? "FULL" : body.contains("\"main\":\"READ\"") ? "READ" : "NONE";
    }

    private Object currentRole(Actor actor) {
        Object current = HttpTestClient.json(api.workRole(actor.token())).get("current");
        return current == null ? null : ((Map<?, ?>) current).get("roleId");
    }

    @Test
    @DisplayName("assigning and unassigning a role take effect on the next request with the same access token")
    void assignAndUnassign() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("反映"));
        fixtures.setting(roleId, PermissionTarget.schema("HR"), MainPermission.READ);
        Actor member = actors.member();
        assertThat(hrMain(member)).isEqualTo("NONE");

        assertThat(api.assignUser(admin.token(), roleId, member.userId()).statusCode())
                .isEqualTo(204);
        assertThat(hrMain(member)).isEqualTo("READ");
        assertThat(currentRole(member)).isEqualTo((int) roleId);
        assertThat(resolver.resolve(member.userId(), HR_EMPLOYEE).main()).isEqualTo(MainPermission.READ);

        assertThat(api.unassignUser(admin.token(), roleId, member.userId()).statusCode())
                .isEqualTo(204);
        assertThat(hrMain(member)).isEqualTo("NONE");
        assertThat(currentRole(member)).isNull();
        assertThat(resolver.resolve(member.userId(), HR_EMPLOYEE)).isEqualTo(EffectivePermission.NONE);
    }

    @Test
    @DisplayName("switching the work role and saving permissions take effect on the next request")
    void switchAndSave() {
        long readRole = fixtures.role(RoleFixtures.uniqueName("反映 読む"));
        long fullRole = fixtures.role(RoleFixtures.uniqueName("反映 全部"));
        fixtures.setting(readRole, PermissionTarget.schema("HR"), MainPermission.READ);
        fixtures.setting(fullRole, PermissionTarget.schema("HR"), MainPermission.FULL);
        Actor member = actors.member();
        fixtures.assignUser(readRole, member.userId());
        fixtures.assignUser(fullRole, member.userId());
        assertThat(hrMain(member)).isEqualTo("READ");

        assertThat(api.switchWorkRole(member.token(), fullRole).statusCode()).isEqualTo(204);
        assertThat(hrMain(member)).isEqualTo("FULL");

        assertThat(api.save(
                                admin.token(),
                                fullRole,
                                RoleApi.saveBody(
                                        "HR", null, List.of(RoleApi.entry("HR", null, null, "NONE", null, null))))
                        .statusCode())
                .isEqualTo(204);
        assertThat(hrMain(member)).isEqualTo("NONE");
    }

    @Test
    @DisplayName("removing the user from a group takes the group's role away on the next request")
    void groupMemberRemoved() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("反映 グループ"));
        fixtures.setting(roleId, PermissionTarget.schema("HR"), MainPermission.READ);
        Actor member = actors.member();
        long groupId = groups.group(GroupFixtures.uniqueName("反映"));
        groups.member(groupId, member.userId(), Instant.parse("2026-10-02T00:00:00Z"));
        fixtures.assignGroup(roleId, groupId);
        assertThat(hrMain(member)).isEqualTo("READ");

        assertThat(new GroupApi(port)
                        .removeMember(admin.token(), groupId, member.userId())
                        .statusCode())
                .isEqualTo(204);

        assertThat(hrMain(member)).isEqualTo("NONE");
        assertThat(currentRole(member)).isNull();
    }
}
