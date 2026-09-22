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
    @DisplayName("initial admin settings hide the password but show the email address")
    void initialAdminProperties() {
        InitialAdminProperties properties = new InitialAdminProperties("admin@example.com", SECRET);

        assertThat(properties.toString()).doesNotContain(SECRET).contains("admin@example.com");
    }
}
