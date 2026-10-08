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
package cherry.mastersmith.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * グループとメンバーの表の読み取り（BR6.4・BR6.5・BR7.2〜BR7.4・BR1.4、NFR2.3・NFR2.6、{@code performance-design.md} 1節）の結合テスト。
 *
 * <p>テストのクラスごとの一時の内部DB を使うが、テストの間で行を消さないため、どのテストも自分で作った ID だけを見る。
 */
@SpringBootTest
class GroupRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-10-03T00:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    GroupRepository groups;

    @Autowired
    GroupMemberRepository members;

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
    @DisplayName("the page lists groups in id order and the count covers every group")
    void pageInIdOrder() {
        long first = fixtures.group(GroupFixtures.uniqueName("一覧 B"));
        long second = fixtures.group(GroupFixtures.uniqueName("一覧 A"));
        long third = fixtures.group(GroupFixtures.uniqueName("一覧 C"));
        long total = groups.countAll();

        List<GroupRowView> all = groups.findPage(PageRequest.of(0, Math.toIntExact(total)));
        List<GroupRowView> firstTwo = groups.findPage(PageRequest.of(0, 2));

        assertThat(all).extracting(GroupRowView::groupId).isSorted().contains(first, second, third);
        assertThat(all).hasSize(Math.toIntExact(total));
        assertThat(firstTwo).hasSize(2).extracting(GroupRowView::groupId).isSorted();
        assertThat(first).isLessThan(second);
        assertThat(second).isLessThan(third);
    }

    @Test
    @DisplayName("member counts are read for the given groups, and a group without members has no row")
    void memberCounts() {
        long busy = fixtures.group(GroupFixtures.uniqueName("数 多"));
        long single = fixtures.group(GroupFixtures.uniqueName("数 一"));
        long empty = fixtures.group(GroupFixtures.uniqueName("数 零"));
        for (int i = 0; i < 3; i++) {
            fixtures.member(busy, fixtures.user("営業 " + i), T0.plusSeconds(i));
        }
        fixtures.member(single, fixtures.user("営業 単"), T0);

        List<GroupMemberCount> counts = members.countByGroups(Set.of(busy, single, empty));

        assertThat(counts).containsExactlyInAnyOrder(new GroupMemberCount(busy, 3), new GroupMemberCount(single, 1));
        assertThat(members.countByGroup(busy)).isEqualTo(3);
        assertThat(members.countByGroup(empty)).isZero();
    }

    @Test
    @DisplayName("the detail lists members by the time they were added and then by user id")
    void detailOrder() {
        long groupId = fixtures.group(GroupFixtures.uniqueName("詳細"));
        long early = fixtures.user("営業 早");
        long tieHigh = fixtures.user("営業 同時 後");
        long tieLow = fixtures.user("営業 同時 先");
        long smaller = Math.min(tieHigh, tieLow);
        long larger = Math.max(tieHigh, tieLow);
        fixtures.member(groupId, larger, T0.plusSeconds(10));
        fixtures.member(groupId, early, T0);
        fixtures.member(groupId, smaller, T0.plusSeconds(10));

        List<GroupMemberRow> rows = members.findMembers(groupId);

        assertThat(rows).extracting(GroupMemberRow::userId).containsExactly(early, smaller, larger);
        assertThat(rows.getFirst().addedAt()).isEqualTo(T0);
        assertThat(groups.findView(groupId)).map(GroupRowView::groupId).contains(groupId);
        assertThat(groups.findView(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("the groups of a user, the member check and the existence check read only existing rows")
    void groupsOfUserAndChecks() {
        long userId = fixtures.user("営業 所属");
        long a = fixtures.group(GroupFixtures.uniqueName("所属 A"));
        long b = fixtures.group(GroupFixtures.uniqueName("所属 B"));
        fixtures.group(GroupFixtures.uniqueName("所属 なし"));
        fixtures.member(b, userId, T0);
        fixtures.member(a, userId, T0);

        assertThat(members.findGroupIdsOfUser(userId)).containsExactly(a, b);
        assertThat(members.findGroupIdsOfUser(Long.MAX_VALUE)).isEmpty();
        assertThat(members.isMember(a, userId)).isTrue();
        assertThat(members.isMember(a, Long.MAX_VALUE)).isFalse();
        assertThat(groups.existsGroup(a)).isTrue();
        assertThat(groups.existsGroup(0L)).isFalse();
        assertThat(groups.findViews(Set.of(b, a, Long.MAX_VALUE)))
                .extracting(GroupRowView::groupId)
                .containsExactly(a, b);
    }

    @Test
    @DisplayName("member pairs leave out missing groups and give an empty group one row without a user")
    void memberPairs() {
        long withMembers = fixtures.group(GroupFixtures.uniqueName("組 有"));
        long empty = fixtures.group(GroupFixtures.uniqueName("組 空"));
        long first = fixtures.user("営業 組 一");
        long second = fixtures.user("営業 組 二");
        fixtures.member(withMembers, second, T0);
        fixtures.member(withMembers, first, T0.plusSeconds(1));

        List<GroupMemberPair> pairs = members.findMemberPairs(Set.of(withMembers, empty, Long.MAX_VALUE));

        assertThat(pairs)
                .containsExactly(
                        new GroupMemberPair(withMembers, Math.min(first, second)),
                        new GroupMemberPair(withMembers, Math.max(first, second)),
                        new GroupMemberPair(empty, null));
    }

    @Test
    @DisplayName("the name key lookup finds the group whose key matches and the user id index exists")
    void nameKeyAndIndex() {
        String raw = GroupFixtures.uniqueName("Sales");
        long groupId = fixtures.group(raw);

        assertThat(groups.findIdByNameKey(raw.toLowerCase(java.util.Locale.ROOT)))
                .contains(groupId);
        assertThat(groups.findIdByNameKey(raw)).isEmpty();
        assertThat(jdbc.queryForList(
                        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS"
                                + " WHERE TABLE_NAME = 'GROUP_MEMBERS' AND INDEX_NAME = 'IX_GROUP_MEMBERS_USER_ID'",
                        String.class))
                .containsExactly("USER_ID");
    }
}
