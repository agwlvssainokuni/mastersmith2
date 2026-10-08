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

import cherry.mastersmith.role.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** ロールの表の読み取り（BR3.5・BR3.7・BR1.4）。書き込みの方法を持たない。 */
public interface RoleRepository extends Repository<Role, Long> {

    /**
     * ロールの数を返す（一覧の全体の件数）。
     *
     * @return 件数
     */
    @Query("select count(r) from Role r")
    long countAll();

    /**
     * ロールの ID の順に1ページ分を読む（BR3.5）。
     *
     * @param pageable ページ（読み始めの位置と件数）
     * @return ID と名前の一覧
     */
    @Query("select new cherry.mastersmith.role.repository.RoleRowView(r.roleId, r.name) from Role r order by r.roleId")
    List<RoleRowView> findPage(Pageable pageable);

    /**
     * 1つのロールを排他なしで読む（BR3.7）。
     *
     * @param roleId ロールの ID
     * @return ロール（いなければ空）
     */
    @Query("select new cherry.mastersmith.role.repository.RoleView(r.roleId, r.name, r.createdAt, r.updatedAt)"
            + " from Role r where r.roleId = :roleId")
    Optional<RoleView> findView(@Param("roleId") long roleId);

    /**
     * ロールがいるかを返す（木の読み取りの入口。BR4.10）。
     *
     * @param roleId ロールの ID
     * @return いれば true
     */
    @Query("select count(r) > 0 from Role r where r.roleId = :roleId")
    boolean existsRole(@Param("roleId") long roleId);

    /**
     * 名前の鍵が同じロールの ID を読む（書く前の重なりの判定。BR1.4）。鍵は {@code RoleName} が作った値を渡す。
     *
     * @param nameKey 名前の鍵
     * @return 同じ鍵のロールの ID（いなければ空）
     */
    @Query("select r.roleId from Role r where r.nameKey = :nameKey")
    Optional<Long> findIdByNameKey(@Param("nameKey") String nameKey);
}
