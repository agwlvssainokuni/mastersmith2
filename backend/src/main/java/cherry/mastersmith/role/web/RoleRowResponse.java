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

import cherry.mastersmith.role.service.RoleListResult;

/**
 * 一覧の1行の応答（契約 C7 の RoleRow、BR3.4）。
 *
 * @param roleId ロールの ID
 * @param name 名前
 * @param userCount 利用者への直接の割り当ての数
 * @param groupCount グループへの割り当ての数
 */
public record RoleRowResponse(long roleId, String name, long userCount, long groupCount) {

    /**
     * 業務処理の1行から作る。
     *
     * @param row 一覧の1行
     * @return 応答の1行
     */
    static RoleRowResponse from(RoleListResult.Row row) {
        return new RoleRowResponse(row.roleId(), row.name(), row.userCount(), row.groupCount());
    }
}
