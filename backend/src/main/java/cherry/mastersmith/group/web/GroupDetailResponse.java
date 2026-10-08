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

import cherry.mastersmith.group.service.GroupDetailResult;
import java.util.List;

/**
 * グループの詳細の応答（契約 C6 の GroupDetail、BR7.4）。メンバーは足した順に全件。文字列にするとメンバーの氏名とメールアドレスを
 * 伏せる（{@link GroupMemberResponse#toString()}）。
 *
 * @param groupId グループの ID
 * @param name 名前
 * @param members メンバー
 */
public record GroupDetailResponse(long groupId, String name, List<GroupMemberResponse> members) {

    /** メンバーを写して持つ。 */
    public GroupDetailResponse {
        members = List.copyOf(members);
    }

    /**
     * 業務処理の詳細から作る。
     *
     * @param found 詳細
     * @return 応答
     */
    static GroupDetailResponse from(GroupDetailResult.Found found) {
        return new GroupDetailResponse(
                found.groupId(),
                found.name(),
                found.members().stream().map(GroupMemberResponse::from).toList());
    }
}
