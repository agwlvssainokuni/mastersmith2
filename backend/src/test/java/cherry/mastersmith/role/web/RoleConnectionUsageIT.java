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
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.group.testsupport.ConnectionHoldRecorder;
import cherry.mastersmith.group.testsupport.GroupFixtures;
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
import cherry.mastersmith.user.repository.UserRepository;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
 * 1つの要求が同時に持つ内部DB の接続の数（{@code scalability-design.md} 2.1・2.2、NFR2.7・NFR2.11、計画の D-9）の結合テスト（B4 の経路）。
 * 見積もりは書き込み（成功・業務の拒否・違反の読み替え）が 2 本（操作と確定の後の監査）、読み取りと {@code ROLE_BUSY} が 1 本で、3 本以上は
 * 見積もりの誤りとする。group の {@link ConnectionHoldRecorder} を使い回す。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class, ConnectionHoldRecorder.Config.class})
class RoleConnectionUsageIT {

    @Autowired
    ConnectionHoldRecorder recorder;

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

    @Autowired
    UserRepository users;

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
    }

    /** 要求を1つ送り、その間にどのスレッドも同時に持った接続の数の最大を返す。 */
    private int held(Supplier<HttpResponse<String>> call, int expectedStatus) {
        recorder.reset();
        HttpResponse<String> response = call.get();
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return recorder.maxHeldByOneThread();
    }

    @Test
    @DisplayName("successful writes hold at most two connections and reads hold one")
    void successAndReads() {
        long roleId = ((Number) HttpTestClient.json(api.create(admin.token(), RoleFixtures.uniqueName("接続 対象")))
                        .get("roleId"))
                .longValue();
        fixtures.setting(roleId, PermissionTarget.table("SALES", "GONE"), MainPermission.READ);

        assertThat(held(() -> api.create(admin.token(), RoleFixtures.uniqueName("接続")), 201))
                .isEqualTo(2);
        assertThat(held(() -> api.rename(admin.token(), roleId, RoleFixtures.uniqueName("接続 改名")), 204))
                .isEqualTo(2);
        assertThat(held(
                        () -> api.save(
                                admin.token(),
                                roleId,
                                saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", null, null)))),
                        204))
                .isEqualTo(2);
        assertThat(held(
                        () -> api.clear(
                                admin.token(),
                                roleId,
                                RoleApi.json(Map.of(
                                        "targets", List.of(Map.of("schemaName", "SALES", "tableName", "GONE"))))),
                        204))
                .isEqualTo(2);
        assertThat(held(() -> api.list(admin.token(), ""), 200)).isEqualTo(1);
        assertThat(held(() -> api.detail(admin.token(), roleId), 200)).isEqualTo(1);
        assertThat(held(() -> api.schemas(admin.token(), roleId), 200)).isEqualTo(1);
        assertThat(held(() -> api.columns(admin.token(), roleId, "SALES", "ORDER_LINE"), 200))
                .isEqualTo(1);
        assertThat(held(() -> api.delete(admin.token(), roleId), 204)).isEqualTo(2);
    }

    @Test
    @DisplayName("a business rejection holds two connections and ROLE_BUSY holds one")
    void rejectionAndBusy() throws Exception {
        String name = RoleFixtures.uniqueName("接続 拒否");
        long roleId = fixtures.role(name);

        assertThat(held(() -> api.rename(admin.token(), roleId, name), 409)).isEqualTo(2);
        assertThat(held(() -> api.delete(admin.token(), Long.MAX_VALUE), 404)).isEqualTo(2);
        assertThat(held(() -> api.detail(admin.token(), Long.MAX_VALUE), 404)).isEqualTo(1);
        try (RowLockHolder holder = RowLockHolder.hold(
                TestDatabase.url(tempDir), "SELECT role_id FROM roles WHERE role_id = ? FOR UPDATE", roleId)) {
            assertThat(holder.lockedRows()).isEqualTo(1);
            assertThat(held(() -> api.delete(admin.token(), roleId), 409)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a violation read as a rejection holds at most two connections in the losing request")
    void violation() throws Exception {
        String name = RoleFixtures.uniqueName("接続 違反");
        Gate later = barrier.hold(
                Point.AFTER_CHECK, RoleOperation.CREATE, RoleFixtures.name(name).key());
        recorder.reset();

        CompletableFuture<HttpResponse<String>> b =
                CompletableFuture.supplyAsync(() -> api.create(admin.token(), name));
        later.awaitArrival();
        HttpResponse<String> a = api.create(admin.token(), name);
        later.release();
        HttpResponse<String> lost = b.get(TestRoleBarrier.WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);

        assertThat(a.statusCode()).isEqualTo(201);
        assertThat(lost.statusCode()).isEqualTo(409);
        assertThat(recorder.maxHeldByOneThread()).isEqualTo(2);
    }

    @Test
    @DisplayName("assignments, unassignments and a writing switch hold two connections, the rest one (B5)")
    void assignmentsAndSwitches() {
        long roleId = fixtures.role(RoleFixtures.uniqueName("接続 割り当て"));
        long other = fixtures.role(RoleFixtures.uniqueName("接続 割り当て 二"));
        Actor member = new RoleActors(userAccountService, revocationService, transactionManager, port).member();
        long groupId = new GroupFixtures(users, jdbc).group(GroupFixtures.uniqueName("接続"));
        fixtures.assignUser(other, member.userId());

        assertThat(held(() -> api.assignUser(admin.token(), roleId, member.userId()), 204))
                .isEqualTo(2);
        assertThat(held(() -> api.assignGroup(admin.token(), roleId, groupId), 204))
                .isEqualTo(2);
        assertThat(held(() -> api.assignUser(admin.token(), roleId, member.userId()), 409))
                .as("拒否")
                .isEqualTo(2);
        assertThat(held(() -> api.switchWorkRole(member.token(), roleId), 204))
                .as("書く切り替え")
                .isEqualTo(2);
        assertThat(held(() -> api.switchWorkRole(member.token(), roleId), 204))
                .as("書かない切り替え")
                .isEqualTo(1);
        assertThat(held(() -> api.assignments(admin.token(), roleId), 200)).isEqualTo(1);
        assertThat(held(() -> api.userRoles(admin.token(), member.userId()), 200))
                .isEqualTo(1);
        assertThat(held(() -> api.groupRoles(admin.token(), groupId), 200)).isEqualTo(1);
        assertThat(held(() -> api.workRole(member.token()), 200)).isEqualTo(1);
        assertThat(held(() -> api.mySchemas(member.token()), 200)).isEqualTo(1);
        assertThat(held(() -> api.unassignGroup(admin.token(), roleId, groupId), 204))
                .isEqualTo(2);
        assertThat(held(() -> api.unassignUser(admin.token(), roleId, member.userId()), 204))
                .isEqualTo(2);
    }
}
