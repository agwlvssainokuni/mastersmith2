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

import cherry.mastersmith.auth.domain.AccessTokenValue;
import cherry.mastersmith.auth.domain.TokenAuthenticationException;
import cherry.mastersmith.auth.domain.TokenFailureReason;
import cherry.mastersmith.auth.testsupport.AuthTestTokens;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.auth.testsupport.TestSigningKeyEnvironmentPostProcessor;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccessTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private final String key = TestSigningKeyEnvironmentPostProcessor.randomKey(32);

    private final MutableClock clock = new MutableClock(NOW);

    private AccessTokenService service;

    private AuthTestTokens tokens;

    @BeforeEach
    void setUp() {
        service = new AccessTokenService(
                new SigningKeyProvider(SigningKeyProviderTest.properties(key)),
                SigningKeyProviderTest.properties(key),
                clock);
        tokens = new AuthTestTokens(key);
    }

    private TokenFailureReason reasonOf(String token) {
        try {
            service.verify(new AccessTokenValue(token));
        } catch (TokenAuthenticationException e) {
            return e.reason();
        }
        throw new AssertionError("検証が通ってしまった");
    }

    @Test
    @DisplayName("the issued token carries only sub, iat and exp and verifies to the user id")
    void issueAndVerify() throws Exception {
        IssuedAccessToken issued = service.issue(42);

        SignedJWT jwt = SignedJWT.parse(issued.value().value());
        assertThat(jwt.getJWTClaimsSet().getClaims()).containsOnlyKeys("sub", "iat", "exp");
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("HS256");
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(service.verify(issued.value())).isEqualTo(42);
    }

    @Test
    @DisplayName("a token is valid at 4 minutes 59 seconds and expired at exactly 5 minutes")
    void expiryBoundary() {
        AccessTokenValue token = service.issue(42).value();

        clock.set(NOW.plusSeconds(299));
        assertThat(service.verify(token)).isEqualTo(42);
        clock.set(NOW.plusSeconds(300));
        assertThat(reasonOf(token.value())).isEqualTo(TokenFailureReason.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("a tampered signature or another key is TOKEN_INVALID")
    void tampered() {
        Instant exp = NOW.plusSeconds(300);

        assertThat(reasonOf(tokens.tamperedSignature(1, NOW, exp))).isEqualTo(TokenFailureReason.TOKEN_INVALID);
        assertThat(reasonOf(tokens.hs256WithOtherKey(1, NOW, exp))).isEqualTo(TokenFailureReason.TOKEN_INVALID);
    }

    @Test
    @DisplayName("alg none, HS512 and RS256 are TOKEN_INVALID")
    void otherAlgorithms() {
        Instant exp = NOW.plusSeconds(300);

        assertThat(reasonOf(AuthTestTokens.algNone(1, NOW, exp))).isEqualTo(TokenFailureReason.TOKEN_INVALID);
        assertThat(reasonOf(AuthTestTokens.hs512(1, NOW, exp))).isEqualTo(TokenFailureReason.TOKEN_INVALID);
        assertThat(reasonOf(AuthTestTokens.rs256(1, NOW, exp))).isEqualTo(TokenFailureReason.TOKEN_INVALID);
    }

    @ParameterizedTest
    @DisplayName("broken values are TOKEN_MALFORMED")
    @ValueSource(strings = {"", "abc", "a.b", "not.a.jwt", "%%%.%%%.%%%"})
    void malformed(String token) {
        assertThat(reasonOf(token)).isEqualTo(TokenFailureReason.TOKEN_MALFORMED);
    }

    @Test
    @DisplayName("a token signed with the right key but already expired is TOKEN_EXPIRED")
    void expiredFromHelper() {
        assertThat(reasonOf(tokens.hs256(1, NOW.minusSeconds(600), NOW.minusSeconds(300))))
                .isEqualTo(TokenFailureReason.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("the failure message never contains the token value")
    void messageHasNoToken() {
        String token = tokens.tamperedSignature(1, NOW, NOW.plusSeconds(300));

        assertThatThrownBy(() -> service.verify(new AccessTokenValue(token)))
                .hasMessageNotContaining(token)
                .hasMessageNotContaining(token.substring(token.lastIndexOf('.') + 1));
    }
}
