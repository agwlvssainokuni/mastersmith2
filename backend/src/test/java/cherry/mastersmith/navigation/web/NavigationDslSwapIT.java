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
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.navigation.testsupport.NavigationActors;
import cherry.mastersmith.navigation.testsupport.NavigationDslFixture;
import cherry.mastersmith.navigation.testsupport.NavigationModels;
import cherry.mastersmith.navigation.testsupport.TestNavigationBarrier;
import cherry.mastersmith.navigation.testsupport.TestNavigationBarrier.Gate;
import cherry.mastersmith.navigation.testsupport.TestNavigationBarrier.Point;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 読み取りと DSL の適用し直しの重なりの結合テスト（BR1.5、NFR1.10、{@code reliability-design.md} の 2節、計画の 2.2 の R-05 の手当て）。
 *
 * <p>{@link TestNavigationBarrier} の点で要求を止め、止まった合図を受けてから DSL を差し替え、放す。{@code sleep}・経過の時間・壁時計を
 * 使わない。待ちの上限 20 秒は止まったままになったときの守りだけで、合否は応答の中身で決める。
 *
 * <p>メニューの場合の期待は「古い DSL の menus を、新しい DSL の写しで絞った木（{@code label} は古い値）」で、項目ごとに書く。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestNavigationBarrier.Config.class})
class NavigationDslSwapIT {

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

    @Autowired
    TestNavigationBarrier barrier;

    private NavigationActors actors;

    private Actor member;

    @BeforeEach
    void setUp() {
        actors = new NavigationActors(
                new RoleActors(userAccountService, revocationService, transactionManager, port),
                new RoleFixtures(jdbc),
                port);
        NavigationDslFixture.install(
                holder,
                List.of(NavigationModels.schema("SALES", "T0", "T1")),
                List.of(
                        NavigationModels.tableItem("古い T0", "SALES", "T0"),
                        NavigationModels.tableItem("古い T1", "SALES", "T1")));
        member = actors.memberWithWorkRole(
                actors.role("SALES", Map.of("T0", MainPermission.READ, "T1", MainPermission.READ)));
    }

    @AfterEach
    void tearDown() {
        barrier.reset();
    }

    /** 点で止めた要求の間に DSL を差し替え、放した後の応答を返す。 */
    private HttpResponse<String> swapWhileHeld(
            Point point, Supplier<HttpResponse<String>> request, List<DslSchema> schemas, String... newMenuTables)
            throws InterruptedException, ExecutionException, TimeoutException {
        Gate gate = barrier.hold(point);
        CompletableFuture<HttpResponse<String>> response = CompletableFuture.supplyAsync(request);
        try {
            gate.awaitArrival();
            List<cherry.mastersmith.dsl.domain.DslMenuItem> menus = java.util.Arrays.stream(newMenuTables)
                    .map(table -> NavigationModels.tableItem("新しい " + table, "SALES", table))
                    .toList();
            NavigationDslFixture.install(holder, schemas, menus);
        } finally {
            gate.release();
        }
        return response.get(TestNavigationBarrier.WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return (List<Map<String, Object>>) HttpTestClient.json(response).get("items");
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "menu: a table removed while held is not navigable and the old labels are filtered by the new snapshot")
    void menuWithRemovedTable() throws Exception {
        HttpResponse<String> response = swapWhileHeld(
                Point.AFTER_DSL_READ,
                () -> actors.navigation(member.token()),
                List.of(NavigationModels.schema("SALES", "T0")),
                "T0");

        assertThat(items(response))
                .singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("id", "0")
                        .containsEntry("label", Map.of("ja", "古い T0", "en", "古い T0-en"))
                        .containsEntry("navigable", true)
                        .containsEntry("table", Map.of("schemaName", "SALES", "tableName", "T0")));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "menu: a table added while held gives no item without permission and keeps the old menus and labels")
    void menuWithAddedTable() throws Exception {
        HttpResponse<String> response = swapWhileHeld(
                Point.AFTER_DSL_READ,
                () -> actors.navigation(member.token()),
                List.of(NavigationModels.schema("SALES", "T0", "T1", "T2")),
                "T2",
                "T0");

        List<Map<String, Object>> items = items(response);
        assertThat(items).extracting(item -> item.get("id")).containsExactly("0", "1");
        assertThat(items.get(0)).containsEntry("label", Map.of("ja", "古い T0", "en", "古い T0-en"));
        assertThat(items.get(1))
                .containsEntry("label", Map.of("ja", "古い T1", "en", "古い T1-en"))
                .containsEntry("table", Map.of("schemaName", "SALES", "tableName", "T1"));
        assertThat(response.body()).doesNotContain("T2", "新しい");
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "table access: a table removed after the resolve gives 403 without the display name")
    void tableAccessWithRemovedTable() throws Exception {
        HttpResponse<String> response = swapWhileHeld(
                Point.AFTER_RESOLVE,
                () -> actors.tableAccess(member.token(), "SALES", "T1"),
                List.of(NavigationModels.schema("SALES", "T0")),
                "T0");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "ACCESS_DENIED");
        assertThat(response.body()).doesNotContain("テーブル", "Table T1");
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "table access: a table kept after the resolve gives 200 with the new display name")
    void tableAccessWithKeptTable() throws Exception {
        DslTable renamed = new DslTable(
                "T1",
                new DisplayName("新しい明細", "New lines"),
                false,
                List.of("ID"),
                List.of(),
                Map.of("ID", RoleDslFixture.column("ID")));
        DslSchema schema = RoleDslFixture.schema("SALES", RoleDslFixture.table("T0", "ID"), renamed);

        HttpResponse<String> response = swapWhileHeld(
                Point.AFTER_RESOLVE, () -> actors.tableAccess(member.token(), "SALES", "T1"), List.of(schema), "T1");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(HttpTestClient.json(response))
                .containsEntry("displayName", Map.of("ja", "新しい明細", "en", "New lines"))
                .containsEntry("main", "READ");
    }
}
