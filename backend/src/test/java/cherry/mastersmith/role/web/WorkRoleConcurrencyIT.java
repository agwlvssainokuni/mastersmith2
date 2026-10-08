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
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.role.testsupport.TestRoleBarrier;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Gate;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Point;
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
 * 作業ロールの切り替えの同時の重なり（{@code reliability-design.md} 4.2 の #8・#9、BR7.8・BR8.4、NFR3.1 (d)、AC4.1.5、計画の 8.3）の
 * 結合テスト。重なりは待ち合わせの口（{@link TestRoleBarrier}）で作り、合否は経過の時間ではなく状態コード・保存の値・次の要求の作業ロールで
 * 決める。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class})
class WorkRoleConcurrencyIT {

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

    @Test
    @DisplayName(
            "#8 an unassignment committed while a switch is checked lets both succeed and reads the first role next")
    void switchAndUnassign() throws Exception {
        long first = fixtures.role(RoleFixtures.uniqueName("重なり 一"));
        long second = fixtures.role(RoleFixtures.uniqueName("重なり 二"));
        Actor member = actors.member();
        fixtures.assignUser(first, member.userId());
        fixtures.assignUser(second, member.userId());
        Gate checked = barrier.hold(Point.AFTER_CHECK, RoleOperation.SWITCH_WORK_ROLE, String.valueOf(member.userId()));

        CompletableFuture<HttpResponse<String>> switching = async(() -> api.switchWorkRole(member.token(), second));
        checked.awaitArrival();
        assertThat(api.unassignUser(admin.token(), second, member.userId()).statusCode())
                .isEqualTo(204);
        checked.release();

        assertThat(result(switching).statusCode()).isEqualTo(204);
        assertThat(fixtures.storedWorkRole(member.userId())).isEqualTo(second);
        Object current = HttpTestClient.json(api.workRole(member.token())).get("current");
        assertThat(((Map<?, ?>) current).get("roleId")).as("割り当ての外のロールを使わない").isEqualTo((int) first);
    }

    @Test
    @DisplayName(
            "#9 two first switches of the same user: the later waits on the key and is ROLE_BUSY, the first is stored")
    void twoFirstSwitches() throws Exception {
        long first = fixtures.role(RoleFixtures.uniqueName("初めて 一"));
        long second = fixtures.role(RoleFixtures.uniqueName("初めて 二"));
        Actor member = actors.member();
        fixtures.assignUser(first, member.userId());
        fixtures.assignUser(second, member.userId());
        Gate written = barrier.hold(Point.AFTER_WRITE, RoleOperation.SWITCH_WORK_ROLE, String.valueOf(member.userId()));
        int switched = auditRows("WORK_ROLE_SWITCHED", "SUCCESS");

        CompletableFuture<HttpResponse<String>> a = async(() -> api.switchWorkRole(member.token(), second));
        written.awaitArrival();
        HttpResponse<String> b = api.switchWorkRole(member.token(), first);
        assertThat(a).as("先の側は放すまで終わらない").isNotDone();
        written.release();

        assertCode(b, 409, "ROLE_BUSY");
        assertThat(result(a).statusCode()).isEqualTo(204);
        assertThat(fixtures.storedWorkRole(member.userId())).isEqualTo(second);
        assertThat(auditRows("WORK_ROLE_SWITCHED", "SUCCESS")).isEqualTo(switched + 1);
    }
}
