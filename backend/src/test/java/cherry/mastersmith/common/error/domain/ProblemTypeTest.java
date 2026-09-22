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
package cherry.mastersmith.common.error.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemTypeTest {

    private static final LocalizedText TEXT = new LocalizedText("説明", "description");

    private static ProblemType type(String code, int status) {
        return new ProblemType(code, status, TEXT, TEXT, null);
    }

    @Test
    @DisplayName("valid code, status and texts create a problem type with a derived slug")
    void createsWithDerivedSlug() {
        ProblemType type = type("VALIDATION_FAILED", 400);

        assertThat(type.slug()).isEqualTo("validation-failed");
        assertThat(type.resolution()).isNull();
        assertThat(type.title().in(DisplayLanguage.JA)).isEqualTo("説明");
        assertThat(type.title().in(DisplayLanguage.EN)).isEqualTo("description");
    }

    @Test
    @DisplayName("code with lowercase letters, a leading digit or no characters is rejected")
    void rejectsMalformedCode() {
        assertThatThrownBy(() -> type("Validation_failed", 400)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> type("1_FAILED", 400)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> type("", 400)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> type("_FAILED", 400)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> type(null, 400)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("status outside 400 to 599 is rejected and the boundaries are accepted")
    void rejectsStatusOutOfRange() {
        assertThatThrownBy(() -> type("A", 399)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> type("A", 600)).isInstanceOf(IllegalArgumentException.class);
        assertThat(type("A", 400).status()).isEqualTo(400);
        assertThat(type("A", 599).status()).isEqualTo(599);
    }

    @Test
    @DisplayName("localized text requires both Japanese and English")
    void localizedTextRequiresBothLanguages() {
        assertThatThrownBy(() -> new LocalizedText("", "en")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalizedText("日本語", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalizedText(null, "en")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("title and description are mandatory")
    void titleAndDescriptionAreMandatory() {
        assertThatThrownBy(() -> new ProblemType("A", 400, null, TEXT, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProblemType("A", 400, TEXT, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("business exception carries the problem type and a user-facing detail but only the code as message")
    void businessExceptionCarriesProblemType() {
        BusinessException exception = new BusinessException(CommonProblemTypes.NOT_FOUND, "表示してよい説明");

        assertThat(exception.getProblemType()).isSameAs(CommonProblemTypes.NOT_FOUND);
        assertThat(exception.getDetail()).isEqualTo("表示してよい説明");
        assertThat(exception.getMessage()).isEqualTo("NOT_FOUND");
        assertThat(new BusinessException(CommonProblemTypes.NOT_FOUND).getDetail())
                .isNull();
    }

    @Property
    @Label("slug derived from any valid code uses only lowercase letters, digits and hyphens and maps back to the code")
    void slugRoundTrip(@ForAll("validCodes") String code) {
        String slug = ProblemType.toSlug(code);

        assertThat(slug).matches("[a-z][a-z0-9-]*");
        assertThat(ProblemType.toCode(slug)).isEqualTo(code);
        assertThat(type(code, 400).slug()).isEqualTo(slug);
    }

    @Provide
    Arbitrary<String> validCodes() {
        Arbitrary<Character> first = Arbitraries.chars().range('A', 'Z');
        Arbitrary<String> rest = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMaxLength(30);
        return net.jqwik.api.Combinators.combine(first, rest).as((f, r) -> f + r);
    }
}
