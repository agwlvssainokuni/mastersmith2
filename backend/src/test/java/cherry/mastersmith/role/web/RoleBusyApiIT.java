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
import java.util.List;
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
 * 待ちの上限切れ（{@code reliability-design.md} 4.2 の #3・#5、BR8.3・BR8.4・BR11.3、NFR3.3）の結合テスト。先の側を放さずに、後の側を
 * H2 の上限（名前の鍵 約 2 秒、ロールの行 3 秒）まで待たせて切らせる。合否は時間ではなく、{@code ROLE_BUSY}・監査の行が無いこと・状態で
 * 決める。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class})
class RoleBusyApiIT {

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
            "#3 a creation waiting for an uncommitted same name key is ROLE_BUSY without audit, the first one wins")
    void nameKeyTimeout() throws Exception {
        String name = RoleFixtures.uniqueName("鍵の待ち");
        String key = RoleFixtures.name(name).key();
        Gate first = barrier.hold(Point.AFTER_WRITE, RoleOperation.CREATE, key);
        int failuresBefore = auditRows("ROLE_CREATED", "FAILURE");

        CompletableFuture<HttpResponse<String>> a = async(() -> api.create(admin.token(), name));
        first.awaitArrival();
        HttpResponse<String> b = api.create(admin.token(), name);
        assertThat(a).as("先の側は放すまで終わらない").isNotDone();
        first.release();

        assertCode(b, 409, "ROLE_BUSY");
        assertThat(result(a).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE name_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(auditRows("ROLE_CREATED", "FAILURE")).as("BUSY は監査に残さない").isEqualTo(failuresBefore);
    }

    @Test
    @DisplayName("#5 a role row held past the 3 second limit makes a save and a rename ROLE_BUSY without audit")
    void rowLockTimeout() throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("行の待ち"));
        Gate holding = barrier.hold(Point.AFTER_LOCK, RoleOperation.DELETE, String.valueOf(roleId));
        int changedBefore =
                auditRows("ROLE_PERMISSION_CHANGED", "FAILURE") + auditRows("ROLE_PERMISSION_CHANGED", "SUCCESS");

        CompletableFuture<HttpResponse<String>> delete = async(() -> api.delete(admin.token(), roleId));
        holding.awaitArrival();
        HttpResponse<String> save = api.save(
                admin.token(),
                roleId,
                saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", null, null))));
        HttpResponse<String> rename = api.rename(admin.token(), roleId, RoleFixtures.uniqueName("待ち 改名"));
        assertThat(delete).as("先の側は放すまで終わらない").isNotDone();
        holding.release();

        assertCode(save, 409, "ROLE_BUSY");
        assertCode(rename, 409, "ROLE_BUSY");
        assertThat(result(delete).statusCode()).isEqualTo(204);
        assertThat(auditRows("ROLE_PERMISSION_CHANGED", "FAILURE") + auditRows("ROLE_PERMISSION_CHANGED", "SUCCESS"))
                .isEqualTo(changedBefore);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ROLE_RENAMED' AND target_role_id = ?",
                        Integer.class,
                        roleId))
                .isZero();
    }
}
