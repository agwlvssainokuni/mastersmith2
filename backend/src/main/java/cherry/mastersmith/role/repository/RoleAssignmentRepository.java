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

import cherry.mastersmith.role.domain.UserRoleAssignment;
import cherry.mastersmith.role.domain.UserRoleAssignmentId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 割り当ての表（利用者への割り当てとグループへの割り当て）の読み取り（{@code logical-components.md} の L10、BR3.2・BR3.4・BR6.3〜BR6.5・
 * BR10.1・BR10.2、NFR2.3・NFR2.6、{@code performance-design.md} 1節）。書き込みの方法を持たない。
 *
 * <p>有効な作業ロールを決める読み取り（直接の割り当てとグループの割り当て）は、ロールの名前を結合して1回で読む（作業ロールの名前の
 * ために読み取りを足さない。計画の D-15）。数はロール・グループの ID の集合でまとめて1回で集計する。
 */
public interface RoleAssignmentRepository extends Repository<UserRoleAssignment, UserRoleAssignmentId> {

    /**
     * 利用者への直接の割り当てのロールを、ロールの ID の順に読む（BR6.5・BR7.1）。
     *
     * @param userId 利用者 ID
     * @return ロールの ID と名前
     */
    @Query("select new cherry.mastersmith.role.repository.AssignedRoleRow(r.roleId, r.name)"
            + " from UserRoleAssignment a join Role r on r.roleId = a.roleId"
            + " where a.userId = :userId order by r.roleId")
    List<AssignedRoleRow> findDirectRolesOfUser(@Param("userId") long userId);

    /**
     * グループの ID の集合で、グループへの割り当てのロールを読む（ロールの ID・グループの ID の順。BR6.5）。
     *
     * @param groupIds グループの ID の集合（空でないこと）
     * @return グループの ID・ロールの ID・名前
     */
    @Query("select new cherry.mastersmith.role.repository.GroupAssignedRoleRow(a.groupId, r.roleId, r.name)"
            + " from GroupRoleAssignment a join Role r on r.roleId = a.roleId"
            + " where a.groupId in :groupIds order by r.roleId, a.groupId")
    List<GroupAssignedRoleRow> findRolesOfGroups(@Param("groupIds") Collection<Long> groupIds);

    /**
     * ロールに直接割り当てた利用者の ID を、利用者 ID の順に読む（割り当ての一覧。BR6.11）。
     *
     * @param roleId ロールの ID
     * @return 利用者 ID
     */
    @Query("select a.userId from UserRoleAssignment a where a.roleId = :roleId order by a.userId")
    List<Long> findUserIdsOfRole(@Param("roleId") long roleId);

    /**
     * ロールを割り当てたグループの ID を、グループの ID の順に読む（割り当ての一覧。BR6.11）。
     *
     * @param roleId ロールの ID
     * @return グループの ID
     */
    @Query("select a.groupId from GroupRoleAssignment a where a.roleId = :roleId order by a.groupId")
    List<Long> findGroupIdsOfRole(@Param("roleId") long roleId);

    /**
     * 利用者への割り当ての組があるかを返す（BR6.3・BR6.4）。
     *
     * @param roleId ロールの ID
     * @param userId 利用者 ID
     * @return あれば true
     */
    @Query("select count(a) > 0 from UserRoleAssignment a where a.roleId = :roleId and a.userId = :userId")
    boolean existsUserAssignment(@Param("roleId") long roleId, @Param("userId") long userId);

    /**
     * グループへの割り当ての組があるかを返す（BR6.3・BR6.4）。
     *
     * @param roleId ロールの ID
     * @param groupId グループの ID
     * @return あれば true
     */
    @Query("select count(a) > 0 from GroupRoleAssignment a where a.roleId = :roleId and a.groupId = :groupId")
    boolean existsGroupAssignment(@Param("roleId") long roleId, @Param("groupId") long groupId);

    /**
     * ロールの ID の集合で、利用者への直接の割り当ての数をまとめて1回で数える（一覧の userCount。BR3.4）。割り当てが無いロールは行を持たない。
     *
     * @param roleIds ロールの ID の集合（空でないこと）
     * @return ロールの ID ごとの数
     */
    @Query("select new cherry.mastersmith.role.repository.AssignmentCount(a.roleId, count(a))"
            + " from UserRoleAssignment a where a.roleId in :roleIds group by a.roleId")
    List<AssignmentCount> countUsersByRoles(@Param("roleIds") Collection<Long> roleIds);

    /**
     * ロールの ID の集合で、グループへの割り当ての数をまとめて1回で数える（一覧の groupCount。BR3.4）。割り当てが無いロールは行を持たない。
     *
     * @param roleIds ロールの ID の集合（空でないこと）
     * @return ロールの ID ごとの数
     */
    @Query("select new cherry.mastersmith.role.repository.AssignmentCount(a.roleId, count(a))"
            + " from GroupRoleAssignment a where a.roleId in :roleIds group by a.roleId")
    List<AssignmentCount> countGroupsByRoles(@Param("roleIds") Collection<Long> roleIds);

    /**
     * ロールの利用者への直接の割り当ての数を返す（削除の確かめ。BR3.2・BR3.4）。
     *
     * @param roleId ロールの ID
     * @return 数
     */
    @Query("select count(a) from UserRoleAssignment a where a.roleId = :roleId")
    long countUsersOfRole(@Param("roleId") long roleId);

    /**
     * ロールのグループへの割り当ての数を返す（削除の確かめ。BR3.2・BR3.4）。
     *
     * @param roleId ロールの ID
     * @return 数
     */
    @Query("select count(a) from GroupRoleAssignment a where a.roleId = :roleId")
    long countGroupsOfRole(@Param("roleId") long roleId);

    /**
     * グループの ID の集合で、グループへの割り当ての数をまとめて1回で数える（group の問う口。BR10.1・BR10.2）。割り当てが無いグループは
     * 行を持たない。
     *
     * @param groupIds グループの ID の集合（空でないこと）
     * @return グループの ID ごとの数
     */
    @Query("select new cherry.mastersmith.role.repository.AssignmentCount(a.groupId, count(a))"
            + " from GroupRoleAssignment a where a.groupId in :groupIds group by a.groupId")
    List<AssignmentCount> countByGroups(@Param("groupIds") Collection<Long> groupIds);
}
