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
package cherry.mastersmith.role.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreUnexpectedException;
import cherry.mastersmith.role.testsupport.FailingSettingTrigger;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.role.testsupport.TestRoleBarrier;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Gate;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Point;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

/**
 * 確かめと適用の入口と、適用中のロールの排他の結合テスト（{@code reliability-design.md} 2.6 と 4.2 の #11・#12、NFR3.1 (h)・NFR3.3、
 * 計画の D-20）。重なりは待ち合わせの口（{@link TestRoleBarrier}）で作り、合否は結果の型・状態・WARN で決める（経過の時間で決めない）。
 */
@SpringBootTest
@Import(TestRoleBarrier.Config.class)
class RoleTransferBusyIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.72", "Mozilla/5.0", "trace-0072");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleTransferService service;

    @Autowired
    RoleAssignmentService assignment;

    @Autowired
    ActiveDslModelHolder holder;

    @Autowired
    TestRoleBarrier barrier;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private RoleFixtures roles;

    private GroupFixtures groups;

    private long actor;

    private long roleId;

    private String yaml;

    @BeforeEach
    void setUp() {
        roles = new RoleFixtures(jdbc);
        groups = new GroupFixtures(users, jdbc);
        actor = groups.user("入口 確かめ一郎");
        RoleDslFixture.install(holder, RoleDslFixture.sample());
        String name = RoleFixtures.uniqueName("入口");
        roleId = roles.role(name);
        roles.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        yaml = "version: 1\nroles:\n  \"" + name + "\":\n    schemas:\n      SALES: {main: FULL}\n";
    }

    @AfterEach
    void tearDown() {
        barrier.reset();
        executor.shutdownNow();
        FailingSettingTrigger.remove(jdbc);
    }

    private static RoleTransferBody body(String yaml) {
        return new RoleTransferBody(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private String fingerprint() {
        return ((TransferCheckResult.Checked) service.check(body(yaml))).plan().fingerprint();
    }

    private int appliedRows() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ROLE_TRANSFER_APPLIED'", Integer.class);
    }

    private static <T> T result(CompletableFuture<T> future) throws Exception {
        return future.get(TestRoleBarrier.WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("while an apply holds the slot, another check and apply are ROLE_BUSY with one WARN each (#11)")
    void secondCheckAndApplyAreBusy() throws Exception {
        String fingerprint = fingerprint();
        int auditBefore = appliedRows();
        Gate gate = barrier.hold(Point.AFTER_LOCK, RoleOperation.APPLY_TRANSFER, String.valueOf(roleId));
        CompletableFuture<TransferApplyResult> first =
                CompletableFuture.supplyAsync(() -> service.apply(actor, ORIGIN, body(yaml), fingerprint), executor);
        gate.awaitArrival();

        TransferCheckResult check;
        TransferApplyResult apply;
        try (LogEvents logs = LogEvents.capture(RoleTransferSlot.class)) {
            check = service.check(body(yaml));
            apply = service.apply(actor, ORIGIN, body(yaml), fingerprint);

            assertThat(logs.list()).hasSize(2).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getKeyValuePairs())
                        .singleElement()
                        .satisfies(pair ->
                                assertThat(pair.key + "=" + pair.value).isEqualTo("lockKind=ROLE_TRANSFER_SLOT"));
            });
        }
        gate.release();

        assertThat(check).isInstanceOf(TransferCheckResult.Busy.class);
        assertThat(apply).isInstanceOf(TransferApplyResult.Busy.class);
        assertThat(result(first)).isEqualTo(new TransferApplyResult.Applied(0, 1));
        assertThat(appliedRows()).as("2つ目の確かめと適用は監査に残らない").isEqualTo(auditBefore + 1);
    }

    @Test
    @DisplayName("an assignment to a role locked by an apply waits 3 seconds and is ROLE_BUSY without change (#12)")
    void assignmentDuringApplyIsBusy() throws Exception {
        long userId = groups.user("適用中 割り当て一郎");
        String fingerprint = fingerprint();
        Gate gate = barrier.hold(Point.AFTER_LOCK, RoleOperation.APPLY_TRANSFER, String.valueOf(roleId));
        CompletableFuture<TransferApplyResult> applying =
                CompletableFuture.supplyAsync(() -> service.apply(actor, ORIGIN, body(yaml), fingerprint), executor);
        gate.awaitArrival();

        RoleAssignmentResult assigned;
        try (LogEvents logs = LogEvents.capture(RoleStore.class)) {
            assigned = assignment.assignUser(actor, ORIGIN, roleId, userId);

            assertThat(logs.list())
                    .anySatisfy(event -> assertThat(event.getKeyValuePairs())
                            .anySatisfy(pair -> assertThat(pair.key + "=" + pair.value)
                                    .isEqualTo("lockKind=" + RoleStore.ROLE_ROW)));
        }
        gate.release();

        assertThat(assigned).isInstanceOf(RoleAssignmentResult.Busy.class);
        assertThat(result(applying)).isEqualTo(new TransferApplyResult.Applied(0, 1));
        assertThat(roles.assignmentRows(roleId)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT main_permission FROM permission_settings WHERE role_id = ?", String.class, roleId))
                .isEqualTo("FULL");
    }

    @Test
    @DisplayName("after an apply ends with an exception the slot is free and the next apply passes")
    void slotIsReleasedAfterAnException() {
        String failing = yaml.replace(
                "SALES: {main: FULL}",
                "SALES:\n        tables:\n          T:\n            columns:\n" + "              "
                        + FailingSettingTrigger.MARK + ": READ");
        String failingFingerprint = ((TransferCheckResult.Checked) service.check(body(failing)))
                .plan()
                .fingerprint();
        FailingSettingTrigger.install(jdbc);

        assertThatThrownBy(() -> service.apply(actor, ORIGIN, body(failing), failingFingerprint))
                .isInstanceOf(RoleStoreUnexpectedException.class);
        FailingSettingTrigger.remove(jdbc);

        assertThat(service.apply(actor, ORIGIN, body(yaml), fingerprint()))
                .isEqualTo(new TransferApplyResult.Applied(0, 1));
    }
}
