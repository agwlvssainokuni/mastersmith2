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
package cherry.mastersmith.access.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.domain.TokenAuthenticationException;
import cherry.mastersmith.auth.domain.TokenFailureReason;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class AccessDeniedReasonTest {

    @Test
    @DisplayName("an expired token produces no reason so that no event is published")
    void expiredProducesNoReason() {
        assertThat(AccessDeniedReason.of(TokenFailureReason.TOKEN_EXPIRED)).isEmpty();
        assertThat(AccessDeniedReason.of(new TokenAuthenticationException(TokenFailureReason.TOKEN_EXPIRED)))
                .isEmpty();
    }

    @Test
    @DisplayName("the other token failures map to the matching reason")
    void otherFailuresMap() {
        assertThat(AccessDeniedReason.of(TokenFailureReason.TOKEN_MALFORMED))
                .contains(AccessDeniedReason.TOKEN_MALFORMED);
        assertThat(AccessDeniedReason.of(TokenFailureReason.TOKEN_INVALID)).contains(AccessDeniedReason.TOKEN_INVALID);
        assertThat(AccessDeniedReason.of(TokenFailureReason.USER_NOT_FOUND))
                .contains(AccessDeniedReason.USER_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(TokenFailureReason.class)
    @DisplayName("every token failure reason of U2 is covered by the conversion")
    void everyTokenFailureReasonIsCovered(TokenFailureReason reason) {
        Optional<AccessDeniedReason> converted = AccessDeniedReason.of(reason);

        if (reason == TokenFailureReason.TOKEN_EXPIRED) {
            assertThat(converted).isEmpty();
        } else {
            assertThat(converted).isPresent();
            assertThat(converted.orElseThrow().name()).isEqualTo(reason.name());
        }
    }

    @Test
    @DisplayName("an exception that is not a token failure is treated as a missing token")
    void otherExceptionsAreMissingToken() {
        assertThat(AccessDeniedReason.of(new InsufficientAuthenticationException("認証が足りない")))
                .contains(AccessDeniedReason.TOKEN_MISSING);
    }

    @Test
    @DisplayName("no exception at all is treated as a missing token")
    void noExceptionIsMissingToken() {
        assertThat(AccessDeniedReason.of((org.springframework.security.core.AuthenticationException) null))
                .contains(AccessDeniedReason.TOKEN_MISSING);
    }

    @Test
    @DisplayName("the reasons cover the values the audit log of U4 records")
    void reasonsMatchTheAuditLog() {
        assertThat(AccessDeniedReason.values())
                .extracting(Enum::name)
                .containsExactly("NOT_ADMIN", "TOKEN_MISSING", "TOKEN_MALFORMED", "TOKEN_INVALID", "USER_NOT_FOUND");
    }
}
