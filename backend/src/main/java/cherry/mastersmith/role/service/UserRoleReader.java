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

import cherry.mastersmith.group.service.GroupMembershipQuery;
import cherry.mastersmith.role.repository.GroupAssignedRoleRow;
import cherry.mastersmith.role.repository.RoleAssignmentRepository;
import cherry.mastersmith.role.repository.WorkRoleSelectionRepository;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 利用者のロールを決める読み取り（{@code performance-design.md} 1節、NFR2.3、BR6.5・BR7.2、計画の D-15）。
 *
 * <p>直接の割り当て（ロールの名前を結合）・所属のグループ（group の {@code groupIdsOfUser}）・グループの割り当て（グループの ID の集合で1回、
 * ロールの名前を結合）・作業ロールの保存の、合計4回で読む（所属のグループが無ければグループの割り当ては読まず3回）。呼び出し元の
 * トランザクションの中で使う。書き込まない（読み替えで保存を書き換えない。BR7.3）。
 */
@Component
public class UserRoleReader {

    private final RoleAssignmentRepository assignments;

    private final GroupMembershipQuery groups;

    private final WorkRoleSelectionRepository selections;

    /**
     * 作る。
     *
     * @param assignments 割り当ての表の読み取り
     * @param groups group の読み取りの口
     * @param selections 作業ロールの保存の表の読み取り
     */
    public UserRoleReader(
            RoleAssignmentRepository assignments, GroupMembershipQuery groups, WorkRoleSelectionRepository selections) {
        this.assignments = assignments;
        this.groups = groups;
        this.selections = selections;
    }

    /**
     * 利用者のロールと作業ロールの保存を読む。
     *
     * @param userId 利用者 ID
     * @return 読み取りの結果
     */
    public UserRoles read(long userId) {
        var direct = assignments.findDirectRolesOfUser(userId);
        Set<Long> groupIds = groups.groupIdsOfUser(userId);
        List<GroupAssignedRoleRow> viaGroups =
                groupIds.isEmpty() ? List.of() : assignments.findRolesOfGroups(Set.copyOf(groupIds));
        OptionalLong stored =
                selections.findRoleIdOfUser(userId).map(OptionalLong::of).orElseGet(OptionalLong::empty);
        return new UserRoles(direct, viaGroups, stored);
    }
}
