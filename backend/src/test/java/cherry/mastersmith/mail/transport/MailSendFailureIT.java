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

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.service.MailSender;
import cherry.mastersmith.mail.testsupport.MailTestApplication;
import cherry.mastersmith.mail.testsupport.SilentSmtpServer;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.mail.testsupport.TestCertificates;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 送信の失敗の結合テスト（NFR9.2・NFR6.1・NFR6.3、BR1.5・BR5.3）。受け手の側で失敗を作り、失敗の種類と、受け手が受けた接続が
 * 1回だけであること（自動でやり直さない）を確かめる。時間切れはテストの設定で短い値（1 秒）にする。
 */
class MailSendFailureIT {

    private static final MailRequest REQUEST =
            new MailRequest("sample", "ja", "taro@example.com", Map.of("name", "太郎", "link", "https://example.com/"));

    private static TestCertificates certificates;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void createCertificates() {
        certificates = TestCertificates.create();
    }

    @AfterAll
    static void deleteCertificates() {
        certificates.close();
    }

    private MailSendResult send(int port, Map<String, String> settings) {
        try (ConfigurableApplicationContext context = MailTestApplication.start(tempDir, port, settings)) {
            return context.getBean(MailSender.class).send(REQUEST);
        }
    }

    private static Map<String, String> starttlsRequired() {
        Map<String, String> settings = new HashMap<>(certificates.clientTrustSettings());
        settings.put("spring.mail.properties.mail.smtp.starttls.enable", "true");
        settings.put("spring.mail.properties.mail.smtp.starttls.required", "true");
        return settings;
    }

    @Test
    @DisplayName("a refused connection (closed port) is CONNECTION_FAILED")
    void closedPort() throws Exception {
        assertThat(send(MailTestApplication.closedPort(), Map.of()))
                .isEqualTo(MailSendResult.failed(MailFailureKind.CONNECTION_FAILED));
    }

    @Test
    @DisplayName("a receiver that accepts and never answers is TIMEOUT after one connection")
    void silentReceiver() throws Exception {
        try (SilentSmtpServer silent = new SilentSmtpServer()) {
            long started = System.nanoTime();

            assertThat(send(silent.port(), Map.of())).isEqualTo(MailSendResult.failed(MailFailureKind.TIMEOUT));

            assertThat(silent.connections()).isEqualTo(1);
            assertThat(System.nanoTime() - started).as("起動を含めても待ち続けない").isLessThan(60_000_000_000L);
        }
    }

    @Test
    @DisplayName(
            "required STARTTLS against a receiver without STARTTLS is CONNECTION_FAILED with no mail and one connection")
    void starttlsNotOffered() {
        try (SmtpTestServer server = SmtpTestServer.builder().start()) {
            assertThat(send(server.port(), starttlsRequired()))
                    .isEqualTo(MailSendResult.failed(MailFailureKind.CONNECTION_FAILED));

            assertThat(server.messages()).isEmpty();
            assertThat(server.connections()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("an authentication refusal is REJECTED with one connection")
    void authenticationRejected() {
        String user = "user" + TestDatabase.randomSecret();
        try (SmtpTestServer server = SmtpTestServer.builder()
                .tls(SmtpTestServer.Tls.STARTTLS_REQUIRED, certificates.serverContext(true))
                .requireAuth(user, TestDatabase.randomSecret())
                .start()) {
            Map<String, String> settings = starttlsRequired();
            settings.put("spring.mail.username", user);
            settings.put("spring.mail.password", TestDatabase.randomSecret());

            assertThat(send(server.port(), settings)).isEqualTo(MailSendResult.failed(MailFailureKind.REJECTED));

            assertThat(server.messages()).isEmpty();
            assertThat(server.connections()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("correct credentials over required STARTTLS are accepted")
    void authenticationAccepted() {
        String user = "user" + TestDatabase.randomSecret();
        String password = TestDatabase.randomSecret();
        try (SmtpTestServer server = SmtpTestServer.builder()
                .tls(SmtpTestServer.Tls.STARTTLS_REQUIRED, certificates.serverContext(true))
                .requireAuth(user, password)
                .start()) {
            Map<String, String> settings = starttlsRequired();
            settings.put("spring.mail.username", user);
            settings.put("spring.mail.password", password);

            assertThat(send(server.port(), settings)).isEqualTo(MailSendResult.sent());
            assertThat(server.messages()).hasSize(1);
        }
    }

    @Test
    @DisplayName("a recipient refusal is REJECTED with no mail and one connection")
    void recipientRejected() {
        try (SmtpTestServer server = SmtpTestServer.builder().rejectRecipients().start()) {
            assertThat(send(server.port(), Map.of())).isEqualTo(MailSendResult.failed(MailFailureKind.REJECTED));

            assertThat(server.messages()).isEmpty();
            assertThat(server.connections()).isEqualTo(1);
        }
    }
}
