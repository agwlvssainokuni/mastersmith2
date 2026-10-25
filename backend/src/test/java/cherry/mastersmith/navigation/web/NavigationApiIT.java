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
package cherry.mastersmith.navigation.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.navigation.testsupport.NavigationActors;
import cherry.mastersmith.navigation.testsupport.NavigationDslFixture;
import cherry.mastersmith.navigation.testsupport.NavigationModels;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleFixtures;
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
 * 2本の API の結合テスト（BR1.1・BR1.6・BR2.1・BR3.1〜BR3.5・BR5.2〜BR5.4・BR7.3、NFR1.1・NFR1.4・NFR1.9・NFR6.1、AC5.1.1〜AC5.1.6・
 * AC5.1.11・AC5.1.17）。
 *
 * <p>DSL: スキーマ {@code SALES} のテーブル {@code T0}〜{@code T5}。メニューは「まとまり 販売（T0・T1）」「T2（子 T3）」「T4」の順で、
 * {@code T5} はどの項目も指さない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class NavigationApiIT {

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

    private RoleActors roleActors;

    private RoleFixtures fixtures;

    private NavigationActors actors;

    @BeforeEach
    void setUp() {
        roleActors = new RoleActors(userAccountService, revocationService, transactionManager, port);
        fixtures = new RoleFixtures(jdbc);
        actors = new NavigationActors(roleActors, fixtures, port);
        installSample();
    }

    private void installSample() {
        NavigationDslFixture.install(
                holder,
                List.of(NavigationModels.schema("SALES", "T0", "T1", "T2", "T3", "T4", "T5")),
                List.of(
                        NavigationModels.group(
                                "販売",
                                NavigationModels.tableItem("T0", "SALES", "T0"),
                                NavigationModels.tableItem("T1", "SALES", "T1")),
                        NavigationModels.tableItem(
                                "T2", "SALES", "T2", NavigationModels.tableItem("T3", "SALES", "T3")),
                        NavigationModels.tableItem("T4", "SALES", "T4")));
    }

    /** 半分を NONE にしたロール（T0 READ・T1 NONE・T2 NONE・T3 READ・T4 FULL・T5 READ）。 */
    private long halfNoneRole() {
        return actors.role(
                "SALES",
                Map.of(
                        "T0", MainPermission.READ,
                        "T1", MainPermission.NONE,
                        "T2", MainPermission.NONE,
                        "T3", MainPermission.READ,
                        "T4", MainPermission.FULL,
                        "T5", MainPermission.READ));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("items");
    }

    private static Map<String, Object> ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return HttpTestClient.json(response);
    }

    /** 応答の木の移れる項目のテーブルの名前（深さ優先の順）。 */
    private static List<String> navigableTables(Map<String, Object> body) {
        List<String> found = new java.util.ArrayList<>();
        collect(items(body), found);
        return found;
    }

    @SuppressWarnings("unchecked")
    private static void collect(List<Map<String, Object>> nodes, List<String> found) {
        for (Map<String, Object> node : nodes) {
            if (Boolean.TRUE.equals(node.get("navigable"))) {
                found.add((String) ((Map<String, Object>) node.get("table")).get("tableName"));
            }
            collect((List<Map<String, Object>>) node.get("items"), found);
        }
    }

    private int auditRows() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("the menu is filtered by the main permission of the work role and keeps ids, labels and icons")
    void filteredTree() {
        Actor member = actors.memberWithWorkRole(halfNoneRole());

        Map<String, Object> body = ok(actors.navigation(member.token()));

        assertThat(body).containsEntry("emptyReason", null);
        List<Map<String, Object>> roots = items(body);
        assertThat(roots).extracting(node -> node.get("id")).containsExactly("0", "1", "2");
        assertThat(roots.getFirst())
                .containsEntry("navigable", false)
                .containsEntry("table", null)
                .containsEntry("icon", "home")
                .containsEntry("label", Map.of("ja", "販売", "en", "販売-en"));
        assertThat(items(roots.getFirst()))
                .singleElement()
                .satisfies(node -> assertThat(node)
                        .containsEntry("id", "0.0")
                        .containsEntry("navigable", true)
                        .containsEntry("icon", "list")
                        .containsEntry("table", Map.of("schemaName", "SALES", "tableName", "T0")));
        assertThat(roots.get(1)).containsEntry("navigable", false).containsEntry("table", null);
        assertThat(items(roots.get(1))).extracting(node -> node.get("id")).containsExactly("1.0");
        assertThat(roots.get(2)).containsEntry("navigable", true);
        assertThat(navigableTables(body)).containsExactly("T0", "T3", "T4");
        assertThat(roots.getFirst().keySet())
                .containsExactlyInAnyOrder("id", "label", "icon", "table", "navigable", "items");
    }

    @Test
    @DisplayName("a userId parameter is ignored and the subject's own tree is returned")
    void idorIsPrevented() {
        Actor member = actors.memberWithWorkRole(halfNoneRole());
        Actor other = actors.memberWithWorkRole(actors.role(
                "SALES", Map.of("T0", MainPermission.FULL, "T1", MainPermission.FULL, "T2", MainPermission.FULL)));

        Map<String, Object> body = ok(actors.get("/api/me/navigation?userId=" + other.userId(), member.token()));

        assertThat(navigableTables(body)).containsExactly("T0", "T3", "T4");
    }

    @Test
    @DisplayName("the removed table access path is not served: 404 for a member and 401 without a token (FR3.3)")
    void tableAccessPathIsGone() {
        Actor member = actors.memberWithWorkRole(halfNoneRole());

        HttpResponse<String> asMember = actors.get("/api/me/table-access?schema=SALES&table=T0", member.token());
        HttpResponse<String> anonymous = actors.get("/api/me/table-access?schema=SALES&table=T0", null);

        assertThat(asMember.statusCode()).as(asMember.body()).isEqualTo(404);
        assertThat(asMember.body()).doesNotContain("T0", "SALES", "テーブル");
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(ok(actors.navigation(member.token()))).containsKey("items");
    }

    @Test
    @DisplayName(
            "switching the work role, changing the main permission and removing the assignment apply on the next request")
    void changesApplyOnTheNextRequest() {
        long halfNone = halfNoneRole();
        Actor member = actors.memberWithWorkRole(halfNone);
        long allRead = actors.role("SALES", Map.of("T1", MainPermission.READ));
        fixtures.assignUser(allRead, member.userId());
        String token = member.token();
        assertThat(navigableTables(ok(actors.navigation(token)))).containsExactly("T0", "T3", "T4");

        fixtures.selectWorkRole(member.userId(), allRead);
        assertThat(navigableTables(ok(actors.navigation(token)))).containsExactly("T1");

        jdbc.update(
                "UPDATE permission_settings SET main_permission = 'NONE' WHERE role_id = ? AND table_name = 'T1'",
                allRead);
        assertThat(ok(actors.navigation(token))).containsEntry("emptyReason", "NOTHING_VISIBLE");

        fixtures.selectWorkRole(member.userId(), halfNone);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ?", member.userId());
        assertThat(ok(actors.navigation(token))).containsEntry("emptyReason", "NOTHING_VISIBLE");
    }

    @Test
    @DisplayName("a tree of exactly five levels is returned without losing any level")
    void fiveLevels() {
        NavigationDslFixture.install(
                holder,
                List.of(NavigationModels.schema("SALES", "T0")),
                List.of(NavigationModels.group(
                        "1",
                        NavigationModels.group(
                                "2",
                                NavigationModels.group(
                                        "3",
                                        NavigationModels.group(
                                                "4", NavigationModels.tableItem("5", "SALES", "T0")))))));
        Actor member = actors.memberWithWorkRole(actors.role("SALES", Map.of("T0", MainPermission.READ)));

        Map<String, Object> node = ok(actors.navigation(member.token()));
        for (int level = 1; level <= 5; level++) {
            assertThat(items(node)).hasSize(1);
            node = items(node).getFirst();
        }

        assertThat(node).containsEntry("id", "0.0.0.0.0").containsEntry("navigable", true);
        assertThat(items(node)).isEmpty();
    }

    @Test
    @DisplayName("no DSL and empty menus are NOT_CONFIGURED, and no work role is NOTHING_VISIBLE")
    void emptyReasons() {
        Actor withoutRole = actors.member();
        assertThat(ok(actors.navigation(withoutRole.token())))
                .containsEntry("items", List.of())
                .containsEntry("emptyReason", "NOTHING_VISIBLE");

        NavigationDslFixture.install(holder, List.of(NavigationModels.schema("SALES", "T0")), List.of());
        assertThat(ok(actors.navigation(withoutRole.token()))).containsEntry("emptyReason", "NOT_CONFIGURED");

        NavigationDslFixture.remove(holder);
        assertThat(ok(actors.navigation(withoutRole.token())))
                .containsEntry("items", List.of())
                .containsEntry("emptyReason", "NOT_CONFIGURED");
    }

    @Test
    @DisplayName("an administrator without a work role gets no admin menu, and the API adds no audit rows")
    void noAdminMenuAndNoAudit() {
        Actor admin = roleActors.admin();
        Actor member = actors.memberWithWorkRole(halfNoneRole());
        int before = auditRows();

        assertThat(ok(actors.navigation(admin.token())))
                .containsEntry("items", List.of())
                .containsEntry("emptyReason", "NOTHING_VISIBLE");
        ok(actors.navigation(member.token()));

        assertThat(auditRows()).isEqualTo(before);
    }
}
