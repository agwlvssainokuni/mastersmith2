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

import jakarta.mail.internet.InternetAddress;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** 設定の点検の単体テスト（security-design.md の 2.2 の表、NFR2.1・NFR2.5・NFR2.6・NFR6.2）。 */
class MailSettingsTest {

    private static final String FROM = "noreply@example.com";

    private static final MastersmithMailProperties GOOD_FROM = new MastersmithMailProperties(FROM, null);

    /** 既定の設定（application.yaml と同じ時間切れ）の送信の部品を作る。接続はしない。 */
    static JavaMailSenderImpl sender(Consumer<JavaMailSenderImpl> customizer) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.example.com");
        Properties properties = new Properties();
        for (String protocol : List.of("smtp", "smtps")) {
            for (String key : MailSettings.TIMEOUT_KEYS) {
                properties.setProperty("mail." + protocol + "." + key, "3000");
            }
        }
        sender.setJavaMailProperties(properties);
        customizer.accept(sender);
        return sender;
    }

    private static Consumer<JavaMailSenderImpl> starttls() {
        return s -> {
            s.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "true");
            s.getJavaMailProperties().setProperty("mail.smtp.starttls.required", "true");
        };
    }

    @Test
    @DisplayName("no sender and no sender address means not configured without any problem items")
    void nothingConfigured() {
        assertThat(MailSettings.inspect(null, new MastersmithMailProperties(null, null)))
                .isInstanceOf(MailSettings.NotConfigured.class);
        assertThat(MailSettings.inspect(null, new MastersmithMailProperties(" ", "表示名だけ")))
                .isInstanceOf(MailSettings.NotConfigured.class);
        assertThat(MailSettings.inspect(null, null)).isInstanceOf(MailSettings.NotConfigured.class);
    }

    static Stream<Arguments> invalidRows() {
        return Stream.of(
                Arguments.of("no sender but a from address", null, GOOD_FROM, List.of(MailSettings.HOST)),
                Arguments.of("blank host", sender(s -> s.setHost("  ")), GOOD_FROM, List.of(MailSettings.HOST)),
                Arguments.of(
                        "missing from",
                        sender(s -> {}),
                        new MastersmithMailProperties(null, null),
                        List.of(MailSettings.FROM)),
                Arguments.of(
                        "from not matching the format",
                        sender(s -> {}),
                        new MastersmithMailProperties("NoReply@Example.com", null),
                        List.of(MailSettings.FROM)),
                Arguments.of(
                        "from with a line break",
                        sender(s -> {}),
                        new MastersmithMailProperties("noreply@example.com\r\nBcc: x@example.com", null),
                        List.of(MailSettings.FROM)),
                Arguments.of(
                        "display name with a line break",
                        sender(s -> {}),
                        new MastersmithMailProperties(FROM, "Master\nSmith"),
                        List.of(MailSettings.FROM_NAME)),
                Arguments.of(
                        "username without password",
                        sender(starttls().andThen(s -> s.setUsername("user"))),
                        GOOD_FROM,
                        List.of(MailSettings.PASSWORD)),
                Arguments.of(
                        "password without username",
                        sender(starttls().andThen(s -> s.setPassword("pass"))),
                        GOOD_FROM,
                        List.of(MailSettings.USERNAME)),
                Arguments.of("port 0", sender(s -> s.setPort(0)), GOOD_FROM, List.of(MailSettings.PORT)),
                Arguments.of("port 65536", sender(s -> s.setPort(65536)), GOOD_FROM, List.of(MailSettings.PORT)),
                Arguments.of(
                        "unknown protocol",
                        sender(s -> s.setProtocol("imap")),
                        GOOD_FROM,
                        List.of(MailSettings.PROTOCOL)),
                Arguments.of(
                        "timeout 0",
                        sender(s -> s.getJavaMailProperties().setProperty("mail.smtp.timeout", "0")),
                        GOOD_FROM,
                        List.of("spring.mail.properties.mail.smtp.timeout")),
                Arguments.of(
                        "negative connection timeout",
                        sender(s -> s.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "-1")),
                        GOOD_FROM,
                        List.of("spring.mail.properties.mail.smtp.connectiontimeout")),
                Arguments.of(
                        "non-numeric write timeout",
                        sender(s -> s.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "3s")),
                        GOOD_FROM,
                        List.of("spring.mail.properties.mail.smtp.writetimeout")),
                Arguments.of(
                        "empty timeout",
                        sender(s -> s.getJavaMailProperties().setProperty("mail.smtp.timeout", "")),
                        GOOD_FROM,
                        List.of("spring.mail.properties.mail.smtp.timeout")),
                Arguments.of(
                        "smtps reads the smtps timeouts",
                        sender(s -> {
                            s.setProtocol("smtps");
                            s.getJavaMailProperties().setProperty("mail.smtps.timeout", "abc");
                        }),
                        GOOD_FROM,
                        List.of("spring.mail.properties.mail.smtps.timeout")),
                Arguments.of(
                        "credentials without encryption",
                        sender(s -> {
                            s.setUsername("user");
                            s.setPassword("pass");
                        }),
                        GOOD_FROM,
                        List.of(MailSettings.USERNAME)),
                Arguments.of(
                        "credentials with starttls.enable only are sent in the clear",
                        sender(s -> {
                            s.setUsername("user");
                            s.setPassword("pass");
                            s.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "true");
                        }),
                        GOOD_FROM,
                        List.of(MailSettings.USERNAME)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRows")
    @DisplayName("each row of the inspection table gives Invalid with only the item names")
    void invalidRows(
            String label, JavaMailSenderImpl sender, MastersmithMailProperties properties, List<String> items) {
        MailSettings settings = MailSettings.inspect(sender, properties);

        assertThat(settings).isInstanceOf(MailSettings.Invalid.class);
        assertThat(((MailSettings.Invalid) settings).problemItems()).containsExactlyElementsOf(items);
    }

    @Test
    @DisplayName("several problems are collected into one list")
    void collectsAllProblems() {
        JavaMailSenderImpl sender = sender(s -> {
            s.setHost("");
            s.setPort(70000);
            s.setUsername("user");
        });

        MailSettings settings = MailSettings.inspect(sender, new MastersmithMailProperties("bad", "a\rb"));

        assertThat(((MailSettings.Invalid) settings).problemItems())
                .containsExactly(
                        MailSettings.HOST,
                        MailSettings.FROM,
                        MailSettings.FROM_NAME,
                        MailSettings.PASSWORD,
                        MailSettings.PORT);
    }

    @Test
    @DisplayName("a usable setting uses MasterSmith as the default display name and keeps the mode")
    void usableWithDefaultName() {
        MailSettings settings = MailSettings.inspect(sender(s -> s.setPort(1025)), GOOD_FROM);

        assertThat(settings).isInstanceOf(MailSettings.Usable.class);
        MailSettings.Usable usable = (MailSettings.Usable) settings;
        assertThat(usable.mode()).isEqualTo(EncryptionMode.NONE);
        assertThat(usable.from().getAddress()).isEqualTo(FROM);
        assertThat(usable.from().getPersonal()).isEqualTo("MasterSmith");
    }

    @Test
    @DisplayName("credentials with STARTTLS or SMTPS are usable and the Japanese display name is encoded")
    void usableWithCredentials() throws Exception {
        MailSettings starttls = MailSettings.inspect(
                sender(starttls().andThen(s -> {
                    s.setUsername("user");
                    s.setPassword("pass");
                })),
                new MastersmithMailProperties(FROM, "マスタースミス"));
        MailSettings smtps = MailSettings.inspect(
                sender(s -> {
                    s.setProtocol("smtps");
                    s.setUsername("user");
                    s.setPassword("pass");
                }),
                GOOD_FROM);

        assertThat(((MailSettings.Usable) starttls).mode()).isEqualTo(EncryptionMode.STARTTLS);
        InternetAddress from = ((MailSettings.Usable) starttls).from();
        assertThat(from.getPersonal()).isEqualTo("マスタースミス");
        assertThat(from.toString()).startsWith("=?UTF-8?").endsWith("<" + FROM + ">");
        assertThat(((MailSettings.Usable) smtps).mode()).isEqualTo(EncryptionMode.SMTPS);
    }

    @Test
    @DisplayName("the text forms show neither the sender address, the display name nor the credentials")
    void textFormsHideValues() {
        JavaMailSenderImpl sender = sender(starttls().andThen(s -> {
            s.setUsername("secret-user");
            s.setPassword("secret-pass");
        }));
        MastersmithMailProperties properties = new MastersmithMailProperties(FROM, "秘密の表示名");
        MailSettings usable = MailSettings.inspect(sender, properties);
        MailSettings invalid = MailSettings.inspect(sender(s -> s.setUsername("secret-user")), properties);

        assertThat(usable.toString()).isEqualTo("Usable[mode=STARTTLS]");
        assertThat(invalid.toString()).doesNotContain("secret-user");
        assertThat(properties.toString())
                .isEqualTo("MastersmithMailProperties[from=***, fromName=***]")
                .doesNotContain(FROM);
        assertThat(new MastersmithMailProperties(null, "").toString())
                .isEqualTo("MastersmithMailProperties[from=(empty), fromName=(empty)]");
    }
}
