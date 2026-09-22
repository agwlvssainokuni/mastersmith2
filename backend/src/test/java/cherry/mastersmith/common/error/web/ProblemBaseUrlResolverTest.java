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
package cherry.mastersmith.common.error.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.web.MastersmithWebProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;

class ProblemBaseUrlResolverTest {

    private static ProblemBaseUrlResolver resolver(String baseUrl) {
        return new ProblemBaseUrlResolver(new MastersmithWebProperties(baseUrl, false, DataSize.ofMegabytes(1)));
    }

    private static MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        return request;
    }

    @Test
    @DisplayName("configured base URL takes precedence over the request")
    void configuredBaseUrlWins() {
        assertThat(resolver("https://app.example.com").resolve(request("http", "localhost", 8080)))
                .isEqualTo("https://app.example.com");
    }

    @Test
    @DisplayName("trailing slashes of the configured base URL are removed")
    void trailingSlashRemoved() {
        assertThat(resolver(" https://app.example.com/base// ").resolve(request("http", "localhost", 8080)))
                .isEqualTo("https://app.example.com/base");
    }

    @Test
    @DisplayName("without configuration the base URL is built from scheme, host and port of the request")
    void builtFromRequest() {
        assertThat(resolver(null).resolve(request("http", "localhost", 8080))).isEqualTo("http://localhost:8080");
        assertThat(resolver("").resolve(request("HTTPS", "example.test", 8443))).isEqualTo("https://example.test:8443");
    }

    @Test
    @DisplayName("default ports 80 and 443 are omitted")
    void defaultPortsOmitted() {
        assertThat(resolver(null).resolve(request("http", "example.test", 80))).isEqualTo("http://example.test");
        assertThat(resolver(null).resolve(request("https", "example.test", 443)))
                .isEqualTo("https://example.test");
        assertThat(resolver(null).resolve(request("http", "example.test", 443))).isEqualTo("http://example.test:443");
    }

    @Test
    @DisplayName("forwarded headers are ignored unless the forwarded header filter applied them")
    void forwardedHeadersIgnored() {
        MockHttpServletRequest request = request("http", "localhost", 8080);
        request.addHeader("X-Forwarded-Host", "evil.example.com");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("Forwarded", "host=evil.example.com;proto=https");

        assertThat(resolver(null).resolve(request)).isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("IPv6 hosts are enclosed in brackets")
    void ipv6Host() {
        assertThat(resolver(null).resolve(request("http", "::1", 8080))).isEqualTo("http://[::1]:8080");
    }
}
