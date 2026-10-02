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
package cherry.mastersmith.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebSecretTypesTest {

    @Test
    @DisplayName("the login request hides the password and the email")
    void loginRequest() {
        LoginRequest request = new LoginRequest("user@example.com", "ひみつのパスワード1");

        assertThat(request.toString())
                .contains("email=***")
                .contains("password=***")
                .doesNotContain("user@example.com")
                .doesNotContain("ひみつ");
        assertThat(request.email()).isEqualTo("user@example.com");
        assertThat(request.password()).isEqualTo("ひみつのパスワード1");
    }

    @Test
    @DisplayName("the token response hides the access token and the current user's email and name")
    void tokenResponse() {
        TokenResponse response = new TokenResponse(
                "header.payload.signature",
                Instant.parse("2026-09-22T00:05:00Z"),
                new CurrentUserResponse("user@example.com", true, "テスト 利用者", "ja", "system", "md"));

        assertThat(response.toString())
                .contains("accessToken=***")
                .doesNotContain("header.payload.signature")
                .doesNotContain("user@example.com")
                .doesNotContain("テスト 利用者")
                .contains("CurrentUserResponse[email=***");
        assertThat(response.accessToken()).isEqualTo("header.payload.signature");
    }

    @Test
    @DisplayName("the current user response hides the email and the display name but keeps the other fields")
    void currentUserResponse() {
        CurrentUserResponse response = new CurrentUserResponse("user@example.com", true, "テスト 利用者", "en", "dark", "lg");

        assertThat(response.toString())
                .contains("email=***")
                .contains("displayName=***")
                .contains("admin=true")
                .contains("language=en")
                .contains("theme=dark")
                .contains("fontSize=lg")
                .doesNotContain("user@example.com")
                .doesNotContain("テスト 利用者");
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.displayName()).isEqualTo("テスト 利用者");
    }
}
