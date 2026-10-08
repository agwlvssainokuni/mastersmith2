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
import cherry.mastersmith.group.domain.GroupOperation;
import cherry.mastersmith.group.testsupport.GroupApi;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.TestGroupBarrier;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.role.testsupport.TestRoleBarrier;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Gate;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Point;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Signal;
import cherry.mastersmith.user.repository.UserRepository;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
 * グループの削除とグループへの割り当ての同時の重なり（{@code reliability-design.md} 4.2 の #10、BR8.2・BR10.1、NFR3.1 (e)、AC2.1.5 のロールの
 * 側、計画の 8.3）の結合テスト。group の待ち合わせの口（{@link TestGroupBarrier}）と role の待ち合わせの口（{@link TestRoleBarrier}）を両方
 * 使い、問う口は本番の実装のまま（差し替えない）。どちらの順に並んでも、終わった後にグループが消えていれば割り当ては 0 で、負けた側は 4xx。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class, TestGroupBarrier.Config.class})
class GroupRoleAssignmentConcurrencyIT {

    @Autowired
    TestRoleBarrier barrier;

    @Autowired
    TestGroupBarrier groupBarrier;

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

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void tearDown() {
        barrier.reset();
        executor.shutdownNow();
    }

    private CompletableFuture<HttpResponse<String>> async(Supplier<HttpResponse<String>> call) {
        return CompletableFuture.supplyAsync(call, executor);
    }

    private static HttpResponse<String> result(CompletableFuture<HttpResponse<String>> future) throws Exception {
        return future.get(TestRoleBarrier.WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);
    }

    @AfterEach
    void resetGroupBarrier() {
        groupBarrier.reset();
    }

    private int groupAssignments(long groupId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_role_assignments WHERE group_id = ?", Integer.class, groupId);
    }

    @Test
    @DisplayName("#10 a group delete holding the group row first wins and the assignment is GROUP_NOT_FOUND")
    void deleteFirst() throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("グループ削除が先"));
        long groupId = groups.group(GroupFixtures.uniqueName("削除が先"));
        TestGroupBarrier.Gate deleting =
                groupBarrier.hold(TestGroupBarrier.Point.AFTER_LOCK, GroupOperation.DELETE, String.valueOf(groupId));
        Signal assigning = barrier.signal(Point.AFTER_LOCK, RoleOperation.ASSIGN, String.valueOf(roleId));
        GroupApi groupApi = new GroupApi(port);

        CompletableFuture<HttpResponse<String>> delete = async(() -> groupApi.delete(admin.token(), groupId));
        deleting.awaitArrival();
        CompletableFuture<HttpResponse<String>> assign = async(() -> api.assignGroup(admin.token(), roleId, groupId));
        assigning.awaitPassed();
        deleting.release();

        assertThat(result(delete).statusCode()).isEqualTo(204);
        HttpResponse<String> loser = result(assign);
        assertThat(loser.statusCode()).as(loser.body()).isIn(404, 409);
        assertThat(HttpTestClient.json(loser).get("code")).isIn("GROUP_NOT_FOUND", "GROUP_BUSY");
        assertThat(groups.groupRows(groupId)).isZero();
        assertThat(groupAssignments(groupId)).isZero();
    }

    @Test
    @DisplayName("#10 an assignment holding the group row first wins and the group delete is GROUP_IN_USE")
    void assignFirst() throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("割り当てが先"));
        long groupId = groups.group(GroupFixtures.uniqueName("割り当てが先"));
        Gate assigning = barrier.hold(Point.AFTER_WRITE, RoleOperation.ASSIGN, roleId + ":G" + groupId);
        GroupApi groupApi = new GroupApi(port);

        CompletableFuture<HttpResponse<String>> assign = async(() -> api.assignGroup(admin.token(), roleId, groupId));
        assigning.awaitArrival();
        CompletableFuture<HttpResponse<String>> delete = async(() -> groupApi.delete(admin.token(), groupId));
        assigning.release();

        assertThat(result(assign).statusCode()).isEqualTo(204);
        HttpResponse<String> loser = result(delete);
        assertThat(loser.statusCode()).as(loser.body()).isEqualTo(409);
        assertThat(HttpTestClient.json(loser).get("code")).isIn("GROUP_IN_USE", "GROUP_BUSY");
        assertThat(groups.groupRows(groupId)).isOne();
        assertThat(groupAssignments(groupId)).isOne();
    }
}
