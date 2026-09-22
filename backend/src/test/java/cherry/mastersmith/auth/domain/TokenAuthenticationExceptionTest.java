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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.AuthenticationException;

class TokenAuthenticationExceptionTest {

    @ParameterizedTest
    @DisplayName("each reason is carried by the exception")
    @EnumSource(TokenFailureReason.class)
    void carriesReason(TokenFailureReason reason) {
        TokenAuthenticationException exception = new TokenAuthenticationException(reason);

        assertThat(exception.reason()).isEqualTo(reason);
        assertThat(exception.getMessage()).isEqualTo(reason.name());
    }

    @Test
    @DisplayName("the exception is an AuthenticationException so that the entry point receives it")
    void isAuthenticationException() {
        assertThat(new TokenAuthenticationException(TokenFailureReason.TOKEN_EXPIRED))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("the error carries the invalid_token code and the reason only")
    void errorContents() {
        TokenAuthenticationException exception = new TokenAuthenticationException(TokenFailureReason.TOKEN_INVALID);

        assertThat(exception.getError().getErrorCode()).isEqualTo("invalid_token");
        assertThat(exception.getError().getDescription()).isEqualTo("TOKEN_INVALID");
        assertThat(exception.getError().getUri()).isNull();
    }

    @Test
    @DisplayName("four reasons are defined")
    void fourReasons() {
        assertThat(TokenFailureReason.values()).hasSize(4);
    }
}
