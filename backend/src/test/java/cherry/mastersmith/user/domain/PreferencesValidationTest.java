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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** プリファレンスの保存の入力の検証（BR3.2、security-design 3節）と4つの組の単体テスト。 */
class PreferencesValidationTest {

    @Test
    @DisplayName("every error is collected in the order of the request fields")
    void collectsEveryErrorInOrder() {
        List<FieldError> errors = PreferencesValidation.validate("a".repeat(255), "EN", "", null);

        assertThat(errors)
                .containsExactly(
                        new FieldError("displayName", FieldErrorReason.TOO_LONG),
                        new FieldError("language", FieldErrorReason.INVALID_VALUE),
                        new FieldError("theme", FieldErrorReason.REQUIRED),
                        new FieldError("fontSize", FieldErrorReason.REQUIRED));
    }

    @Test
    @DisplayName("one field carries only one reason and valid fields are not listed")
    void oneReasonPerField() {
        List<FieldError> errors = PreferencesValidation.validate(" ", "ja", "dark", "xl");

        assertThat(errors)
                .containsExactly(
                        new FieldError("displayName", FieldErrorReason.REQUIRED),
                        new FieldError("fontSize", FieldErrorReason.INVALID_VALUE));
    }

    @Test
    @DisplayName("valid values produce no error")
    void validValues() {
        assertThat(PreferencesValidation.validate("山田 花子", "en", "system", "lg"))
                .isEmpty();
    }

    @Test
    @DisplayName("the errors never carry the entered values")
    void errorsCarryNoValue() {
        String name = "入れた値" + "​";

        List<FieldError> errors = PreferencesValidation.validate(name, "unknown-language", "system", "md");

        assertThat(errors.toString()).doesNotContain("入れた値").doesNotContain("unknown-language");
    }

    @Test
    @DisplayName("valid values become preferences with the stripped name, and invalid values are refused")
    void toPreferences() {
        Preferences preferences = PreferencesValidation.toPreferences("　山田 花子 ", "en", "dark", "sm");

        assertThat(preferences).isEqualTo(new Preferences("山田 花子", Language.EN, Theme.DARK, FontSize.SM));
        assertThatThrownBy(() -> PreferencesValidation.toPreferences("", "en", "dark", "sm"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("preferences accept only a stripped valid name and all four values")
    void preferencesInvariants() {
        assertThatThrownBy(() -> new Preferences(" 山田", Language.JA, Theme.SYSTEM, FontSize.MD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Preferences("山田", null, Theme.SYSTEM, FontSize.MD))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Preferences("山田", Language.JA, null, FontSize.MD))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Preferences("山田", Language.JA, Theme.SYSTEM, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the string form masks the name, which may be an email address")
    void stringFormMasksTheName() {
        Preferences preferences = new Preferences("user@example.com", Language.JA, Theme.SYSTEM, FontSize.MD);

        assertThat(preferences.toString())
                .doesNotContain("user@example.com")
                .contains("displayName=***")
                .contains("language=ja");
    }
}
