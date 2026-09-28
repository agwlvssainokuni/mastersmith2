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

import java.security.SecureRandom;
import java.util.HexFormat;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 招待のトークンの単体テスト（BR3.1・BR3.2、NFR1.1・NFR1.6、性質ベースのテストを含む）。 */
class InvitationTokenTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String VALID = "A".repeat(42) + "_";

    @Test
    @DisplayName("a generated token has 43 URL-safe characters and a 32-byte hash")
    void generated() {
        InvitationToken token = InvitationToken.generate(RANDOM);

        assertThat(token.value()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(token.hash()).hasSize(32);
    }

    @Test
    @DisplayName("two generated tokens differ and the hash is stable for the same value")
    void distinctAndStable() {
        InvitationToken a = InvitationToken.generate(RANDOM);
        InvitationToken b = InvitationToken.generate(RANDOM);

        assertThat(a.value()).isNotEqualTo(b.value());
        assertThat(InvitationToken.parse(a.value()).orElseThrow().hash()).isEqualTo(a.hash());
        assertThat(a.hash()).isNotEqualTo(b.hash());
    }

    @Test
    @DisplayName("the hash is the SHA-256 of the UTF-8 value")
    void hashIsSha256() throws Exception {
        byte[] expected = java.security.MessageDigest.getInstance("SHA-256")
                .digest(VALID.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(new InvitationToken(VALID).hash()).isEqualTo(expected);
    }

    @Test
    @DisplayName("exactly 43 allowed characters are parsed")
    void parsesBoundary() {
        assertThat(InvitationToken.parse(VALID)).isPresent();
        assertThat(InvitationToken.parse("-".repeat(43))).isPresent();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    @DisplayName("empty, 42 and 44 characters are not parsed")
    void rejectsLength(String raw) {
        assertThat(InvitationToken.parse(raw)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"+", "/", "=", " ", "Ａ", "é"})
    @DisplayName("characters outside the URL-safe Base64 alphabet are not parsed")
    void rejectsCharacters(String bad) {
        assertThat(InvitationToken.parse("A".repeat(42) + bad)).isEmpty();
        assertThatThrownBy(() -> new InvitationToken("A".repeat(42) + bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("AAAA");
    }

    @Test
    @DisplayName("the string form hides the value and the hash")
    void toStringHidesValue() {
        InvitationToken token = InvitationToken.generate(RANDOM);

        assertThat(token.toString())
                .doesNotContain(token.value())
                .doesNotContain(HexFormat.of().formatHex(token.hash()));
    }

    @Provide
    Arbitrary<String> anyText() {
        return Arbitraries.oneOf(
                Arbitraries.strings().all().ofMaxLength(50),
                Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .withCharRange('a', 'z')
                        .withCharRange('0', '9')
                        .withChars('-', '_', '+', '/', '=')
                        .ofMinLength(41)
                        .ofMaxLength(45));
    }

    @Property(tries = 500)
    @Label("a text is parsed only when it has 43 characters all in the URL-safe alphabet")
    void parsedOnlyWhenWellFormed(@ForAll("anyText") String raw) {
        boolean wellFormed = raw.length() == 43 && raw.chars().allMatch(InvitationTokenTest::allowed);

        assertThat(InvitationToken.parse(raw).isPresent()).isEqualTo(wellFormed);
    }

    @Property(tries = 200)
    @Label("a generated token is always parsed back to the same value")
    void generatedIsAlwaysParsed() {
        InvitationToken token = InvitationToken.generate(RANDOM);

        assertThat(InvitationToken.parse(token.value())).contains(token);
    }

    private static boolean allowed(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }
}
