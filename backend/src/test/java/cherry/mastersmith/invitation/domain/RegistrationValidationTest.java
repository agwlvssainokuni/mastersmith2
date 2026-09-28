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

import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.FieldErrorReason;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 登録の完了の入力の検証の単体テスト（BR7.2、NFR9.12）。 */
class RegistrationValidationTest {

    private static final String PASSWORD = "テスト用パスワード-0000";

    private static List<FieldError> validate(String name, String password, String confirmation) {
        return RegistrationValidation.validate(name, password, confirmation, "ja", "system", "md");
    }

    @Test
    @DisplayName("valid input has no errors")
    void valid() {
        assertThat(validate("山田 花子", PASSWORD, PASSWORD)).isEmpty();
    }

    @Test
    @DisplayName("all errors are collected in the field order of the contract with one reason each")
    void collectsInOrder() {
        assertThat(RegistrationValidation.validate(null, null, null, null, null, null))
                .extracting(FieldError::field)
                .containsExactly("displayName", "password", "passwordConfirmation", "language", "theme", "fontSize");
        assertThat(RegistrationValidation.validate(" ", "short", "other", "JA", "blue", "xl"))
                .containsExactly(
                        new FieldError("displayName", FieldErrorReason.REQUIRED),
                        new FieldError("password", FieldErrorReason.TOO_SHORT),
                        new FieldError("passwordConfirmation", FieldErrorReason.MISMATCH),
                        new FieldError("language", FieldErrorReason.INVALID_VALUE),
                        new FieldError("theme", FieldErrorReason.INVALID_VALUE),
                        new FieldError("fontSize", FieldErrorReason.INVALID_VALUE));
    }

    @ParameterizedTest(name = "[{index}] {0} x {1}")
    @CsvSource({"11, a, TOO_SHORT", "12, a, ", "72, a, ", "73, a, TOO_LONG", "11, 😀, TOO_SHORT", "12, 😀, "})
    @DisplayName("password boundaries by code points and UTF-8 bytes")
    void passwordBoundaries(int count, String unit, FieldErrorReason reason) {
        String password = unit.repeat(count);
        List<FieldError> errors = validate("山田", password, password);

        if (reason == null) {
            assertThat(errors).isEmpty();
        } else {
            assertThat(errors).containsExactly(new FieldError("password", reason));
        }
    }

    @Test
    @DisplayName("the confirmation must match exactly, including surrounding spaces")
    void confirmation() {
        assertThat(validate("山田", PASSWORD, PASSWORD + " "))
                .containsExactly(new FieldError("passwordConfirmation", FieldErrorReason.MISMATCH));
        assertThat(validate("山田", PASSWORD, ""))
                .containsExactly(new FieldError("passwordConfirmation", FieldErrorReason.REQUIRED));
    }

    @Test
    @DisplayName("display name boundaries use the U2 rules (254/255 code points, Cc and Cf)")
    void displayName() {
        assertThat(validate("😀".repeat(254), PASSWORD, PASSWORD)).isEmpty();
        assertThat(validate("😀".repeat(255), PASSWORD, PASSWORD))
                .containsExactly(new FieldError("displayName", FieldErrorReason.TOO_LONG));
        assertThat(validate("山田\u0007", PASSWORD, PASSWORD))
                .containsExactly(new FieldError("displayName", FieldErrorReason.INVALID_CHARACTER));
        assertThat(validate("山田​花子", PASSWORD, PASSWORD))
                .containsExactly(new FieldError("displayName", FieldErrorReason.INVALID_CHARACTER));
    }

    @Test
    @DisplayName("the errors never carry the entered values")
    void noValues() {
        String secret = "ひみつの-パスワード";
        assertThat(validate("山田", secret, secret + "x").toString()).doesNotContain(secret);
    }
}
