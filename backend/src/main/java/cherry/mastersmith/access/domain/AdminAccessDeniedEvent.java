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
package cherry.mastersmith.access.domain;

import cherry.mastersmith.auth.domain.ClientInfo;
import java.time.Instant;
import java.util.Objects;

/**
 * 管理者のみの API へのアクセスを拒否した出来事（BR3.1〜BR3.4。U4 が監査ログに記録する）。
 *
 * <p>U3 は {@code ApplicationEventPublisher} で知らせるだけで、受け取り側を知らない（ADR-004、BR3.5）。受け取りは要求と同じ
 * スレッドで、応答を書く前に行われる（U3 は内部DBの更新を伴わないため、U2 の認証の出来事と異なり確定の後ではない）。
 *
 * <p>項目は監査ログの記録項目にそろえる。アクセストークン・Authorization ヘッダー・パスワードの項目は持たない（NFR3.7）。
 * 文字列化ではメールアドレスを伏せる（アプリのログにメールアドレスを出さない。NFR10.3）。
 *
 * @param eventType 種類（{@link AccessEventType#ACCESS_DENIED}）
 * @param occurredAt 日時（UTC の時計から得た時点）
 * @param result 結果（{@link AccessResult#FAILURE}）
 * @param failureReason 拒否の理由
 * @param enteredEmail 利用者のメールアドレス（特定できたときだけ。分からなければ null）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（512 文字で切ったもの。無ければ null）
 * @param requestPath 要求のパス（正規化済み、問い合わせの部分を除き、512 文字で切ったもの）
 * @param traceId トレースID（無ければ null）
 */
public record AdminAccessDeniedEvent(
        AccessEventType eventType,
        Instant occurredAt,
        AccessResult result,
        AccessDeniedReason failureReason,
        String enteredEmail,
        String sourceIp,
        String userAgent,
        String requestPath,
        String traceId) {

    /** 要求のパスの長さの上限（コードポイントで数える）。 */
    public static final int MAX_REQUEST_PATH_LENGTH = 512;

    /** 必ずある項目を確かめ、要求のパスを正規化する。 */
    public AdminAccessDeniedEvent {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(failureReason, "failureReason");
        requestPath = normalizeRequestPath(requestPath);
    }

    /**
     * 送り手の情報を使って出来事を作る。
     *
     * @param occurredAt 日時
     * @param failureReason 拒否の理由
     * @param enteredEmail 利用者のメールアドレス（分からなければ null）
     * @param requestPath 要求のパス
     * @param client 送り手の情報
     * @return 出来事
     */
    public static AdminAccessDeniedEvent of(
            Instant occurredAt,
            AccessDeniedReason failureReason,
            String enteredEmail,
            String requestPath,
            ClientInfo client) {
        return new AdminAccessDeniedEvent(
                AccessEventType.ACCESS_DENIED,
                occurredAt,
                AccessResult.FAILURE,
                failureReason,
                enteredEmail,
                client.sourceIp(),
                client.userAgent(),
                requestPath,
                client.traceId());
    }

    /**
     * 要求のパスを、問い合わせの部分を除き、上限の長さで切りそろえる。
     *
     * @param requestPath 要求のパス（null でもよい）
     * @return 正規化したパス
     */
    public static String normalizeRequestPath(String requestPath) {
        if (requestPath == null) {
            return null;
        }
        int query = requestPath.indexOf('?');
        String path = query < 0 ? requestPath : requestPath.substring(0, query);
        int length = path.codePointCount(0, path.length());
        if (length <= MAX_REQUEST_PATH_LENGTH) {
            return path;
        }
        return path.substring(0, path.offsetByCodePoints(0, MAX_REQUEST_PATH_LENGTH));
    }

    /** メールアドレスを伏せて文字列にする（アプリのログにメールアドレスを出さないため）。 */
    @Override
    public String toString() {
        return "AdminAccessDeniedEvent[eventType=" + eventType
                + ", occurredAt=" + occurredAt
                + ", result=" + result
                + ", failureReason=" + failureReason
                + ", enteredEmail=" + (enteredEmail == null ? "null" : "***")
                + ", sourceIp=" + sourceIp
                + ", userAgent=" + userAgent
                + ", requestPath=" + requestPath
                + ", traceId=" + traceId
                + "]";
    }
}
