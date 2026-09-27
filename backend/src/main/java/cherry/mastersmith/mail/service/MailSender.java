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
package cherry.mastersmith.mail.service;

import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.domain.MailUnexpectedException;

/**
 * メールの送信（契約 C1）。呼び出し元（U3）は、招待の確定の後にトランザクションの外で呼ぶ（ADR-009、BR5.4）。
 *
 * <p>同時に呼ばれてもよい（準備したテンプレートと設定は読むだけ）。
 */
public interface MailSender {

    /**
     * SMTP の設定があるか（接続先と差出人がそろい、設定に不正が無いか）を返す。設定の値や理由は返さない（BR1.7）。
     *
     * @return 送れる設定なら true
     */
    boolean isConfigured();

    /**
     * メールを1通、1回だけ送る。想定内の失敗は例外にせず、失敗の種類の結果で返す（BR5.3）。
     *
     * @param request 送信の依頼
     * @return 送信の結果（成功、または失敗とその種類）
     * @throws MailUnexpectedException U1 の不具合など想定外の失敗のとき（文言と原因に秘密を持たない）
     */
    MailSendResult send(MailRequest request);
}
