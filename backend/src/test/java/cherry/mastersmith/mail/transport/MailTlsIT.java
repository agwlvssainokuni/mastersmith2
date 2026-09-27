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
 * TLS の結合テスト（NFR2.7、BR1.5）。自己署名の証明書はテストの設定の中だけで信頼させ（SSL の bundle）、証明書を無条件に信じる
 * 指定（{@code mail.smtp.ssl.trust}）は使わない。名前の確かめは本番と同じ {@code checkserveridentity} の既定の設定で行う。
 */
class MailTlsIT {

    private static final MailRequest REQUEST =
            new MailRequest("sample", "en", "taro@example.com", Map.of("name", "Taro", "link", "https://example.com/"));

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

    private MailSendResult send(SmtpTestServer server, Map<String, String> extra) {
        Map<String, String> settings = new HashMap<>(certificates.clientTrustSettings());
        settings.putAll(extra);
        try (ConfigurableApplicationContext context = MailTestApplication.start(tempDir, server.port(), settings)) {
            return context.getBean(MailSender.class).send(REQUEST);
        }
    }

    private static Map<String, String> starttlsSettings() {
        return Map.of(
                "spring.mail.properties.mail.smtp.starttls.enable", "true",
                "spring.mail.properties.mail.smtp.starttls.required", "true");
    }

    @Test
    @DisplayName("a mail is sent over required STARTTLS to a receiver with a matching certificate")
    void sendsOverStarttls() {
        try (SmtpTestServer server = SmtpTestServer.builder()
                .tls(SmtpTestServer.Tls.STARTTLS_REQUIRED, certificates.serverContext(true))
                .start()) {
            assertThat(send(server, starttlsSettings())).isEqualTo(MailSendResult.sent());
            assertThat(server.messages()).hasSize(1);
        }
    }

    @Test
    @DisplayName("a mail is sent over SMTPS to a receiver with a matching certificate")
    void smtps() {
        try (SmtpTestServer server = SmtpTestServer.builder()
                .tls(SmtpTestServer.Tls.SMTPS, certificates.serverContext(true))
                .start()) {
            assertThat(send(server, Map.of("spring.mail.protocol", "smtps"))).isEqualTo(MailSendResult.sent());
            assertThat(server.messages()).hasSize(1);
        }
    }

    @Test
    @DisplayName("a certificate whose name does not match the host is CONNECTION_FAILED with no mail")
    void mismatchingCertificate() {
        try (SmtpTestServer server = SmtpTestServer.builder()
                .tls(SmtpTestServer.Tls.STARTTLS_REQUIRED, certificates.serverContext(false))
                .start()) {
            assertThat(send(server, starttlsSettings()))
                    .isEqualTo(MailSendResult.failed(MailFailureKind.CONNECTION_FAILED));
            assertThat(server.messages()).isEmpty();
        }
        try (SmtpTestServer server = SmtpTestServer.builder()
                .tls(SmtpTestServer.Tls.SMTPS, certificates.serverContext(false))
                .start()) {
            assertThat(send(server, Map.of("spring.mail.protocol", "smtps")))
                    .isEqualTo(MailSendResult.failed(MailFailureKind.CONNECTION_FAILED));
            assertThat(server.messages()).isEmpty();
        }
    }
}
