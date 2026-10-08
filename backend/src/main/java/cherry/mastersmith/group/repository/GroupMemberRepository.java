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

import cherry.mastersmith.group.domain.GroupMembership;
import cherry.mastersmith.group.domain.GroupMembershipId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * メンバーの表の読み取り（BR3.3〜BR3.6・BR4.1・BR6.4・BR6.5・BR7.3・BR7.4、{@code performance-design.md} 1節）。
 *
 * <p>Spring Data の {@code Repository} だけを継ぎ、書き込みの方法・行の排他・{@code @Modifying} を置かない（計画の D-4）。どの問い合わせも
 * 件数・メンバーの数に比例して回数が増えない（NFR2.3・NFR2.6）。
 */
public interface GroupMemberRepository extends Repository<GroupMembership, GroupMembershipId> {

    /**
     * 1つのグループのメンバーの数を返す（削除の判定。BR4.1）。
     *
     * @param groupId グループの ID
     * @return メンバーの数
     */
    @Query("select count(m) from GroupMembership m where m.groupId = :groupId")
    long countByGroup(@Param("groupId") long groupId);

    /**
     * 複数のグループのメンバーの数を {@code GROUP BY} の1回で読む（一覧の1ページ。BR7.3）。メンバーが 0 のグループは行が無い。
     *
     * @param groupIds グループの ID（空でないこと）
     * @return グループごとのメンバーの数
     */
    @Query("select new cherry.mastersmith.group.repository.GroupMemberCount(m.groupId, count(m))"
            + " from GroupMembership m where m.groupId in :groupIds group by m.groupId")
    List<GroupMemberCount> countByGroups(@Param("groupIds") Collection<Long> groupIds);

    /**
     * 1つのグループのメンバーを、足した順（同じなら利用者 ID の順）にすべて読む（詳細。BR7.4）。
     *
     * @param groupId グループの ID
     * @return 利用者 ID と足した日時
     */
    @Query("select new cherry.mastersmith.group.repository.GroupMemberRow(m.userId, m.addedAt)"
            + " from GroupMembership m where m.groupId = :groupId order by m.addedAt, m.userId")
    List<GroupMemberRow> findMembers(@Param("groupId") long groupId);

    /**
     * 利用者がメンバーかを返す（重ねての追加の判定。BR3.3）。
     *
     * @param groupId グループの ID
     * @param userId 利用者 ID
     * @return メンバーなら true
     */
    @Query("select count(m) > 0 from GroupMembership m where m.groupId = :groupId and m.userId = :userId")
    boolean isMember(@Param("groupId") long groupId, @Param("userId") long userId);

    /**
     * 利用者が属するグループの ID を読む（利用者の ID の索引で1回。BR3.6・BR6.4）。
     *
     * @param userId 利用者 ID
     * @return グループの ID の一覧
     */
    @Query("select m.groupId from GroupMembership m where m.userId = :userId order by m.groupId")
    List<Long> findGroupIdsOfUser(@Param("userId") long userId);

    /**
     * 複数のグループのメンバーの利用者 ID を、グループの表からの左の外部結合の1回で読む（BR6.5）。メンバーが 0 のグループは利用者 ID が
     * null の行が1つになり、存在しないグループは行が無い。
     *
     * @param groupIds グループの ID（空でないこと）
     * @return グループとメンバーの組
     */
    @Query("select new cherry.mastersmith.group.repository.GroupMemberPair(g.groupId, m.userId)"
            + " from UserGroup g left join GroupMembership m on m.groupId = g.groupId"
            + " where g.groupId in :groupIds order by g.groupId, m.userId")
    List<GroupMemberPair> findMemberPairs(@Param("groupIds") Collection<Long> groupIds);
}
