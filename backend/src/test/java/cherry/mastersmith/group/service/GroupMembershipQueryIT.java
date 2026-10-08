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
package cherry.mastersmith.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
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
 * U4 role への読み取りと排他の口（契約 C4、BR3.6・BR5.3・BR6.4・BR6.5、AC2.1.9・AC2.1.10・AC2.2.1・AC2.2.10）の結合テスト。
 *
 * <p>行の排他の上限切れは、別の接続でグループの行を持ち続けて H2 の上限（3 秒）で切らせる。合否は経過の時間ではなく結果の型で決める。
 */
@SpringBootTest
class GroupMembershipQueryIT {

    private static final Instant T0 = Instant.parse("2026-10-08T12:00:00Z");

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.100", null, null);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    GroupMembershipQuery query;

    @Autowired
    GroupAdminService service;

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

    @Test
    @DisplayName("the groups of a user are read, and a removed membership is gone at the next read")
    void groupsOfUserAfterRemoval() {
        long userId = fixtures.user("営業 所属");
        long a = fixtures.group(GroupFixtures.uniqueName("所属 A"));
        long b = fixtures.group(GroupFixtures.uniqueName("所属 B"));
        fixtures.member(a, userId, T0);
        fixtures.member(b, userId, T0);
        assertThat(query.groupIdsOfUser(userId)).containsExactlyInAnyOrder(a, b);

        assertThat(service.removeMember(1L, ORIGIN, a, userId)).isEqualTo(new GroupChangeResult.Done());

        assertThat(query.groupIdsOfUser(userId)).containsExactly(b);
        assertThat(query.groupIdsOfUser(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("existence and summaries read only existing groups, in id order")
    void existsAndSummaries() {
        String nameA = GroupFixtures.uniqueName("要約 A");
        String nameB = GroupFixtures.uniqueName("要約 B");
        long a = fixtures.group(nameA);
        long b = fixtures.group(nameB);

        assertThat(query.exists(a)).isTrue();
        assertThat(query.exists(0L)).isFalse();
        assertThat(query.exists(-1L)).isFalse();
        assertThat(query.summaries(Set.of(b, a, Long.MAX_VALUE)))
                .containsExactly(new GroupSummary(a, nameA), new GroupSummary(b, nameB));
        assertThat(query.summaries(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("member user ids leave out missing groups and keep an empty group with an empty set")
    void memberUserIds() {
        long withMembers = fixtures.group(GroupFixtures.uniqueName("メンバー 有"));
        long empty = fixtures.group(GroupFixtures.uniqueName("メンバー 空"));
        long first = fixtures.user("営業 一");
        long second = fixtures.user("営業 二");
        fixtures.member(withMembers, first, T0);
        fixtures.member(withMembers, second, T0);

        Map<Long, Set<Long>> result = query.memberUserIds(Set.of(withMembers, empty, Long.MAX_VALUE));

        assertThat(result).containsOnlyKeys(withMembers, empty);
        assertThat(result.get(withMembers)).containsExactlyInAnyOrder(first, second);
        assertThat(result.get(empty)).isEmpty();
        assertThat(query.memberUserIds(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("locking for an assignment reports existence inside a transaction and refuses outside of one")
    void lockForAssignment() {
        long groupId = fixtures.group(GroupFixtures.uniqueName("排他"));

        GroupRowLock existing = tx.execute(status -> query.lockForAssignment(groupId));
        GroupRowLock missing = tx.execute(status -> query.lockForAssignment(Long.MAX_VALUE));

        assertThat(existing).isEqualTo(new GroupRowLock.Locked(true));
        assertThat(missing).isEqualTo(new GroupRowLock.Locked(false));
        assertThatThrownBy(() -> query.lockForAssignment(groupId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("locking for an assignment is Busy when another transaction holds the group row past the limit")
    void lockForAssignmentBusy() throws Exception {
        long groupId = fixtures.group(GroupFixtures.uniqueName("排他 待ち"));

        GroupRowLock result;
        try (RowLockHolder held = RowLockHolder.hold(
                TestDatabase.url(tempDir), "SELECT group_id FROM groups WHERE group_id = ? FOR UPDATE", groupId)) {
            assertThat(held.lockedRows()).isEqualTo(1);
            result = tx.execute(status -> {
                GroupRowLock lock = query.lockForAssignment(groupId);
                status.setRollbackOnly();
                return lock;
            });
        }

        assertThat(result).isEqualTo(new GroupRowLock.Busy());
    }
}
