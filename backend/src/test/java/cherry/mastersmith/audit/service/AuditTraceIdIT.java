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
package cherry.mastersmith.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/** 監査イベントとアプリのログのトレースIDが一致することの結合テスト（FR10.2、BR2.4、NFR10.4）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.auth.password.bcrypt-cost=4",
            // メソッドの呼び出しの追跡（U1）で、記録の処理のログを同じ要求のトレースIDつきで出す。
            "logging.level.cherry.mastersmith.audit=TRACE"
        })
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AuditTraceIdIT {

    private static final String PASSWORD = "正しいパスワード-1234";

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

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    private AuthApi api;

    private HttpTestClient client;

    private AuditRows rows;

    private String email;

    @BeforeEach
    void setUp() {
        api = new AuthApi(port);
        client = new HttpTestClient(port);
        rows = new AuditRows(jdbc);
        email = "trace-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), false);
    }

    /** traceparent を付けてログインする（U2 の補助はヘッダーを足せないため、要求を組み立てて送る）。 */
    private void loginWithTraceparent(String traceparent) {
        String body = JsonMapper.builder().build().writeValueAsString(Map.of("email", email, "password", PASSWORD));
        client.send(client.request("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("traceparent", traceparent)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    /** 監査の記録の処理が出したアプリのログを、トレースIDつきで取り出す。 */
    private static List<Map<String, Object>> auditLogs(CapturedOutput output) {
        // 本番のコードのロガーだけを見る（テストのクラス自身のロガーは Spring の起動のログを出すため除く）。
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> String.valueOf(record.get("logger")).startsWith("cherry.mastersmith.audit."))
                .filter(record -> !AuditTraceIdIT.class.getName().equals(record.get("logger")))
                .toList();
    }

    @Test
    @DisplayName("with a traceparent the audit row carries the trace id of the request and of the application log")
    void traceparentIsCarriedIntoTheAuditRow(CapturedOutput output) {
        loginWithTraceparent("00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-01");

        AuditRows.AuditRow row = rows.last();
        assertThat(row.eventType()).isEqualTo("LOGIN_SUCCEEDED");
        assertThat(row.traceId()).isEqualTo(TRACE_ID);
        assertThat(auditLogs(output))
                .filteredOn(record -> TRACE_ID.equals(record.get("traceId")))
                .isNotEmpty();
    }

    @Test
    @DisplayName("without a traceparent the audit row carries the new trace id of the application log")
    void newTraceIsCarriedIntoTheAuditRow(CapturedOutput output) {
        api.login(email, PASSWORD);

        AuditRows.AuditRow row = rows.last();
        assertThat(row.traceId()).matches("[0-9a-f]{32}").isNotEqualTo(TRACE_ID);
        assertThat(auditLogs(output))
                .filteredOn(record -> row.traceId().equals(record.get("traceId")))
                .as("監査イベントのトレースIDと同じ要求のアプリのログのトレースIDが一致する")
                .isNotEmpty();
    }

    @Test
    @DisplayName("two requests get two different trace ids in their audit rows")
    void differentRequestsGetDifferentTraceIds() {
        api.login(email, PASSWORD);
        String first = rows.last().traceId();
        api.login(email, "まちがい");
        String second = rows.last().traceId();

        assertThat(first).isNotEqualTo(second);
        assertThat(second).matches("[0-9a-f]{32}");
    }
}
