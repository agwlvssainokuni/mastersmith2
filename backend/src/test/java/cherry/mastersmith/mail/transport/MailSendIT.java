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

import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.service.MailSender;
import cherry.mastersmith.mail.testsupport.MailTestApplication;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 送信の成功の結合テスト（NFR9.1、AC3.1.1・AC3.1.10、BR5.1）。JVM の中の SubEtha SMTP で実際に受け、宛先・件名・本文・言語・
 * Content-Type・差出人の表示名を確かめる。送信の部品はモックにしない。宛先は予約されたドメインだけ。
 */
class MailSendIT {

    @TempDir
    static Path tempDir;

    private static SmtpTestServer server;

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void start() {
        server = SmtpTestServer.builder().start();
        context = MailTestApplication.start(tempDir, server.port(), Map.of("mastersmith.mail.from-name", "マスタースミス"));
    }

    @AfterAll
    static void stop() {
        context.close();
        server.close();
    }

    private static MimeMessage receivedLast() throws Exception {
        return server.messages().getLast().getMimeMessage();
    }

    @Test
    @DisplayName("a Japanese mail arrives as HTML in UTF-8 with the title as subject and lang ja")
    void sendsJapaneseMail() throws Exception {
        int before = server.messages().size();
        MailSendResult result = context.getBean(MailSender.class)
                .send(new MailRequest(
                        "sample",
                        "ja",
                        "hanako@example.com",
                        Map.of("name", "花子", "link", "https://example.com/r?t=1")));

        assertThat(result).isEqualTo(MailSendResult.sent());
        assertThat(server.messages()).hasSize(before + 1);
        MimeMessage message = receivedLast();
        assertThat(server.messages().getLast().getEnvelopeReceiver()).isEqualTo("hanako@example.com");
        assertThat(message.getRecipients(Message.RecipientType.TO))
                .extracting(Object::toString)
                .containsExactly("hanako@example.com");
        assertThat(message.getSubject()).isEqualTo("花子 さんへのお知らせ");
        assertThat(message.getContentType()).startsWith("text/html").containsIgnoringCase("charset=UTF-8");
        String body = (String) message.getContent();
        assertThat(body)
                .contains("<html lang=\"ja\">")
                .contains("花子 さん、こんにちは。")
                .contains("href=\"https://example.com/r?t=1\"");
        assertThat(body).doesNotContain("Licensed").doesNotContain("{{");
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo(MailTestApplication.FROM);
        assertThat(from.getPersonal()).isEqualTo("マスタースミス");
    }

    @Test
    @DisplayName("an English mail arrives with the English template only")
    void sendsEnglishMail() throws Exception {
        MailSendResult result = context.getBean(MailSender.class)
                .send(new MailRequest(
                        "sample", "en", "john@example.com", Map.of("name", "John", "link", "https://example.com/")));

        assertThat(result.isSent()).isTrue();
        MimeMessage message = receivedLast();
        assertThat(message.getSubject()).isEqualTo("Notice for John");
        assertThat((String) message.getContent())
                .contains("<html lang=\"en\">")
                .contains("Hello, John.")
                .doesNotContain("こんにちは");
    }

    @Test
    @DisplayName("each send opens exactly one connection and the service reports that it is configured")
    void oneConnectionPerSend() {
        MailSender sender = context.getBean(MailSender.class);
        int before = server.connections();

        sender.send(new MailRequest("sample", "ja", "a@example.com", Map.of("name", "a", "link", "b")));

        assertThat(sender.isConfigured()).isTrue();
        assertThat(server.connections()).isEqualTo(before + 1);
    }
}
