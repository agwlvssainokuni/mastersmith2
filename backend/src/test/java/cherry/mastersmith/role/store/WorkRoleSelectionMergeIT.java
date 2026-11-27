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
package cherry.mastersmith.role.store;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.CommittableWrite;
import cherry.mastersmith.common.testsupport.H2BlockedSessions;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 作業ロールの保存（「あれば更新・無ければ挿入」の共通の手順。Intent 261114-internal-db-postgresql の U2）の同時の実行の確かめ。
 *
 * <p>前の版は H2 の {@code MERGE ... KEY} の待ちの振る舞いの見張りだったが、保存は H2 と PostgreSQL の両方に通る「更新 → 挿入 →
 * 重なりなら更新やり直し」の手順に替わったため、新しい手順の期待を確かめる。別の接続を待ち合わせ（{@code INFORMATION_SCHEMA.SESSIONS}
 * の待ちの印）で確実に作り、実時刻の {@code sleep} は合否に使わない。上限切れの確かめだけ、H2 の待ちの上限（約 4 秒）を超えて持つ。
 */
@SpringBootTest
class WorkRoleSelectionMergeIT {

    private static final OffsetDateTime AT =
            OffsetDateTime.ofInstant(Instant.parse("2026-10-09T01:00:00Z"), ZoneOffset.UTC);

    private static final Instant NOW = Instant.parse("2026-10-09T02:00:00Z");

    /** 待ち合わせの上限（待ちに入るのを待つ。実アプリの待ちの上限（約 4 秒）より十分長い）。 */
    private static final Duration AWAIT = Duration.ofSeconds(30);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleStore store;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    private GroupFixtures groups;

    private RoleFixtures roles;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        groups = new GroupFixtures(users, jdbc);
        roles = new RoleFixtures(jdbc);
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private RoleStoreOutcome<Void> saveInTransaction(long userId, long roleId) {
        return tx.execute(status -> {
            RoleStoreOutcome<Void> outcome = store.saveWorkRoleSelection(userId, roleId, NOW);
            if (!outcome.isDone()) {
                status.setRollbackOnly();
            }
            return outcome;
        });
    }

    private long selectedRole(long userId) {
        return jdbc.queryForObject("SELECT role_id FROM work_role_selections WHERE user_id = ?", Long.class, userId);
    }

    private int selectionRows(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_role_selections WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    @DisplayName("a save racing an uncommitted insert of the same user converges to the later writer's role")
    void laterWriterWinsAfterTheEarlierInsertCommits() throws Exception {
        long userId = groups.user("作業ロール 確かめ一郎");
        long earlierRole = roles.role(RoleFixtures.uniqueName("先の側"));
        long laterRole = roles.role(RoleFixtures.uniqueName("後の側"));

        RoleStoreOutcome<Void> outcome;
        try (CommittableWrite earlier = CommittableWrite.hold(
                TestDatabase.url(tempDir),
                "INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (?, ?, ?)",
                userId,
                earlierRole,
                AT)) {
            Future<RoleStoreOutcome<Void>> later = executor.submit(() -> saveInTransaction(userId, laterRole));
            H2BlockedSessions.awaitBlocked(TestDatabase.url(tempDir), AWAIT);
            earlier.commit();
            outcome = later.get(AWAIT.toSeconds(), TimeUnit.SECONDS);
        }

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(selectionRows(userId)).isOne();
        assertThat(selectedRole(userId)).as("後の側の値が残る").isEqualTo(laterRole);
    }

    @Test
    @DisplayName("a save against an existing row waits for the other transaction's update and then wins")
    void existingRowSerializesAndTheLaterValueRemains() throws Exception {
        long userId = groups.user("作業ロール 確かめ二郎");
        long initialRole = roles.role(RoleFixtures.uniqueName("初めの値"));
        long earlierRole = roles.role(RoleFixtures.uniqueName("先の側の更新"));
        long laterRole = roles.role(RoleFixtures.uniqueName("後の側の更新"));
        jdbc.update(
                "INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (?, ?, ?)",
                userId,
                initialRole,
                AT);

        RoleStoreOutcome<Void> outcome;
        try (CommittableWrite earlier = CommittableWrite.hold(
                TestDatabase.url(tempDir),
                "UPDATE work_role_selections SET role_id = ? WHERE user_id = ?",
                earlierRole,
                userId)) {
            Future<RoleStoreOutcome<Void>> later = executor.submit(() -> saveInTransaction(userId, laterRole));
            H2BlockedSessions.awaitBlocked(TestDatabase.url(tempDir), AWAIT);
            earlier.commit();
            outcome = later.get(AWAIT.toSeconds(), TimeUnit.SECONDS);
        }

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(selectionRows(userId)).isOne();
        assertThat(selectedRole(userId)).as("後の側の値が残る").isEqualTo(laterRole);
    }

    @Test
    @DisplayName("a save held past the lock wait limit is Busy and the same transaction can keep writing")
    void waitTimeoutIsBusyAndTheTransactionStaysUsable(TestReporter reporter) throws Exception {
        long userId = groups.user("作業ロール 確かめ三郎");
        long otherUser = groups.user("作業ロール 確かめ四郎");
        long heldRole = roles.role(RoleFixtures.uniqueName("持たれた値"));
        long otherRole = roles.role(RoleFixtures.uniqueName("続きの値"));

        long[] elapsedNanos = new long[1];
        RoleStoreOutcome<Void> busy;
        RoleStoreOutcome<Void> next;
        try (CommittableWrite earlier = CommittableWrite.hold(
                TestDatabase.url(tempDir),
                "INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (?, ?, ?)",
                userId,
                heldRole,
                AT)) {
            Object[] results = tx.execute(status -> {
                long start = System.nanoTime();
                RoleStoreOutcome<Void> first = store.saveWorkRoleSelection(userId, heldRole, NOW);
                elapsedNanos[0] = System.nanoTime() - start;
                // 待ちの上限切れの後も、同じ接続（トランザクション）で次の文が通る（セーブポイントまで戻っている）。
                RoleStoreOutcome<Void> second = store.saveWorkRoleSelection(otherUser, otherRole, NOW);
                assertThat(selectionRows(otherUser)).isOne();
                status.setRollbackOnly();
                return new Object[] {first, second};
            });
            busy = castOutcome(results[0]);
            next = castOutcome(results[1]);
        }

        reporter.publishEntry("lockWaitMillis", Long.toString(TimeUnit.NANOSECONDS.toMillis(elapsedNanos[0])));
        assertThat(busy).isEqualTo(new RoleStoreOutcome.Busy<>(RoleStore.WORK_ROLE_SELECTION_KEY));
        assertThat(next).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(selectionRows(userId)).isZero();
    }

    @SuppressWarnings("unchecked")
    private static RoleStoreOutcome<Void> castOutcome(Object value) {
        return (RoleStoreOutcome<Void>) value;
    }
}
