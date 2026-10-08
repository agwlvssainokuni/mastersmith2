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

import cherry.mastersmith.role.domain.WorkRoleSelection;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 作業ロールの保存の表の読み取り（{@code logical-components.md} の L10、BR7.2・BR7.6）。書き込みの方法を持たない。 */
public interface WorkRoleSelectionRepository extends Repository<WorkRoleSelection, Long> {

    /**
     * 利用者の保存した作業ロールの ID を読む（有効な作業ロールを決める4回の読み取りの1つ。NFR2.3）。
     *
     * @param userId 利用者 ID
     * @return ロールの ID（保存が無ければ空）
     */
    @Query("select w.roleId from WorkRoleSelection w where w.userId = :userId")
    Optional<Long> findRoleIdOfUser(@Param("userId") long userId);
}
