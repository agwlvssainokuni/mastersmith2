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
package cherry.mastersmith.mail.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.mail.domain.MailFailureKind;
import cherry.mastersmith.mail.domain.MailRequest;
import cherry.mastersmith.mail.domain.MailSendResult;
import cherry.mastersmith.mail.service.MailSender;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * メールの設定と起動の結合テスト（BR1.2〜BR1.4・BR1.8、NFR2.2・NFR2.3・NFR2.5・NFR6.2・NFR6.4・NFR9.4、計画の 9節の決定 3）。
 *
 * <p>アプリ全体を起動する。内部DB は組み込みの H2 を使い、コンテナは使わない。SMTP の受け手は起動せず、届かない先（手元の閉じた
 * 番号）を設定する（起動のときに SMTP へ接続しないことも合わせて確かめる）。
 */
@ExtendWith(OutputCaptureExtension.class)
class MailConfigurationIT {

    private static final String CONFIG_LOGGER = MailConfig.class.getName();

    @TempDir
    Path tempDir;

    /** 指定した設定で起動する。 */
    static ConfigurableApplicationContext start(Path dir, Map<String, String> settings) {
        List<String> args = new ArrayList<>();
        args.add("--spring.datasource.url=" + TestDatabase.url(dir));
        args.add("--server.port=0");
        args.add("--mastersmith.auth.password.bcrypt-cost=4");
        settings.forEach((key, value) -> args.add("--" + key + "=" + value));
        return new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new));
    }

    /** 閉じた（何も待ち受けていない）手元の番号を返す。 */
    static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static List<Map<String, Object>> configRecords(CapturedOutput output, String level) {
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> CONFIG_LOGGER.equals(record.get("logger")))
                .filter(record -> level.equals(record.get("level")))
                .filter(record -> !record.containsKey("count"))
                .toList();
    }

    @Test
    @DisplayName("without any mail setting the application starts, creates no sender and logs no warning")
    void startsWithoutSetting(CapturedOutput output) {
        try (SmtpTestServer receiver = SmtpTestServer.builder().start();
                ConfigurableApplicationContext context = start(tempDir, Map.of())) {
            assertThat(context.getBean(MailSettings.class)).isInstanceOf(MailSettings.NotConfigured.class);
            assertThat(context.getBeansOfType(JavaMailSenderImpl.class)).isEmpty();

            MailSender sender = context.getBean(MailSender.class);
            assertThat(sender.isConfigured()).isFalse();
            assertThat(sender.send(
                            new MailRequest("sample", "ja", "taro@example.com", Map.of("name", "a", "link", "b"))))
                    .isEqualTo(MailSendResult.failed(MailFailureKind.NOT_CONFIGURED));
            assertThat(receiver.connections()).isZero();
            assertThat(receiver.messages()).isEmpty();
        }
        assertThat(configRecords(output, "WARN")).isEmpty();
        assertThat(configRecords(output, "INFO"))
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("state", "NOT_CONFIGURED")
                        .containsEntry("encryption", "UNUSED"));
    }

    static Stream<Arguments> invalidSettings() throws IOException {
        String port = Integer.toString(closedPort());
        String from = "noreply@example.com";
        return Stream.of(
                Arguments.of(
                        "blank host",
                        Map.of("spring.mail.host", " ", "mastersmith.mail.from", from),
                        MailSettings.HOST),
                Arguments.of(
                        "missing sender address",
                        Map.of("spring.mail.host", "127.0.0.1", "spring.mail.port", port),
                        MailSettings.FROM),
                Arguments.of(
                        "credentials without encryption",
                        Map.of(
                                "spring.mail.host",
                                "127.0.0.1",
                                "spring.mail.port",
                                port,
                                "spring.mail.username",
                                "secret-user-" + port,
                                "spring.mail.password",
                                TestDatabase.randomSecret(),
                                "mastersmith.mail.from",
                                from),
                        MailSettings.USERNAME),
                Arguments.of(
                        "invalid timeout",
                        Map.of(
                                "spring.mail.host",
                                "127.0.0.1",
                                "spring.mail.port",
                                port,
                                "spring.mail.properties.mail.smtp.timeout",
                                "0",
                                "mastersmith.mail.from",
                                from),
                        "spring.mail.properties.mail.smtp.timeout"),
                Arguments.of(
                        "port out of range",
                        Map.of(
                                "spring.mail.host",
                                "127.0.0.1",
                                "spring.mail.port",
                                "70000",
                                "mastersmith.mail.from",
                                from),
                        MailSettings.PORT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    @DisplayName("an invalid mail setting keeps the application running with exactly one warning of item names only")
    void invalidSettingWarnsOnce(String label, Map<String, String> settings, String item, CapturedOutput output) {
        try (ConfigurableApplicationContext context = start(tempDir, settings)) {
            assertThat(context.getBean(MailSettings.class)).isInstanceOf(MailSettings.Invalid.class);
        }
        assertThat(configRecords(output, "WARN"))
                .singleElement()
                .satisfies(record -> assertThat(record).containsEntry("items", item));
        List<String> values = settings.entrySet().stream()
                .filter(entry ->
                        !entry.getKey().endsWith(".port") && !entry.getValue().isBlank())
                .filter(entry -> !entry.getKey().endsWith(".timeout"))
                .map(Map.Entry::getValue)
                .toList();
        JsonLogRecords.assertContainsNoSecret(output.getAll(), values.toArray(String[]::new));
    }

    @Test
    @DisplayName("a usable setting does not connect at startup, keeps health UP and uses the safe defaults")
    void usableSettingUsesSafeDefaults(CapturedOutput output) throws IOException {
        int port = closedPort();
        try (ConfigurableApplicationContext context = start(
                tempDir,
                Map.of(
                        "spring.mail.host", "127.0.0.1",
                        "spring.mail.port", Integer.toString(port),
                        "mastersmith.mail.from", "noreply@example.com",
                        "mastersmith.mail.from-name", "表示名の値"))) {
            assertThat(context.getBean(MailSettings.class)).isInstanceOf(MailSettings.Usable.class);

            HttpResponse<String> health = new HttpTestClient(port(context)).get("/actuator/health");
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(HttpTestClient.json(health)).containsOnlyKeys("status").containsEntry("status", "UP");
            assertThat(context.containsBean("mailHealthContributor")).isFalse();
            assertThat(context.containsBean("mailHealthIndicator")).isFalse();

            JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getSession().getDebug()).isFalse();
            assertThat(sender.getJavaMailProperties())
                    .containsEntry("mail.debug", "false")
                    .containsEntry("mail.smtp.connectiontimeout", "3000")
                    .containsEntry("mail.smtp.timeout", "3000")
                    .containsEntry("mail.smtp.writetimeout", "3000")
                    .containsEntry("mail.smtps.connectiontimeout", "3000")
                    .containsEntry("mail.smtps.timeout", "3000")
                    .containsEntry("mail.smtps.writetimeout", "3000")
                    .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                    .containsEntry("mail.smtps.ssl.checkserveridentity", "true")
                    .containsEntry("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
            for (String logger : List.of("org.eclipse.angus", "jakarta.mail")) {
                assertThat(((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(logger)).getLevel())
                        .isEqualTo(Level.OFF);
            }
        }
        assertThat(configRecords(output, "WARN")).isEmpty();
        assertThat(configRecords(output, "INFO"))
                .singleElement()
                .satisfies(record ->
                        assertThat(record).containsEntry("state", "CONFIGURED").containsEntry("encryption", "NONE"));
        JsonLogRecords.assertContainsNoSecret(output.getAll(), "noreply@example.com", "表示名の値", "127.0.0.1:" + port);
    }

    private static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
    }
}
