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
package cherry.mastersmith.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * グループのメンバー（表 {@code group_members}。{@code entities.md} の GroupMembership）。1人の利用者が1つのグループに属すること。
 *
 * <p>主キーはグループの ID と利用者 ID の組（重ねての追加は主キーの違反。BR3.3・BR5.5）。グループへの外部キー（削除の制限）は最後の
 * 守り（BR5.4）、利用者への外部キーは登録の終わった利用者だけを指す（BR3.1）。足し外しは {@code group.store} の {@code GroupStore}
 * だけが行う。作った後に値を変える手段を持たない。
 */
@Entity
@Table(name = "group_members")
@IdClass(GroupMembershipId.class)
public class GroupMembership {

    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    /** JPA が使う。 */
    protected GroupMembership() {}

    /**
     * メンバーの行を作る。
     *
     * @param groupId グループの ID
     * @param userId 利用者 ID
     * @param addedAt 足した日時（注入した時計の値）
     */
    public GroupMembership(long groupId, long userId, Instant addedAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.addedAt = Objects.requireNonNull(addedAt, "addedAt");
    }

    /**
     * グループの ID を返す。
     *
     * @return グループの ID
     */
    public Long getGroupId() {
        return groupId;
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
     * 足した日時を返す。
     *
     * @return 日時
     */
    public Instant getAddedAt() {
        return addedAt;
    }

    @Override
    public String toString() {
        return "GroupMembership[groupId=" + groupId + ", userId=" + userId + ", addedAt=" + addedAt + "]";
    }
}
