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
package cherry.mastersmith.role.web;

import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.service.RoleRef;

/**
 * ロールの参照の応答（契約 C7 の RoleRef・C8 の roles と current の1件）。
 *
 * @param roleId ロールの ID
 * @param name ロールの名前
 */
public record RoleRefResponse(long roleId, String name) {

    static RoleRefResponse from(RoleRef role) {
        return new RoleRefResponse(role.roleId(), role.name());
    }

    static RoleRefResponse from(WorkRoleRef role) {
        return new RoleRefResponse(role.roleId(), role.name());
    }
}
