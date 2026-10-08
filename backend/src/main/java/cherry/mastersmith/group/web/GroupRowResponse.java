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
package cherry.mastersmith.group.web;

import cherry.mastersmith.group.service.GroupListResult;

/**
 * 一覧の1行の応答（契約 C6 の GroupRow、BR7.3）。
 *
 * @param groupId グループの ID
 * @param name 名前
 * @param memberCount メンバーの数
 * @param assignedRoleCount 割り当てたロールの数
 */
public record GroupRowResponse(long groupId, String name, long memberCount, int assignedRoleCount) {

    /**
     * 業務処理の1行から作る。
     *
     * @param row 一覧の1行
     * @return 応答の1行
     */
    static GroupRowResponse from(GroupListResult.Row row) {
        return new GroupRowResponse(row.groupId(), row.name(), row.memberCount(), row.assignedRoleCount());
    }
}
