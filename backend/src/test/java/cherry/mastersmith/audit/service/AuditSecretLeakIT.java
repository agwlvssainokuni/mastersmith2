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

import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.access.web.AdminCheckController;
import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.Mode;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

/** 監査イベントの行とアプリのログに秘密情報が出ないことの結合テスト（BR2.3、NFR3.1、team.md の必須のテスト）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.auth.password.bcrypt-cost=4",
            // 記録の処理の引数・戻り値まで出す状態にして、それでも秘密情報が出ないことを確かめる。
            "logging.level.cherry.mastersmith.audit=TRACE"
        })
@Import({AdminAccessTestConfig.class, FailingAuditEventRepositoryConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class AuditSecretLeakIT {

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

    @Autowired
    FailingAuditEventRepository repository;

    private HttpTestClient client;

    private AuthApi api;

    private AdminTestUsers users;

    private AuditRows rows;

    @BeforeEach
    void setUp() {
        client = new HttpTestClient(port);
        api = new AuthApi(port);
        users = new AdminTestUsers(userAccountService, port);
        rows = new AuditRows(jdbc);
        repository.mode(Mode.NONE);
    }

    private static String render(AuditRows.AuditRow row) {
        return String.valueOf(row.auditEventId())
                + row.occurredAt()
                + row.eventType()
                + row.result()
                + row.enteredEmail()
                + row.failureReason()
                + row.sourceIp()
                + row.userAgent()
                + row.requestPath()
                + row.traceId();
    }

    @Test
    @DisplayName("neither the audit rows nor the application log carry a password, a hash or a token")
    void noSecretInTheRowsOrTheLog(CapturedOutput output) {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        HttpResponse<String> login = api.login(member.email(), AdminTestUsers.PASSWORD);
        String accessToken = AuthApi.accessToken(login);
        String refreshToken = AuthApi.cookieValue(login);
        api.login(member.email(), "まちがったパスワード");
        client.get(AdminCheckController.PATH, "Authorization", "Bearer " + accessToken);
        api.logout(refreshToken, api.origin());
        repository.mode(Mode.APPEND_FAILURE);
        api.login(member.email(), AdminTestUsers.PASSWORD);

        List<AuditRows.AuditRow> all = rows.all();
        assertThat(all).isNotEmpty();
        String rendered = all.stream().map(AuditSecretLeakIT::render).reduce("", String::concat);
        assertThat(rendered)
                .doesNotContain(AdminTestUsers.PASSWORD)
                .doesNotContain(accessToken)
                .doesNotContain(refreshToken)
                .doesNotContain("$2a$")
                .doesNotContain("Bearer ");
        JsonLogRecords.assertContainsNoSecret(output.getOut(), AdminTestUsers.PASSWORD, accessToken, refreshToken);
        assertThat(output.getOut()).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("the audit rows carry the four recorded event types and no column beyond the recorded fields")
    void recordedTypesAndColumns() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String accessToken = AuthApi.accessToken(api.login(member.email(), AdminTestUsers.PASSWORD));
        api.login(member.email(), "まちがったパスワード");
        client.get(AdminCheckController.PATH, "Authorization", "Bearer " + accessToken);
        api.logout(AuthApi.cookieValue(api.login(member.email(), AdminTestUsers.PASSWORD)), api.origin());

        assertThat(rows.all())
                .extracting(AuditRows.AuditRow::eventType)
                .contains("LOGIN_SUCCEEDED", "LOGIN_FAILED", "LOGGED_OUT", "ACCESS_DENIED");
        List<String> columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'AUDIT_EVENTS'", String.class);
        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "AUDIT_EVENT_ID",
                        "OCCURRED_AT",
                        "EVENT_TYPE",
                        "RESULT",
                        "ENTERED_EMAIL",
                        "FAILURE_REASON",
                        "SOURCE_IP",
                        "USER_AGENT",
                        "REQUEST_PATH",
                        "TRACE_ID",
                        // Intent 260923-dsl-schema-loader の U4 の V6 で足した、DSL の操作の列（契約 C7。本文・接続先は持たない）
                        "ACTOR_USER_ID",
                        "DSL_HASH",
                        "DSL_SOURCE",
                        "REJECTION_KIND");
    }

    @Test
    @DisplayName("every log line is one JSON object and the audit lines carry a trace id")
    void everyLineIsJsonAndTheAuditLinesCarryATraceId(CapturedOutput output) {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        api.login(member.email(), AdminTestUsers.PASSWORD);

        JsonLogRecords.assertAllLinesAreJson(output.getOut());
        // 本番のコードのロガーだけを見る（テストのクラス自身のロガーは Spring の起動のログを出すため除く）。
        List<Map<String, Object>> auditLines = JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> String.valueOf(record.get("logger")).startsWith("cherry.mastersmith.audit."))
                .filter(record -> !AuditSecretLeakIT.class.getName().equals(record.get("logger")))
                .toList();
        assertThat(auditLines).isNotEmpty();
        assertThat(auditLines)
                .allSatisfy(
                        record -> assertThat(record.get("traceId")).asString().matches("[0-9a-f]{32}"));
    }
}
