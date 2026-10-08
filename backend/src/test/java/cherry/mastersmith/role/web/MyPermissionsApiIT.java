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
import cherry.mastersmith.role.domain.PermissionValues;
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
 * 自分の権限の木の API（契約 C8、FS の 2.11、BR4.12・BR5.3〜BR5.5、AC4.2.1〜AC4.2.6、計画の 8.3。Should）の結合テスト。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class MyPermissionsApiIT {

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
    private static List<Map<String, Object>> items(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return (List<Map<String, Object>>) HttpTestClient.json(response).get("items");
    }

    private Actor withWorkRole(long roleId) {
        Actor member = actors.member();
        fixtures.assignUser(roleId, member.userId());
        fixtures.selectWorkRole(member.userId(), roleId);
        return member;
    }

    @Test
    @DisplayName("the tree is built from the work role: effective values, inMenu on tables and the display names")
    void treeOfTheWorkRole() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("自分の木"));
        fixtures.setting(
                roleId, PermissionTarget.schema("SALES"), new PermissionValues(MainPermission.READ, true, null));
        fixtures.setting(roleId, PermissionTarget.table("SALES", "ORDER_HEAD"), MainPermission.NONE);
        fixtures.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), MainPermission.FULL);
        Actor member = withWorkRole(roleId);

        HttpResponse<String> schemas = api.mySchemas(member.token());
        assertThat(HttpTestClient.json(schemas).get("workRole"))
                .isEqualTo(Map.of(
                        "roleId", (int) roleId, "name", fixtures.roleRow(roleId).get("NAME")));
        assertThat(items(schemas)).extracting(item -> item.get("schemaName")).containsExactly("SALES", "HR");
        assertThat(items(schemas).get(0))
                .containsEntry("displayName", "スキーマ SALES")
                .containsEntry("effective", Map.of("main", "READ", "create", true, "delete", false))
                .containsEntry("hasChildren", true);
        List<Map<String, Object>> tables = items(api.myTables(member.token(), "SALES"));
        assertThat(tables).extracting(item -> item.get("tableName")).containsExactly("ORDER_LINE", "ORDER_HEAD");
        assertThat(tables.get(0)).containsEntry("inMenu", true);
        assertThat(tables.get(1)).containsEntry("inMenu", false);
        List<Map<String, Object>> columns = items(api.myColumns(member.token(), "SALES", "ORDER_LINE"));
        assertThat(columns.stream().map(item -> String.valueOf(((Map<?, ?>) item.get("effective")).get("main"))))
                .containsExactly("READ", "READ", "FULL");
        assertThat(HttpTestClient.json(api.mySchemas(member.token(), "Accept-Language", "en"))
                        .toString())
                .contains("Schema SALES");
    }

    @Test
    @DisplayName("without a work role everything is NONE, and without a DSL the items are empty")
    void noWorkRoleOrNoDsl() {
        Actor member = actors.member();

        HttpResponse<String> noRole = api.mySchemas(member.token());
        assertThat(HttpTestClient.json(noRole)).containsEntry("workRole", null);
        assertThat(items(noRole))
                .allSatisfy(item -> assertThat(item.get("effective"))
                        .isEqualTo(Map.of("main", "NONE", "create", false, "delete", false)));

        RoleDslFixture.remove(holder);
        try {
            assertThat(items(api.mySchemas(member.token()))).isEmpty();
            assertThat(items(api.myTables(member.token(), "SALES"))).isEmpty();
        } finally {
            RoleDslFixture.install(holder, RoleDslFixture.sample());
        }
    }

    @Test
    @DisplayName("names with symbols are read through the query parameters, and missing parameters are 400")
    void symbolsAndParameters() {
        RoleDslFixture.install(holder, RoleDslFixture.withSymbols());
        try {
            long roleId = fixtures.role(RoleFixtures.uniqueName("記号"));
            fixtures.setting(roleId, PermissionTarget.schema(RoleDslFixture.SYMBOL_SCHEMA), MainPermission.READ);
            Actor member = withWorkRole(roleId);

            assertThat(items(api.myTables(member.token(), RoleDslFixture.SYMBOL_SCHEMA)))
                    .singleElement()
                    .satisfies(item -> assertThat(item).containsEntry("tableName", RoleDslFixture.SYMBOL_TABLE));
            assertThat(items(api.myColumns(member.token(), RoleDslFixture.SYMBOL_SCHEMA, RoleDslFixture.SYMBOL_TABLE)))
                    .extracting(item -> item.get("columnName"))
                    .containsExactlyElementsOf(RoleDslFixture.SYMBOL_COLUMNS);
            assertThat(items(api.myTables(member.token(), "x".repeat(300)))).isEmpty();
            assertCode(api.myTables(member.token(), null), 400, "VALIDATION_FAILED");
            assertCode(api.myColumns(member.token(), "SALES", null), 400, "VALIDATION_FAILED");
            assertCode(api.myTables(member.token(), ""), 400, "VALIDATION_FAILED");
        } finally {
            RoleDslFixture.install(holder, RoleDslFixture.sample());
        }
    }

    @Test
    @DisplayName("only the work role counts: other roles of the user are not added up (BR5.5)")
    void onlyTheWorkRole() {
        long work = fixtures.role(RoleFixtures.uniqueName("作業ロール"));
        long other = fixtures.role(RoleFixtures.uniqueName("ほかのロール"));
        fixtures.setting(other, PermissionTarget.schema("HR"), MainPermission.FULL);
        Actor member = withWorkRole(work);
        fixtures.assignUser(other, member.userId());

        assertThat(items(api.myTables(member.token(), "HR")))
                .allSatisfy(item -> assertThat(((Map<?, ?>) item.get("effective")).get("main"))
                        .isEqualTo("NONE"));
    }
}
