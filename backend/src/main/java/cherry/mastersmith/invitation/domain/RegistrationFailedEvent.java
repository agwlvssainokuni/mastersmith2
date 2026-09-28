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
package cherry.mastersmith.invitation.domain;

import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Instant;
import java.util.Objects;

/**
 * 登録の完了の要求のリンクを拒否したことの出来事（アプリの中の知らせ、保存しない。契約 C8 の REGISTRATION_FAILED、BR8.3）。
 *
 * <p>トランザクションが巻き戻った後（または何も書かなかった後）に要求の中で知らせ、AuditLog がその場で結果 FAILURE と理由で1件追記する。
 * リンクの確かめ（verify）の失敗と入力の誤りでは出さない。トークン・ハッシュ・URL・パスワード・メールアドレスを持たない（BR8.6）。
 *
 * @param invitationId 招待の ID（トークンから招待が見つかったときだけ。見つからないときは null）
 * @param reason 拒否の理由（BR7.6）
 * @param occurredAt 出来事が起きた日時（注入できる時計の値）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（512 文字で切った値。無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record RegistrationFailedEvent(
        Long invitationId,
        LinkRejection reason,
        Instant occurredAt,
        String sourceIp,
        String userAgent,
        String traceId) {

    /** 必須の値と、理由と招待の ID の組み合わせを確かめる。 */
    public RegistrationFailedEvent {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sourceIp, "sourceIp");
        if (reason == LinkRejection.INVITATION_NOT_FOUND && invitationId != null) {
            throw new IllegalArgumentException("見つからない招待の出来事は招待の ID を持ちません");
        }
        if (reason != LinkRejection.INVITATION_NOT_FOUND && invitationId == null) {
            throw new IllegalArgumentException("見つかった招待の拒否の出来事は招待の ID が要ります");
        }
    }

    /**
     * 出来事を作る。
     *
     * @param invitationId 招待の ID（見つからないときは null）
     * @param reason 拒否の理由
     * @param occurredAt 出来事が起きた日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static RegistrationFailedEvent of(
            Long invitationId, LinkRejection reason, Instant occurredAt, RequestOrigin origin) {
        return new RegistrationFailedEvent(
                invitationId, reason, occurredAt, origin.sourceIp(), origin.userAgent(), origin.traceId());
    }
}
