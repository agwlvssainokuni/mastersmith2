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
package cherry.mastersmith.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.access.domain.AccessProblemTypes;
import cherry.mastersmith.access.service.AccessDeniedEventPublisher;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.config.SecurityHeaderProperties;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.firewall.RequestRejectedException;

class AccessRequestRejectedHandlerTest {

    private static final String CSP = "default-src 'self'; frame-ancestors 'none'";

    private static final String CRAFTED_PATH = "/api/admin/..;/secret";

    private final ErrorResponseWriter errorResponseWriter = mock(ErrorResponseWriter.class);

    private final AccessDeniedEventPublisher eventPublisher = mock(AccessDeniedEventPublisher.class);

    private final AccessRequestRejectedHandler handler =
            new AccessRequestRejectedHandler(errorResponseWriter, new SecurityHeaderProperties(CSP));

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", CRAFTED_PATH);
    }

    private static RequestRejectedException rejection() {
        return new RequestRejectedException(
                "The request was rejected because the URL contained a potentially" + " malicious String \";\"");
    }

    @Test
    @DisplayName("the 400 response is written by the error response writer of U1")
    void responseIsWrittenByU1() throws IOException {
        MockHttpServletRequest request = request();

        handler.handle(request, response, rejection());

        verify(errorResponseWriter, times(1)).write(eq(request), eq(response), eq(AccessProblemTypes.REQUEST_REJECTED));
        assertThat(AccessProblemTypes.REQUEST_REJECTED.status()).isEqualTo(400);
        assertThat(AccessProblemTypes.REQUEST_REJECTED.code()).isEqualTo("REQUEST_REJECTED");
    }

    @Test
    @DisplayName("the four security headers are written with the values configured in U1")
    void securityHeadersUseTheConfigurationOfU1() throws IOException {
        handler.handle(request(), response, rejection());

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(CSP);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
    }

    @Test
    @DisplayName("the cache control header is left to the filter of U1")
    void cacheControlIsLeftToU1() throws IOException {
        handler.handle(request(), response, rejection());

        assertThat(response.getHeader("Cache-Control")).isNull();
    }

    @Test
    @DisplayName("the warning carries the code only, the trace id being added by the logging of U1")
    void warningCarriesTheCodeOnly() throws IOException {
        try (LogEvents events = LogEvents.capture(AccessRequestRejectedHandler.class)) {
            handler.handle(request(), response, rejection());

            assertThat(events.list()).hasSize(1);
            assertThat(events.list().getFirst().getLevel()).isEqualTo(Level.WARN);
            Map<String, Object> pairs = events.list().getFirst().getKeyValuePairs().stream()
                    .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
            assertThat(pairs).containsOnlyKeys("code");
            assertThat(pairs).containsEntry("code", "REQUEST_REJECTED");
        }
    }

    @Test
    @DisplayName("the warning never writes the rejected path or the message of the rejection")
    void warningHidesTheRejectedPath() throws IOException {
        try (LogEvents events = LogEvents.capture(AccessRequestRejectedHandler.class)) {
            handler.handle(request(), response, rejection());

            assertThat(events.list().getFirst().getFormattedMessage())
                    .doesNotContain(CRAFTED_PATH)
                    .doesNotContain("..;")
                    .doesNotContain("malicious String");
            assertThat(events.list().getFirst().getThrowableProxy()).isNull();
        }
    }

    @Test
    @DisplayName("the rejection produces no access denied event because it happens before the decision")
    void noEventIsPublished() throws IOException {
        handler.handle(request(), response, rejection());

        verifyNoInteractions(eventPublisher);
        verify(errorResponseWriter, times(1)).write(any(), any(), eq(AccessProblemTypes.REQUEST_REJECTED));
    }
}
