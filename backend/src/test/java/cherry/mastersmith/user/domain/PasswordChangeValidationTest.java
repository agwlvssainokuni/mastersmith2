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

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** パスワードの変更の入力の検証（BR4.1、NFR9.1、AC5.1.3）の単体テストと性質ベースのテスト。 */
class PasswordChangeValidationTest {

    private static final String CURRENT = "今のパスワード-0001";

    private static List<FieldError> newPasswordErrors(String newPassword) {
        return PasswordChangeValidation.validate(CURRENT, newPassword, newPassword);
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(
            delimiter = '|',
            value = {"11|a|TOO_SHORT", "12|a|OK", "72|a|OK", "73|a|TOO_LONG", "11|😀|TOO_SHORT", "12|😀|OK"})
    @DisplayName("the new password follows the creation rule of the password policy at its boundaries")
    void newPasswordBoundaries(int count, String unit, String expected) {
        List<FieldError> errors = newPasswordErrors(unit.repeat(count));

        if ("OK".equals(expected)) {
            assertThat(errors).isEmpty();
        } else {
            assertThat(errors).containsExactly(new FieldError("newPassword", FieldErrorReason.valueOf(expected)));
        }
    }

    @Test
    @DisplayName("an empty new password and an empty confirmation are REQUIRED")
    void emptyNewPasswordIsRequired() {
        assertThat(PasswordChangeValidation.validate(CURRENT, "", ""))
                .containsExactly(
                        new FieldError("newPassword", FieldErrorReason.REQUIRED),
                        new FieldError("newPasswordConfirmation", FieldErrorReason.REQUIRED));
        assertThat(PasswordChangeValidation.validate(CURRENT, null, null)).hasSize(2);
    }

    @Test
    @DisplayName("a confirmation that differs from the new password is MISMATCH, including surrounding spaces")
    void mismatch() {
        String newPassword = "新しいパスワード-0002";

        assertThat(PasswordChangeValidation.validate(CURRENT, newPassword, newPassword + "x"))
                .containsExactly(new FieldError("newPasswordConfirmation", FieldErrorReason.MISMATCH));
        assertThat(PasswordChangeValidation.validate(CURRENT, newPassword, " " + newPassword))
                .containsExactly(new FieldError("newPasswordConfirmation", FieldErrorReason.MISMATCH));
    }

    @Test
    @DisplayName("an empty current password is REQUIRED while a current password over 72 bytes is not an input error")
    void currentPassword() {
        String newPassword = "新しいパスワード-0002";

        assertThat(PasswordChangeValidation.validate("", newPassword, newPassword))
                .containsExactly(new FieldError("currentPassword", FieldErrorReason.REQUIRED));
        assertThat(PasswordChangeValidation.validate(null, newPassword, newPassword))
                .containsExactly(new FieldError("currentPassword", FieldErrorReason.REQUIRED));
        assertThat(PasswordChangeValidation.validate("a".repeat(73), newPassword, newPassword))
                .isEmpty();
    }

    @Test
    @DisplayName("every error is collected in the order of the request fields")
    void collectsEveryError() {
        assertThat(PasswordChangeValidation.validate("", "short", "other"))
                .containsExactly(
                        new FieldError("currentPassword", FieldErrorReason.REQUIRED),
                        new FieldError("newPassword", FieldErrorReason.TOO_SHORT),
                        new FieldError("newPasswordConfirmation", FieldErrorReason.MISMATCH));
    }

    @Test
    @DisplayName("the errors never carry the passwords")
    void errorsCarryNoPassword() {
        List<FieldError> errors = PasswordChangeValidation.validate("", "みじかい", "ちがうもの");

        assertThat(errors.toString()).doesNotContain("みじかい").doesNotContain("ちがうもの");
    }

    @Property
    @Label("the new-password check agrees with the creation rule of the password policy")
    void agreesWithPasswordPolicy(@ForAll String newPassword) {
        boolean accepted =
                PasswordChangeValidation.checkNewPassword(newPassword).isEmpty();

        assertThat(accepted).isEqualTo(!newPassword.isEmpty() && PasswordPolicy.isAcceptableForCreation(newPassword));
    }

    @Property
    @Label("a confirmation equal to the new password is never MISMATCH")
    void equalConfirmationIsNeverMismatch(@ForAll String newPassword) {
        assertThat(PasswordChangeValidation.validate(CURRENT, newPassword, newPassword))
                .extracting(FieldError::reason)
                .doesNotContain(FieldErrorReason.MISMATCH);
    }
}
