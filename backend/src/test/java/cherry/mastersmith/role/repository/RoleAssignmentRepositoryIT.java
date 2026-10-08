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
package cherry.mastersmith.role.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.util.List;
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

/**
 * 割り当て・作業ロールの保存・写しと祖先の設定の読み取り（BR3.2・BR3.4・BR6.3〜BR6.5・BR7.2・BR10.1・BR10.2、NFR2.3・NFR2.6、計画の 8.3・
 * D-15・D-16）の結合テスト。
 */
@SpringBootTest
class RoleAssignmentRepositoryIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleAssignmentRepository assignments;

    @Autowired
    WorkRoleSelectionRepository selections;

    @Autowired
    PermissionSettingRepository settings;

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

    @Test
    @DisplayName("the direct roles of a user are read with their names in id order")
    void directRolesOfUser() {
        long later = roles.role(RoleFixtures.uniqueName("後のロール"));
        long earlier = roles.role(RoleFixtures.uniqueName("先のロール"));
        long other = roles.role(RoleFixtures.uniqueName("ほかのロール"));
        long userId = groups.user("直接 確かめ一郎");
        long otherUser = groups.user("直接 確かめ二郎");
        roles.assignUser(earlier, userId);
        roles.assignUser(later, userId);
        roles.assignUser(other, otherUser);

        List<AssignedRoleRow> rows = assignments.findDirectRolesOfUser(userId);

        assertThat(rows).extracting(AssignedRoleRow::roleId).containsExactly(later, earlier);
        assertThat(rows.getFirst().name()).isEqualTo(roles.roleRow(later).get("NAME"));
        assertThat(assignments.findDirectRolesOfUser(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("the roles of several groups are read once with group id, role id and name, in role id order")
    void rolesOfGroups() {
        long first = roles.role(RoleFixtures.uniqueName("グループのロール一"));
        long second = roles.role(RoleFixtures.uniqueName("グループのロール二"));
        long groupA = groups.group(GroupFixtures.uniqueName("グループ甲"));
        long groupB = groups.group(GroupFixtures.uniqueName("グループ乙"));
        long groupC = groups.group(GroupFixtures.uniqueName("グループ丙"));
        roles.assignGroup(second, groupA);
        roles.assignGroup(first, groupB);
        roles.assignGroup(second, groupB);
        roles.assignGroup(first, groupC);

        List<GroupAssignedRoleRow> rows = assignments.findRolesOfGroups(Set.of(groupA, groupB));

        assertThat(rows)
                .extracting(GroupAssignedRoleRow::roleId, GroupAssignedRoleRow::groupId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, groupB),
                        org.assertj.core.groups.Tuple.tuple(second, groupA),
                        org.assertj.core.groups.Tuple.tuple(second, groupB));
        assertThat(rows).allSatisfy(row -> assertThat(row.name()).isNotBlank());
    }

    @Test
    @DisplayName("the users and groups of a role, the existence of a pair and the counts are read")
    void membersExistenceAndCounts() {
        long roleId = roles.role(RoleFixtures.uniqueName("数のロール"));
        long emptyRole = roles.role(RoleFixtures.uniqueName("空のロール"));
        long userB = groups.user("数 確かめ二郎");
        long userA = groups.user("数 確かめ一郎");
        long groupId = groups.group(GroupFixtures.uniqueName("数のグループ"));
        long otherGroup = groups.group(GroupFixtures.uniqueName("割り当ての無いグループ"));
        roles.assignUser(roleId, userB);
        roles.assignUser(roleId, userA);
        roles.assignGroup(roleId, groupId);

        assertThat(assignments.findUserIdsOfRole(roleId))
                .containsExactly(Math.min(userA, userB), Math.max(userA, userB));
        assertThat(assignments.findGroupIdsOfRole(roleId)).containsExactly(groupId);
        assertThat(assignments.existsUserAssignment(roleId, userA)).isTrue();
        assertThat(assignments.existsUserAssignment(emptyRole, userA)).isFalse();
        assertThat(assignments.existsGroupAssignment(roleId, groupId)).isTrue();
        assertThat(assignments.existsGroupAssignment(roleId, otherGroup)).isFalse();
        assertThat(assignments.countUsersOfRole(roleId)).isEqualTo(2);
        assertThat(assignments.countGroupsOfRole(roleId)).isEqualTo(1);
        assertThat(assignments.countUsersOfRole(emptyRole)).isZero();
        assertThat(assignments.countUsersByRoles(Set.of(roleId, emptyRole)))
                .containsExactly(new AssignmentCount(roleId, 2));
        assertThat(assignments.countGroupsByRoles(Set.of(roleId, emptyRole)))
                .containsExactly(new AssignmentCount(roleId, 1));
        assertThat(assignments.countByGroups(Set.of(groupId, otherGroup)))
                .containsExactly(new AssignmentCount(groupId, 1));
    }

    @Test
    @DisplayName("the stored work role of a user is read, and a user without one has none")
    void storedWorkRole() {
        long roleId = roles.role(RoleFixtures.uniqueName("保存のロール"));
        long userId = groups.user("保存 確かめ一郎");
        long otherUser = groups.user("保存 確かめ二郎");
        roles.selectWorkRole(userId, roleId);

        assertThat(selections.findRoleIdOfUser(userId)).contains(roleId);
        assertThat(selections.findRoleIdOfUser(otherUser)).isEmpty();
    }

    @Test
    @DisplayName("all settings of a role are read at once, and the ancestors of one target are at most three rows")
    void snapshotAndAncestors() {
        long roleId = roles.role(RoleFixtures.uniqueName("写しのロール"));
        long otherRole = roles.role(RoleFixtures.uniqueName("ほかの写しのロール"));
        roles.setting(roleId, PermissionTarget.schema("SALES"), new PermissionValues(MainPermission.READ, true, null));
        roles.setting(roleId, PermissionTarget.table("SALES", "ORDER_LINE"), MainPermission.FULL);
        roles.setting(roleId, PermissionTarget.table("SALES", "ORDER_HEAD"), MainPermission.NONE);
        roles.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "UNIT_PRICE"), MainPermission.NONE);
        roles.setting(roleId, PermissionTarget.column("SALES", "ORDER_LINE", "QTY"), MainPermission.READ);
        roles.setting(roleId, PermissionTarget.schema("HR"), MainPermission.FULL);
        roles.setting(otherRole, PermissionTarget.schema("SALES"), MainPermission.FULL);

        assertThat(settings.findAllOfRole(roleId)).hasSize(6);
        assertThat(settings.findAncestors(roleId, "SALES", "ORDER_LINE", "UNIT_PRICE"))
                .extracting(PermissionSettingRow::target)
                .containsExactlyInAnyOrder(
                        PermissionTarget.schema("SALES"),
                        PermissionTarget.table("SALES", "ORDER_LINE"),
                        PermissionTarget.column("SALES", "ORDER_LINE", "UNIT_PRICE"));
        assertThat(settings.findAncestors(roleId, "SALES", "ORDER_LINE", ""))
                .extracting(PermissionSettingRow::target)
                .containsExactlyInAnyOrder(
                        PermissionTarget.schema("SALES"), PermissionTarget.table("SALES", "ORDER_LINE"));
        assertThat(settings.findAncestors(roleId, "SALES", "", ""))
                .extracting(PermissionSettingRow::target)
                .containsExactly(PermissionTarget.schema("SALES"));
        assertThat(settings.findAncestors(roleId, "sales", "ORDER_LINE", ""))
                .as("大文字と小文字を区別する")
                .isEmpty();
    }
}
