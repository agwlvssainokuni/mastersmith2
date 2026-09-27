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

import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 暗号化の方式の分類の単体テスト（security-design.md の 2.3 の表のすべての行、NFR2.6）。 */
class EncryptionModeTest {

    private static Properties props(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of("nothing set", "smtp", props(), EncryptionMode.NONE),
                Arguments.of("null protocol is smtp", null, props(), EncryptionMode.NONE),
                Arguments.of(
                        "starttls enable and required",
                        "smtp",
                        props("mail.smtp.starttls.enable", "true", "mail.smtp.starttls.required", "true"),
                        EncryptionMode.STARTTLS),
                Arguments.of(
                        "starttls values are case-insensitive",
                        "smtp",
                        props("mail.smtp.starttls.enable", "TRUE", "mail.smtp.starttls.required", "True"),
                        EncryptionMode.STARTTLS),
                Arguments.of(
                        "starttls enable only is NONE",
                        "smtp",
                        props("mail.smtp.starttls.enable", "true"),
                        EncryptionMode.NONE),
                Arguments.of(
                        "starttls required only is NONE",
                        "smtp",
                        props("mail.smtp.starttls.required", "true"),
                        EncryptionMode.NONE),
                Arguments.of("protocol smtps", "smtps", props(), EncryptionMode.SMTPS),
                Arguments.of("ssl.enable of smtp", "smtp", props("mail.smtp.ssl.enable", "true"), EncryptionMode.SMTPS),
                Arguments.of(
                        "SMTPS wins over STARTTLS",
                        "smtp",
                        props(
                                "mail.smtp.ssl.enable",
                                "true",
                                "mail.smtp.starttls.enable",
                                "true",
                                "mail.smtp.starttls.required",
                                "true"),
                        EncryptionMode.SMTPS),
                Arguments.of(
                        "ssl.enable of the other protocol is ignored",
                        "smtp",
                        props("mail.smtps.ssl.enable", "true"),
                        EncryptionMode.NONE),
                Arguments.of("ssl.enable false", "smtp", props("mail.smtp.ssl.enable", "false"), EncryptionMode.NONE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    @DisplayName("the encryption mode follows the classification table")
    void classify(String label, String protocol, Properties properties, EncryptionMode expected) {
        assertThat(EncryptionMode.classify(protocol, properties)).isEqualTo(expected);
    }
}
