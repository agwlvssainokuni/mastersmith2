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

import static cherry.mastersmith.role.testsupport.RoleApi.entry;
import static cherry.mastersmith.role.testsupport.RoleApi.saveBody;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
 * 権限の設定の5つの口（契約 C7、BR4・BR5.1〜BR5.3・BR13、AC1.2.1〜AC1.2.4・AC1.2.6・AC1.2.7・AC1.2.10・AC1.2.13・AC1.2.16・AC1.2.17）の
 * 結合テスト。木の3つの階層の形と値、名前に記号を含む引数、DSL が無いとき、差分の保存、今の DSL に無い対象を確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RolePermissionApiIT {

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

    private long roleId;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        fixtures = new RoleFixtures(jdbc);
        admin = new RoleActors(userAccountService, revocationService, transactionManager, port).admin();
        roleId = fixtures.role(RoleFixtures.uniqueName("権限"));
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    @AfterEach
    void tearDown() {
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return (List<Map<String, Object>>) json(response).get("items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> explicit(Map<String, Object> node) {
        return (Map<String, Object>) node.get("explicit");
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(json(response)).containsEntry("code", code);
    }

    private int auditRows(String eventType, String result) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = ? AND result = ? AND target_role_id = ?",
                Integer.class,
                eventType,
                result,
                roleId);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName(
            "the schema level lists the DSL schemas in order with the decided keys and the display name by language")
    void schemaLevel() {
        fixtures.setting(roleId, PermissionTarget.schema("HR"), new PermissionValues(MainPermission.FULL, true, null));
        fixtures.setting(roleId, PermissionTarget.column("OLD", "T", "C"), MainPermission.READ);

        List<Map<String, Object>> ja = items(api.schemas(admin.token(), roleId));
        List<Map<String, Object>> en = items(api.schemas(admin.token(), roleId, "Accept-Language", "en"));

        assertThat(ja).extracting(node -> node.get("schemaName")).containsExactly("SALES", "HR", "OLD");
        assertThat(ja.getFirst())
                .containsOnlyKeys(
                        "schemaName",
                        "tableName",
                        "columnName",
                        "displayName",
                        "explicit",
                        "effective",
                        "inheritedFrom",
                        "inMenu",
                        "inCurrentDsl",
                        "hasChildren")
                .containsEntry("displayName", "スキーマ SALES")
                .containsEntry("inheritedFrom", "DEFAULT")
                .containsEntry("effective", Map.of("main", "NONE", "create", false, "delete", false));
        assertThat(en.getFirst()).containsEntry("displayName", "Schema SALES");
        assertThat(ja.get(1))
                .containsEntry("inheritedFrom", "EXPLICIT")
                .containsEntry("effective", Map.of("main", "FULL", "create", true, "delete", false));
        assertThat(explicit(ja.get(1))).containsEntry("main", "FULL").containsEntry("delete", null);
        assertThat(ja.get(2))
                .containsEntry("displayName", "OLD")
                .containsEntry("inCurrentDsl", false)
                .containsEntry("hasChildren", true);
    }

    @Test
    @DisplayName(
            "tables and columns show the inherited values, the source and the menu flag (AC1.2.2 to AC1.2.4, AC1.2.16)")
    void inheritanceInTheTree() {
        fixtures.setting(
                roleId, PermissionTarget.schema("SALES"), new PermissionValues(MainPermission.FULL, null, true));
        fixtures.setting(roleId, PermissionTarget.table("SALES", "ORDER_LINE"), MainPermission.READ);
        fixtures.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "UNIT_PRICE"), MainPermission.NONE);
        fixtures.setting(roleId, PermissionTarget.table("SALES", "ORDER_HEAD"), MainPermission.NONE);

        List<Map<String, Object>> tables = items(api.tables(admin.token(), roleId, "SALES"));
        List<Map<String, Object>> columns = items(api.columns(admin.token(), roleId, "SALES", "ORDER_LINE"));

        assertThat(tables.get(0))
                .containsEntry("tableName", "ORDER_LINE")
                .containsEntry("inheritedFrom", "EXPLICIT")
                .containsEntry("inMenu", true)
                .containsEntry("effective", Map.of("main", "READ", "create", false, "delete", true));
        assertThat(tables.get(1)).containsEntry("inMenu", false);
        assertThat(columns).extracting(node -> node.get("columnName")).containsExactly("ORDER_NO", "UNIT_PRICE", "QTY");
        assertThat(columns.get(0))
                .containsEntry("inheritedFrom", "TABLE")
                .containsEntry("inMenu", false)
                .containsEntry("hasChildren", false)
                .containsEntry("effective", Map.of("main", "READ", "create", false, "delete", true));
        assertThat(columns.get(1)).containsEntry("inheritedFrom", "EXPLICIT");
    }

    @Test
    @DisplayName("names with slashes, dots, semicolons, percent signs and spaces are read through query parameters")
    void namesWithSymbols() {
        RoleDslFixture.install(holder, RoleDslFixture.withSymbols());

        List<Map<String, Object>> tables = items(api.tables(admin.token(), roleId, RoleDslFixture.SYMBOL_SCHEMA));
        List<Map<String, Object>> columns =
                items(api.columns(admin.token(), roleId, RoleDslFixture.SYMBOL_SCHEMA, RoleDslFixture.SYMBOL_TABLE));

        assertThat(tables).extracting(node -> node.get("tableName")).containsExactly(RoleDslFixture.SYMBOL_TABLE);
        assertThat(columns).extracting(node -> node.get("columnName")).containsExactly("true", "null", "123");
        assertThat(items(api.tables(admin.token(), roleId, "x".repeat(300))))
                .as("長さで拒否しない")
                .isEmpty();
        HttpResponse<String> missing = api.tables(admin.token(), roleId, null);
        assertCode(missing, 400, "VALIDATION_FAILED");
        assertThat(json(missing))
                .containsEntry("fieldErrors", List.of(Map.of("field", "schema", "reason", "REQUIRED")));
        assertCode(api.columns(admin.token(), roleId, "SALES", ""), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("without an applied DSL the tree and the save are DSL_NOT_APPLIED, only the save is audited (AC1.2.6)")
    void noDsl() {
        RoleDslFixture.remove(holder);
        int before = auditRows("ROLE_PERMISSION_CHANGED", "FAILURE");

        assertCode(api.schemas(admin.token(), roleId), 409, "DSL_NOT_APPLIED");
        assertCode(api.columns(admin.token(), roleId, "SALES", "ORDER_LINE"), 409, "DSL_NOT_APPLIED");
        assertCode(
                api.save(
                        admin.token(),
                        roleId,
                        saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", null, null)))),
                409,
                "DSL_NOT_APPLIED");
        assertThat(auditRows("ROLE_PERMISSION_CHANGED", "FAILURE")).isEqualTo(before + 1);
        assertCode(api.schemas(admin.token(), Long.MAX_VALUE), 404, "ROLE_NOT_FOUND");
    }

    @Test
    @DisplayName("a save replaces only the listed targets, is read back, and the same values again are no change")
    void saveAndReadBack() {
        fixtures.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), MainPermission.NONE);
        String body = saveBody(
                "SALES",
                "ORDER_LINE",
                List.of(
                        entry("SALES", "ORDER_LINE", null, "READ", true, null),
                        entry("SALES", "ORDER_LINE", "UNIT_PRICE", "FULL", null, null)));

        assertThat(api.save(admin.token(), roleId, body).statusCode()).isEqualTo(204);

        List<Map<String, Object>> columns = items(api.columns(admin.token(), roleId, "SALES", "ORDER_LINE"));
        assertThat(columns.stream().map(node -> explicit(node).get("main")).toList())
                .containsExactly(null, "FULL", "NONE");
        assertThat(fixtures.settingRows(roleId)).isEqualTo(3);
        assertCode(api.save(admin.token(), roleId, body), 409, "ROLE_NO_CHANGE");
        String clearing =
                saveBody("SALES", "ORDER_LINE", List.of(entry("SALES", "ORDER_LINE", "UNIT_PRICE", null, null, null)));
        assertThat(api.save(admin.token(), roleId, clearing).statusCode()).isEqualTo(204);
        assertThat(fixtures.settingRows(roleId)).as("すべて設定なしの行は消える").isEqualTo(2);
        assertThat(auditRows("ROLE_PERMISSION_CHANGED", "SUCCESS")).isEqualTo(2);
    }

    @Test
    @DisplayName("value errors, column auxiliaries, targets outside the scope and duplicates are 400 without audit")
    void inputErrors() {
        Map<String, String> cases = Map.of(
                saveBody("SALES", null, List.of(entry("SALES", null, null, "ADMIN", null, null))),
                "entries[0].main",
                saveBody("SALES", "ORDER_LINE", List.of(entry("SALES", "ORDER_LINE", "QTY", null, true, null))),
                "entries[0].create",
                saveBody("SALES", null, List.of(entry("SALES", "ORDER_LINE", null, "READ", null, null))),
                "entries[0]",
                saveBody("SALES", null, List.of()),
                "entries");
        cases.forEach((body, field) -> {
            HttpResponse<String> response = api.save(admin.token(), roleId, body);
            assertCode(response, 400, "VALIDATION_FAILED");
            assertThat(response.body()).contains("\"field\":\"" + field + "\"");
        });
        assertThat(api.save(
                                admin.token(),
                                roleId,
                                "{\"scope\":{\"schemaName\":\"SALES\"},\"entries\":[{\"schemaName\":"
                                        + "\"SALES\",\"create\":\"yes\"}]}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(auditRows("ROLE_PERMISSION_CHANGED", "FAILURE")).isZero();
    }

    @Test
    @DisplayName("a value for a target not in the DSL is refused, and clearing removes only settings outside the DSL")
    void targetsOutsideTheDsl() {
        fixtures.setting(roleId, PermissionTarget.table("SALES", "GONE"), MainPermission.READ);

        assertCode(
                api.save(
                        admin.token(),
                        roleId,
                        saveBody("SALES", "GONE", List.of(entry("SALES", "GONE", null, "FULL", null, null)))),
                409,
                "PERMISSION_TARGET_NOT_IN_DSL");
        String target =
                RoleApi.json(Map.of("targets", List.of(Map.of("schemaName", "SALES", "tableName", "ORDER_LINE"))));
        HttpResponse<String> inDsl = api.clear(admin.token(), roleId, target);
        assertCode(inDsl, 400, "VALIDATION_FAILED");
        String gone = RoleApi.json(Map.of("targets", List.of(Map.of("schemaName", "SALES", "tableName", "GONE"))));
        assertThat(api.clear(admin.token(), roleId, gone).statusCode()).isEqualTo(204);
        assertThat(fixtures.settingRows(roleId)).isZero();
        assertCode(api.clear(admin.token(), roleId, gone), 409, "ROLE_NO_CHANGE");
        assertThat(auditRows("ROLE_PERMISSION_CHANGED", "SUCCESS")).isEqualTo(1);
        assertCode(api.clear(admin.token(), Long.MAX_VALUE, gone), 404, "ROLE_NOT_FOUND");
    }
}
