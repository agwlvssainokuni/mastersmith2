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
package cherry.mastersmith.useradmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.TestLoginAttemptBarrier;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.domain.AdminOperation;
import cherry.mastersmith.useradmin.domain.RejectionReason;
import cherry.mastersmith.useradmin.testsupport.TestUserAdminBarrier;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 最後の有効な管理者の保護と、操作した人の確かめ直しの同時の重なりの結合テスト（Intent 260930-user-admin の U3、AC2.1.6・AC2.1.12・
 * AC3.1.5、BR3.1・BR3.2・BR2.5、NFR4.1・NFR4.5・NFR1.4、{@code reliability-design.md} 2.2 の表、確かめ 1b の本番版）。
 *
 * <p>重なりはスレッドの数に頼らず、待ち合わせの口（{@link TestUserAdminBarrier}・{@link TestLoginAttemptBarrier}）で1つ目を止め、2つ目が
 * 行の排他の待ちに入ったこと（H2 のセッションの一覧）を確かめてから1つ目を進める。止める時間の上限は 3000 ミリ秒より短い（2000 ミリ秒）。
 * 内部DB はクラスで共有するため、各テストの初めに既存の管理者の印をすべて外す。
 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestUserAdminBarrier.Config.class, TestLoginAttemptBarrier.Config.class})
class UserAdminConcurrencyIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.80", "IT", null);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserAdminService service;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    RefreshTokenRevocationService revocations;

    @Autowired
    TestUserAdminBarrier barrier;

    @Autowired
    TestLoginAttemptBarrier loginBarrier;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    private UserAdminFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new UserAdminFixtures(userAccountService, revocations, transactionManager, jdbc, 0);
        fixtures.demoteAllAdmins();
    }

    @AfterEach
    void tearDown() {
        barrier.reset();
        loginBarrier.reset();
    }

    private long user(String label, boolean admin) {
        return fixtures.create(label + "-" + UserAdminFixtures.marker() + "@example.com", "同時 " + label, admin);
    }

    /** 監査の行（種類・結果・理由・操作した人・対象）を、対象の利用者で絞って古い順に返す。 */
    private List<Map<String, Object>> auditRows(long... targets) {
        StringBuilder in = new StringBuilder();
        Object[] args = new Object[targets.length];
        for (int i = 0; i < targets.length; i++) {
            in.append(i == 0 ? "?" : ", ?");
            args[i] = targets[i];
        }
        return jdbc.queryForList(
                "SELECT event_type, result, failure_reason, actor_user_id, target_user_id FROM audit_events"
                        + " WHERE target_user_id IN (" + in + ") ORDER BY audit_event_id",
                args);
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(30, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("two admins revoking each other leave exactly one active admin, with one success and one LAST_ADMIN")
    void mutualRevoke() throws Exception {
        long a = user("a", true);
        long b = user("b", true);
        CompletableFuture<OperationResult> second =
                barrier.whileCounting(AdminOperation.REVOKE_ADMIN, b, () -> service.revokeAdmin(b, ORIGIN, a));

        OperationResult first = service.revokeAdmin(a, ORIGIN, b);

        assertThat(barrier.secondDoneAtRelease()).isFalse();
        assertThat(first).isEqualTo(new OperationResult.Done());
        assertThat(await(second)).isEqualTo(new OperationResult.Rejected(RejectionReason.LAST_ACTIVE_ADMIN));
        assertThat(fixtures.activeAdminIds()).containsExactly(a);
        assertThat(auditRows(a, b))
                .extracting(row -> row.get("EVENT_TYPE") + "/" + row.get("RESULT") + "/" + row.get("FAILURE_REASON"))
                .containsExactlyInAnyOrder(
                        "USER_ADMIN_REVOKED/SUCCESS/null", "USER_ADMIN_REVOKED/FAILURE/LAST_ACTIVE_ADMIN");
    }

    @Test
    @DisplayName("one admin revokes while the other suspends: exactly one active admin remains")
    void revokeAgainstSuspend() throws Exception {
        long a = user("a", true);
        long b = user("b", true);
        CompletableFuture<OperationResult> second =
                barrier.whileCounting(AdminOperation.REVOKE_ADMIN, b, () -> service.suspend(b, ORIGIN, a));

        OperationResult first = service.revokeAdmin(a, ORIGIN, b);

        assertThat(barrier.secondDoneAtRelease()).isFalse();
        assertThat(first).isEqualTo(new OperationResult.Done());
        assertThat(await(second)).isEqualTo(new OperationResult.Rejected(RejectionReason.LAST_ACTIVE_ADMIN));
        assertThat(fixtures.activeAdminIds()).containsExactly(a);
        assertThat(fixtures.userColumns(a)).containsEntry("SUSPENDED", false);
        assertThat(auditRows(a, b))
                .extracting(row -> row.get("EVENT_TYPE") + "/" + row.get("RESULT") + "/" + row.get("FAILURE_REASON"))
                .containsExactlyInAnyOrder(
                        "USER_ADMIN_REVOKED/SUCCESS/null", "USER_SUSPENDED/FAILURE/LAST_ACTIVE_ADMIN");
    }

    @Test
    @DisplayName("two admins suspending each other leave exactly one active admin")
    void mutualSuspend() throws Exception {
        long a = user("a", true);
        long b = user("b", true);
        CompletableFuture<OperationResult> second =
                barrier.whileCounting(AdminOperation.SUSPEND, b, () -> service.suspend(b, ORIGIN, a));

        OperationResult first = service.suspend(a, ORIGIN, b);

        assertThat(barrier.secondDoneAtRelease()).isFalse();
        assertThat(first).isEqualTo(new OperationResult.Done());
        assertThat(await(second)).isEqualTo(new OperationResult.Rejected(RejectionReason.LAST_ACTIVE_ADMIN));
        assertThat(fixtures.activeAdminIds()).containsExactly(a);
        assertThat(auditRows(a, b))
                .extracting(row -> row.get("EVENT_TYPE") + "/" + row.get("RESULT") + "/" + row.get("FAILURE_REASON"))
                .containsExactlyInAnyOrder("USER_SUSPENDED/SUCCESS/null", "USER_SUSPENDED/FAILURE/LAST_ACTIVE_ADMIN");
    }

    @Test
    @DisplayName("an admin flag granted while waiting is counted after the lock (check 1b in production code)")
    void grantedWhileWaitingIsCounted() throws Exception {
        long a = user("a", true);
        long c = user("c", false);
        CompletableFuture<OperationResult> second =
                barrier.whileCounting(AdminOperation.GRANT_ADMIN, c, () -> service.revokeAdmin(c, ORIGIN, a));

        OperationResult first = service.grantAdmin(a, ORIGIN, c);

        assertThat(barrier.secondDoneAtRelease()).isFalse();
        assertThat(first).isEqualTo(new OperationResult.Done());
        assertThat(await(second)).isEqualTo(new OperationResult.Done());
        assertThat(fixtures.activeAdminIds()).containsExactly(c);
    }

    @Test
    @DisplayName("an operator whose flag is removed while waiting is refused with NOT_ADMIN on a flag operation")
    void operatorRevokedWhileWaitingOnFlagOperation() throws Exception {
        long a = user("a", true);
        long x = user("x", true);
        long t = user("t", false);
        CompletableFuture<OperationResult> second =
                barrier.whileCounting(AdminOperation.REVOKE_ADMIN, x, () -> service.grantAdmin(x, ORIGIN, t));

        OperationResult first = service.revokeAdmin(a, ORIGIN, x);

        assertThat(barrier.secondDoneAtRelease()).isFalse();
        assertThat(first).isEqualTo(new OperationResult.Done());
        assertThat(await(second)).isEqualTo(new OperationResult.OperatorNotAdmin());
        assertThat(fixtures.userColumns(t)).containsEntry("ADMIN_FLAG", false);
        assertThat(auditRows(t))
                .extracting(row -> row.get("EVENT_TYPE") + "/" + row.get("RESULT") + "/" + row.get("FAILURE_REASON"))
                .containsExactly("USER_ADMIN_GRANTED/FAILURE/NOT_ADMIN");
    }

    @Test
    @DisplayName("an operator whose flag is removed while waiting is refused with NOT_ADMIN on resume")
    void operatorRevokedWhileWaitingOnResume() throws Exception {
        long a = user("a", true);
        long x = user("x", true);
        long t = user("t", true);
        fixtures.suspend(t);
        CompletableFuture<OperationResult> second =
                barrier.whileCounting(AdminOperation.REVOKE_ADMIN, x, () -> service.resume(x, ORIGIN, t));

        OperationResult first = service.revokeAdmin(a, ORIGIN, x);

        assertThat(barrier.secondDoneAtRelease()).isFalse();
        assertThat(first).isEqualTo(new OperationResult.Done());
        assertThat(await(second)).isEqualTo(new OperationResult.OperatorNotAdmin());
        assertThat(fixtures.userColumns(t)).containsEntry("SUSPENDED", true);
        assertThat(auditRows(t))
                .extracting(row -> row.get("EVENT_TYPE") + "/" + row.get("RESULT") + "/" + row.get("FAILURE_REASON"))
                .containsExactly("USER_RESUMED/FAILURE/NOT_ADMIN");
    }

    @Test
    @DisplayName("an operator whose flag is removed while the reset holds the lock state row is refused with NOT_ADMIN")
    void operatorRevokedWhileResetting() throws Exception {
        long a = user("a", true);
        long x = user("x", true);
        long t = user("t", false);
        fixtures.lockState(t, 3, null);
        CompletableFuture<OperationResult> second =
                loginBarrier.whileLocked(t, () -> service.revokeAdmin(a, ORIGIN, x));

        OperationResult first = service.resetLoginFailures(x, ORIGIN, t);

        assertThat(loginBarrier.secondDoneAtRelease()).isTrue();
        assertThat(await(second)).isEqualTo(new OperationResult.Done());
        assertThat(first).isEqualTo(new OperationResult.OperatorNotAdmin());
        assertThat(fixtures.lockColumns(t)).containsEntry("CONSECUTIVE_FAILURES", 3);
        assertThat(auditRows(t))
                .extracting(row -> row.get("EVENT_TYPE") + "/" + row.get("RESULT") + "/" + row.get("FAILURE_REASON"))
                .containsExactly("LOGIN_FAILURES_RESET/FAILURE/NOT_ADMIN");
    }
}
