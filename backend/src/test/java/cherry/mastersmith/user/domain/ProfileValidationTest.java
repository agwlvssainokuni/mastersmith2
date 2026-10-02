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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 管理者による氏名と言語の変更の検証（BR5.1）の単体テスト。 */
class ProfileValidationTest {

    @Test
    @DisplayName("a valid name and language pass and make a stripped update")
    void valid() {
        assertThat(ProfileValidation.validate(" テスト 太郎 ", "en")).isEmpty();

        ProfileUpdate update = ProfileValidation.toProfileUpdate(" テスト 太郎 ", "en");

        assertThat(update.displayName()).isEqualTo("テスト 太郎");
        assertThat(update.language()).isEqualTo(Language.EN);
    }

    @Test
    @DisplayName("an empty, blank or missing name is REQUIRED")
    void nameRequired() {
        assertThat(ProfileValidation.validate("　 ", "ja"))
                .containsExactly(new FieldError("displayName", FieldErrorReason.REQUIRED));
        assertThat(ProfileValidation.validate(null, "ja"))
                .containsExactly(new FieldError("displayName", FieldErrorReason.REQUIRED));
    }

    @Test
    @DisplayName("254 code points are accepted and 255 are TOO_LONG")
    void nameLength() {
        assertThat(ProfileValidation.validate("あ".repeat(254), "ja")).isEmpty();
        assertThat(ProfileValidation.validate("あ".repeat(255), "ja"))
                .containsExactly(new FieldError("displayName", FieldErrorReason.TOO_LONG));
    }

    @Test
    @DisplayName("a control character in the name is INVALID_CHARACTER")
    void nameControlCharacter() {
        assertThat(ProfileValidation.validate("テスト\u0007太郎", "ja"))
                .containsExactly(new FieldError("displayName", FieldErrorReason.INVALID_CHARACTER));
    }

    @Test
    @DisplayName("a language other than lower-case ja or en is invalid, and a missing one is REQUIRED")
    void language() {
        assertThat(ProfileValidation.validate("テスト", "JA"))
                .containsExactly(new FieldError("language", FieldErrorReason.INVALID_VALUE));
        assertThat(ProfileValidation.validate("テスト", "fr"))
                .containsExactly(new FieldError("language", FieldErrorReason.INVALID_VALUE));
        assertThat(ProfileValidation.validate("テスト", null))
                .containsExactly(new FieldError("language", FieldErrorReason.REQUIRED));
    }

    @Test
    @DisplayName("errors in both fields are listed in the order displayName then language")
    void bothErrorsInOrder() {
        assertThat(ProfileValidation.validate("", "fr"))
                .containsExactly(
                        new FieldError("displayName", FieldErrorReason.REQUIRED),
                        new FieldError("language", FieldErrorReason.INVALID_VALUE));
    }

    @Test
    @DisplayName("making an update from invalid input fails without the value in the message")
    void toProfileUpdateRejectsInvalid() {
        assertThatThrownBy(() -> ProfileValidation.toProfileUpdate("漏れ確認 花子\u0007", "ja"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("漏れ確認");
        assertThatThrownBy(() -> new ProfileUpdate(" 前後に空白 ", Language.JA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString of the update hides the name")
    void updateRedacted() {
        ProfileUpdate update = new ProfileUpdate("漏れ確認 花子", Language.JA);

        assertThat(update.toString()).doesNotContain("漏れ確認").contains("language=ja");
    }
}
