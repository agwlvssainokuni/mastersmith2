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

import cherry.mastersmith.role.domain.UserRoleView;
import java.util.List;

/**
 * 利用者のロールと出どころの応答（契約 C7 の UserRole、BR6.5）。
 *
 * @param roleId ロールの ID
 * @param name ロールの名前
 * @param sources 出どころ（直接を先に、グループはグループの ID の順）
 */
public record UserRoleResponse(long roleId, String name, List<RoleSourceResponse> sources) {

    static UserRoleResponse from(UserRoleView view) {
        return new UserRoleResponse(
                view.roleId(),
                view.name(),
                view.sources().stream().map(RoleSourceResponse::from).toList());
    }
}
