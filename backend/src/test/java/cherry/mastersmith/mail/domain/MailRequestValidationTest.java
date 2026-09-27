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
package cherry.mastersmith.mail.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 送信の依頼の確かめの単体テスト（BR3.1〜BR3.4、承認の場の U1 R-01）。 */
class MailRequestValidationTest {

    private static final Set<String> NAMES = Set.of("registrationUrl", "validityHours");

    private static final String TO = "taro@example.com";

    private static Map<String, String> values(String url, String hours) {
        Map<String, String> values = new HashMap<>();
        values.put("registrationUrl", url);
        values.put("validityHours", hours);
        return values;
    }

    private static Optional<MailFailureKind> validate(
            String language, boolean known, String to, Map<String, String> values) {
        return MailRequestValidation.validate(new MailRequest("invitation", language, to, values), known, NAMES);
    }

    private static Map<String, String> good() {
        return values("https://example.com/register?t=abc", "24");
    }

    @Test
    @DisplayName("a request that satisfies every rule passes")
    void validRequestPasses() {
        assertThat(validate("ja", true, TO, good())).isEmpty();
        assertThat(validate("en", true, TO, good())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "JA", "", " ja"})
    @DisplayName("an unsupported language is INVALID_INPUT")
    void unsupportedLanguage(String language) {
        assertThat(validate(language, true, TO, good())).contains(MailFailureKind.INVALID_INPUT);
        assertThat(validate(null, true, TO, good())).contains(MailFailureKind.INVALID_INPUT);
    }

    @Test
    @DisplayName("a template id missing from the catalog is TEMPLATE_ERROR")
    void unknownTemplate() {
        assertThat(validate("ja", false, TO, good())).contains(MailFailureKind.TEMPLATE_ERROR);
    }

    @Test
    @DisplayName("the first failing rule wins: language before template, template before line breaks and address")
    void firstFailureWins() {
        Map<String, String> broken = values("a\nb", null);

        assertThat(validate("xx", false, "BAD\r\n", broken)).contains(MailFailureKind.INVALID_INPUT);
        assertThat(validate("ja", false, "BAD\r\n", broken)).contains(MailFailureKind.TEMPLATE_ERROR);
        assertThat(validate("ja", true, "BAD\r\n", broken)).contains(MailFailureKind.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"taro@example.com\r\nBcc: x@example.com", "taro@example.com\n", "\rtaro@example.com"})
    @DisplayName("a CR or LF in the recipient is INVALID_INPUT")
    void lineBreakInRecipient(String to) {
        assertThat(validate("ja", true, to, good())).contains(MailFailureKind.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/\r\nX-Injected: 1", "24\n", "\r"})
    @DisplayName("a CR or LF in any value is INVALID_INPUT")
    void lineBreakInValue(String value) {
        assertThat(validate("ja", true, TO, values(value, "24"))).contains(MailFailureKind.INVALID_INPUT);
        assertThat(validate("ja", true, TO, values("https://example.com/", value)))
                .contains(MailFailureKind.INVALID_INPUT);
    }

    @Test
    @DisplayName("the recipient boundary: 254 characters pass and 255 characters fail")
    void recipientLengthBoundary() {
        String domain = "@example.com";
        String at254 = "a".repeat(254 - domain.length()) + domain;
        String at255 = "a".repeat(255 - domain.length()) + domain;

        assertThat(at254).hasSize(254);
        assertThat(validate("ja", true, at254, good())).isEmpty();
        assertThat(validate("ja", true, at255, good())).contains(MailFailureKind.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Taro@example.com",
                " taro@example.com",
                "taro@example.com ",
                "taro",
                "taro@example",
                "@example.com",
                "ta ro@example.com",
                "taro@@example.com",
                ""
            })
    @DisplayName("a recipient that is not normalized or does not match the format is INVALID_INPUT")
    void badRecipientFormat(String to) {
        assertThat(validate("ja", true, to, good())).contains(MailFailureKind.INVALID_INPUT);
        assertThat(validate("ja", true, null, good())).contains(MailFailureKind.INVALID_INPUT);
    }

    @Test
    @DisplayName("a missing or an extra variable name is INVALID_INPUT")
    void variableNamesMustMatchExactly() {
        Map<String, String> missing = new HashMap<>(Map.of("registrationUrl", "https://example.com/"));
        Map<String, String> extra = new HashMap<>(good());
        extra.put("name", "山田");

        assertThat(validate("ja", true, TO, missing)).contains(MailFailureKind.INVALID_INPUT);
        assertThat(validate("ja", true, TO, extra)).contains(MailFailureKind.INVALID_INPUT);
        assertThat(validate("ja", true, TO, Map.of())).contains(MailFailureKind.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "　", " \t　 "})
    @DisplayName("an empty or whitespace-only value is INVALID_INPUT for every variable name")
    void emptyOrBlankValuesAreRejected(String blank) {
        assertThat(validate("ja", true, TO, values(blank, "24"))).contains(MailFailureKind.INVALID_INPUT);
        assertThat(validate("ja", true, TO, values("https://example.com/", blank)))
                .contains(MailFailureKind.INVALID_INPUT);
    }

    @Test
    @DisplayName("a null value is INVALID_INPUT for every variable name")
    void nullValuesAreRejected() {
        assertThat(validate("ja", true, TO, values(null, "24"))).contains(MailFailureKind.INVALID_INPUT);
        assertThat(validate("ja", true, TO, values("https://example.com/", null)))
                .contains(MailFailureKind.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " a ", "　a　"})
    @DisplayName("a value with content passes even with surrounding whitespace, for every variable name")
    void valuesWithContentPass(String value) {
        assertThat(validate("ja", true, TO, values(value, "24"))).isEmpty();
        assertThat(validate("ja", true, TO, values("https://example.com/", value)))
                .isEmpty();
    }
}
