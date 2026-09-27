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
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 設定の点検の Bean と、環境変数から送信の部品の設定への結び付きの単体テスト（NFR6.2、logical-components.md の 10節、
 * 計画の Step 8）。
 *
 * <p>プロセスの環境変数は変えず、名前 {@code systemEnvironment} の {@link SystemEnvironmentPropertySource} を置いて、環境変数と
 * 同じ結び付きの規則で確かめる。
 */
class MailConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MastersmithMailProperties.class)
    static class PropertiesConfig {}

    private static ApplicationContextRunner runner(Map<String, Object> environment) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(PropertiesConfig.class, MailConfig.class)
                .withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environment)));
    }

    private static List<ILoggingEvent> warnings(LogEvents events) {
        return events.list().stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    @Test
    @DisplayName("environment variables bind to the dotted keys of spring.mail.properties and reach the sender")
    void environmentVariablesReachTheDottedKeys() {
        runner(Map.of(
                        "SPRING_MAIL_HOST", "localhost",
                        "SPRING_MAIL_PORT", "1025",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT", "1234",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT", "2345",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_WRITETIMEOUT", "3456",
                        "MASTERSMITH_MAIL_FROM", "noreply@example.com"))
                .run(context -> {
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getJavaMailProperties())
                            .containsEntry("mail.smtp.connectiontimeout", "1234")
                            .containsEntry("mail.smtp.timeout", "2345")
                            .containsEntry("mail.smtp.writetimeout", "3456");
                    assertThat(context.getBean(MailSettings.class)).isInstanceOf(MailSettings.Usable.class);
                });
    }

    @Test
    @DisplayName("environment variables for STARTTLS classify the mode as STARTTLS")
    void environmentVariablesForStarttls() {
        runner(Map.of(
                        "SPRING_MAIL_HOST", "localhost",
                        "SPRING_MAIL_PORT", "587",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE", "true",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED", "true",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT", "3000",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT", "3000",
                        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_WRITETIMEOUT", "3000",
                        "MASTERSMITH_MAIL_FROM", "noreply@example.com"))
                .run(context -> assertThat(((MailSettings.Usable) context.getBean(MailSettings.class)).mode())
                        .isEqualTo(EncryptionMode.STARTTLS));
    }

    @Test
    @DisplayName("without a host no sender is created and the state is not configured without a warning")
    void noHostNoSender() {
        try (LogEvents events = LogEvents.capture(MailConfig.class)) {
            runner(Map.of()).run(context -> {
                assertThat(context).doesNotHaveBean(JavaMailSenderImpl.class);
                assertThat(context.getBean(MailSettings.class)).isInstanceOf(MailSettings.NotConfigured.class);
            });
            assertThat(warnings(events)).isEmpty();
            assertThat(events.list().stream()
                            .filter(event -> event.getFormattedMessage().contains("設定を点検"))
                            .toList())
                    .singleElement()
                    .satisfies(event -> assertThat(event.getKeyValuePairs().toString())
                            .contains("state=\"NOT_CONFIGURED\"")
                            .contains("encryption=\"UNUSED\""));
        }
    }

    @Test
    @DisplayName("an invalid setting logs exactly one warning with only the item names")
    void invalidSettingLogsOneWarning() {
        try (LogEvents events = LogEvents.capture(MailConfig.class)) {
            runner(Map.of(
                            "SPRING_MAIL_HOST", "smtp.example.net",
                            "SPRING_MAIL_USERNAME", "secret-user",
                            "SPRING_MAIL_PASSWORD", "secret-password",
                            "SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT", "3000",
                            "SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT", "3000",
                            "SPRING_MAIL_PROPERTIES_MAIL_SMTP_WRITETIMEOUT", "3000",
                            "MASTERSMITH_MAIL_FROM", "noreply@example.com"))
                    .run(context ->
                            assertThat(context.getBean(MailSettings.class)).isInstanceOf(MailSettings.Invalid.class));
            List<ILoggingEvent> warnings = warnings(events);
            assertThat(warnings).hasSize(1);
            String text = warnings.getFirst().getFormattedMessage()
                    + warnings.getFirst().getKeyValuePairs();
            assertThat(text)
                    .contains("items=\"" + MailSettings.USERNAME + "\"")
                    .doesNotContain("secret-user")
                    .doesNotContain("secret-password")
                    .doesNotContain("smtp.example.net")
                    .doesNotContain("noreply@example.com");
        }
    }

    @Test
    @DisplayName("the state names cover every state")
    void stateNames() {
        assertThat(MailConfig.stateName(new MailSettings.NotConfigured())).isEqualTo("NOT_CONFIGURED");
        assertThat(MailConfig.stateName(new MailSettings.Invalid(List.of("x")))).isEqualTo("INVALID");
        assertThat(MailConfig.encryptionName(new MailSettings.Invalid(List.of("x"))))
                .isEqualTo("UNUSED");
    }
}
