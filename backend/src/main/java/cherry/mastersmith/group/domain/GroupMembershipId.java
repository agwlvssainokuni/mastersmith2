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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** メンバーの行の主キー（グループの ID と利用者 ID の組。JPA の {@code @IdClass}）。 */
public class GroupMembershipId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long groupId;

    private Long userId;

    /** JPA が使う。 */
    protected GroupMembershipId() {}

    /**
     * 主キーを作る。
     *
     * @param groupId グループの ID
     * @param userId 利用者 ID
     */
    public GroupMembershipId(long groupId, long userId) {
        this.groupId = groupId;
        this.userId = userId;
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

    @Override
    public boolean equals(Object other) {
        return other instanceof GroupMembershipId id
                && Objects.equals(groupId, id.groupId)
                && Objects.equals(userId, id.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }

    @Override
    public String toString() {
        return "GroupMembershipId[groupId=" + groupId + ", userId=" + userId + "]";
    }
}
