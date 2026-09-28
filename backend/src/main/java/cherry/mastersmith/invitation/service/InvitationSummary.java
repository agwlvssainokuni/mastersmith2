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
package cherry.mastersmith.invitation.service;

import java.time.Instant;

/**
 * 招待の要約（一覧・招待・送り直しの応答に渡す。契約 C5 の Invitation、BR5.3・BR5.4）。トークン・ハッシュ・招待の URL を持たない。
 * 文字列にするとメールアドレスと招待した管理者の氏名を伏せる。
 *
 * @param invitationId 招待の ID
 * @param email 招待先のメールアドレス（正規化済み）
 * @param language 招待の言語（{@code ja}・{@code en}）
 * @param invitedBy 招待した管理者の氏名（利用者の行が無ければ空の文字列）
 * @param invitedAt 招待した時点
 * @param expiresAt 有効期限
 * @param sendResult 送信の結果（{@code SENT}・{@code FAILED}）
 * @param expired 期限切れなら true
 */
public record InvitationSummary(
        long invitationId,
        String email,
        String language,
        String invitedBy,
        Instant invitedAt,
        Instant expiresAt,
        String sendResult,
        boolean expired) {

    /** メールアドレスと氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "InvitationSummary[invitationId=" + invitationId + ", email=***, language=" + language
                + ", invitedBy=***, invitedAt=" + invitedAt + ", expiresAt=" + expiresAt + ", sendResult=" + sendResult
                + ", expired=" + expired + "]";
    }
}
