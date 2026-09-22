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
package cherry.mastersmith.auth.domain;

import java.time.Instant;

/**
 * 認証の出来事（BR7.1、BR7.2。U4 が監査ログに記録する）。パスワード・トークン・ハッシュ値の項目は持たない。
 *
 * <p>U2 のトランザクションの中で {@code ApplicationEventPublisher} で知らせる。受け取り側は
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} で確定の後に同じスレッドで記録する（取り消されたら記録しない）。
 *
 * @param eventType 種類
 * @param occurredAt 日時
 * @param enteredEmail 入力されたメールアドレス（前後の空白を除き小文字にそろえた値）
 * @param userId 利用者ID（分からなければ null）
 * @param failureReason 失敗の理由（失敗のときだけ）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（512 文字で切ったもの）
 * @param traceId トレースID（無ければ null）
 */
public record AuthenticationEvent(
        AuthenticationEventType eventType,
        Instant occurredAt,
        String enteredEmail,
        Long userId,
        LoginFailureReason failureReason,
        String sourceIp,
        String userAgent,
        String traceId) {

    /**
     * 送り手の情報を使って出来事を作る。
     *
     * @param eventType 種類
     * @param occurredAt 日時
     * @param enteredEmail 入力されたメールアドレス
     * @param userId 利用者ID（分からなければ null）
     * @param failureReason 失敗の理由（失敗のときだけ）
     * @param client 送り手の情報
     * @return 出来事
     */
    public static AuthenticationEvent of(
            AuthenticationEventType eventType,
            Instant occurredAt,
            String enteredEmail,
            Long userId,
            LoginFailureReason failureReason,
            ClientInfo client) {
        return new AuthenticationEvent(
                eventType,
                occurredAt,
                enteredEmail,
                userId,
                failureReason,
                client.sourceIp(),
                client.userAgent(),
                client.traceId());
    }
}
