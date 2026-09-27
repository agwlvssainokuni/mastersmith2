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

import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.service.MailSender;
import cherry.mastersmith.mail.testsupport.MailTestApplication;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

/** メールのヘッダーへの差し込みの結合テスト（NFR2.8、AC3.1.7、BR3.2・BR4.2、team.md のメールの必須のテスト）。 */
class MailHeaderInjectionIT {

    @TempDir
    static Path tempDir;

    private static SmtpTestServer server;

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void start() {
        server = SmtpTestServer.builder().start();
        context = MailTestApplication.start(tempDir, server.port(), Map.of());
    }

    @AfterAll
    static void stop() {
        context.close();
        server.close();
    }

    @Test
    @DisplayName("a CR or LF in the recipient or any value is INVALID_INPUT and no mail is received or connected")
    void lineBreaksAreRejected() {
        MailSender sender = context.getBean(MailSender.class);
        int connections = server.connections();
        List<MailRequest> requests = List.of(
                new MailRequest(
                        "sample", "ja", "taro@example.com\r\nBcc: evil@example.com", Map.of("name", "a", "link", "b")),
                new MailRequest(
                        "sample", "ja", "taro@example.com", Map.of("name", "a\r\nBcc: evil@example.com", "link", "b")),
                new MailRequest("sample", "ja", "taro@example.com", Map.of("name", "a", "link", "b\nX-Injected: 1")));

        for (MailRequest request : requests) {
            assertThat(sender.send(request)).isEqualTo(MailSendResult.failed(MailFailureKind.INVALID_INPUT));
        }
        assertThat(server.messages()).isEmpty();
        assertThat(server.connections()).isEqualTo(connections);
    }

    @Test
    @DisplayName("a title with line breaks becomes a one-line subject and adds no header")
    void multilineTitleBecomesOneLine() throws Exception {
        MailSendResult result = context.getBean(MailSender.class)
                .send(new MailRequest("multiline", "ja", "taro@example.com", Map.of("name", "太郎")));

        assertThat(result.isSent()).isTrue();
        MimeMessage message = server.messages().getLast().getMimeMessage();
        assertThat(message.getSubject()).isEqualTo("太郎 さんへの お知らせ");
        List<String> headers = Collections.list(message.getAllHeaderLines());
        assertThat(headers).noneMatch(line -> line.startsWith("Bcc") || line.startsWith("X-Injected"));
        String raw = new String(server.messages().getLast().getData(), StandardCharsets.UTF_8);
        String subjectHeader = raw.lines()
                .dropWhile(line -> !line.startsWith("Subject:"))
                .takeWhile(line -> line.startsWith("Subject:") || line.startsWith(" ") || line.startsWith("\t"))
                .reduce("", String::concat);
        assertThat(subjectHeader).startsWith("Subject: =?UTF-8?");
    }

    @Test
    @DisplayName("an injected value is escaped in the body and the title and never becomes a header")
    void valuesStayInTheBody() throws Exception {
        MailSendResult result = context.getBean(MailSender.class)
                .send(new MailRequest(
                        "sample",
                        "en",
                        "taro@example.com",
                        Map.of("name", "<script>x</script> Bcc: evil@example.com", "link", "\"><b>")));

        assertThat(result.isSent()).isTrue();
        MimeMessage message = server.messages().getLast().getMimeMessage();
        String body = (String) message.getContent();
        assertThat(body)
                .doesNotContain("<script>")
                .doesNotContain("\"><b>")
                .contains("&lt;script&gt;")
                .contains("&quot;&gt;&lt;b&gt;");
        assertThat(message.getHeader("Bcc")).isNull();
        assertThat(server.messages().getLast().getEnvelopeReceiver()).isEqualTo("taro@example.com");
    }
}
