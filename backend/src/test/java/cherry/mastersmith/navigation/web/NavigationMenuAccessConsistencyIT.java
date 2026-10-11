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
import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.MenuDepth;
import cherry.mastersmith.dsl.domain.TableRef;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.navigation.testsupport.NavigationActors;
import cherry.mastersmith.navigation.testsupport.NavigationDslFixture;
import cherry.mastersmith.navigation.testsupport.NavigationModels;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * メニューと置き場の一致のテスト（NFR1.12、{@code security-design.md} の 5節、計画の 2.2 の R-03 の手当て）。
 *
 * <p>期待はメニューの応答から作らず、テストの用意から書いた期待の表（DSL の全テーブルの期待の主権限）で持つ。確かめることは2つ。
 *
 * <ul>
 *   <li>置き場: DSL の全テーブルについて、期待が READ・FULL なら 200（{@code main} も期待どおり）、NONE なら 403
 *   <li>メニュー: 移れる項目のテーブルの集まりが、「期待が READ・FULL で、メニューの項目が指すテーブル」の集まりと等しい
 * </ul>
 *
 * <p>組1〜3・5・6 の DSL には、どのメニューの項目も指さない READ（以上）のテーブルを1つ以上置く（組6 は NONE のテーブルとは別に置く）。
 * 組4（作業ロールなし）・組7（DSL なし）は作れないため置かない（R-03）。口の値の網羅は role の {@code EffectivePermissionConsistencyIT}
 * が受け持つ。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class NavigationMenuAccessConsistencyIT {

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

    private RoleFixtures fixtures;

    private NavigationActors actors;

    @BeforeEach
    void setUp() {
        fixtures = new RoleFixtures(jdbc);
        actors = new NavigationActors(
                new RoleActors(userAccountService, revocationService, transactionManager, port), fixtures, port);
    }

    /** 7つの組。 */
    enum Group {
        WORST,
        SCHEMA_ONLY,
        TABLE_ONLY,
        NO_WORK_ROLE,
        OVERRIDES,
        COLUMN_ONLY,
        NO_DSL
    }

    /**
     * 組の用意。
     *
     * @param schemas DSL のスキーマ（{@code dsl} が false なら、問い合わせに使う名前の出どころだけ）
     * @param menus メニュー
     * @param dsl DSL を置くか
     * @param settings 作業ロールの明示の設定を入れる操作（null は作業ロールなし）
     * @param expected 全テーブルの期待の主権限
     */
    private record Case(
            List<DslSchema> schemas,
            List<DslMenuItem> menus,
            boolean dsl,
            LongConsumer settings,
            Map<TableRef, MainPermission> expected) {}

    @ParameterizedTest(name = "{0}")
    @EnumSource(Group.class)
    @DisplayName("the table access and the navigable menu items agree with the expected table")
    void menuAndAccessAgree(Group group) {
        Case target = build(group);
        if (target.dsl()) {
            NavigationDslFixture.install(holder, target.schemas(), target.menus());
        } else {
            NavigationDslFixture.remove(holder);
        }
        Actor member;
        if (target.settings() == null) {
            member = actors.member();
        } else {
            long roleId = fixtures.role(RoleFixtures.uniqueName("一致"));
            target.settings().accept(roleId);
            member = actors.memberWithWorkRole(roleId);
        }

        for (Map.Entry<TableRef, MainPermission> entry : target.expected().entrySet()) {
            TableRef table = entry.getKey();
            HttpResponse<String> response = actors.tableAccess(member.token(), table.schema(), table.name());
            if (entry.getValue() == MainPermission.NONE) {
                assertThat(response.statusCode())
                        .as("%s %s", table, response.body())
                        .isEqualTo(403);
            } else {
                assertThat(response.statusCode())
                        .as("%s %s", table, response.body())
                        .isEqualTo(200);
                assertThat(HttpTestClient.json(response))
                        .containsEntry("main", entry.getValue().name());
            }
        }

        Set<TableRef> pointed = new HashSet<>();
        collectPointed(target.menus(), pointed);
        Set<TableRef> expectedNavigable = new HashSet<>();
        for (TableRef table : pointed) {
            if (target.dsl() && target.expected().getOrDefault(table, MainPermission.NONE) != MainPermission.NONE) {
                expectedNavigable.add(table);
            }
        }
        HttpResponse<String> menu = actors.navigation(member.token());
        assertThat(menu.statusCode()).isEqualTo(200);
        assertThat(navigable(HttpTestClient.json(menu))).isEqualTo(expectedNavigable);
        if (group != Group.NO_WORK_ROLE && group != Group.NO_DSL) {
            assertThat(target.expected().entrySet())
                    .as("どの項目も指さない READ 以上のテーブルがある（R-03）")
                    .anyMatch(entry -> entry.getValue() != MainPermission.NONE && !pointed.contains(entry.getKey()));
        }
    }

    private static void collectPointed(List<DslMenuItem> items, Set<TableRef> pointed) {
        for (DslMenuItem item : items) {
            if (item.table() != null) {
                pointed.add(item.table());
            }
            collectPointed(item.items(), pointed);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<TableRef> navigable(Map<String, Object> body) {
        Set<TableRef> found = new HashSet<>();
        collectNavigable((List<Map<String, Object>>) body.get("items"), found);
        return found;
    }

    @SuppressWarnings("unchecked")
    private static void collectNavigable(List<Map<String, Object>> nodes, Set<TableRef> found) {
        for (Map<String, Object> node : nodes) {
            if (Boolean.TRUE.equals(node.get("navigable"))) {
                Map<String, Object> table = (Map<String, Object>) node.get("table");
                found.add(new TableRef((String) table.get("schemaName"), (String) table.get("tableName")));
            }
            collectNavigable((List<Map<String, Object>>) node.get("items"), found);
        }
    }

    private Case build(Group group) {
        return switch (group) {
            case WORST -> worst();
            case SCHEMA_ONLY -> {
                DslSchema schema = NavigationModels.schema("SALES", "T0", "T1", "T2", "T3");
                yield new Case(
                        List.of(schema),
                        pointing("SALES", "T0", "T1", "T2"),
                        true,
                        roleId -> fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ),
                        expected(
                                "SALES",
                                Map.of(
                                        "T0", MainPermission.READ,
                                        "T1", MainPermission.READ,
                                        "T2", MainPermission.READ,
                                        "T3", MainPermission.READ)));
            }
            case TABLE_ONLY -> {
                Map<String, MainPermission> mains = Map.of(
                        "T0", MainPermission.READ,
                        "T1", MainPermission.FULL,
                        "T2", MainPermission.NONE,
                        "T3", MainPermission.READ);
                yield new Case(
                        List.of(NavigationModels.schema("SALES", "T0", "T1", "T2", "T3")),
                        pointing("SALES", "T0", "T1", "T2"),
                        true,
                        roleId -> mains.forEach((table, main) ->
                                fixtures.setting(roleId, PermissionTarget.table("SALES", table), main)),
                        expected("SALES", mains));
            }
            case NO_WORK_ROLE ->
                new Case(
                        List.of(NavigationModels.schema("SALES", "T0", "T1")),
                        pointing("SALES", "T0", "T1"),
                        true,
                        null,
                        expected("SALES", Map.of("T0", MainPermission.NONE, "T1", MainPermission.NONE)));
            case OVERRIDES -> overrides();
            case COLUMN_ONLY ->
                new Case(
                        List.of(NavigationModels.schema("SALES", "T0", "T1")),
                        pointing("SALES", "T0"),
                        true,
                        roleId -> {
                            fixtures.setting(roleId, PermissionTarget.column("SALES", "T0", "ID"), MainPermission.READ);
                            fixtures.setting(roleId, PermissionTarget.table("SALES", "T1"), MainPermission.READ);
                        },
                        expected("SALES", Map.of("T0", MainPermission.NONE, "T1", MainPermission.READ)));
            case NO_DSL ->
                new Case(
                        List.of(NavigationModels.schema("SALES", "T0", "T1")),
                        pointing("SALES", "T0", "T1"),
                        false,
                        roleId -> fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.FULL),
                        expected("SALES", Map.of("T0", MainPermission.NONE, "T1", MainPermission.NONE)));
        };
    }

    private static List<DslMenuItem> pointing(String schema, String... tables) {
        List<DslMenuItem> items = new ArrayList<>();
        for (String table : tables) {
            items.add(NavigationModels.tableItem(table, schema, table));
        }
        return List.of(NavigationModels.group("まとまり", items.toArray(DslMenuItem[]::new)));
    }

    private static Map<TableRef, MainPermission> expected(String schema, Map<String, MainPermission> mains) {
        Map<TableRef, MainPermission> map = new LinkedHashMap<>();
        mains.forEach((table, main) -> map.put(new TableRef(schema, table), main));
        return map;
    }

    /** 組5: 上書きの向き（スキーマ NONE・テーブル READ と、スキーマ FULL・テーブル NONE）。 */
    private Case overrides() {
        List<DslSchema> schemas =
                List.of(NavigationModels.schema("A", "T0", "T1"), NavigationModels.schema("B", "T0", "T1"));
        List<DslMenuItem> menus = List.of(
                NavigationModels.tableItem("A.T0", "A", "T0"),
                NavigationModels.group(
                        "まとまり",
                        NavigationModels.tableItem("A.T1", "A", "T1"),
                        NavigationModels.tableItem("B.T0", "B", "T0")));
        Map<TableRef, MainPermission> expected = new LinkedHashMap<>();
        expected.put(new TableRef("A", "T0"), MainPermission.READ);
        expected.put(new TableRef("A", "T1"), MainPermission.NONE);
        expected.put(new TableRef("B", "T0"), MainPermission.NONE);
        expected.put(new TableRef("B", "T1"), MainPermission.FULL);
        return new Case(
                schemas,
                menus,
                true,
                roleId -> {
                    fixtures.setting(roleId, PermissionTarget.schema("A"), MainPermission.NONE);
                    fixtures.setting(roleId, PermissionTarget.table("A", "T0"), MainPermission.READ);
                    fixtures.setting(roleId, PermissionTarget.schema("B"), MainPermission.FULL);
                    fixtures.setting(roleId, PermissionTarget.table("B", "T0"), MainPermission.NONE);
                },
                expected);
    }

    /**
     * 組1: 悪い側（100 テーブル × 100 カラム、1万カラムに READ と FULL を交互に明示、テーブルの番号が偶数なら NONE・奇数なら READ と FULL を
     * 交互に明示、menus 1,000 項目・深さ 5）。メニューは P000〜P098 を指し、P099（FULL）はどの項目も指さない。
     */
    private Case worst() {
        List<String> tables = new ArrayList<>();
        Map<String, DslTable> map = new LinkedHashMap<>();
        Map<String, MainPermission> tableMains = new LinkedHashMap<>();
        Map<String, Map<String, MainPermission>> columnMains = new LinkedHashMap<>();
        for (int t = 0; t < 100; t++) {
            String table = String.format("P%03d", t);
            tables.add(table);
            String[] columns = new String[100];
            Map<String, MainPermission> perColumn = new LinkedHashMap<>();
            for (int c = 0; c < 100; c++) {
                columns[c] = String.format("C%03d", c);
                perColumn.put(columns[c], c % 2 == 0 ? MainPermission.READ : MainPermission.FULL);
            }
            map.put(table, RoleDslFixture.table(table, columns));
            columnMains.put(table, perColumn);
            tableMains.put(
                    table, t % 2 == 0 ? MainPermission.NONE : t % 4 == 1 ? MainPermission.READ : MainPermission.FULL);
        }
        DslSchema schema = RoleDslFixture.schema("SALES", map.values().toArray(DslTable[]::new));
        List<DslMenuItem> menus = NavigationDslFixture.worstMenus("SALES", tables.subList(0, 99));
        assertThat(count(menus)).as("menus の項目の数").isEqualTo(1_000);
        assertThat(MenuDepth.depth(menus)).as("menus の深さ").isEqualTo(5);
        return new Case(
                List.of(schema),
                menus,
                true,
                roleId -> {
                    NavigationDslFixture.insertTableSettings(jdbc, roleId, "SALES", tableMains);
                    NavigationDslFixture.insertColumnSettings(jdbc, roleId, "SALES", columnMains);
                },
                expected("SALES", tableMains));
    }

    private static int count(List<DslMenuItem> items) {
        int total = 0;
        for (DslMenuItem item : items) {
            total += 1 + count(item.items());
        }
        return total;
    }
}
