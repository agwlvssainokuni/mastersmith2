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
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.role.testsupport.TestRoleBarrier;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Gate;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Point;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
 * 権限の YAML の受け渡しの3つの口（契約 C7 と FS の 8節の差、FS の 2.13〜2.15、BR9.2・BR9.6〜BR9.15、AC3.1.1〜AC3.1.6・AC3.1.10・
 * AC3.1.14〜AC3.1.17）の結合テスト。書き出しの形と添付、確かめの応答の形、適用の成功と業務の拒否、本文の型の 415、指紋のヘッダーの 400、
 * DSL が無いときの扱いを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class})
class RoleTransferApiIT {

    @Autowired
    TestRoleBarrier barrier;

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

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        fixtures = new RoleFixtures(jdbc);
        admin = new RoleActors(userAccountService, revocationService, transactionManager, port).admin();
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(json(response)).containsEntry("code", code);
    }

    @AfterEach
    void tearDown() {
        barrier.reset();
    }

    private static String yaml(String name, String schemaBody) {
        return "version: 1\nroles:\n  \"" + name + "\":\n    schemas:\n" + schemaBody;
    }

    private String fingerprint(String yaml) {
        HttpResponse<String> checked = api.check(admin.token(), yaml);
        assertThat(checked.statusCode()).as(checked.body()).isEqualTo(200);
        return RoleApi.fingerprintOf(checked);
    }

    private long roleIdOf(String name) {
        return jdbc.queryForObject("SELECT role_id FROM roles WHERE name = ?", Long.class, name);
    }

    @Test
    @DisplayName("the export is a YAML attachment with every role and is written even without an applied DSL")
    void export() {
        String name = RoleFixtures.uniqueName("書き出し");
        long roleId = fixtures.role(name);
        fixtures.setting(roleId, PermissionTarget.table("SALES", "ORDER_LINE"), MainPermission.FULL);
        RoleDslFixture.remove(holder);

        HttpResponse<String> response = api.export(admin.token());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/yaml"));
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=\"role-permissions.yaml\"");
        assertThat(response.headers().firstValue("X-Role-Transfer-Exceeds-Import-Limit"))
                .isEmpty();
        assertThat(response.body())
                .startsWith("version: 1\nroles:\n")
                .contains("  " + name + ":\n    schemas:\n      SALES:\n        tables:\n          ORDER_LINE:\n"
                        + "            main: FULL\n");
    }

    @Test
    @DisplayName(
            "a check returns the plan with role ids, changes and the DSL flag, and an apply creates and replaces roles")
    void checkAndApply() {
        String replaced = RoleFixtures.uniqueName("置き換え");
        long replacedId = fixtures.role(replaced);
        fixtures.setting(replacedId, PermissionTarget.schema("HR"), MainPermission.READ);
        String created = RoleFixtures.uniqueName("作る");
        String body = "version: 1\nroles:\n  \"" + replaced.toUpperCase(Locale.ROOT) + "\":\n    schemas:\n"
                + "      SALES: {main: FULL, create: true}\n      GONE: {main: READ}\n"
                + "  \"" + created + "\":\n    schemas:\n      HR: {main: READ}\n";

        HttpResponse<String> checked = api.check(admin.token(), body);

        assertThat(checked.statusCode()).as(checked.body()).isEqualTo(200);
        Map<String, Object> plan = json(checked);
        assertThat((String) plan.get("fingerprint")).matches("[0-9a-f]{64}");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) plan.get("roles");
        Map<String, Object> replace = roles.stream()
                .filter(role -> "REPLACE".equals(role.get("action")))
                .findFirst()
                .orElseThrow();
        assertThat(replace)
                .containsEntry("name", replaced)
                .containsEntry("roleId", (int) replacedId)
                .containsEntry("added", 2)
                .containsEntry("cleared", 1)
                .containsEntry("notInDsl", 1);
        assertThat(roles)
                .anySatisfy(role -> assertThat(role)
                        .containsEntry("action", "CREATE")
                        .containsEntry("roleId", null)
                        .containsEntry("name", created));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) replace.get("changes");
        assertThat(changes).anySatisfy(change -> {
            assertThat(change).containsEntry("reason", "ADDED").containsEntry("inCurrentDsl", false);
            Map<String, Object> target = new java.util.HashMap<>();
            target.put("schemaName", "GONE");
            target.put("tableName", null);
            target.put("columnName", null);
            assertThat(change.get("target")).isEqualTo(target);
            assertThat(((Map<?, ?>) change.get("before")).get("main")).isNull();
            assertThat(((Map<?, ?>) change.get("after")).get("main")).isEqualTo("READ");
        });
        assertThat(roleIdOf(replaced)).as("確かめは何も書かない").isEqualTo(replacedId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE name = ?", Integer.class, created))
                .isZero();

        HttpResponse<String> applied = api.apply(admin.token(), body, (String) plan.get("fingerprint"));

        assertThat(applied.statusCode()).as(applied.body()).isEqualTo(200);
        assertThat(json(applied)).isEqualTo(Map.of("created", 1, "replaced", 1));
        assertThat(jdbc.queryForList(
                        "SELECT schema_name FROM permission_settings WHERE role_id = ? ORDER BY schema_name",
                        String.class,
                        replacedId))
                .containsExactly("GONE", "SALES");
        assertThat(jdbc.queryForObject("SELECT name FROM roles WHERE role_id = ?", String.class, replacedId))
                .as("置き換えでは名前を変えない")
                .isEqualTo(replaced);
        assertThat(fixtures.settingRows(roleIdOf(created))).isEqualTo(1);
    }

    @Test
    @DisplayName("a body other than application/yaml is 415 for the check and the apply")
    void unsupportedMediaType() {
        byte[] body = "version: 1\nroles: {}\n".getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> check = api.sendYaml("/api/admin/role-transfer/check", admin.token(), body, "text/plain");
        HttpResponse<String> apply = api.sendYaml(
                "/api/admin/role-transfer/apply",
                admin.token(),
                body,
                "application/json",
                "X-Role-Transfer-Fingerprint",
                "a".repeat(64));

        assertCode(check, 415, "UNSUPPORTED_MEDIA_TYPE");
        assertCode(apply, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName(
            "a missing or malformed fingerprint header is 400 with the header in fieldErrors and nothing is audited")
    void fingerprintHeader() {
        String body = yaml(RoleFixtures.uniqueName("指紋"), "      SALES: {main: READ}\n");
        int before = transferAuditRows();

        HttpResponse<String> missing = api.apply(admin.token(), body, null);
        HttpResponse<String> upper = api.apply(admin.token(), body, "A".repeat(64));
        HttpResponse<String> shortOne = api.apply(admin.token(), body, "a".repeat(63));

        assertCode(missing, 400, "VALIDATION_FAILED");
        assertThat(json(missing).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "X-Role-Transfer-Fingerprint", "reason", "REQUIRED")));
        assertCode(upper, 400, "VALIDATION_FAILED");
        assertThat(json(upper).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "X-Role-Transfer-Fingerprint", "reason", "INVALID_VALUE")));
        assertCode(shortOne, 400, "VALIDATION_FAILED");
        assertThat(transferAuditRows()).isEqualTo(before);
    }

    @Test
    @DisplayName("an apply that changes nothing is ROLE_NO_CHANGE and a stale fingerprint is ROLE_TRANSFER_STALE")
    void noChangeAndStale() {
        String name = RoleFixtures.uniqueName("変わらない");
        long roleId = fixtures.role(name);
        fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        String same = yaml(name, "      SALES: {main: READ}\n");
        String changed = yaml(name, "      SALES: {main: FULL}\n");

        assertCode(api.apply(admin.token(), same, fingerprint(same)), 409, "ROLE_NO_CHANGE");
        String fingerprint = fingerprint(changed);
        fixtures.setting(roleId, PermissionTarget.schema("HR"), MainPermission.READ);
        assertCode(api.apply(admin.token(), changed, fingerprint), 409, "ROLE_TRANSFER_STALE");
    }

    @Test
    @DisplayName("without an applied DSL the check and the apply are DSL_NOT_APPLIED")
    void dslAbsent() {
        String body = yaml(RoleFixtures.uniqueName("DSL なし"), "      SALES: {main: READ}\n");
        String fingerprint = fingerprint(body);
        RoleDslFixture.remove(holder);

        assertCode(api.check(admin.token(), body), 409, "DSL_NOT_APPLIED");
        assertCode(api.apply(admin.token(), body, fingerprint), 409, "DSL_NOT_APPLIED");
    }

    @Test
    @DisplayName("a role of the same name held uncommitted by another create makes the apply wait and end as ROLE_BUSY")
    void sameNameRaceIsBusy() throws Exception {
        String name = RoleFixtures.uniqueName("重なり");
        String body = yaml(name, "      SALES: {main: READ}\n");
        String fingerprint = fingerprint(body);
        Gate gate = barrier.hold(
                Point.AFTER_WRITE, RoleOperation.CREATE, RoleFixtures.name(name).key());
        CompletableFuture<HttpResponse<String>> create =
                CompletableFuture.supplyAsync(() -> api.create(admin.token(), name));
        gate.awaitArrival();

        HttpResponse<String> applied;
        try {
            applied = api.apply(admin.token(), body, fingerprint);
        } finally {
            gate.release();
        }

        assertCode(applied, 409, "ROLE_BUSY");
        assertThat(create.get(TestRoleBarrier.WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS)
                        .statusCode())
                .isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE name = ?", Integer.class, name))
                .isEqualTo(1);
    }

    private int transferAuditRows() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ROLE_TRANSFER_APPLIED'", Integer.class);
    }
}
