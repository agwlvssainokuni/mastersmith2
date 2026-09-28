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
package cherry.mastersmith.invitation.web;

import cherry.mastersmith.invitation.service.InvitationPage;
import java.util.List;

/**
 * 招待の一覧の応答（契約 C5 の InvitationPage）。
 *
 * @param items 招待
 * @param page ページ
 * @param size 1ページの件数（20）
 * @param total 招待中の件数
 * @param invitationEnabled 招待を使える設定なら true
 * @param unavailableReasons 使えない理由
 */
public record InvitationPageResponse(
        List<InvitationResponse> items,
        int page,
        int size,
        long total,
        boolean invitationEnabled,
        List<String> unavailableReasons) {

    /**
     * 業務処理の1ページから作る。
     *
     * @param page 1ページ
     * @return 応答
     */
    public static InvitationPageResponse from(InvitationPage page) {
        return new InvitationPageResponse(
                page.items().stream().map(InvitationResponse::from).toList(),
                page.page(),
                page.size(),
                page.total(),
                page.invitationEnabled(),
                page.unavailableReasons().stream().map(Enum::name).toList());
    }
}
