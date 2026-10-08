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

import cherry.mastersmith.role.service.WorkRoleView;
import java.util.List;

/**
 * 自分のロールと今の作業ロールの応答（契約 C8 の {@code GET /api/me/work-role}、BR7.1・BR7.2）。
 *
 * @param roles ロール（ロールの ID の順。先頭が「最初のロール」）
 * @param current 有効な作業ロール（無ければ null）
 */
public record WorkRoleResponse(List<RoleRefResponse> roles, RoleRefResponse current) {

    static WorkRoleResponse from(WorkRoleView view) {
        return new WorkRoleResponse(
                view.roles().stream().map(RoleRefResponse::from).toList(),
                view.current().map(RoleRefResponse::from).orElse(null));
    }
}
