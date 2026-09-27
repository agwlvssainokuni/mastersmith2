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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.testsupport.MailTestApplication;
import cherry.mastersmith.mail.testsupport.SilentSmtpServer;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.mail.testsupport.TestCertificates;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * メールの秘密の値がログに出ないことの結合テスト（NFR1.1・NFR2.1、NFR 設計の Q1 A、team.md のメールの必須のテスト。既存の
 * {@code *SecretLeakIT} と同じ形）。
 *
 * <p>{@code cherry.mastersmith} のロガーを TRACE にしてメソッドの呼び出しの追跡（TraceAspect）を有効にし、引数・戻り値まで出る
 * 状態で、成功と、拒む・応答しない・拒否の応答・入力の誤りのそれぞれを確かめる。
 */
@ExtendWith(OutputCaptureExtension.class)
class MailSecretLeakIT {

    @TempDir
    Path tempDir;

    private static Map<String, String> trace(Map<String, String> settings) {
        Map<String, String> all = new HashMap<>(settings);
        all.put("logging.level.cherry.mastersmith", "TRACE");
        return all;
    }

    private static MailRequest request(String recipient, String token) {
        return new MailRequest(
                "sample",
                "ja",
                recipient,
                Map.of("name", "秘密の氏名" + token, "link", "https://example.com/register?t=" + token));
    }

    private MailSendResult send(int port, Map<String, String> settings, MailRequest request) {
        try (ConfigurableApplicationContext context = MailTestApplication.start(tempDir, port, trace(settings))) {
            return context.getBean(MailSender.class).send(request);
        }
    }

    private static void assertNoSecret(CapturedOutput output, String recipient, String token) {
        JsonLogRecords.assertContainsNoSecret(output.getAll(), recipient, token, "秘密の氏名");
    }

    @Test
    @DisplayName(
            "with TRACE enabled a successful send logs neither the recipient, the values, the subject nor the body")
    void successLeaksNothing(CapturedOutput output) {
        String token = TestDatabase.randomSecret();
        String recipient = "r" + token.substring(1, 9) + "@example.com";
        try (SmtpTestServer server = SmtpTestServer.builder().start()) {
            assertThat(send(server.port(), Map.of(), request(recipient, token))).isEqualTo(MailSendResult.sent());
            assertThat(server.messages()).hasSize(1);
        }
        assertThat(output.getOut())
                .as("TRACE のメソッドの追跡が送信の入口の出入りを出している")
                .contains("ENTER SmtpMailSender#send(MailRequest[templateId=sample, language=ja])")
                .contains("EXIT  SmtpMailSender#send(): MailSendResult[outcome=SENT, failureKind=null]");
        assertNoSecret(output, recipient, token);
        assertThat(output.getAll()).doesNotContain("さんへのお知らせ").doesNotContain("こんにちは");
    }

    @Test
    @DisplayName("a refused connection and a silent receiver log neither the recipient nor the values")
    void connectionFailuresLeakNothing(CapturedOutput output) throws Exception {
        String token = TestDatabase.randomSecret();
        String recipient = "c" + token.substring(1, 9) + "@example.com";
        assertThat(send(MailTestApplication.closedPort(), Map.of(), request(recipient, token))
                        .failureKind())
                .isEqualTo(MailFailureKind.CONNECTION_FAILED);
        try (SilentSmtpServer silent = new SilentSmtpServer()) {
            assertThat(send(silent.port(), Map.of(), request(recipient, token)).failureKind())
                    .isEqualTo(MailFailureKind.TIMEOUT);
        }
        assertNoSecret(output, recipient, token);
    }

    @Test
    @DisplayName("a refusal answer logs neither the recipient, the SMTP answer nor the credentials")
    void refusalLeaksNothing(CapturedOutput output) {
        String token = TestDatabase.randomSecret();
        String recipient = "x" + token.substring(1, 9) + "@example.com";
        String user = "user" + TestDatabase.randomSecret();
        String password = TestDatabase.randomSecret();
        try (TestCertificates certificates = TestCertificates.create();
                SmtpTestServer server = SmtpTestServer.builder()
                        .tls(SmtpTestServer.Tls.STARTTLS_REQUIRED, certificates.serverContext(true))
                        .requireAuth(user, TestDatabase.randomSecret())
                        .start()) {
            Map<String, String> settings = new HashMap<>(certificates.clientTrustSettings());
            settings.put("spring.mail.properties.mail.smtp.starttls.enable", "true");
            settings.put("spring.mail.properties.mail.smtp.starttls.required", "true");
            settings.put("spring.mail.username", user);
            settings.put("spring.mail.password", password);
            assertThat(send(server.port(), settings, request(recipient, token)).failureKind())
                    .isEqualTo(MailFailureKind.REJECTED);
        }
        try (SmtpTestServer server = SmtpTestServer.builder().rejectRecipients().start()) {
            assertThat(send(server.port(), Map.of(), request(recipient, token)).failureKind())
                    .isEqualTo(MailFailureKind.REJECTED);
        }
        assertNoSecret(output, recipient, token);
        JsonLogRecords.assertContainsNoSecret(output.getAll(), password);
        // SMTP の応答の形（3桁の番号と空白）が、U1 とメールの部品のログに出ていない（受け手のテストの部品のログは対象外）。
        assertThat(JsonLogRecords.parse(output.getOut()).stream()
                        .filter(record -> String.valueOf(record.get("logger")).startsWith("cherry.mastersmith.mail")
                                || String.valueOf(record.get("logger")).startsWith("org.springframework.mail")
                                || String.valueOf(record.get("logger")).startsWith("jakarta.mail")
                                || String.valueOf(record.get("logger")).startsWith("org.eclipse.angus"))
                        .map(Object::toString))
                .isNotEmpty()
                .noneMatch(text -> text.matches("(?s).*(?<![0-9a-fA-F])5[0-9]{2}[ -].*"));
    }

    @Test
    @DisplayName("an input error logs only the four keys and neither the recipient nor the values")
    void inputErrorLeaksNothing(CapturedOutput output) {
        String token = TestDatabase.randomSecret();
        String recipient = "Upper" + token.substring(1, 9) + "@example.com";
        try (SmtpTestServer server = SmtpTestServer.builder().start()) {
            assertThat(send(server.port(), Map.of(), request(recipient, token)).failureKind())
                    .isEqualTo(MailFailureKind.INVALID_INPUT);
        }
        assertNoSecret(output, recipient, token);
        assertThat(JsonLogRecords.parse(output.getOut()).stream()
                        .filter(record -> SmtpMailSender.class.getName().equals(record.get("logger")))
                        .filter(record -> !"TRACE".equals(record.get("level"))))
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("level", "WARN")
                        .containsEntry("templateId", "sample")
                        .containsEntry("language", "ja")
                        .containsEntry("failureKind", "INVALID_INPUT")
                        .doesNotContainKey("exceptionType"));
    }
}
