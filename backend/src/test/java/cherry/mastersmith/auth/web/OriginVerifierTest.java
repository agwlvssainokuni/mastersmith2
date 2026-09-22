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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.web.ProblemBaseUrlResolver;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.web.MastersmithWebProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;

class OriginVerifierTest {

    private final OriginVerifier verifier = new OriginVerifier(
            new ProblemBaseUrlResolver(new MastersmithWebProperties(null, false, DataSize.ofMegabytes(1))));

    private static MockHttpServletRequest request(String origin, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(port);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }

    @Test
    @DisplayName("a matching origin is accepted")
    void matches() {
        assertThatCode(() -> verifier.verify(request("http://localhost:8080", 8080)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a different origin is rejected with ORIGIN_NOT_ALLOWED")
    void mismatch() {
        assertThatThrownBy(() -> verifier.verify(request("http://evil.example.com", 8080)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getProblemType()).isEqualTo(AuthProblemTypes.ORIGIN_NOT_ALLOWED));
    }

    @Test
    @DisplayName("a different port is rejected")
    void differentPort() {
        assertThatThrownBy(() -> verifier.verify(request("http://localhost:5173", 8080)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("a missing origin is rejected and the warning tells only whether it was present")
    void missingOrigin() {
        try (LogEvents events = LogEvents.capture(OriginVerifier.class)) {
            assertThatThrownBy(() -> verifier.verify(request(null, 8080))).isInstanceOf(BusinessException.class);

            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getKeyValuePairs().toString())
                        .contains("originPresent")
                        .contains("false");
            });
        }
    }
}
