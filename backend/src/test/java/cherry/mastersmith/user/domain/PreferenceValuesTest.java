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
package cherry.mastersmith.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 言語・テーマ・文字の大きさの値の決まり（BR2.1）の単体テストと性質ベースのテスト。 */
class PreferenceValuesTest {

    private static final Set<String> ACCEPTED = Set.of("ja", "en", "light", "dark", "system", "sm", "md", "lg");

    @Test
    @DisplayName("every decided value is accepted and maps back to the same lower-case string")
    void decidedValuesAreAccepted() {
        for (Language language : Language.values()) {
            assertThat(Language.check(language.value())).isEmpty();
            assertThat(Language.fromValue(language.value())).contains(language);
        }
        for (Theme theme : Theme.values()) {
            assertThat(Theme.check(theme.value())).isEmpty();
            assertThat(Theme.fromValue(theme.value())).contains(theme);
        }
        for (FontSize fontSize : FontSize.values()) {
            assertThat(FontSize.check(fontSize.value())).isEmpty();
            assertThat(FontSize.fromValue(fontSize.value())).contains(fontSize);
        }
        assertThat(Language.JA.value()).isEqualTo("ja");
        assertThat(Theme.SYSTEM.value()).isEqualTo("system");
        assertThat(FontSize.MD.value()).isEqualTo("md");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN", "Ja", " en", "en ", "english", "xl"})
    @DisplayName("upper-case, padded or unknown values are INVALID_VALUE without being normalized")
    void invalidValues(String raw) {
        assertThat(Language.check(raw)).contains(FieldErrorReason.INVALID_VALUE);
        assertThat(Language.fromValue(raw)).isEmpty();
    }

    @Test
    @DisplayName("theme and font size reject upper-case, padded and unknown values")
    void invalidThemeAndFontSize() {
        assertThat(Theme.check("Dark")).contains(FieldErrorReason.INVALID_VALUE);
        assertThat(Theme.check(" system")).contains(FieldErrorReason.INVALID_VALUE);
        assertThat(FontSize.check(" md")).contains(FieldErrorReason.INVALID_VALUE);
        assertThat(FontSize.check("xl")).contains(FieldErrorReason.INVALID_VALUE);
        assertThat(FontSize.check(" ")).contains(FieldErrorReason.INVALID_VALUE);
    }

    @Test
    @DisplayName("a missing value (null or empty) is REQUIRED")
    void missingIsRequired() {
        assertThat(Language.check(null)).contains(FieldErrorReason.REQUIRED);
        assertThat(Language.check("")).contains(FieldErrorReason.REQUIRED);
        assertThat(Theme.check(null)).contains(FieldErrorReason.REQUIRED);
        assertThat(FontSize.check("")).contains(FieldErrorReason.REQUIRED);
        assertThat(Theme.fromValue(null)).isEmpty();
    }

    @Test
    @DisplayName("column values map back to the enum and an unexpected stored value fails fast")
    void columnValues() {
        assertThat(PreferenceValueRules.fromColumn(Theme.values(), "dark")).isEqualTo(Theme.DARK);
        assertThat(PreferenceValueRules.fromColumn(Theme.values(), null)).isNull();
        assertThatThrownBy(() -> PreferenceValueRules.fromColumn(Theme.values(), "DARK"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Property
    @Label("any string other than the decided values is always rejected")
    void otherStringsAreRejected(@ForAll String raw) {
        if (!ACCEPTED.contains(raw)) {
            assertThat(Language.check(raw)).isNotEmpty();
            assertThat(Theme.check(raw)).isNotEmpty();
            assertThat(FontSize.check(raw)).isNotEmpty();
        }
    }
}
