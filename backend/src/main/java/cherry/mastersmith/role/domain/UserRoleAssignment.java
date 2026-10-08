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
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * 利用者への直接のロールの割り当て（表 {@code user_role_assignments}。{@code entities.md} の UserRoleAssignment、BR6.1〜BR6.3・BR6.7）。
 *
 * <p>主キーはロールの ID と利用者 ID の組（重ねての割り当ては主キーの違反。BR6.3）。ロールと利用者への外部キー（削除の制限）は最後の
 * 守り（BR6.7）。足し外しは {@code role.store} の {@code RoleStore} だけが行う。作った後に値を変える手段を持たない。JPQL のエンティティ
 * 名は {@code UserRoleAssignment}（計画の D-2）。
 */
@Entity(name = "UserRoleAssignment")
@Table(name = "user_role_assignments")
@IdClass(UserRoleAssignmentId.class)
public class UserRoleAssignment {

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    /** JPA が使う。 */
    protected UserRoleAssignment() {}

    /**
     * 割り当ての行を作る。
     *
     * @param roleId ロールの ID
     * @param userId 利用者 ID
     * @param assignedAt 割り当てた日時（注入した時計の値）
     */
    public UserRoleAssignment(long roleId, long userId, Instant assignedAt) {
        this.roleId = roleId;
        this.userId = userId;
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt");
    }

    /**
     * ロールの ID を返す。
     *
     * @return ロールの ID
     */
    public Long getRoleId() {
        return roleId;
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
     * 割り当てた日時を返す。
     *
     * @return 日時
     */
    public Instant getAssignedAt() {
        return assignedAt;
    }

    /** ID だけを出す。 */
    @Override
    public String toString() {
        return "UserRoleAssignment[roleId=" + roleId + ", userId=" + userId + "]";
    }
}
