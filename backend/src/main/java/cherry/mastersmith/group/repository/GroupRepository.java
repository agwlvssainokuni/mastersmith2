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

import cherry.mastersmith.group.domain.Group;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * グループの表の読み取り（BR7.1〜BR7.4・BR1.4・BR6.4、{@code performance-design.md} 1節）。
 *
 * <p>Spring Data の {@code Repository} だけを継ぎ、書き込みの方法・行の排他・{@code @Modifying} を置かない（計画の D-4、
 * {@code security-design.md} 4.1）。問い合わせは定数の JPQL と名前の付いた引数だけで組む（NFR1.9）。JPQL のエンティティ名は
 * {@code UserGroup}（計画の D-3）。
 */
public interface GroupRepository extends Repository<Group, Long> {

    /**
     * グループの数を返す（一覧の全体の件数。BR7.1）。
     *
     * @return 件数
     */
    @Query("select count(g) from UserGroup g")
    long countAll();

    /**
     * グループの ID の順に1ページ分を読む（BR7.2）。
     *
     * @param pageable ページ（読み始めの位置と件数）
     * @return ID と名前の一覧
     */
    @Query("select new cherry.mastersmith.group.repository.GroupRowView(g.groupId, g.name) from UserGroup g"
            + " order by g.groupId")
    List<GroupRowView> findPage(Pageable pageable);

    /**
     * 1つのグループの ID と名前を排他なしで読む（詳細。BR7.4）。
     *
     * @param groupId グループの ID
     * @return ID と名前（いなければ空）
     */
    @Query("select new cherry.mastersmith.group.repository.GroupRowView(g.groupId, g.name) from UserGroup g"
            + " where g.groupId = :groupId")
    Optional<GroupRowView> findView(@Param("groupId") long groupId);

    /**
     * 複数のグループの ID と名前を ID の順に読む（要約。いない ID は含めない。BR6.4）。
     *
     * @param groupIds グループの ID（空でないこと）
     * @return ID と名前の一覧
     */
    @Query("select new cherry.mastersmith.group.repository.GroupRowView(g.groupId, g.name) from UserGroup g"
            + " where g.groupId in :groupIds order by g.groupId")
    List<GroupRowView> findViews(@Param("groupIds") Collection<Long> groupIds);

    /**
     * グループがいるかを返す（BR6.4）。
     *
     * @param groupId グループの ID
     * @return いれば true
     */
    @Query("select count(g) > 0 from UserGroup g where g.groupId = :groupId")
    boolean existsGroup(@Param("groupId") long groupId);

    /**
     * 名前の鍵が同じグループの ID を読む（書く前の重なりの判定。BR1.4）。鍵は {@code GroupName} が作った値を渡す。
     *
     * @param nameKey 名前の鍵
     * @return 同じ鍵のグループの ID（いなければ空）
     */
    @Query("select g.groupId from UserGroup g where g.nameKey = :nameKey")
    Optional<Long> findIdByNameKey(@Param("nameKey") String nameKey);
}
