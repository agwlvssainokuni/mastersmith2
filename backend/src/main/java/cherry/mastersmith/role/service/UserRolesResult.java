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

import cherry.mastersmith.role.domain.UserRoleView;
import java.util.List;

/** 利用者のロールの読み取りの結果（契約 C7 の {@code GET /api/admin/users/{userId}/roles}、FS の 2.8、BR6.5）。 */
public sealed interface UserRolesResult permits UserRolesResult.Found, UserRolesResult.UserNotFound {

    /**
     * 利用者のロールと出どころ。
     *
     * @param roles ロール（ロールの ID の順）
     */
    record Found(List<UserRoleView> roles) implements UserRolesResult {

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

    /** 利用者がいない（404 user の {@code USER_NOT_FOUND}。招待中の人を含む。監査なし）。 */
    record UserNotFound() implements UserRolesResult {}
}
