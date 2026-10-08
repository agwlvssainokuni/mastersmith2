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
package cherry.mastersmith.group.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.domain.GroupOperation;
import cherry.mastersmith.group.testsupport.ConnectionHoldRecorder;
import cherry.mastersmith.group.testsupport.GroupActors;
import cherry.mastersmith.group.testsupport.GroupActors.Actor;
import cherry.mastersmith.group.testsupport.GroupApi;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.TestGroupBarrier;
import cherry.mastersmith.group.testsupport.TestGroupBarrier.Gate;
import cherry.mastersmith.group.testsupport.TestGroupBarrier.Point;
import cherry.mastersmith.group.testsupport.TestGroupDeletionGuard;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Locale;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 1つの要求が同時に持つ内部DB の接続の数（{@code scalability-design.md} 2.1・2.2、NFR2.7、計画の D-9）の結合テスト。見積もりは書き込み
 * （成功・書き込みの前の拒否・違反の読み替え）が 2 本（操作と確定の後の監査）、読み取りと {@code GROUP_BUSY} が 1 本で、3 本以上は
 * 見積もりの誤りとする。
 *
 * <p>接続を貸す部品をテストだけで包み（{@link ConnectionHoldRecorder}）、スレッドごとの同時の本数の最大を記録する。確定の後の監査は同じ
 * スレッドで2本目を借りるため、途中で止めなくても最大に表れる（計画の D-9 の「監査の途中で止めて数える」を、決定的な最大の記録で代えた）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({
    AuthApiTestConfig.class,
    TestGroupBarrier.Config.class,
    TestGroupDeletionGuard.Config.class,
    ConnectionHoldRecorder.Config.class
})
class GroupConnectionUsageIT {

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
    ConnectionHoldRecorder recorder;

    @Autowired
    TestGroupBarrier barrier;

    private GroupApi api;

    private GroupActors actors;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new GroupApi(port);
        actors = new GroupActors(userAccountService, revocationService, transactionManager, port);
        admin = actors.admin();
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

    private long group(String name) {
        return ((Number) HttpTestClient.json(api.create(admin.token(), name)).get("groupId")).longValue();
    }

    @Test
    @DisplayName("successful writes hold at most two connections and reads hold one")
    void successAndReads() {
        String name = GroupFixtures.uniqueName("接続");
        Actor user = actors.user("見本 接続");

        assertThat(held(() -> api.create(admin.token(), name), 201)).isEqualTo(2);
        long groupId = group(GroupFixtures.uniqueName("接続 対象"));
        assertThat(held(() -> api.rename(admin.token(), groupId, GroupFixtures.uniqueName("接続 改名")), 204))
                .isEqualTo(2);
        assertThat(held(() -> api.addMember(admin.token(), groupId, user.userId()), 204))
                .isEqualTo(2);
        assertThat(held(() -> api.list(admin.token(), ""), 200)).isEqualTo(1);
        assertThat(held(() -> api.detail(admin.token(), groupId), 200)).isEqualTo(1);
        assertThat(held(() -> api.removeMember(admin.token(), groupId, user.userId()), 204))
                .isEqualTo(2);
        assertThat(held(() -> api.delete(admin.token(), groupId), 204)).isEqualTo(2);
    }

    @Test
    @DisplayName("a rejection before the write holds two connections and GROUP_BUSY holds one")
    void rejectionAndBusy() throws Exception {
        String name = GroupFixtures.uniqueName("接続 拒否");
        long groupId = group(name);

        assertThat(held(() -> api.rename(admin.token(), groupId, name), 409)).isEqualTo(2);
        assertThat(held(() -> api.detail(admin.token(), Long.MAX_VALUE), 404)).isEqualTo(1);
        try (RowLockHolder holder = RowLockHolder.hold(
                TestDatabase.url(tempDir), "SELECT group_id FROM groups WHERE group_id = ? FOR UPDATE", groupId)) {
            assertThat(holder.lockedRows()).isEqualTo(1);
            assertThat(held(() -> api.delete(admin.token(), groupId), 409)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a violation read as a rejection holds at most two connections in the losing request")
    void violation() throws Exception {
        String name = GroupFixtures.uniqueName("接続 違反");
        Gate later = barrier.hold(Point.AFTER_CHECK, GroupOperation.CREATE, name.toLowerCase(Locale.ROOT));
        recorder.reset();

        CompletableFuture<HttpResponse<String>> b =
                CompletableFuture.supplyAsync(() -> api.create(admin.token(), name));
        later.awaitArrival();
        HttpResponse<String> a = api.create(admin.token(), name);
        later.release();
        HttpResponse<String> lost = b.get(TestGroupBarrier.WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);

        assertThat(a.statusCode()).isEqualTo(201);
        assertThat(lost.statusCode()).isEqualTo(409);
        assertThat(recorder.maxHeldByOneThread()).isEqualTo(2);
    }
}
