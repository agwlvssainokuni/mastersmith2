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
import cherry.mastersmith.group.testsupport.TestGroupBarrier.Signal;
import cherry.mastersmith.group.testsupport.TestGroupDeletionGuard;
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
 * グループの行の排他による同時の重なり（{@code reliability-design.md} 4.2 の #1・#5・#6、BR5.1〜BR5.3、NFR3.1・NFR3.3、AC2.1.5）の
 * 結合テスト。重なりはスレッドの数に頼らず、待ち合わせの口（{@link TestGroupBarrier}）で作る。合否は経過の時間ではなく、状態コードと
 * code・状態・監査の行で決める。
 *
 * <p>#1・#5 では、後の側が排他の待ちに入る前に先の側が放されうる（後の側が {@code beforeLock} を通った後、排他を取りに行く前に先の側が
 * 確定することがある）。どちらの順でも期待（最新の状態での判定）は同じで合否は決定的だが、「後の側が排他の待ちを通った」ことまでは保証
 * しない。待つ経路の確かめは #6（先の側を放さずに行の上限切れまで待たせる）が受け持つ（NFR 設計の読み直しの R-02 の残り）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
class GroupConcurrencyIT {

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

    private long group() {
        HttpResponse<String> created = api.create(admin.token(), GroupFixtures.uniqueName("重なり"));
        return ((Number) HttpTestClient.json(created).get("groupId")).longValue();
    }

    private int groupRows(long groupId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM groups WHERE group_id = ?", Integer.class, groupId);
    }

    private int memberRows(long groupId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM group_members WHERE group_id = ?", Integer.class, groupId);
    }

    private int auditRows(String eventType, long groupId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = ? AND target_group_id = ?",
                Integer.class,
                eventType,
                groupId);
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(HttpTestClient.json(response)).containsEntry("code", code);
    }

    @Test
    @DisplayName("#1 a deletion held after its lock wins: the member addition then finds no group, nothing is left")
    void deleteThenAdd() throws Exception {
        long groupId = group();
        Actor user = actors.user("見本 重なり");
        String key = String.valueOf(groupId);
        Gate deleting = barrier.hold(Point.AFTER_LOCK, GroupOperation.DELETE, key);
        Signal adding = barrier.signal(Point.BEFORE_LOCK, GroupOperation.ADD_MEMBER, key);

        CompletableFuture<HttpResponse<String>> delete = async(() -> api.delete(admin.token(), groupId));
        deleting.awaitArrival();
        CompletableFuture<HttpResponse<String>> add = async(() -> api.addMember(admin.token(), groupId, user.userId()));
        adding.awaitPassed();
        deleting.release();

        assertThat(result(delete).statusCode()).isEqualTo(204);
        assertCode(result(add), 404, "GROUP_NOT_FOUND");
        assertThat(groupRows(groupId)).isZero();
        assertThat(memberRows(groupId)).as("グループが無ければメンバーは 0").isZero();
    }

    @Test
    @DisplayName("#1 a member addition held after its lock wins: the deletion then sees the member and is in use")
    void addThenDelete() throws Exception {
        long groupId = group();
        Actor user = actors.user("見本 重なり");
        String key = String.valueOf(groupId);
        Gate adding = barrier.hold(Point.AFTER_LOCK, GroupOperation.ADD_MEMBER, key);
        Signal deleting = barrier.signal(Point.BEFORE_LOCK, GroupOperation.DELETE, key);

        CompletableFuture<HttpResponse<String>> add = async(() -> api.addMember(admin.token(), groupId, user.userId()));
        adding.awaitArrival();
        CompletableFuture<HttpResponse<String>> delete = async(() -> api.delete(admin.token(), groupId));
        deleting.awaitPassed();
        adding.release();

        assertThat(result(add).statusCode()).isEqualTo(204);
        HttpResponse<String> refused = result(delete);
        assertCode(refused, 409, "GROUP_IN_USE");
        assertThat(HttpTestClient.json(refused)).containsEntry("members", 1);
        assertThat(groupRows(groupId)).as("メンバーが残ればグループは残る").isEqualTo(1);
        assertThat(memberRows(groupId)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "#5 the same member added twice: the later one sees the committed member and is no change, audited once")
    void sameMemberTwice() throws Exception {
        long groupId = group();
        Actor user = actors.user("見本 二重");
        String memberKey = groupId + ":" + user.userId();
        Gate first = barrier.hold(Point.AFTER_WRITE, GroupOperation.ADD_MEMBER, memberKey);
        Signal second = barrier.signal(Point.BEFORE_LOCK, GroupOperation.ADD_MEMBER, String.valueOf(groupId));

        CompletableFuture<HttpResponse<String>> a = async(() -> api.addMember(admin.token(), groupId, user.userId()));
        first.awaitArrival();
        CompletableFuture<HttpResponse<String>> b = async(() -> api.addMember(admin.token(), groupId, user.userId()));
        second.awaitPassed();
        first.release();

        assertThat(result(a).statusCode()).isEqualTo(204);
        assertCode(result(b), 409, "GROUP_NO_CHANGE");
        assertThat(memberRows(groupId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'GROUP_MEMBER_ADDED'"
                                + " AND target_group_id = ? AND result = 'FAILURE' AND failure_reason = 'NO_CHANGE'",
                        Integer.class,
                        groupId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("#6 a group row held past the 3 second limit makes the other operations GROUP_BUSY without audit")
    void rowLockTimeout() throws Exception {
        long groupId = group();
        Actor user = actors.user("見本 待ち");
        String originalName = jdbc.queryForObject("SELECT name FROM groups WHERE group_id = ?", String.class, groupId);
        Gate holding = barrier.hold(Point.AFTER_LOCK, GroupOperation.RENAME, String.valueOf(groupId));
        String renamed = GroupFixtures.uniqueName("待ち 改名");

        CompletableFuture<HttpResponse<String>> rename = async(() -> api.rename(admin.token(), groupId, renamed));
        holding.awaitArrival();
        HttpResponse<String> delete = api.delete(admin.token(), groupId);
        HttpResponse<String> add = api.addMember(admin.token(), groupId, user.userId());
        assertThat(rename).as("先の側は放すまで終わらない").isNotDone();
        holding.release();

        assertCode(delete, 409, "GROUP_BUSY");
        assertCode(add, 409, "GROUP_BUSY");
        assertThat(result(rename).statusCode()).isEqualTo(204);
        assertThat(groupRows(groupId)).isEqualTo(1);
        assertThat(memberRows(groupId)).isZero();
        assertThat(auditRows("GROUP_DELETED", groupId)).as("BUSY は監査に残さない").isZero();
        assertThat(auditRows("GROUP_MEMBER_ADDED", groupId)).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM groups WHERE group_id = ?", String.class, groupId))
                .isEqualTo(renamed)
                .isNotEqualTo(originalName);
    }
}
