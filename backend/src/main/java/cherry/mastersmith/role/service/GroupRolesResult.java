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
package cherry.mastersmith.role.service;

import java.util.List;

/** グループに割り当てたロールの読み取りの結果（契約 C7 の {@code GET /api/admin/groups/{groupId}/roles}、FS の 2.8）。 */
public sealed interface GroupRolesResult permits GroupRolesResult.Found, GroupRolesResult.GroupNotFound {

    /**
     * 割り当てたロール。
     *
     * @param roles ロール（ロールの ID の順）
     */
    record Found(List<RoleRef> roles) implements GroupRolesResult {

        /** 値を写して持つ。 */
        public Found {
            roles = List.copyOf(roles);
        }

        /** 件数だけを出す。 */
        @Override
        public String toString() {
            return "Found[roles=" + roles.size() + "]";
        }
    }

    /** グループがいない（404 group の {@code GROUP_NOT_FOUND}。監査なし）。 */
    record GroupNotFound() implements GroupRolesResult {}
}
