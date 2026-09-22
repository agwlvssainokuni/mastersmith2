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

import java.time.Duration;
import java.time.Instant;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenExpiryTest {

    private static final Instant ISSUED = Instant.parse("2026-09-22T00:00:00Z");

    @Property
    @Label("a token is valid exactly when now is before the expiry")
    void equivalentToIsBefore(
            @ForAll @LongRange(min = -1_000_000, max = 1_000_000) long nowOffset,
            @ForAll @LongRange(min = -1_000_000, max = 1_000_000) long expiryOffset) {
        Instant now = ISSUED.plusMillis(nowOffset);
        Instant expiresAt = ISSUED.plusMillis(expiryOffset);

        assertThat(TokenExpiry.isValid(now, expiresAt)).isEqualTo(nowOffset < expiryOffset);
    }

    @Test
    @DisplayName("an access token is valid at 4 minutes 59 seconds and invalid at exactly 5 minutes")
    void accessTokenBoundary() {
        Instant expiresAt = ISSUED.plus(Duration.ofMinutes(5));

        assertThat(TokenExpiry.isValid(ISSUED.plusSeconds(299), expiresAt)).isTrue();
        assertThat(TokenExpiry.isValid(ISSUED.plusSeconds(300), expiresAt)).isFalse();
    }

    @Test
    @DisplayName("a refresh token is valid at 23:59:59 and invalid at exactly 24 hours")
    void refreshTokenBoundary() {
        Instant expiresAt = ISSUED.plus(Duration.ofHours(24));

        assertThat(TokenExpiry.isValid(expiresAt.minusSeconds(1), expiresAt)).isTrue();
        assertThat(TokenExpiry.isValid(expiresAt, expiresAt)).isFalse();
    }

    @Test
    @DisplayName("a time after the expiry is invalid")
    void afterExpiry() {
        assertThat(TokenExpiry.isValid(ISSUED.plusSeconds(1), ISSUED)).isFalse();
    }
}
