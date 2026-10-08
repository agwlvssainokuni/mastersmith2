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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** グループへの割り当ての主キー（ロールの ID とグループの ID の組）。 */
public class GroupRoleAssignmentId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long roleId;

    private Long groupId;

    /** JPA が使う。 */
    protected GroupRoleAssignmentId() {}

    /**
     * 主キーを作る。
     *
     * @param roleId ロールの ID
     * @param groupId グループの ID
     */
    public GroupRoleAssignmentId(long roleId, long groupId) {
        this.roleId = roleId;
        this.groupId = groupId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GroupRoleAssignmentId id
                && Objects.equals(roleId, id.roleId)
                && Objects.equals(groupId, id.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, groupId);
    }

    @Override
    public String toString() {
        return "GroupRoleAssignmentId[roleId=" + roleId + ", groupId=" + groupId + "]";
    }
}
