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
 * 招待を取り消したことの出来事（アプリの中の知らせ、保存しない。契約 C8 の INVITATION_CANCELLED、BR8.1）。
 *
 * <p>確定の後に AuditLog が受けて、操作した管理者と対象の招待と結果 SUCCESS の監査イベントを1件追記する。拒否（400・404・409・503）と
 * 送信の失敗では出さない。トークン・ハッシュ・招待の URL・メールアドレスを持たない（BR8.6）。
 *
 * @param invitationId 対象の招待の ID
 * @param actorUserId 操作した管理者の利用者 ID
 * @param occurredAt 出来事が起きた日時（注入できる時計の値）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（512 文字で切った値。無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record InvitationCancelledEvent(
        long invitationId, long actorUserId, Instant occurredAt, String sourceIp, String userAgent, String traceId) {

    /** 必須の値を確かめる。 */
    public InvitationCancelledEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sourceIp, "sourceIp");
    }

    /**
     * 出来事を作る。
     *
     * @param invitationId 対象の招待の ID
     * @param actorUserId 操作した管理者の利用者 ID
     * @param occurredAt 出来事が起きた日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static InvitationCancelledEvent of(
            long invitationId, long actorUserId, Instant occurredAt, RequestOrigin origin) {
        return new InvitationCancelledEvent(
                invitationId, actorUserId, occurredAt, origin.sourceIp(), origin.userAgent(), origin.traceId());
    }
}
