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
package cherry.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.user.domain.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginCommandTest {

    @Test
    @DisplayName("the string form hides both the email and the password while the values stay readable")
    void stringFormHidesEmailAndPassword() {
        String email = "login-command@example.com";
        String password = "ひみつのパスワード-9876";
        LoginCommand command = new LoginCommand(email, new Password(password));

        assertThat(command.toString())
                .doesNotContain(email)
                .doesNotContain("login-command")
                .doesNotContain(password)
                .contains("email=***");
        assertThat(command.email()).isEqualTo(email);
        assertThat(command.password().value()).isEqualTo(password);
    }
}
