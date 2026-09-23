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

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** 共通のエラー応答の結合テスト（BR5.1〜BR5.10、BR5.16、計画の P1 の決定）。実際の番号で起動したアプリへ要求を送る。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mastersmith.test-fixture.error-endpoints=true", "mastersmith.test-fixture.public-api=true"})
@ExtendWith(OutputCaptureExtension.class)
class ErrorResponseIT {

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

    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsEntry("status", status).containsEntry("code", code);
        assertThat(body.get("traceId")).asString().matches("[0-9a-f]{32}");
    }

    /** 指定したトレースIDのログのうち、エラー応答への変換のログだけを返す。 */
    private static List<Map<String, Object>> conversionLogs(CapturedOutput output, Object traceId) {
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> GlobalExceptionHandler.class.getName().equals(record.get("logger")))
                .filter(record -> traceId.equals(record.get("traceId")))
                .toList();
    }

    @Test
    @DisplayName("unknown API returns 404 NOT_FOUND with type built from the request host")
    void unknownApi() {
        HttpResponse<String> response = client().get("/api/no-such-api");

        assertProblem(response, 404, "NOT_FOUND");
        assertThat(HttpTestClient.json(response))
                .containsEntry("type", "http://localhost:" + port + "/api/problems/not-found")
                .containsEntry("instance", "/api/no-such-api");
    }

    @Test
    @DisplayName("validation failure returns 400 VALIDATION_FAILED")
    void validationFailure() {
        HttpResponse<String> response = client().send(client().request("/api/test-fixture/echo")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"\"}"))
                .build());

        assertProblem(response, 400, "VALIDATION_FAILED");
        assertProblem(client().get("/api/test-fixture/number?value=abc"), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("unexpected exception returns 500 INTERNAL_ERROR without message or stack trace and logs ERROR once")
    void unexpectedException(CapturedOutput output) {
        String secret = TestDatabase.randomSecret();

        HttpResponse<String> response = client().get("/api/test-fixture/boom?secret=" + secret);

        assertProblem(response, 500, "INTERNAL_ERROR");
        assertThat(response.body())
                .doesNotContain(secret)
                .doesNotContain("IllegalStateException")
                .doesNotContain("\tat ");
        List<Map<String, Object>> logs =
                conversionLogs(output, HttpTestClient.json(response).get("traceId"));
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst()).containsEntry("level", "ERROR").containsKey("exception");
    }

    @Test
    @DisplayName("business exception returns its own problem type and logs WARN once without stack trace")
    void businessException(CapturedOutput output) {
        HttpResponse<String> response = client().get("/api/test-fixture/business");

        assertProblem(response, 409, "TEST_CONFLICT");
        assertThat(HttpTestClient.json(response))
                .containsEntry("detail", "表示してよい説明")
                .containsEntry("type", "http://localhost:" + port + "/api/problems/test-conflict");
        List<Map<String, Object>> logs =
                conversionLogs(output, HttpTestClient.json(response).get("traceId"));
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst()).containsEntry("level", "WARN").doesNotContainKey("exception");
        assertThat(logs.getFirst()).containsEntry("code", "TEST_CONFLICT").containsEntry("status", 409);
    }

    @Test
    @DisplayName("type URL follows the Host header and ignores forwarded headers by default")
    void typeFollowsHostAndIgnoresForwardedHeaders() {
        HttpResponse<String> response = client().get(
                        "/api/no-such-api",
                        "Host",
                        "example.test:9999",
                        "X-Forwarded-Host",
                        "evil.example.com",
                        "X-Forwarded-Proto",
                        "https",
                        "Forwarded",
                        "host=evil.example.com;proto=https");

        assertThat(HttpTestClient.json(response))
                .containsEntry("type", "http://example.test:9999/api/problems/not-found");
    }

    @Test
    @DisplayName("framework 4xx keep their status with dedicated codes and no stack trace in the log")
    void frameworkClientErrors(CapturedOutput output) {
        HttpResponse<String> methodNotAllowed = client().send(client().request("/api/test-fixture/number?value=1")
                .DELETE()
                .build());
        assertProblem(methodNotAllowed, 405, "METHOD_NOT_ALLOWED");
        assertThat(methodNotAllowed.headers().firstValue("Allow"))
                .hasValueSatisfying(allow -> assertThat(allow).contains("GET"));

        assertProblem(client().get("/api/test-fixture/number?value=1", "Accept", "text/csv"), 406, "NOT_ACCEPTABLE");

        assertProblem(
                client().send(client().request("/api/test-fixture/echo")
                        .header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofString("x"))
                        .build()),
                415,
                "UNSUPPORTED_MEDIA_TYPE");

        HttpResponse<String> malformed = client().send(client().request("/api/test-fixture/echo")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":"))
                .build());
        assertProblem(malformed, 400, "MALFORMED_REQUEST");
        List<Map<String, Object>> logs =
                conversionLogs(output, HttpTestClient.json(malformed).get("traceId"));
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst()).containsEntry("level", "WARN").doesNotContainKey("exception");
    }

    @Test
    @DisplayName("missing request parameter returns 400 VALIDATION_FAILED")
    void missingParameter() {
        assertProblem(client().get("/api/test-fixture/number"), 400, "VALIDATION_FAILED");
    }

    @Nested
    @TestPropertySource(properties = "mastersmith.web.base-url=https://app.example.com/")
    @DisplayName("with a configured base URL")
    class ConfiguredBaseUrl {

        @LocalServerPort
        int nestedPort;

        @Test
        @DisplayName("configured base URL takes precedence over the request host")
        void configuredBaseUrlWins() {
            HttpResponse<String> response =
                    new HttpTestClient(nestedPort).get("/api/no-such-api", "Host", "example.test:9999");

            assertThat(HttpTestClient.json(response))
                    .containsEntry("type", "https://app.example.com/api/problems/not-found");
        }
    }

    @Nested
    @TestPropertySource(properties = "mastersmith.web.trust-forwarded-headers=true")
    @DisplayName("when forwarded headers are trusted")
    class TrustedForwardedHeaders {

        @LocalServerPort
        int nestedPort;

        @Test
        @DisplayName("forwarded scheme and host are used for the type URL")
        void forwardedHeadersUsed() {
            HttpResponse<String> response = new HttpTestClient(nestedPort)
                    .get("/api/no-such-api", "X-Forwarded-Host", "proxy.example.com", "X-Forwarded-Proto", "https");

            assertThat(HttpTestClient.json(response))
                    .containsEntry("type", "https://proxy.example.com/api/problems/not-found");
        }
    }
}
