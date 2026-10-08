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

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.UncommittedWrite;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.service.RoleFirstStep;
import cherry.mastersmith.role.service.RoleStoreTransactions;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 割り当ての主キーと作業ロールの保存の主キー、割り当ての表の外部キーの結合テスト（{@code reliability-design.md} 4.2 の #6・#9、
 * BR6.3・BR6.7・BR8.4・BR8.5、NFR3.7、計画の 8.3）。
 *
 * <p>本物の H2 で、ロールの行を排他せずに store の割り当ての書き込みを直接呼ぶ（API からは行の排他で並ぶため届かない、最後の守りの
 * 確かめ）。待たない違反は先の側を確定させてから書き、上限切れは先の側を未確定のまま放さずに H2 の上限で切らせる。1つ目の
 * トランザクションは本番と同じ {@link RoleStoreTransactions} を通し、{@code UnexpectedRollbackException} が出ないことを確かめる。合否は
 * 経過の時間ではなく、結果の型・行の数・WARN で決める。
 */
@SpringBootTest
class RoleStoreKeyGuardIT {

    private static final Instant NOW = Instant.parse("2026-10-09T02:00:00Z");

    private static final OffsetDateTime AT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleStoreTransactions transactions;

    @Autowired
    EntityManager entityManager;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures roles;

    private GroupFixtures groups;

    @BeforeEach
    void setUp() {
        roles = new RoleFixtures(jdbc);
        groups = new GroupFixtures(users, jdbc);
    }

    /** ロールの行を排他せずに読み、store を1回呼ぶ（本番の1つ目のトランザクションの入口を通す）。 */
    private <T> RoleStoreOutcome<?> withoutLock(long roleId, BiFunction<RoleStore, Role, RoleStoreOutcome<T>> call) {
        RoleFirstStep<T> step = transactions.inFirst(store -> {
            RoleStoreOutcome<T> outcome = call.apply(store, entityManager.find(Role.class, roleId));
            return outcome.isDone()
                    ? new RoleFirstStep.Done<>(((RoleStoreOutcome.Done<T>) outcome).value())
                    : new RoleFirstStep.Store<>(outcome, null);
        });
        return switch (step) {
            case RoleFirstStep.Done<T>(T value) -> new RoleStoreOutcome.Done<>(value);
            case RoleFirstStep.Store<T>(RoleStoreOutcome<?> outcome, var detail) -> outcome;
            case RoleFirstStep.Rejected<T> rejected -> throw new IllegalStateException(rejected.toString());
        };
    }

    private int userAssignments(long roleId, long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_role_assignments WHERE role_id = ? AND user_id = ?",
                Integer.class,
                roleId,
                userId);
    }

    private int groupAssignments(long roleId, long groupId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_role_assignments WHERE role_id = ? AND group_id = ?",
                Integer.class,
                roleId,
                groupId);
    }

    @Test
    @DisplayName("a second user assignment of a committed pair is AlreadyAssigned without waiting (#6)")
    void userAssignmentViolationWithoutWaiting() {
        long roleId = roles.role(RoleFixtures.uniqueName("主キー"));
        long userId = groups.user("主キー 確かめ一郎");

        assertThat(withoutLock(roleId, (store, role) -> store.assignToUser(role, userId, NOW)))
                .isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(withoutLock(roleId, (store, role) -> store.assignToUser(role, userId, NOW)))
                .isEqualTo(new RoleStoreOutcome.AlreadyAssigned<>());
        assertThat(userAssignments(roleId, userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a second group assignment of a committed pair is AlreadyAssigned without waiting (#6)")
    void groupAssignmentViolationWithoutWaiting() {
        long roleId = roles.role(RoleFixtures.uniqueName("主キー"));
        long groupId = groups.group(GroupFixtures.uniqueName("主キー"));

        assertThat(withoutLock(roleId, (store, role) -> store.assignToGroup(role, groupId, NOW)))
                .isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(withoutLock(roleId, (store, role) -> store.assignToGroup(role, groupId, NOW)))
                .isEqualTo(new RoleStoreOutcome.AlreadyAssigned<>());
        assertThat(groupAssignments(roleId, groupId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a user assignment of a pair held uncommitted is Busy of ROLE_ASSIGNMENT_KEY with one WARN (#6)")
    void userAssignmentWaitTimesOut() throws Exception {
        long roleId = roles.role(RoleFixtures.uniqueName("上限切れ"));
        long userId = groups.user("上限切れ 確かめ一郎");

        RoleStoreOutcome<?> outcome;
        try (LogEvents logs = LogEvents.capture(RoleStore.class);
                UncommittedWrite held = UncommittedWrite.hold(
                        TestDatabase.url(tempDir),
                        "INSERT INTO user_role_assignments (role_id, user_id, assigned_at) VALUES (?, ?, ?)",
                        roleId,
                        userId,
                        AT)) {
            outcome = withoutLock(roleId, (store, role) -> store.assignToUser(role, userId, NOW));

            assertThat(logs.list()).singleElement().satisfies(warn -> {
                assertThat(warn.getLevel()).isEqualTo(Level.WARN);
                assertThat(warn.getThrowableProxy()).isNull();
                assertThat(warn.getKeyValuePairs())
                        .anySatisfy(pair -> assertThat(pair.key + "=" + pair.value)
                                .isEqualTo("lockKind=" + RoleStore.ROLE_ASSIGNMENT_KEY));
            });
        }

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.Busy<>(RoleStore.ROLE_ASSIGNMENT_KEY));
        assertThat(userAssignments(roleId, userId)).isZero();
    }

    @Test
    @DisplayName("saving a work role while another transaction holds the same user's selection is Busy (#9)")
    void selectionWaitTimesOut() throws Exception {
        long roleId = roles.role(RoleFixtures.uniqueName("保存の上限切れ"));
        long userId = groups.user("保存 確かめ一郎");

        RoleStoreOutcome<?> outcome;
        try (LogEvents logs = LogEvents.capture(RoleStore.class);
                UncommittedWrite held = UncommittedWrite.hold(
                        TestDatabase.url(tempDir),
                        "INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (?, ?, ?)",
                        userId,
                        roleId,
                        AT)) {
            outcome = withoutLock(roleId, (store, role) -> store.saveWorkRoleSelection(userId, roleId, NOW));

            assertThat(logs.list())
                    .singleElement()
                    .satisfies(warn -> assertThat(warn.getKeyValuePairs())
                            .anySatisfy(pair -> assertThat(pair.key + "=" + pair.value)
                                    .isEqualTo("lockKind=" + RoleStore.WORK_ROLE_SELECTION_KEY)));
        }

        assertThat(outcome).isEqualTo(new RoleStoreOutcome.Busy<>(RoleStore.WORK_ROLE_SELECTION_KEY));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM work_role_selections WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    @DisplayName("assignments to a missing user, a missing group or a deleted role are Referenced to that side")
    void foreignKeys() {
        long roleId = roles.role(RoleFixtures.uniqueName("外部キー"));
        long goneRoleId = roles.role(RoleFixtures.uniqueName("消えた"));
        long userId = groups.user("外部キー 確かめ一郎");
        Role detached = ((RoleFirstStep.Done<Role>) transactions.<Role>inFirst(
                        store -> new RoleFirstStep.Done<>(entityManager.find(Role.class, goneRoleId))))
                .value();
        jdbc.update("DELETE FROM roles WHERE role_id = ?", goneRoleId);

        assertThat(withoutLock(roleId, (store, role) -> store.assignToUser(role, Long.MAX_VALUE, NOW)))
                .isEqualTo(new RoleStoreOutcome.Referenced<>(Referent.USER));
        assertThat(withoutLock(roleId, (store, role) -> store.assignToGroup(role, Long.MAX_VALUE, NOW)))
                .isEqualTo(new RoleStoreOutcome.Referenced<>(Referent.GROUP));
        assertThat(withoutLock(roleId, (store, role) -> store.saveWorkRoleSelection(Long.MAX_VALUE, roleId, NOW)))
                .isEqualTo(new RoleStoreOutcome.Referenced<>(Referent.USER));
        assertThat(withoutLock(roleId, (store, role) -> store.assignToUser(detached, userId, NOW)))
                .as("消えたロールへの割り当て")
                .isEqualTo(new RoleStoreOutcome.Referenced<>(Referent.ROLE));
        assertThat(userAssignments(roleId, userId)).isZero();
    }

    @Test
    @DisplayName(
            "unassigning reports whether a row was removed, and deleting a role removes the selections pointing at it")
    void unassignAndDelete() {
        long roleId = roles.role(RoleFixtures.uniqueName("外しと削除"));
        long userId = groups.user("外し 確かめ一郎");
        long groupId = groups.group(GroupFixtures.uniqueName("外しと削除"));
        jdbc.update(
                "INSERT INTO user_role_assignments (role_id, user_id, assigned_at) VALUES (?, ?, ?)",
                roleId,
                userId,
                AT);
        jdbc.update(
                "INSERT INTO group_role_assignments (role_id, group_id, assigned_at) VALUES (?, ?, ?)",
                roleId,
                groupId,
                AT);
        jdbc.update(
                "INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (?, ?, ?)", userId, roleId, AT);

        assertThat(withoutLock(roleId, RoleStore::deleteRole))
                .as("割り当てが残るロールの削除は外部キーが最後の守り")
                .isEqualTo(new RoleStoreOutcome.Referenced<>(Referent.ROLE));
        assertThat(roles.roleRows(roleId)).isOne();
        assertThat(withoutLock(roleId, (store, role) -> store.unassignFromUser(role, userId)))
                .isEqualTo(new RoleStoreOutcome.Done<>(true));
        assertThat(withoutLock(roleId, (store, role) -> store.unassignFromUser(role, userId)))
                .isEqualTo(new RoleStoreOutcome.Done<>(false));
        assertThat(withoutLock(roleId, (store, role) -> store.unassignFromGroup(role, groupId)))
                .isEqualTo(new RoleStoreOutcome.Done<>(true));
        assertThat(withoutLock(roleId, (store, role) -> store.unassignFromGroup(role, Long.MAX_VALUE)))
                .isEqualTo(new RoleStoreOutcome.Done<>(false));
        assertThat(jdbc.queryForObject(
                        "SELECT role_id FROM work_role_selections WHERE user_id = ?", Long.class, userId))
                .as("外しは作業ロールの保存を書き換えない（BR7.3）")
                .isEqualTo(roleId);

        assertThat(withoutLock(roleId, RoleStore::deleteRole)).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(roles.roleRows(roleId)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM work_role_selections WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    @DisplayName("saving the work role inserts the first time and updates the row afterwards")
    void saveSelection() {
        long first = roles.role(RoleFixtures.uniqueName("保存の一"));
        long second = roles.role(RoleFixtures.uniqueName("保存の二"));
        long userId = groups.user("保存 確かめ二郎");

        assertThat(withoutLock(first, (store, role) -> store.saveWorkRoleSelection(userId, first, NOW)))
                .isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(withoutLock(
                        second, (store, role) -> store.saveWorkRoleSelection(userId, second, NOW.plusSeconds(5))))
                .isEqualTo(new RoleStoreOutcome.Done<>(null));

        assertThat(jdbc.queryForList("SELECT role_id, updated_at FROM work_role_selections WHERE user_id = ?", userId))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row).containsEntry("ROLE_ID", second);
                    assertThat(((OffsetDateTime) row.get("UPDATED_AT")).toInstant())
                            .isEqualTo(NOW.plusSeconds(5));
                });
    }
}
