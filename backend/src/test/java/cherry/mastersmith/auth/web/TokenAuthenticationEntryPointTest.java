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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.auth.domain.TokenAuthenticationException;
import cherry.mastersmith.auth.domain.TokenFailureReason;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import cherry.mastersmith.common.testsupport.LogEvents;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class TokenAuthenticationEntryPointTest {

    private final List<ProblemType> written = new ArrayList<>();

    private final ErrorResponseWriter writer = new ErrorResponseWriter() {
        @Override
        public void write(HttpServletRequest request, HttpServletResponse response, ProblemType problemType)
                throws IOException {
            written.add(problemType);
            response.setStatus(problemType.status());
        }
    };

    private final TokenAuthenticationEntryPoint entryPoint = new TokenAuthenticationEntryPoint(writer);

    private final Logger logger = (Logger) LoggerFactory.getLogger(TokenAuthenticationEntryPoint.class);

    private final Level originalLevel = logger.getLevel();

    @AfterEach
    void restoreLevel() {
        logger.setLevel(originalLevel);
    }

    private MockHttpServletResponse commence(org.springframework.security.core.AuthenticationException exception)
            throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, exception);
        return response;
    }

    @Test
    @DisplayName("the response is 401 AUTHENTICATION_REQUIRED written by the common writer")
    void writesUnauthorized() throws IOException {
        MockHttpServletResponse response = commence(new TokenAuthenticationException(TokenFailureReason.TOKEN_INVALID));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(written).containsExactly(AuthProblemTypes.AUTHENTICATION_REQUIRED);
    }

    @ParameterizedTest
    @DisplayName("the reason of a token failure is logged at DEBUG")
    @EnumSource(TokenFailureReason.class)
    void logsReason(TokenFailureReason reason) throws IOException {
        logger.setLevel(Level.DEBUG);

        try (LogEvents events = LogEvents.capture(TokenAuthenticationEntryPoint.class)) {
            commence(new TokenAuthenticationException(reason));

            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getKeyValuePairs().toString()).contains(reason.name());
            });
        }
    }

    @Test
    @DisplayName("a request without a token is logged as TOKEN_MISSING")
    void missingToken() throws IOException {
        logger.setLevel(Level.DEBUG);

        try (LogEvents events = LogEvents.capture(TokenAuthenticationEntryPoint.class)) {
            commence(new InsufficientAuthenticationException("ログインが必要"));

            assertThat(events.list().getFirst().getKeyValuePairs().toString()).contains("TOKEN_MISSING");
        }
    }

    @Test
    @DisplayName("neither the log nor the response carries the token value")
    void noTokenValue() throws IOException {
        logger.setLevel(Level.DEBUG);
        String token = "header.payload.signature";

        try (LogEvents events = LogEvents.capture(TokenAuthenticationEntryPoint.class)) {
            MockHttpServletResponse response = commence(new InsufficientAuthenticationException("Bearer " + token));

            assertThat(events.list().getFirst().getKeyValuePairs().toString()).doesNotContain(token);
            assertThat(events.list().getFirst().getFormattedMessage()).doesNotContain(token);
            assertThat(response.getContentAsString()).doesNotContain(token);
        }
    }
}
