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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cherry.mastersmith.mail.config.EncryptionMode;
import cherry.mastersmith.mail.config.MailSettings;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailUnexpectedException;
import cherry.mastersmith.mail.template.RenderedMail;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * メールの組み立てと1回だけの送信の単体テスト（BR5.1・BR5.2、NFR6.3）。送信の部品の {@code send} だけを差し替え、組み立ては
 * 部品の {@code createMimeMessage} で行う。SMTP での実際の送信は結合テスト（MailSendIT ほか）で確かめる。
 */
class SmtpMailTransportTest {

    private static final RenderedMail MAIL =
            new RenderedMail("sample", "ja", "山田 さんへのお知らせ", "<html lang=\"ja\"><p>本文</p></html>");

    private static MailSettings.Usable usable(JavaMailSenderImpl sender, String personal) throws Exception {
        return new MailSettings.Usable(
                sender, new InternetAddress("noreply@example.com", personal, "UTF-8"), EncryptionMode.NONE);
    }

    private static JavaMailSenderImpl stubbedSender() {
        JavaMailSenderImpl sender = spy(new JavaMailSenderImpl());
        doNothing().when(sender).send(any(MimeMessage.class));
        return sender;
    }

    @Test
    @DisplayName("the message is HTML only in UTF-8 with one recipient and encoded subject and display name")
    void composesHtmlOnlyMessage() throws Exception {
        JavaMailSenderImpl sender = stubbedSender();

        SendAttempt attempt = new SmtpMailTransport().send(usable(sender, "MasterSmith"), MAIL, "taro@example.com");

        assertThat(attempt.result().isSent()).isTrue();
        assertThat(attempt.exceptionType()).isNull();
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(1)).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        assertThat(message.getContentType()).startsWith("text/html").containsIgnoringCase("charset=UTF-8");
        assertThat(message.getRecipients(Message.RecipientType.TO))
                .extracting(Object::toString)
                .containsExactly("taro@example.com");
        assertThat(message.getRecipients(Message.RecipientType.CC)).isNull();
        assertThat(message.getSubject()).isEqualTo("山田 さんへのお知らせ");
        assertThat(message.getHeader("Subject")[0]).startsWith("=?UTF-8?").doesNotContain("山田");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("MasterSmith");
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        assertThat(raw.toString(StandardCharsets.UTF_8)).doesNotContain("multipart");
    }

    @Test
    @DisplayName("a Japanese display name is encoded in the From header")
    void japaneseDisplayName() throws Exception {
        MimeMessage message = SmtpMailTransport.compose(
                stubbedSender(), usable(stubbedSender(), "マスタースミス"), MAIL, "taro@example.com");

        assertThat(message.getHeader("From")[0]).startsWith("=?UTF-8?").endsWith("<noreply@example.com>");
    }

    @Test
    @DisplayName("a classified failure is returned with its kind and the exception type, and is sent only once")
    void classifiedFailure() throws Exception {
        JavaMailSenderImpl sender = spy(new JavaMailSenderImpl());
        doThrow(new MailSendException("Mail server connection failed", new java.net.ConnectException("refused")))
                .when(sender)
                .send(any(MimeMessage.class));

        SendAttempt attempt = new SmtpMailTransport().send(usable(sender, "MasterSmith"), MAIL, "taro@example.com");

        assertThat(attempt.result().failureKind()).isEqualTo(MailFailureKind.CONNECTION_FAILED);
        assertThat(attempt.exceptionType()).isEqualTo(MailSendException.class.getName());
        verify(sender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("an unexpected failure is wrapped without the cause and without the message text")
    void unexpectedFailureIsWrapped() throws Exception {
        JavaMailSenderImpl sender = spy(new JavaMailSenderImpl());
        doThrow(new MailPreparationException("cannot prepare for taro@example.com"))
                .when(sender)
                .send(any(MimeMessage.class));
        MailSettings.Usable usable = usable(sender, "MasterSmith");

        assertThatThrownBy(() -> new SmtpMailTransport().send(usable, MAIL, "taro@example.com"))
                .isInstanceOfSatisfying(MailUnexpectedException.class, e -> {
                    assertThat(e.getCause()).isNull();
                    assertThat(e.getMessage()).doesNotContain("taro@example.com");
                    assertThat(e.causeTypeNames()).containsExactly(MailPreparationException.class.getName());
                });
        verify(sender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("a recipient that cannot be parsed while composing is an unexpected failure without the address")
    void composeFailureIsWrapped() throws Exception {
        JavaMailSenderImpl sender = stubbedSender();
        MailSettings.Usable usable = usable(sender, "MasterSmith");

        assertThatThrownBy(() -> new SmtpMailTransport().send(usable, MAIL, "bad address<@example.com"))
                .isInstanceOfSatisfying(
                        MailUnexpectedException.class,
                        e -> assertThat(e.getMessage()).doesNotContain("bad address"));
        verify(sender, times(0)).send(any(MimeMessage.class));
    }
}
