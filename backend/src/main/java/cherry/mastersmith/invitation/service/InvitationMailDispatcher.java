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

import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.RegistrationUrl;
import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.service.MailSender;
import cherry.mastersmith.user.domain.Language;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 招待メールの送信の入口（BR4.1・BR4.2、NFR5.1・NFR2.3、{@code performance-design.md} 2.1、{@code observability-design.md} 2.1）。
 *
 * <ul>
 *   <li>入口でトランザクションの中かを確かめ、中なら想定外の誤り（{@link IllegalStateException}、既存の 500 の扱い）として止める。
 *       送信の間は内部DB の接続を持たない（ADR-009）
 *   <li>U1 の {@code send}（契約 C1）を1回だけ呼ぶ。テンプレート {@code invitation}・招待の言語・招待のメールアドレスと、差し込み
 *       {@code registrationUrl}・{@code validityHours} の2つだけで依頼する
 *   <li>送れなかったときだけ INFO を1件出す（{@code invitationId}・{@code operation}・{@code failureKind}）。失敗の WARN は U1 の1件
 *       だけで、ここでは出さない。宛先・URL・SMTP の応答は出さない。成功ではログを出さない
 *   <li>U1 の想定外の失敗（{@code MailUnexpectedException}）は捕まえず、呼び出し元へ伝える（既存の 500 の扱い）
 * </ul>
 */
@Component
public class InvitationMailDispatcher {

    /** 招待メールのテンプレートの識別（契約 C10）。 */
    static final String TEMPLATE_ID = "invitation";

    /** 送信の失敗の INFO のメッセージ。 */
    static final String FAILURE_MESSAGE = "招待メールを送れませんでした。一覧から送り直せます";

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationMailDispatcher.class);

    private final MailSender mailSender;

    private final InvitationSettings settings;

    /**
     * 作る。
     *
     * @param mailSender メールの送信（U1）
     * @param settings 招待の設定（有効期限の時間の数）
     */
    public InvitationMailDispatcher(MailSender mailSender, InvitationSettings settings) {
        this.mailSender = mailSender;
        this.settings = settings;
    }

    /**
     * 招待メールを1回送る。
     *
     * @param invitationId 招待の ID（失敗の INFO に載せる）
     * @param operation 操作の種類
     * @param to 招待先のメールアドレス
     * @param language 招待の言語
     * @param url 招待の URL
     * @return 送信の結果
     * @throws IllegalStateException トランザクションの中で呼ばれたとき
     */
    public MailSendResult dispatch(
            long invitationId,
            InvitationOperation operation,
            InvitationEmail to,
            Language language,
            RegistrationUrl url) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("招待メールの送信はトランザクションの外で行います");
        }
        MailRequest request = new MailRequest(
                TEMPLATE_ID,
                language.value(),
                to.value(),
                Map.of("registrationUrl", url.value(), "validityHours", settings.validityHours()));
        MailSendResult result = mailSender.send(request);
        if (!result.isSent()) {
            LOGGER.atInfo()
                    .addKeyValue("invitationId", invitationId)
                    .addKeyValue("operation", operation.name())
                    .addKeyValue("failureKind", result.failureKind().name())
                    .log(FAILURE_MESSAGE);
        }
        return result;
    }
}
