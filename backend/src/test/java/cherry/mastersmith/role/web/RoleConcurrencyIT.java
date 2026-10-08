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
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Signal;
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
 * 同じロールの同時の保存（{@code reliability-design.md} 4.2 の #7、BR4.9・BR8.1、NFR3.1 (c)、AC1.2.12）の結合テスト。重なりは待ち合わせの
 * 口（{@link TestRoleBarrier}）で作り、合否は経過の時間ではなく状態コード・最後の値・監査の行で決める。後の側が先の側の確定を 3 秒以内に
 * 見られなかったときの {@code ROLE_BUSY} も負けの code として受け入れる（4.2 の注）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class})
class RoleConcurrencyIT {

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
    @DisplayName("#7 two saves of the same role are serialized: the later value wins and both audits chain the values")
    void sameRoleSavedTwice() throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("同時の保存"));
        String key = String.valueOf(roleId);
        String readBody = saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", null, null)));
        String fullBody = saveBody("SALES", null, List.of(entry("SALES", null, null, "FULL", null, null)));
        Gate first = barrier.hold(Point.AFTER_LOCK, RoleOperation.CHANGE_PERMISSIONS, key);
        Signal second = barrier.signal(Point.BEFORE_LOCK, RoleOperation.CHANGE_PERMISSIONS, key);

        CompletableFuture<HttpResponse<String>> a = async(() -> api.save(admin.token(), roleId, readBody));
        first.awaitArrival();
        CompletableFuture<HttpResponse<String>> b = async(() -> api.save(admin.token(), roleId, fullBody));
        second.awaitPassed();
        first.release();

        assertThat(result(a).statusCode()).isEqualTo(204);
        HttpResponse<String> later = result(b);
        if (later.statusCode() == 409) {
            assertCode(later, 409, "ROLE_BUSY");
            return;
        }
        assertThat(later.statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject(
                        "SELECT main_permission FROM permission_settings WHERE role_id = ?", String.class, roleId))
                .isEqualTo("FULL");
        List<String> details = jdbc.queryForList(
                "SELECT detail FROM audit_events WHERE event_type = 'ROLE_PERMISSION_CHANGED' AND target_role_id = ?"
                        + " ORDER BY audit_event_id",
                String.class,
                roleId);
        assertThat(details).hasSize(2);
        assertThat(details.get(0)).contains("\"before\":{\"main\":null").contains("\"after\":{\"main\":\"READ\"");
        assertThat(details.get(1)).contains("\"before\":{\"main\":\"READ\"").contains("\"after\":{\"main\":\"FULL\"");
    }
}
