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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.common.observability.TraceIdProvider;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientInfoTest {

    private final TraceIdProvider traceIdProvider = mock(TraceIdProvider.class);

    private final ClientInfoResolver resolver = new ClientInfoResolver(traceIdProvider);

    private MockHttpServletRequest request(String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        return request;
    }

    @Test
    @DisplayName("the client address, user agent and trace id are taken from the request")
    void takesFields() {
        when(traceIdProvider.currentTraceId()).thenReturn(Optional.of("trace-1"));

        ClientInfo info = resolver.resolve(request("テスト用のブラウザ"));

        assertThat(info).isEqualTo(new ClientInfo("192.0.2.10", "テスト用のブラウザ", "trace-1"));
    }

    @Test
    @DisplayName("a user agent longer than 512 characters is cut to 512")
    void truncatesUserAgent() {
        when(traceIdProvider.currentTraceId()).thenReturn(Optional.empty());

        ClientInfo info = resolver.resolve(request("あ".repeat(600)));

        assertThat(info.userAgent()).hasSize(512).isEqualTo("あ".repeat(512));
    }

    @Test
    @DisplayName("a user agent of exactly 512 characters is kept")
    void keepsBoundary() {
        when(traceIdProvider.currentTraceId()).thenReturn(Optional.empty());

        assertThat(resolver.resolve(request("a".repeat(512))).userAgent()).hasSize(512);
    }

    @Test
    @DisplayName("a missing user agent and a missing trace id become null")
    void missingFields() {
        when(traceIdProvider.currentTraceId()).thenReturn(Optional.empty());

        ClientInfo info = resolver.resolve(request(null));

        assertThat(info.userAgent()).isNull();
        assertThat(info.traceId()).isNull();
        assertThat(info.sourceIp()).isEqualTo("192.0.2.10");
    }
}
