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
import java.nio.file.Path;
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
 * 同じ名前の重なりの違反の経路（{@code reliability-design.md} 4.2 の #2、BR8.5・BR11.2、NFR3.1 (b)・NFR3.4、AC1.1.11）の結合テスト。後の側を
 * 重なりの判定の後（{@code afterCheck}）で止め、先の側を確定させてから放す。後の側は確定済みの鍵に書くため待たずに一意の違反になり、
 * 1つ目を巻き戻した後の2つ目のトランザクションで失敗の監査が残る（500 にならない）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class})
class RoleConflictAuditIT {

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
    TestRoleBarrier barrier;

    @Autowired
    JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newCachedThreadPool();

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

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(HttpTestClient.json(response)).containsEntry("code", code);
    }

    private int auditRows(String eventType, String result) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = ? AND result = ?",
                Integer.class,
                eventType,
                result);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName(
            "#2 a creation that passed the check before the other committed is ROLE_NAME_DUPLICATE with a failure audit")
    void createViolation() throws Exception {
        String name = RoleFixtures.uniqueName("同名");
        String key = RoleFixtures.name(name).key();
        Gate later = barrier.hold(Point.AFTER_CHECK, RoleOperation.CREATE, key);
        int failuresBefore = auditRows("ROLE_CREATED", "FAILURE");

        CompletableFuture<HttpResponse<String>> b = async(() -> api.create(admin.token(), name));
        later.awaitArrival();
        HttpResponse<String> a = api.create(admin.token(), name);
        assertThat(a.statusCode()).as(a.body()).isEqualTo(201);
        later.release();

        assertCode(result(b), 409, "ROLE_NAME_DUPLICATE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE name_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(auditRows("ROLE_CREATED", "FAILURE")).isEqualTo(failuresBefore + 1);
        assertThat(jdbc.queryForMap(
                        "SELECT failure_reason, target_role_id, detail FROM audit_events WHERE event_type = 'ROLE_CREATED'"
                                + " AND result = 'FAILURE' ORDER BY audit_event_id DESC LIMIT 1"))
                .containsEntry("FAILURE_REASON", "ROLE_NAME_DUPLICATE")
                .containsEntry("TARGET_ROLE_ID", null)
                .containsEntry("DETAIL", "{\"name\":\"" + name + "\"}");
    }

    @Test
    @DisplayName("#2 two roles renamed to the same name: the later one is ROLE_NAME_DUPLICATE with a failure audit")
    void renameViolation() throws Exception {
        long first = fixtures.role(RoleFixtures.uniqueName("改名 一"));
        long second = fixtures.role(RoleFixtures.uniqueName("改名 二"));
        String name = RoleFixtures.uniqueName("同じ名前");
        String key = RoleFixtures.name(name).key();
        Gate later = barrier.hold(Point.AFTER_CHECK, RoleOperation.RENAME, key);

        CompletableFuture<HttpResponse<String>> b = async(() -> api.rename(admin.token(), second, name));
        later.awaitArrival();
        assertThat(api.rename(admin.token(), first, name).statusCode()).isEqualTo(204);
        later.release();

        assertCode(result(b), 409, "ROLE_NAME_DUPLICATE");
        assertThat(jdbc.queryForObject("SELECT name FROM roles WHERE role_id = ?", String.class, first))
                .isEqualTo(name);
        assertThat(jdbc.queryForObject("SELECT name FROM roles WHERE role_id = ?", String.class, second))
                .isNotEqualTo(name);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ROLE_RENAMED' AND result = 'FAILURE'"
                                + " AND failure_reason = 'ROLE_NAME_DUPLICATE' AND target_role_id = ?",
                        Integer.class,
                        second))
                .isEqualTo(1);
    }
}
