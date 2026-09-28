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
 * 登録を完了したことの出来事（アプリの中の知らせ、保存しない。契約 C8 の REGISTRATION_COMPLETED、BR8.2）。
 *
 * <p>登録の完了のトランザクションの中で知らせ、確定の後に AuditLog が受けて、操作した人を空、対象の招待と作った利用者で1件追記する。
 * 巻き戻ったときは記録されない。トークン・ハッシュ・URL・パスワード・メールアドレスを持たない（BR8.6）。
 *
 * @param invitationId 完了した招待の ID
 * @param userId 作った利用者の ID
 * @param occurredAt 出来事が起きた日時（注入できる時計の値）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（512 文字で切った値。無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record RegistrationCompletedEvent(
        long invitationId, long userId, Instant occurredAt, String sourceIp, String userAgent, String traceId) {

    /** 必須の値を確かめる。 */
    public RegistrationCompletedEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sourceIp, "sourceIp");
    }

    /**
     * 出来事を作る。
     *
     * @param invitationId 完了した招待の ID
     * @param userId 作った利用者の ID
     * @param occurredAt 出来事が起きた日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static RegistrationCompletedEvent of(
            long invitationId, long userId, Instant occurredAt, RequestOrigin origin) {
        return new RegistrationCompletedEvent(
                invitationId, userId, occurredAt, origin.sourceIp(), origin.userAgent(), origin.traceId());
    }
}
