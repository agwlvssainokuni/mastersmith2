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
package cherry.mastersmith.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.auth.service.AuthProperties;
import cherry.mastersmith.user.service.InitialAdminProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretTypesTest {

    private static final String SECRET = "tsecret-value-0123456789abcdef";

    @Test
    @DisplayName("token value types hide their value")
    void tokenValues() {
        assertThat(new RefreshTokenValue(SECRET).toString()).isEqualTo("***");
        assertThat(new AccessTokenValue(SECRET).toString()).isEqualTo("***");
        assertThat(new RefreshTokenValue(SECRET).value()).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("token value types reject null")
    void rejectsNull() {
        assertThatThrownBy(() -> new RefreshTokenValue(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AccessTokenValue(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("auth settings hide the signing key")
    void authProperties() {
        AuthProperties properties = new AuthProperties(
                SECRET,
                Duration.ofMinutes(5),
                Duration.ofHours(24),
                new AuthProperties.Lock(5, Duration.ofMinutes(30)),
                new AuthProperties.RefreshTokenCleanup(Duration.ofDays(7), "0 30 3 * * *"));

        assertThat(properties.toString()).doesNotContain(SECRET).contains("signingKey=***");
    }

    @Test
    @DisplayName("an authentication event hides the entered email and shows the other items")
    void authenticationEventHidesEmail() {
        String email = "entered-user@example.com";
        AuthenticationEvent event = AuthenticationEvent.of(
                AuthenticationEventType.LOGIN_FAILED,
                Instant.parse("2026-10-02T01:02:03Z"),
                email,
                42L,
                LoginFailureReason.ACCOUNT_SUSPENDED,
                new ClientInfo("192.0.2.10", "Mozilla/5.0", "trace-0001"));

        assertThat(event.toString())
                .doesNotContain(email)
                .doesNotContain("entered-user")
                .contains("enteredEmail=***")
                .contains("LOGIN_FAILED")
                .contains("ACCOUNT_SUSPENDED")
                .contains("userId=42")
                .contains("192.0.2.10")
                .contains("trace-0001");
        // 写し取りのための項目の値は変わらない。
        assertThat(event.enteredEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("an authentication event without an entered email is still shown as masked")
    void authenticationEventWithoutEmail() {
        AuthenticationEvent event = AuthenticationEvent.of(
                AuthenticationEventType.LOGGED_OUT,
                Instant.parse("2026-10-02T01:02:03Z"),
                null,
                null,
                null,
                new ClientInfo("192.0.2.10", null, null));

        assertThat(event.toString()).contains("enteredEmail=***").contains("LOGGED_OUT");
    }

    @Test
    @DisplayName("an authenticated user hides the email but shows the user id and the admin flag")
    void authenticatedUserHidesEmail() {
        String email = "authenticated-user@example.com";
        AuthenticatedUser user = new AuthenticatedUser(7L, email, true);

        assertThat(user.toString())
                .doesNotContain(email)
                .doesNotContain("authenticated-user")
                .contains("email=***")
                .contains("userId=7")
                .contains("admin=true");
        assertThat(user.email()).isEqualTo(email);
    }

    @Test
    @DisplayName("initial admin settings hide the password but show the email address")
    void initialAdminProperties() {
        InitialAdminProperties properties = new InitialAdminProperties("admin@example.com", SECRET);

        assertThat(properties.toString()).doesNotContain(SECRET).contains("admin@example.com");
    }
}
