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
package cherry.mastersmith.config;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 応答のヘッダーの結合テスト（NFR3.10、security-design 2章、performance-design 5章）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.test-fixture.public-api=true")
class SecurityHeadersIT {

    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;"
            + " font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    private HttpTestClient client() {
        return new HttpTestClient(port);
    }

    private static void assertSecurityHeaders(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("Content-Security-Policy")).contains(CSP);
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("same-origin");
        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    static Stream<Arguments> responses() {
        return Stream.of(
                Arguments.of("problem page", "/api/problems/not-found", 200, "no-store"),
                Arguments.of("API error response", "/api/no-such-api", 404, "no-store"),
                Arguments.of("screen entry", "/", 200, "no-cache"),
                Arguments.of("screen URL", "/admin/users", 200, "no-cache"),
                Arguments.of("health", "/actuator/health", 200, "no-store"),
                Arguments.of("hashed screen file", "/assets/app-test.js", 200, "public, max-age=31536000, immutable"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("responses")
    @DisplayName("every kind of response carries the security headers and the expected cache control")
    void headersOnEveryResponse(String name, String path, int status, String cacheControl) {
        HttpResponse<String> response = client().get(path);

        assertThat(response.statusCode()).as(name).isEqualTo(status);
        assertSecurityHeaders(response);
        assertThat(response.headers().allValues("Cache-Control")).as(name).containsExactly(cacheControl);
    }

    @Test
    @DisplayName("413 rejection from the filter chain also carries the security headers and no-store")
    void headersOnPayloadTooLarge() {
        HttpResponse<String> response = client().send(client().request("/api/anything")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[1024 * 1024 + 1]))
                .build());

        assertThat(response.statusCode()).isEqualTo(413);
        assertSecurityHeaders(response);
        assertThat(response.headers().allValues("Cache-Control")).containsExactly("no-store");
        assertThat(HttpTestClient.json(response)).containsEntry("code", "PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("no session cookie is created even for repeated requests")
    void noSessionCookie() {
        for (String path : new String[] {"/", "/api/no-such-api", "/actuator/health", "/api/problems/not-found"}) {
            assertThat(client().get(path).headers().firstValue("Set-Cookie"))
                    .as(path)
                    .isEmpty();
        }
    }
}
