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
import java.util.Locale;
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
 * 同じ名前の同時の作成と、同じ名前への同時の変更（{@code reliability-design.md} 4.2 の #2〜#4、捨ての試しの T1・T2、BR1.4・BR5.2・
 * BR5.5・BR5.7、NFR3.1・NFR3.3・NFR3.4）の結合テスト。
 *
 * <p>違反は先の側を確定させてから後の側を書かせる「待たない違反」（後の側は書く前の判定の後で止める）、上限切れは先の側を書いた後の
 * 未確定のまま放さずに、後の側を名前の鍵の待ちの H2 の上限（約 2 秒）で切らせる形で作る。合否は経過の時間に頼らず、409 と code・
 * 状態が変わらないことで決める（監査の行は {@code GroupConflictAuditIT}）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
class GroupNameConflictIT {

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

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new GroupApi(port);
        admin = new GroupActors(userAccountService, revocationService, transactionManager, port).admin();
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

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private int groupsWithKey(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM groups WHERE name_key = ?", Integer.class, key(name));
    }

    private String nameOf(long groupId) {
        return jdbc.queryForObject("SELECT name FROM groups WHERE group_id = ?", String.class, groupId);
    }

    private long group(String name) {
        HttpResponse<String> created = api.create(admin.token(), name);
        return ((Number) HttpTestClient.json(created).get("groupId")).longValue();
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(HttpTestClient.json(response)).containsEntry("code", code);
    }

    @Test
    @DisplayName(
            "#2 a creation that checked before the other committed hits the unique violation: 409 duplicate, one group")
    void createViolationWithoutWaiting() throws Exception {
        String name = GroupFixtures.uniqueName("Sales");
        Gate later = barrier.hold(Point.AFTER_CHECK, GroupOperation.CREATE, key(name));

        CompletableFuture<HttpResponse<String>> b =
                async(() -> api.create(admin.token(), name.toUpperCase(Locale.ROOT)));
        later.awaitArrival();
        HttpResponse<String> a = api.create(admin.token(), name);
        later.release();

        assertThat(a.statusCode()).isEqualTo(201);
        assertCode(result(b), 409, "GROUP_NAME_DUPLICATE");
        assertThat(groupsWithKey(name)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "#3 a creation that waits on an uncommitted same key past the limit is GROUP_BUSY, the first one is created")
    void createKeyTimeout() throws Exception {
        String name = GroupFixtures.uniqueName("Busy");
        Gate first = barrier.hold(Point.AFTER_WRITE, GroupOperation.CREATE, key(name));

        CompletableFuture<HttpResponse<String>> a = async(() -> api.create(admin.token(), name));
        first.awaitArrival();
        HttpResponse<String> b = api.create(admin.token(), name.toUpperCase(Locale.ROOT));
        assertThat(a).as("先の側は放すまで終わらない").isNotDone();
        first.release();

        assertCode(b, 409, "GROUP_BUSY");
        assertThat(result(a).statusCode()).isEqualTo(201);
        assertThat(groupsWithKey(name)).isEqualTo(1);
    }

    @Test
    @DisplayName("#4 renaming two groups to the same name: the later checker gets a duplicate and keeps its old name")
    void renameViolationWithoutWaiting() throws Exception {
        long g1 = group(GroupFixtures.uniqueName("改名 一"));
        String g2Name = GroupFixtures.uniqueName("改名 二");
        long g2 = group(g2Name);
        String target = GroupFixtures.uniqueName("改名 先");
        Gate later = barrier.hold(Point.AFTER_CHECK, GroupOperation.RENAME, key(target));

        CompletableFuture<HttpResponse<String>> b = async(() -> api.rename(admin.token(), g2, target));
        later.awaitArrival();
        HttpResponse<String> a = api.rename(admin.token(), g1, target);
        later.release();

        assertThat(a.statusCode()).isEqualTo(204);
        assertCode(result(b), 409, "GROUP_NAME_DUPLICATE");
        assertThat(nameOf(g1)).isEqualTo(target);
        assertThat(nameOf(g2)).isEqualTo(g2Name);
    }

    @Test
    @DisplayName("#4 renaming to a name held uncommitted by another rename past the limit is GROUP_BUSY")
    void renameKeyTimeout() throws Exception {
        long g1 = group(GroupFixtures.uniqueName("待ち 一"));
        String g2Name = GroupFixtures.uniqueName("待ち 二");
        long g2 = group(g2Name);
        String target = GroupFixtures.uniqueName("待ち 先");
        Gate first = barrier.hold(Point.AFTER_WRITE, GroupOperation.RENAME, key(target));

        CompletableFuture<HttpResponse<String>> a = async(() -> api.rename(admin.token(), g1, target));
        first.awaitArrival();
        HttpResponse<String> b = api.rename(admin.token(), g2, target);
        first.release();

        assertCode(b, 409, "GROUP_BUSY");
        assertThat(result(a).statusCode()).isEqualTo(204);
        assertThat(nameOf(g1)).isEqualTo(target);
        assertThat(nameOf(g2)).isEqualTo(g2Name);
    }
}
