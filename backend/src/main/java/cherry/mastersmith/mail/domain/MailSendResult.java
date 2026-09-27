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
package cherry.mastersmith.mail.domain;

import java.util.Objects;

/**
 * 送信の結果（契約 C1 の MailSendResult、BR6.1）。成功か、失敗とその種類だけを持つ。
 *
 * <p>宛先・SMTP の応答・例外のメッセージ・資格情報・差し込んだ値・件名は持たない。
 *
 * @param outcome 成否
 * @param failureKind 失敗の種類（成功のときは null、失敗のときは必須）
 */
public record MailSendResult(MailOutcome outcome, MailFailureKind failureKind) {

    private static final MailSendResult SENT = new MailSendResult(MailOutcome.SENT, null);

    /** 成否と失敗の種類の組み合わせを確かめる。 */
    public MailSendResult {
        Objects.requireNonNull(outcome, "outcome は必須です");
        if (outcome == MailOutcome.SENT && failureKind != null) {
            throw new IllegalArgumentException("成功の結果は失敗の種類を持ちません");
        }
        if (outcome == MailOutcome.FAILED && failureKind == null) {
            throw new IllegalArgumentException("失敗の結果は失敗の種類が必須です");
        }
    }

    /**
     * 成功の結果を返す。
     *
     * @return 成功の結果
     */
    public static MailSendResult sent() {
        return SENT;
    }

    /**
     * 失敗の結果を作る。
     *
     * @param failureKind 失敗の種類
     * @return 失敗の結果
     */
    public static MailSendResult failed(MailFailureKind failureKind) {
        return new MailSendResult(MailOutcome.FAILED, Objects.requireNonNull(failureKind, "failureKind は必須です"));
    }

    /**
     * 成功かを返す。
     *
     * @return 成功なら true
     */
    public boolean isSent() {
        return outcome == MailOutcome.SENT;
    }
}
