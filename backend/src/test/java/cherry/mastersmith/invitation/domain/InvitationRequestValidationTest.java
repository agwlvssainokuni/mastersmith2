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
package cherry.mastersmith.invitation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import cherry.mastersmith.user.domain.Language;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 招待の要求の入力の検証の単体テスト（BR1.1・BR1.2、NFR9.12、性質ベースのテストを含む）。 */
class InvitationRequestValidationTest {

    private static String emailOfLength(int length) {
        String domain = "@example.com";
        return "a".repeat(length - domain.length()) + domain;
    }

    @Test
    @DisplayName("a 254-character email is accepted and 255 is TOO_LONG, counted after normalization")
    void lengthBoundary() {
        assertThat(InvitationRequestValidation.validate(emailOfLength(254), "ja"))
                .isEmpty();
        assertThat(InvitationRequestValidation.validate(
                        "  " + emailOfLength(254).toUpperCase() + " ", "ja"))
                .isEmpty();
        assertThat(InvitationRequestValidation.validate(emailOfLength(255), "ja"))
                .containsExactly(new FieldError("email", FieldErrorReason.TOO_LONG));
    }

    @Test
    @DisplayName("the email is normalized by trimming and lower-casing")
    void normalizes() {
        assertThat(InvitationRequestValidation.toEmail("  Hanako@Example.COM ").value())
                .isEqualTo("hanako@example.com");
        assertThat(InvitationRequestValidation.toLanguage("en")).isEqualTo(Language.EN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\rhanako@example.com", "hanako@example.com\n", "hana\r\nko@example.com"})
    @DisplayName("a CR or LF anywhere, even at the ends, is INVALID_CHARACTER before normalization")
    void lineBreaks(String raw) {
        assertThat(InvitationRequestValidation.validate(raw, "ja"))
                .containsExactly(new FieldError("email", FieldErrorReason.INVALID_CHARACTER));
        assertThatThrownBy(() -> InvitationRequestValidation.toEmail(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("hanako");
    }

    @Test
    @DisplayName("missing or blank emails are REQUIRED and malformed ones are INVALID_VALUE")
    void requiredAndFormat() {
        assertThat(InvitationRequestValidation.checkEmail(null)).contains(FieldErrorReason.REQUIRED);
        assertThat(InvitationRequestValidation.checkEmail("")).contains(FieldErrorReason.REQUIRED);
        assertThat(InvitationRequestValidation.checkEmail("   ")).contains(FieldErrorReason.REQUIRED);
        assertThat(InvitationRequestValidation.checkEmail("hanako")).contains(FieldErrorReason.INVALID_VALUE);
        assertThat(InvitationRequestValidation.checkEmail("hana ko@example.com"))
                .contains(FieldErrorReason.INVALID_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"JA", " ja", "fr", "ja "})
    @DisplayName("a language other than exactly ja or en is INVALID_VALUE")
    void languageExactMatch(String language) {
        assertThat(InvitationRequestValidation.validate("hanako@example.com", language))
                .containsExactly(new FieldError("language", FieldErrorReason.INVALID_VALUE));
    }

    @Test
    @DisplayName("errors of both fields are collected in the order email, language")
    void collectsBoth() {
        assertThat(InvitationRequestValidation.validate("x", ""))
                .containsExactly(
                        new FieldError("email", FieldErrorReason.INVALID_VALUE),
                        new FieldError("language", FieldErrorReason.REQUIRED));
        assertThatThrownBy(() -> InvitationRequestValidation.toLanguage("EN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the invitation email value type hides the value and accepts only normalized valid values")
    void emailValueType() {
        InvitationEmail email = new InvitationEmail("hanako@example.com");

        assertThat(email.toString()).doesNotContain("hanako");
        assertThatThrownBy(() -> new InvitationEmail("Hanako@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvitationEmail("hanako")).isInstanceOf(IllegalArgumentException.class);
    }

    @Provide
    Arbitrary<String> anyText() {
        return Arbitraries.strings().all().ofMaxLength(60);
    }

    @Property(tries = 500)
    @Label("any text containing a CR or LF is always rejected as INVALID_CHARACTER")
    void lineBreakAlwaysRejected(
            @ForAll("anyText") String text, @ForAll @IntRange(max = 60) int position, @ForAll boolean cr) {
        int at = Math.min(position, text.length());
        String broken = text.substring(0, at) + (cr ? "\r" : "\n") + text.substring(at);

        assertThat(InvitationRequestValidation.checkEmail(broken)).contains(FieldErrorReason.INVALID_CHARACTER);
    }
}
