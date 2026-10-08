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
package cherry.mastersmith.role.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.role.domain.RoleAuditDetail.PermissionChange;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 監査の detail の型（BR11.5・BR11.6、計画の D-11）の単体テスト。 */
class RoleAuditDetailTest {

    private static PermissionChange change(int index) {
        return new PermissionChange(
                PermissionTarget.column("S", "T", "C" + index),
                PermissionValues.NOT_SET,
                new PermissionValues(MainPermission.READ, null, null));
    }

    @Test
    @DisplayName("a summary keeps the number of changes and only the first ones in their order")
    void summarize() {
        RoleAuditDetail.PermissionChanges changes = new RoleAuditDetail.PermissionChanges(
                "営業",
                IntStream.range(0, 30).mapToObj(RoleAuditDetailTest::change).toList());

        RoleAuditDetail.PermissionChangesSummary summary = changes.summarize(20);

        assertThat(summary.roleName()).isEqualTo("営業");
        assertThat(summary.changedCount()).isEqualTo(30);
        assertThat(summary.firstChanges()).hasSize(20).first().isEqualTo(change(0));
        assertThat(changes.summarize(50).firstChanges()).hasSize(30);
        assertThat(changes.summarize(0).firstChanges()).isEmpty();
        assertThatThrownBy(() -> changes.summarize(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the lists are copied and the counts are checked")
    void invariants() {
        List<PermissionChange> list = new java.util.ArrayList<>(List.of(change(1)));
        RoleAuditDetail.PermissionChanges changes = new RoleAuditDetail.PermissionChanges("営業", list);
        list.clear();

        assertThat(changes.changes()).hasSize(1);
        assertThatThrownBy(() -> new RoleAuditDetail.PermissionChangesSummary("営業", 0, List.of(change(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoleAuditDetail.InUse("営業", -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoleAuditDetail.Rename("a", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PermissionChange(null, PermissionValues.NOT_SET, PermissionValues.NOT_SET))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("an assignment detail has the role name and the group name only for a group")
    void assignment() {
        RoleAuditDetail.Assignment toUser = new RoleAuditDetail.Assignment("営業", null);
        RoleAuditDetail.Assignment toGroup = new RoleAuditDetail.Assignment("営業", "第一営業部");

        assertThat(toUser.groupName()).isNull();
        assertThat(toGroup.groupName()).isEqualTo("第一営業部");
        assertThat(RoleAuditDetail.Assignment.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("roleName", "groupName");
        assertThatThrownBy(() -> new RoleAuditDetail.Assignment(null, "第一営業部"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a work role switch detail keeps the previous role id and name together")
    void workRoleSwitch() {
        RoleAuditDetail.WorkRoleSwitch first = new RoleAuditDetail.WorkRoleSwitch(null, null, "営業", null);
        RoleAuditDetail.WorkRoleSwitch rewrite = new RoleAuditDetail.WorkRoleSwitch(3L, "営業", "営業", 9L);

        assertThat(first.fromRoleId()).isNull();
        assertThat(rewrite.storedBeforeRoleId()).isEqualTo(9L);
        assertThatThrownBy(() -> new RoleAuditDetail.WorkRoleSwitch(3L, null, "営業", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoleAuditDetail.WorkRoleSwitch(null, "営業", "経理", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoleAuditDetail.WorkRoleSwitch(null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
