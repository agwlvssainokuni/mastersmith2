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

import cherry.mastersmith.audit.testsupport.AuditWriteReorderBarrierConfig;
import cherry.mastersmith.audit.testsupport.AuditWriteReorderBarrierConfig.AuditWriteReorderBarrier;
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
 * 同じロールの同時の保存（{@code reliability-design.md} 4.2 の #7、BR4.9・BR8.1、NFR3.1 (c)、AC1.2.12）と、削除と割り当て（#1 の2つの順、
 * NFR3.1 (a)、AC1.1.6）・同じ組の割り当て（#4、NFR3.1 (g)、AC2.2.15。B5）の結合テスト。重なりは待ち合わせの
 * 口（{@link TestRoleBarrier}）で作り、合否は経過の時間ではなく状態コード・最後の値・監査の行で決める。後の側が先の側の確定を 3 秒以内に
 * 見られなかったときの {@code ROLE_BUSY} も負けの code として受け入れる（4.2 の注）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class, AuditWriteReorderBarrierConfig.class})
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
    AuditWriteReorderBarrier reorderBarrier;

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
        // テストが落ちても、監査の書き込みの直前で止めたスレッドを必ず放す。
        reorderBarrier.disarm();
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

    /** #7 で作る、2つの保存の監査の書き込みの順。 */
    private enum AuditOrder {
        /** 先の保存の監査が先に書かれる（自然の順）。 */
        NATURAL,
        /** 後の保存の監査が先に書かれる（入れ替えた順）。 */
        REORDERED
    }

    @Test
    @DisplayName("#7 two saves of the same role are serialized in the natural audit order: the later value wins and"
            + " both audits chain the values")
    void sameRoleSavedTwiceInNaturalAuditOrder() throws Exception {
        sameRoleSavedTwice(AuditOrder.NATURAL);
    }

    @Test
    @DisplayName("#7 two saves of the same role are serialized with the audit writes reordered: the later value wins"
            + " and both audits chain the values")
    void sameRoleSavedTwiceWithAuditWritesReordered() throws Exception {
        sameRoleSavedTwice(AuditOrder.REORDERED);
    }

    /**
     * 同じロールの2つの保存を重ね、監査の2行の書き込みの順を待ち合わせで決めて確かめる（Intent 261016-business-data-views の U5、
     * BR1.1〜BR1.5）。2行は行の ID の順ではなく中身（前と後の主権限）で見分ける。
     *
     * <p>止める相手と放す相手は {@link TestRoleBarrier} の印で特定する。先の保存のスレッドは排他の直後で止まったスレッド、後の保存の
     * スレッドは、先の保存が止まった後に作った排他の直前の知らせを最初に通ったスレッド（先の保存も排他の直前を通るため、知らせは先の保存が
     * 止まった後に作る）。知らせが保証するのは、後の保存が排他の直前の点を通ったことまでで、排他を待っていることまでは保証しない
     * （このテストは監査の書き込みの順と中身の確かめで、排他の待ちが働いたことの確かめではない）。
     *
     * @param order 監査の書き込みの順
     */
    private void sameRoleSavedTwice(AuditOrder order) throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("同時の保存"));
        String key = String.valueOf(roleId);
        String readBody = saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", null, null)));
        String fullBody = saveBody("SALES", null, List.of(entry("SALES", null, null, "FULL", null, null)));
        Gate first = barrier.hold(Point.AFTER_LOCK, RoleOperation.CHANGE_PERMISSIONS, key);

        CompletableFuture<HttpResponse<String>> a = async(() -> api.save(admin.token(), roleId, readBody));
        first.awaitArrival();
        Signal second = barrier.signal(Point.BEFORE_LOCK, RoleOperation.CHANGE_PERMISSIONS, key);
        CompletableFuture<HttpResponse<String>> b = async(() -> api.save(admin.token(), roleId, fullBody));
        second.awaitPassed();
        Thread firstThread = first.arrivedThread();
        Thread secondThread = second.passedThread();
        if (order == AuditOrder.REORDERED) {
            reorderBarrier.armFor(firstThread, secondThread, TestRoleBarrier.WAIT_LIMIT);
        } else {
            reorderBarrier.armFor(secondThread, firstThread, TestRoleBarrier.WAIT_LIMIT);
        }
        first.release();

        // 止めた側の上限と結果を待つ上限が重ならないよう、放す相手の結果を先に待つ。
        HttpResponse<String> later;
        HttpResponse<String> earlier;
        if (order == AuditOrder.REORDERED) {
            later = result(b);
            earlier = result(a);
        } else {
            earlier = result(a);
            later = result(b);
        }
        // どちらの順でも、書き込みの順が作れたことと 409 でないことを、ほかの確かめより前に確かめる（計画の D1: A を自然の順にも
        // 当てる。コードのレビューの R-02）。409 ROLE_BUSY は書き込みの順を作れなかった回として失敗にし、合格に数えない。
        assertReordered();
        assertThat(later.statusCode())
                .as("後の保存が 409 で返り、書き込みの順を作れなかった: code=%s", codeOf(later))
                .isNotEqualTo(409);
        assertThat(earlier.statusCode()).isEqualTo(204);
        assertThat(later.statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject(
                        "SELECT main_permission FROM permission_settings WHERE role_id = ?", String.class, roleId))
                .isEqualTo("FULL");
        List<AuditRow> rows = jdbc.query(
                "SELECT audit_event_id, detail FROM audit_events WHERE event_type = 'ROLE_PERMISSION_CHANGED'"
                        + " AND target_role_id = ?",
                (rs, rowNum) -> new AuditRow(rs.getLong("audit_event_id"), rs.getString("detail")),
                roleId);
        List<String> details = rows.stream().map(AuditRow::detail).toList();
        assertThat(rows).as("監査の行: %s", details).hasSize(2);
        List<AuditRow> toRead =
                rows.stream().filter(row -> row.changes("null", "\"READ\"")).toList();
        List<AuditRow> toFull =
                rows.stream().filter(row -> row.changes("\"READ\"", "\"FULL\"")).toList();
        assertThat(toRead).as("前が空・後が READ の行: %s", details).hasSize(1);
        assertThat(toFull).as("前が READ・後が FULL の行: %s", details).hasSize(1);
        // 待ち合わせが意図した書き込みの順を作れたことの確かめ（BR1.5。合否の判定の BR1.1 とは別）。
        if (order == AuditOrder.REORDERED) {
            assertThat(toFull.getFirst().id())
                    .as("後: FULL の行が先に書かれた")
                    .isLessThan(toRead.getFirst().id());
        } else {
            assertThat(toRead.getFirst().id())
                    .as("後: READ の行が先に書かれた")
                    .isLessThan(toFull.getFirst().id());
        }
    }

    /** 待ち合わせが止める相手を止め、上限ではなく放す相手の書き込みの終わりで放したこと。 */
    private void assertReordered() {
        assertThat(reorderBarrier.heldArrived()).as("止める相手が監査の書き込みの直前に来た").isTrue();
        assertThat(reorderBarrier.releasedByTimeout())
                .as("止める相手を上限で放した（書き込みの順を作れなかった）")
                .isFalse();
    }

    private static Object codeOf(HttpResponse<String> response) {
        return response.statusCode() == 409 ? HttpTestClient.json(response).get("code") : null;
    }

    /**
     * 監査の1行（ID と detail だけ）。
     *
     * @param id 監査の行の ID
     * @param detail 前と後の権限の値
     */
    private record AuditRow(long id, String detail) {

        boolean changes(String beforeMain, String afterMain) {
            return detail.contains("\"before\":{\"main\":" + beforeMain)
                    && detail.contains("\"after\":{\"main\":" + afterMain);
        }
    }

    private long user(String name) {
        return new RoleActors(userAccountService, revocationService, transactionManager, port)
                .user(name)
                .userId();
    }

    @Test
    @DisplayName("#1 a delete holding the role row first wins and the assignment is ROLE_NOT_FOUND (B5)")
    void deleteBeforeAssign() throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("削除が先"));
        long userId = user("削除が先 一郎");
        Gate deleting = barrier.hold(Point.AFTER_LOCK, RoleOperation.DELETE, String.valueOf(roleId));
        Signal assigning = barrier.signal(Point.BEFORE_LOCK, RoleOperation.ASSIGN, String.valueOf(roleId));

        CompletableFuture<HttpResponse<String>> delete = async(() -> api.delete(admin.token(), roleId));
        deleting.awaitArrival();
        CompletableFuture<HttpResponse<String>> assign = async(() -> api.assignUser(admin.token(), roleId, userId));
        assigning.awaitPassed();
        deleting.release();

        assertThat(result(delete).statusCode()).isEqualTo(204);
        HttpResponse<String> loser = result(assign);
        assertThat(loser.statusCode()).as(loser.body()).isIn(404, 409);
        assertThat(HttpTestClient.json(loser).get("code")).isIn("ROLE_NOT_FOUND", "ROLE_BUSY");
        assertThat(fixtures.roleRows(roleId)).isZero();
        assertThat(fixtures.assignmentRows(roleId)).isZero();
    }

    @Test
    @DisplayName("#1 an assignment holding the role row first wins and the delete is ROLE_IN_USE (B5)")
    void assignBeforeDelete() throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("割り当てが先"));
        long userId = user("割り当てが先 一郎");
        Gate assigning = barrier.hold(Point.AFTER_LOCK, RoleOperation.ASSIGN, String.valueOf(roleId));
        Signal deleting = barrier.signal(Point.BEFORE_LOCK, RoleOperation.DELETE, String.valueOf(roleId));

        CompletableFuture<HttpResponse<String>> assign = async(() -> api.assignUser(admin.token(), roleId, userId));
        assigning.awaitArrival();
        CompletableFuture<HttpResponse<String>> delete = async(() -> api.delete(admin.token(), roleId));
        deleting.awaitPassed();
        assigning.release();

        assertThat(result(assign).statusCode()).isEqualTo(204);
        HttpResponse<String> loser = result(delete);
        assertThat(loser.statusCode()).as(loser.body()).isEqualTo(409);
        assertThat(HttpTestClient.json(loser).get("code")).isIn("ROLE_IN_USE", "ROLE_BUSY");
        assertThat(fixtures.roleRows(roleId)).isOne();
        assertThat(fixtures.assignmentRows(roleId)).isOne();
    }

    @Test
    @DisplayName("#4 two assignments of the same pair leave one row, the later is ROLE_NO_CHANGE with an audit (B5)")
    void samePairTwice() throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("同じ組"));
        long userId = user("同じ組 一郎");
        Gate first = barrier.hold(Point.AFTER_WRITE, RoleOperation.ASSIGN, roleId + ":U" + userId);
        Signal second = barrier.signal(Point.BEFORE_LOCK, RoleOperation.ASSIGN, String.valueOf(roleId));
        int failuresBefore = auditRows("ROLE_ASSIGNED", "FAILURE");

        CompletableFuture<HttpResponse<String>> a = async(() -> api.assignUser(admin.token(), roleId, userId));
        first.awaitArrival();
        CompletableFuture<HttpResponse<String>> b = async(() -> api.assignUser(admin.token(), roleId, userId));
        second.awaitPassed();
        first.release();

        assertThat(result(a).statusCode()).isEqualTo(204);
        HttpResponse<String> later = result(b);
        assertThat(later.statusCode()).as(later.body()).isEqualTo(409);
        assertThat(fixtures.assignmentRows(roleId)).isOne();
        if ("ROLE_BUSY".equals(HttpTestClient.json(later).get("code"))) {
            assertThat(auditRows("ROLE_ASSIGNED", "FAILURE")).isEqualTo(failuresBefore);
            return;
        }
        assertCode(later, 409, "ROLE_NO_CHANGE");
        assertThat(auditRows("ROLE_ASSIGNED", "FAILURE")).isEqualTo(failuresBefore + 1);
    }
}
