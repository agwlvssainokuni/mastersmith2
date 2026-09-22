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
package cherry.mastersmith.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * 分散トレースとログの結合テスト（FR10.1〜FR10.3、BR3.1〜BR3.4、BR4.1、BR4.2、BR5.3、NFR10.5、NFR10.6）。
 *
 * <p>1要求の間に複数行のログが出るよう、エラー応答の部品の追跡（TRACE）を有効にして確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.cherry.mastersmith.common.error=TRACE")
@ExtendWith(OutputCaptureExtension.class)
class TracingAndLoggingIT {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String PARENT_SPAN_ID = "00f067aa0ba902b7";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    private static String traceIdOf(HttpResponse<String> response) {
        return (String) HttpTestClient.json(response).get("traceId");
    }

    /** アプリの部品（cherry.mastersmith）が出したログのうち、指定したトレースIDのもの。 */
    private static List<Map<String, Object>> appLogs(CapturedOutput output, String traceId) {
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> String.valueOf(record.get("logger")).startsWith("cherry.mastersmith"))
                .filter(record -> traceId.equals(record.get("traceId")))
                .toList();
    }

    @Test
    @DisplayName("a valid traceparent is continued and its trace id appears in the logs and the error response")
    void validTraceparentContinued(CapturedOutput output) {
        HttpResponse<String> response = new HttpTestClient(port)
                .get("/api/no-such-api", "traceparent", "00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-01");

        assertThat(traceIdOf(response)).isEqualTo(TRACE_ID);
        List<Map<String, Object>> logs = appLogs(output, TRACE_ID);
        assertThat(logs).isNotEmpty();
        assertThat(logs)
                .allSatisfy(record -> assertThat(record.get("spanId"))
                        .asString()
                        .matches("[0-9a-f]{16}")
                        .isNotEqualTo(PARENT_SPAN_ID));
    }

    @Test
    @DisplayName("without traceparent a new trace id is assigned and matches between logs and error response")
    void newTraceWithoutHeader(CapturedOutput output) {
        HttpResponse<String> response = new HttpTestClient(port).get("/api/no-such-api");

        String traceId = traceIdOf(response);
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(appLogs(output, traceId)).isNotEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("malformed traceparent is not rejected and a new trace is started")
    @ValueSource(
            strings = {
                "00-4bf92f3577b34da6a3ce929d0e0e473-00f067aa0ba902b7-01",
                "00-4bf92f3577b34da6a3ce929d0e0e47zz-00f067aa0ba902b7-01",
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
                "garbage"
            })
    void malformedTraceparentStartsNewTrace(String traceparent) {
        HttpResponse<String> response = new HttpTestClient(port).get("/api/no-such-api", "traceparent", traceparent);

        assertThat(response.statusCode()).isEqualTo(404);
        String traceId = traceIdOf(response);
        assertThat(traceId).matches("[0-9a-f]{32}").isNotEqualTo("00000000000000000000000000000000");
        assertThat(traceparent).doesNotContain(traceId);
    }

    @Test
    @DisplayName("every log line of one request carries the same trace id and nothing leaks into the next request")
    void sameTraceIdWithinRequestOnly(CapturedOutput output) {
        HttpTestClient client = new HttpTestClient(port);
        Set<String> traceIds = new HashSet<>();

        for (int i = 0; i < 5; i++) {
            HttpResponse<String> response = client.get("/api/problems/not-found");
            assertThat(response.statusCode()).isEqualTo(200);
            HttpResponse<String> error = client.get("/api/no-such-api");
            String traceId = traceIdOf(error);
            traceIds.add(traceId);
            List<Map<String, Object>> logs = appLogs(output, traceId);
            assertThat(logs).hasSizeGreaterThan(1);
        }

        assertThat(traceIds).hasSize(5);
        List<Map<String, Object>> errorHandlerLogs = JsonLogRecords.parse(output.getOut()).stream()
                .filter(record ->
                        "cherry.mastersmith.common.error.web.GlobalExceptionHandler".equals(record.get("logger")))
                .filter(record -> "WARN".equals(record.get("level")))
                .toList();
        assertThat(errorHandlerLogs).extracting(record -> record.get("traceId")).containsOnlyElementsOf(traceIds);
    }

    @Test
    @DisplayName("every line written to standard output is one JSON record")
    void outputIsJsonLines(CapturedOutput output) {
        new HttpTestClient(port).get("/api/no-such-api");

        JsonLogRecords.assertAllLinesAreJson(output.getOut());
    }

    @Test
    @DisplayName("passwords and tokens sent with a request never appear in the log output")
    void secretsNotLogged(CapturedOutput output) {
        String password = TestDatabase.randomSecret();
        String token = TestDatabase.randomSecret();
        HttpTestClient client = new HttpTestClient(port);

        client.send(client.request("/api/no-such-api?password=" + password)
                .header("Authorization", "Bearer " + token)
                .header("Cookie", "refresh_token=" + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"password\":\"" + password + "\"}"))
                .build());

        JsonLogRecords.assertContainsNoSecret(output.getAll(), password, token);
    }

    @Nested
    @TestPropertySource(properties = "management.tracing.sampling.probability=0.0")
    @DisplayName("with a sampling probability of zero")
    class NoSampling {

        @LocalServerPort
        int nestedPort;

        @Test
        @DisplayName("trace ids are still assigned and appear in the logs and the error response")
        void traceIdsStillAssigned(CapturedOutput output) {
            HttpResponse<String> response = new HttpTestClient(nestedPort)
                    .get("/api/no-such-api", "traceparent", "00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-00");
            assertThat(traceIdOf(response)).isEqualTo(TRACE_ID);
            assertThat(appLogs(output, TRACE_ID)).isNotEmpty();

            HttpResponse<String> fresh = new HttpTestClient(nestedPort).get("/api/no-such-api");
            String traceId = traceIdOf(fresh);
            assertThat(traceId).matches("[0-9a-f]{32}");
            assertThat(appLogs(output, traceId)).isNotEmpty();
        }
    }
}
