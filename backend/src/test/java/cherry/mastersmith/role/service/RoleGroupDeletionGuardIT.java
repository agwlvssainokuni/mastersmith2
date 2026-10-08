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

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.service.DeletionDecision;
import cherry.mastersmith.group.service.GroupDeletionGuard;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * group の問う口の本番の実装の結合テスト（BR10.1〜BR10.3、group のコード生成の読み直しの R-04 (2)、計画の 8.3）。
 *
 * <p>テスト用の差し替え（{@code TestGroupDeletionGuard}）を使わず、本番の Bean を本物の割り当ての表で確かめる。B3 の仮の実装（常に
 * {@code Allowed}・0）が残っていれば、このテストは落ちる。
 */
@SpringBootTest
class RoleGroupDeletionGuardIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    GroupDeletionGuard guard;

    @Autowired
    ApplicationContext context;

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
    @DisplayName("the only GroupDeletionGuard bean is the role implementation")
    void productionBean() {
        assertThat(context.getBeansOfType(GroupDeletionGuard.class)).hasSize(1);
        assertThat(guard).isInstanceOf(RoleGroupDeletionGuard.class);
    }

    @Test
    @DisplayName("a group with a role assignment row is blocked with its count, and the others are allowed with zero")
    void countsTheRealTable() {
        long assigned = groups.group(GroupFixtures.uniqueName("割り当てあり"));
        long twice = groups.group(GroupFixtures.uniqueName("割り当て二つ"));
        long free = groups.group(GroupFixtures.uniqueName("割り当てなし"));
        long roleA = roles.role(RoleFixtures.uniqueName("問う口のロール一"));
        long roleB = roles.role(RoleFixtures.uniqueName("問う口のロール二"));
        roles.assignGroup(roleA, assigned);
        roles.assignGroup(roleA, twice);
        roles.assignGroup(roleB, twice);

        assertThat(guard.canDelete(assigned)).isEqualTo(new DeletionDecision.Blocked(1));
        assertThat(guard.canDelete(twice)).isEqualTo(new DeletionDecision.Blocked(2));
        assertThat(guard.canDelete(free)).isEqualTo(new DeletionDecision.Allowed());
        assertThat(guard.assignedRoleCounts(Set.of(assigned, twice, free, Long.MAX_VALUE)))
                .isEqualTo(Map.of(assigned, 1, twice, 2, free, 0, Long.MAX_VALUE, 0));
    }

    @Test
    @DisplayName("after the assignment is removed the group may be deleted again")
    void afterRemoval() {
        long groupId = groups.group(GroupFixtures.uniqueName("外した後"));
        long roleId = roles.role(RoleFixtures.uniqueName("外した後のロール"));
        roles.assignGroup(roleId, groupId);
        assertThat(guard.canDelete(groupId).assignedRoles()).isEqualTo(1);

        jdbc.update("DELETE FROM group_role_assignments WHERE role_id = ? AND group_id = ?", roleId, groupId);

        assertThat(guard.canDelete(groupId)).isEqualTo(new DeletionDecision.Allowed());
    }
}
