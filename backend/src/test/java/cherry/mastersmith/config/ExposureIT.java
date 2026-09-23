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
import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 公開する範囲と画面の配信の結合テスト（NFR3.5〜NFR3.7、NFR3.12、security-design 4章・6章）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.test-fixture.public-api=true")
class ExposureIT {

    private static final int LIMIT = 1024 * 1024;

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

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Actuator endpoints other than health are not reachable")
    @ValueSource(
            strings = {"/actuator/env", "/actuator/configprops", "/actuator/heapdump", "/actuator/metrics", "/actuator"
            })
    void actuatorEndpointsNotExposed(String path) {
        HttpResponse<String> response = client().get(path);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("/h2-console is not the H2 console but the screen entry page")
    void noH2Console() {
        HttpResponse<String> response = client().get("/h2-console");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("test-spa-index").doesNotContainIgnoringCase("H2 Console");
    }

    @Test
    @DisplayName("screen URLs opened directly return index.html")
    void screenUrlsReturnIndex() {
        HttpResponse<String> response = client().get("/admin/users/42");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
        assertThat(response.body()).contains("test-spa-index");
    }

    @Test
    @DisplayName("unknown API path returns the 404 error response instead of index.html")
    void unknownApiIsNotIndex() {
        HttpResponse<String> response = client().get("/api/users/42");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("test-spa-index");
        assertThat(HttpTestClient.json(response)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("body over the limit with Content-Length returns 413 PAYLOAD_TOO_LARGE")
    void payloadTooLargeWithContentLength() {
        HttpResponse<String> response = client().send(client().request("/api/anything")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[LIMIT + 1]))
                .build());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("chunked body over the limit returns 413 PAYLOAD_TOO_LARGE")
    void payloadTooLargeChunked() {
        HttpResponse<String> response = client().send(client().request("/api/anything")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(new byte[LIMIT + 1])))
                .build());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("body exactly at the limit is accepted by the size check")
    void bodyAtLimitAccepted() {
        HttpResponse<String> response = client().send(client().request("/api/anything")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[LIMIT]))
                .build());

        assertThat(response.statusCode()).isNotEqualTo(413);
    }
}
