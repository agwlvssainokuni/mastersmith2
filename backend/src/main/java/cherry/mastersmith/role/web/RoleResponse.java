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

import cherry.mastersmith.role.service.RoleDetail;
import java.time.Instant;

/**
 * ロール1件の応答（契約 C7 の Role。作成と1件の読み取り）。
 *
 * @param roleId ロールの ID
 * @param name 名前
 * @param createdAt 作成の日時
 * @param updatedAt 更新の日時
 */
public record RoleResponse(long roleId, String name, Instant createdAt, Instant updatedAt) {

    /**
     * 業務処理の値から作る。
     *
     * @param role ロール
     * @return 応答
     */
    static RoleResponse from(RoleDetail role) {
        return new RoleResponse(role.roleId(), role.name(), role.createdAt(), role.updatedAt());
    }
}
