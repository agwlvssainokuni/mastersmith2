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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * 利用者が選んだ作業ロールの保存（表 {@code work_role_selections}。{@code entities.md} の WorkRoleSelection、BR7.2〜BR7.7）。1人に1行。
 *
 * <p>選んだロールの ID はロールへの外部キーを持たない（割り当ての外のロール・消えたロールを指すことがあり、そのときは読みで最初のロールに
 * 読み替える。BR7.2・BR7.4）。読み取りの要求では書き換えない（BR7.3）。書き込みと、ロールの削除に伴う削除は {@code role.store} の
 * {@code RoleStore} だけが行う。JPQL のエンティティ名は {@code WorkRoleSelection}（計画の D-2）。
 */
@Entity(name = "WorkRoleSelection")
@Table(name = "work_role_selections")
public class WorkRoleSelection {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA が使う。 */
    protected WorkRoleSelection() {}

    /**
     * 保存の行を作る。
     *
     * @param userId 利用者 ID
     * @param roleId 選んだロールの ID
     * @param updatedAt 書いた日時（注入した時計の値）
     */
    public WorkRoleSelection(long userId, long roleId, Instant updatedAt) {
        this.userId = userId;
        this.roleId = roleId;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * 利用者 ID を返す。
     *
     * @return 利用者 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 選んだロールの ID を返す。
     *
     * @return ロールの ID
     */
    public Long getRoleId() {
        return roleId;
    }

    /**
     * 書いた日時を返す。
     *
     * @return 日時
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** ID だけを出す。 */
    @Override
    public String toString() {
        return "WorkRoleSelection[userId=" + userId + ", roleId=" + roleId + "]";
    }
}
