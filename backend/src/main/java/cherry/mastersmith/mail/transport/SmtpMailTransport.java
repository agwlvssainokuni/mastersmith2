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
package cherry.mastersmith.mail.transport;

import cherry.mastersmith.mail.config.MailSettings;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.domain.MailUnexpectedException;
import cherry.mastersmith.mail.template.RenderedMail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * メールの組み立てと SMTP での1回だけの送信（BR5.1〜BR5.3、NFR2.8・NFR6.1・NFR6.3）。
 *
 * <p>送信の失敗を {@link SendFailureClassifier} で分類し、分類できたものは失敗の種類で返す。想定外のときだけ
 * {@link MailUnexpectedException#of(Throwable)} で包んで投げる。U1 の中で、送信の失敗の例外を投げるのはこの部品だけ（NFR 設計の
 * 承認の場の U1 R-02）。
 */
@Component
public class SmtpMailTransport {

    /**
     * メールを組み立て、1回だけ送る。自動でやり直さない。
     *
     * @param settings 使える設定（送信の部品・差出人）
     * @param mail 描いた件名と本文
     * @param to 宛先（確かめ済みの正規化されたメールアドレス）
     * @return 送信の試みの結果
     * @throws MailUnexpectedException 組み立ての失敗や、分類できない送信の失敗のとき
     */
    public SendAttempt send(MailSettings.Usable settings, RenderedMail mail, String to) {
        JavaMailSenderImpl sender = settings.sender();
        MimeMessage message = compose(sender, settings, mail, to);
        try {
            sender.send(message);
            return new SendAttempt(MailSendResult.sent(), null);
        } catch (MailException e) {
            Optional<MailFailureKind> kind = SendFailureClassifier.classify(e);
            if (kind.isEmpty()) {
                throw MailUnexpectedException.of(e);
            }
            return new SendAttempt(
                    MailSendResult.failed(kind.get()), e.getClass().getName());
        } catch (RuntimeException e) {
            throw MailUnexpectedException.of(e);
        }
    }

    /**
     * HTML だけ（text/html、UTF-8）のメールを組み立てる。差出人・宛先1人・件名は部品の API で入れ、ヘッダーを文字列で直接足す
     * API は使わない（件名と表示名は規格どおりに符号化される。BR5.1、NFR2.8）。
     */
    static MimeMessage compose(JavaMailSenderImpl sender, MailSettings.Usable settings, RenderedMail mail, String to) {
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.from());
            helper.setTo(to);
            // 件名は title の文面から改行を除いて作ったもの（BR4.2）で、部品が RFC 2047 で符号化する。
            helper.setSubject(mail.subject());
            helper.setText(mail.htmlBody(), true);
        } catch (MessagingException | RuntimeException e) {
            throw MailUnexpectedException.of(e);
        }
        return message;
    }
}
