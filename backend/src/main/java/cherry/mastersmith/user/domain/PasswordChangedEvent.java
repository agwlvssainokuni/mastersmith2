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
package cherry.mastersmith.user.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * パスワードの変更の出来事（アプリの中の知らせ、保存しない。契約 C8 の PASSWORD_CHANGED、BR7.2・BR7.3）。
 *
 * <p>UserAccount が成功と今のパスワードの誤りのときに出し、AuditLog が確定の後に受けて監査イベントを1件追記する。入力の誤りでは出さない。
 * パスワードの値・ハッシュ・メールアドレスを持たない（BR7.4）。
 *
 * @param userId 変えた（変えようとした）本人の利用者 ID
 * @param result 結果
 * @param failureReason 失敗の理由（結果が失敗のときだけ。成功なら null）
 * @param occurredAt 出来事が起きた日時（注入できる時計の値）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（512 文字で切った値。無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record PasswordChangedEvent(
        long userId,
        PasswordChangeOutcome result,
        PasswordChangeFailureReason failureReason,
        Instant occurredAt,
        String sourceIp,
        String userAgent,
        String traceId) {

    /** 結果と失敗の理由の組み合わせと、必須の値を確かめる。 */
    public PasswordChangedEvent {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sourceIp, "sourceIp");
        if (result == PasswordChangeOutcome.SUCCESS && failureReason != null) {
            throw new IllegalArgumentException("成功の出来事に失敗の理由は付けられません");
        }
        if (result == PasswordChangeOutcome.FAILURE && failureReason == null) {
            throw new IllegalArgumentException("失敗の出来事には失敗の理由が要ります");
        }
    }

    /**
     * 成功の出来事を作る。
     *
     * @param userId 本人の利用者 ID
     * @param occurredAt 出来事が起きた日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static PasswordChangedEvent succeeded(long userId, Instant occurredAt, RequestOrigin origin) {
        return new PasswordChangedEvent(
                userId,
                PasswordChangeOutcome.SUCCESS,
                null,
                occurredAt,
                origin.sourceIp(),
                origin.userAgent(),
                origin.traceId());
    }

    /**
     * 失敗の出来事を作る。
     *
     * @param userId 本人の利用者 ID
     * @param reason 失敗の理由
     * @param occurredAt 出来事が起きた日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static PasswordChangedEvent failed(
            long userId, PasswordChangeFailureReason reason, Instant occurredAt, RequestOrigin origin) {
        return new PasswordChangedEvent(
                userId,
                PasswordChangeOutcome.FAILURE,
                Objects.requireNonNull(reason, "reason"),
                occurredAt,
                origin.sourceIp(),
                origin.userAgent(),
                origin.traceId());
    }
}
