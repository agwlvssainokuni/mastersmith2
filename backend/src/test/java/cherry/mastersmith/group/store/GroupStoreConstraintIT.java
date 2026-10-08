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
package cherry.mastersmith.group.store;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.domain.Group;
import cherry.mastersmith.group.domain.GroupName;
import cherry.mastersmith.group.domain.GroupNameValidation;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.UncommittedWrite;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 最後の守りの主キーと外部キー（{@code reliability-design.md} 4.1・4.2 の #7、計画の D-6・D-7、BR5.2・BR5.4・BR5.5、NFR3.3）の結合テスト。
 *
 * <p>API の経路は先にグループの行を排他するため、主キー・外部キーの違反は API から届かない。テストからグループの行を排他せずに
 * {@link GroupStore} の書き込みを呼び、違反と上限切れを決定的に作る。違反は先の側を確定させてから後の側を書く「待たない違反」、上限切れは
 * 先の側を未確定のまま放さずに H2 の上限で後の側を切らせる。合否は経過の時間ではなく、結果の型・行の数・WARN で決める。
 * {@link StoreOutcome.Done} 以外では、本番の {@code GroupStoreTransactions} と同じく巻き戻しの印を付ける。
 */
@SpringBootTest
class GroupStoreConstraintIT {

    private static final Instant NOW = Instant.parse("2026-10-08T07:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    GroupStore store;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private GroupFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new GroupFixtures(users, jdbc);
    }

    /** 1つ目のトランザクションで store を呼び、Done 以外なら巻き戻しの印を付ける（{@code GroupStoreTransactions} と同じ形）。 */
    private <T> StoreOutcome<T> inTransaction(Function<GroupStore, StoreOutcome<T>> call) {
        return tx.execute(status -> {
            StoreOutcome<T> outcome = call.apply(store);
            if (!outcome.isDone()) {
                status.setRollbackOnly();
            }
            return outcome;
        });
    }

    private static GroupName name(String raw) {
        return ((GroupNameValidation.Valid) GroupName.parse(raw)).name();
    }

    @Test
    @DisplayName("adding a member that a committed transaction already added is AlreadyMember without waiting")
    void primaryKeyViolationWithoutWaiting() {
        long groupId = fixtures.group(GroupFixtures.uniqueName("主キー"));
        long userId = fixtures.user("営業 太郎");
        assertThat(inTransaction(s -> s.insertMember(groupId, userId, NOW))).isEqualTo(new StoreOutcome.Done<>(null));

        StoreOutcome<Void> second = inTransaction(s -> s.insertMember(groupId, userId, NOW.plusSeconds(1)));

        assertThat(second).isEqualTo(new StoreOutcome.AlreadyMember<Void>());
        assertThat(fixtures.memberRows(groupId)).isEqualTo(1);
    }

    @Test
    @DisplayName("adding a member held uncommitted by another transaction is Busy with one WARN of GROUP_MEMBER_KEY")
    void primaryKeyWaitTimesOut() throws Exception {
        long groupId = fixtures.group(GroupFixtures.uniqueName("主キーの待ち"));
        long userId = fixtures.user("営業 花子");

        StoreOutcome<Void> outcome;
        try (LogEvents logs = LogEvents.capture(GroupStore.class);
                UncommittedWrite held = UncommittedWrite.hold(
                        TestDatabase.url(tempDir),
                        "INSERT INTO group_members (group_id, user_id, added_at) VALUES (?, ?, ?)",
                        groupId,
                        userId,
                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))) {
            outcome = inTransaction(s -> s.insertMember(groupId, userId, NOW));

            assertThat(logs.list()).singleElement().satisfies(warn -> {
                assertThat(warn.getLevel()).isEqualTo(Level.WARN);
                assertThat(warn.getThrowableProxy()).isNull();
                assertThat(warn.getKeyValuePairs())
                        .anySatisfy(pair -> assertThat(pair.key + "=" + pair.value)
                                .isEqualTo("lockKind=" + GroupStore.GROUP_MEMBER_KEY));
            });
        }

        assertThat(outcome).isEqualTo(new StoreOutcome.Busy<Void>(GroupStore.GROUP_MEMBER_KEY));
        assertThat(fixtures.memberRows(groupId)).isZero();
    }

    @Test
    @DisplayName("deleting a group whose member was committed without the group lock is Referenced and keeps both")
    void foreignKeyOnDelete() {
        long groupId = fixtures.group(GroupFixtures.uniqueName("削除の外部キー"));
        long userId = fixtures.user("営業 次郎");
        assertThat(inTransaction(s -> s.insertMember(groupId, userId, NOW))).isEqualTo(new StoreOutcome.Done<>(null));

        StoreOutcome<Void> outcome = inTransaction(s -> switch (s.lockGroup(groupId)) {
            case StoreOutcome.Done<Group>(Group group) -> s.deleteGroup(group);
            default -> throw new IllegalStateException("グループを排他できません");
        });

        assertThat(outcome).isEqualTo(new StoreOutcome.Referenced<Void>());
        assertThat(fixtures.groupRows(groupId)).isEqualTo(1);
        assertThat(fixtures.memberRows(groupId)).isEqualTo(1);
    }

    @Test
    @DisplayName("adding a member to a group deleted by a committed transaction is Referenced")
    void foreignKeyOnAdd() {
        long groupId = fixtures.group(GroupFixtures.uniqueName("追加の外部キー"));
        long userId = fixtures.user("営業 三郎");
        assertThat(inTransaction(s -> switch (s.lockGroup(groupId)) {
                    case StoreOutcome.Done<Group>(Group group) -> s.deleteGroup(group);
                    default -> throw new IllegalStateException("グループを排他できません");
                }))
                .isEqualTo(new StoreOutcome.Done<>(null));

        StoreOutcome<Void> outcome = inTransaction(s -> s.insertMember(groupId, userId, NOW));

        assertThat(outcome).isEqualTo(new StoreOutcome.Referenced<Void>());
        assertThat(fixtures.memberRows(groupId)).isZero();
    }

    @Test
    @DisplayName("creating a group with a name key committed by another transaction is NameTaken")
    void nameKeyViolation() {
        String raw = GroupFixtures.uniqueName("Sales");
        fixtures.group(raw);

        StoreOutcome<Group> outcome =
                inTransaction(s -> s.insertGroup(name(raw.toUpperCase(java.util.Locale.ROOT)), NOW));

        assertThat(outcome).isEqualTo(new StoreOutcome.NameTaken<Group>());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM groups WHERE name_key = ?",
                        Integer.class,
                        name(raw).key()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("locking a missing group is GroupMissing and the write methods report Done for the normal path")
    void normalPath() {
        assertThat(inTransaction(s -> s.lockGroup(Long.MAX_VALUE))).isEqualTo(new StoreOutcome.GroupMissing<Group>());
        assertThat(inTransaction(s -> s.lockGroup(0L))).isEqualTo(new StoreOutcome.GroupMissing<Group>());

        StoreOutcome<Group> created = inTransaction(s -> s.insertGroup(name(GroupFixtures.uniqueName("通常")), NOW));
        long groupId = ((StoreOutcome.Done<Group>) created).value().getGroupId();
        long userId = fixtures.user("営業 四郎");
        assertThat(inTransaction(s -> s.insertMember(groupId, userId, NOW))).isEqualTo(new StoreOutcome.Done<>(null));
        assertThat(inTransaction(s -> s.deleteMember(groupId, userId))).isEqualTo(new StoreOutcome.Done<>(true));
        assertThat(inTransaction(s -> s.deleteMember(groupId, userId))).isEqualTo(new StoreOutcome.Done<>(false));
        String renamed = GroupFixtures.uniqueName("改名");
        StoreOutcome<Group> rename = inTransaction(s -> switch (s.lockGroup(groupId)) {
            case StoreOutcome.Done<Group>(Group group) -> s.renameGroup(group, name(renamed), NOW.plusSeconds(5));
            default -> throw new IllegalStateException("グループを排他できません");
        });

        assertThat(rename.isDone()).isTrue();
        assertThat(jdbc.queryForMap("SELECT name, updated_at FROM groups WHERE group_id = ?", groupId))
                .containsEntry("NAME", renamed);
        assertThat(inTransaction(s -> switch (s.lockGroup(groupId)) {
                    case StoreOutcome.Done<Group>(Group group) -> s.deleteGroup(group);
                    default -> throw new IllegalStateException("グループを排他できません");
                }))
                .isEqualTo(new StoreOutcome.Done<>(null));
        assertThat(fixtures.groupRows(groupId)).isZero();
    }
}
