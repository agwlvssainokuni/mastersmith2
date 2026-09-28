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

import cherry.mastersmith.invitation.service.InvitationSummary;
import java.time.Instant;

/**
 * 招待の応答（契約 C5 の Invitation）。トークン・ハッシュ・招待の URL を持たない（BR5.4）。文字列にするとメールアドレスと氏名を伏せる。
 *
 * @param invitationId 招待の ID
 * @param email 招待先のメールアドレス
 * @param language 招待の言語
 * @param invitedBy 招待した管理者の氏名（無ければ空の文字列）
 * @param invitedAt 招待した日時（ISO 8601 の UTC）
 * @param expiresAt 有効期限（ISO 8601 の UTC）
 * @param sendResult 送信の結果（{@code SENT}・{@code FAILED}）
 * @param expired 期限切れなら true
 */
public record InvitationResponse(
        long invitationId,
        String email,
        String language,
        String invitedBy,
        Instant invitedAt,
        Instant expiresAt,
        String sendResult,
        boolean expired) {

    /**
     * 業務処理の要約から作る。
     *
     * @param summary 要約
     * @return 応答
     */
    public static InvitationResponse from(InvitationSummary summary) {
        return new InvitationResponse(
                summary.invitationId(),
                summary.email(),
                summary.language(),
                summary.invitedBy(),
                summary.invitedAt(),
                summary.expiresAt(),
                summary.sendResult(),
                summary.expired());
    }

    /** メールアドレスと氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "InvitationResponse[invitationId=" + invitationId + ", email=***, invitedBy=***]";
    }
}
