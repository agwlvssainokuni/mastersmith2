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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.auth.testsupport.TestSigningKeyEnvironmentPostProcessor;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SigningKeyProviderTest {

    static AuthProperties properties(String key) {
        return new AuthProperties(
                key,
                Duration.ofMinutes(5),
                Duration.ofHours(24),
                new AuthProperties.Lock(5, Duration.ofMinutes(30)),
                new AuthProperties.RefreshTokenCleanup(Duration.ofDays(7), "0 30 3 * * *"));
    }

    @Test
    @DisplayName("a 32-byte Base64 key is accepted as an HMAC-SHA256 key")
    void accepts32Bytes() {
        SigningKeyProvider provider =
                new SigningKeyProvider(properties(TestSigningKeyEnvironmentPostProcessor.randomKey(32)));

        assertThat(provider.secretKey().getEncoded()).hasSize(32);
        assertThat(provider.secretKey().getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(provider.toString())
                .doesNotContain(
                        new String(provider.secretKey().getEncoded(), java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    @Test
    @DisplayName("a 31-byte key stops the startup without showing the value")
    void rejects31Bytes() {
        String key = TestSigningKeyEnvironmentPostProcessor.randomKey(31);

        assertThatThrownBy(() -> new SigningKeyProvider(properties(key)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mastersmith.auth.signing-key")
                .hasMessageContaining("32 バイト")
                .hasMessageNotContaining(key)
                .hasNoCause();
    }

    @ParameterizedTest
    @DisplayName("a missing or blank key stops the startup")
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsMissing(String key) {
        assertThatThrownBy(() -> new SigningKeyProvider(properties(key)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTERSMITH_AUTH_SIGNING_KEY");
    }

    @Test
    @DisplayName("a value that is not Base64 stops the startup without showing the value or a cause")
    void rejectsNonBase64() {
        String key = "not-base64-value-%%%%-0123456789-abcdefghijk";

        assertThatThrownBy(() -> new SigningKeyProvider(properties(key)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(key)
                .hasNoCause();
    }
}
