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
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.domain.GroupOperation;
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
import java.util.List;
import java.util.Locale;
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
 * 違反を業務の拒否に読み替えたときの監査（BR5.7・BR8.2・BR8.3、NFR3.4、捨ての試しの T5）の結合テスト。違反で負けた側は 500 にならず、
 * 巻き戻した後の2つ目のトランザクションの失敗の出来事で監査に FAILURE がちょうど1行残り、待ちの上限切れ（{@code GROUP_BUSY}）は監査に
 * 残らないことを、監査の行を読んで確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
class GroupConflictAuditIT {

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
    TestGroupBarrier barrier;

    @Autowired
    JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newCachedThreadPool();

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
        executor.shutdownNow();
    }

    private CompletableFuture<HttpResponse<String>> async(Supplier<HttpResponse<String>> call) {
        return CompletableFuture.supplyAsync(call, executor);
    }

    private static HttpResponse<String> result(CompletableFuture<HttpResponse<String>> future) throws Exception {
        return future.get(TestGroupBarrier.WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);
    }

    private long lastAuditId() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(audit_event_id), 0) FROM audit_events", Long.class);
    }

    private List<Map<String, Object>> auditSince(long after) {
        return jdbc.queryForList(
                "SELECT event_type, result, failure_reason, actor_user_id, target_group_id, target_user_id, detail"
                        + " FROM audit_events WHERE audit_event_id > ? ORDER BY audit_event_id",
                after);
    }

    @Test
    @DisplayName("a creation that loses on the unique violation is 409 and leaves exactly one FAILURE row")
    void createViolationIsAuditedOnce() throws Exception {
        String name = GroupFixtures.uniqueName("監査 作成");
        String key = name.toLowerCase(Locale.ROOT);
        Gate later = barrier.hold(Point.AFTER_CHECK, GroupOperation.CREATE, key);
        long before = lastAuditId();

        CompletableFuture<HttpResponse<String>> b = async(() -> api.create(admin.token(), name));
        later.awaitArrival();
        assertThat(api.create(admin.token(), name).statusCode()).isEqualTo(201);
        later.release();
        HttpResponse<String> lost = result(b);

        assertThat(lost.statusCode()).isEqualTo(409);
        assertThat(HttpTestClient.json(lost)).containsEntry("code", "GROUP_NAME_DUPLICATE");
        List<Map<String, Object>> rows = auditSince(before);
        assertThat(rows).extracting(row -> row.get("RESULT")).containsExactlyInAnyOrder("SUCCESS", "FAILURE");
        Map<String, Object> failure = rows.stream()
                .filter(row -> "FAILURE".equals(row.get("RESULT")))
                .findFirst()
                .orElseThrow();
        assertThat(failure)
                .containsEntry("EVENT_TYPE", "GROUP_CREATED")
                .containsEntry("FAILURE_REASON", "GROUP_NAME_DUPLICATE")
                .containsEntry("ACTOR_USER_ID", admin.userId())
                .containsEntry("TARGET_GROUP_ID", null)
                .containsEntry("DETAIL", "{\"name\":\"" + name + "\"}");
    }

    @Test
    @DisplayName("a rename that loses on the unique violation leaves one FAILURE row with the before and after names")
    void renameViolationIsAuditedOnce() throws Exception {
        HttpResponse<String> first = api.create(admin.token(), GroupFixtures.uniqueName("監査 一"));
        long g1 = ((Number) HttpTestClient.json(first).get("groupId")).longValue();
        String g2Name = GroupFixtures.uniqueName("監査 二");
        HttpResponse<String> second = api.create(admin.token(), g2Name);
        long g2 = ((Number) HttpTestClient.json(second).get("groupId")).longValue();
        String target = GroupFixtures.uniqueName("監査 先");
        Gate later = barrier.hold(Point.AFTER_CHECK, GroupOperation.RENAME, target.toLowerCase(Locale.ROOT));
        long before = lastAuditId();

        CompletableFuture<HttpResponse<String>> b = async(() -> api.rename(admin.token(), g2, target));
        later.awaitArrival();
        assertThat(api.rename(admin.token(), g1, target).statusCode()).isEqualTo(204);
        later.release();

        assertThat(result(b).statusCode()).isEqualTo(409);
        List<Map<String, Object>> failures = auditSince(before).stream()
                .filter(row -> "FAILURE".equals(row.get("RESULT")))
                .toList();
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst())
                .containsEntry("EVENT_TYPE", "GROUP_RENAMED")
                .containsEntry("FAILURE_REASON", "GROUP_NAME_DUPLICATE")
                .containsEntry("TARGET_GROUP_ID", g2)
                .containsEntry("DETAIL", "{\"before\":\"" + g2Name + "\",\"after\":\"" + target + "\"}");
    }

    @Test
    @DisplayName("a creation that times out on the key is GROUP_BUSY and leaves no audit row of its own")
    void busyIsNotAudited() throws Exception {
        String name = GroupFixtures.uniqueName("監査 待ち");
        Gate first = barrier.hold(Point.AFTER_WRITE, GroupOperation.CREATE, name.toLowerCase(Locale.ROOT));
        long before = lastAuditId();

        CompletableFuture<HttpResponse<String>> a = async(() -> api.create(admin.token(), name));
        first.awaitArrival();
        HttpResponse<String> busy = api.create(admin.token(), name);
        long afterBusy = lastAuditId();
        first.release();

        assertThat(busy.statusCode()).isEqualTo(409);
        assertThat(HttpTestClient.json(busy)).containsEntry("code", "GROUP_BUSY");
        assertThat(afterBusy).as("BUSY の時点で監査の行は増えていない").isEqualTo(before);
        assertThat(result(a).statusCode()).isEqualTo(201);
        assertThat(auditSince(before))
                .singleElement()
                .satisfies(row -> assertThat(row).containsEntry("RESULT", "SUCCESS"));
    }
}
