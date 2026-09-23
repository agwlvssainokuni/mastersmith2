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
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
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

/** 監査の記録をアプリのログで代用していないことの結合テスト（BR3.2、NFR9.2）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AuditNotInAppLogIT {

    private static final String PASSWORD = "正しいパスワード-1234";

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

    private AuditRows rows;

    private String email;

    @BeforeEach
    void setUp() {
        api = new AuthApi(port);
        rows = new AuditRows(jdbc);
        email = "notinlog-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), false);
    }

    @Test
    @DisplayName("a successful login adds one row to the table and writes nothing to the application log")
    void successfulRecordIsOnlyInTheTable(CapturedOutput output) {
        int before = rows.count();

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThat(api.login(email, PASSWORD).statusCode()).isEqualTo(200);

            assertThat(logs.list()).as("成功した記録はアプリのログに出さない").isEmpty();
        }

        assertThat(rows.count()).isEqualTo(before + 1);
        assertThat(rows.last().eventType()).isEqualTo("LOGIN_SUCCEEDED");
        assertThat(JsonLogRecords.parse(output.getOut()))
                .noneMatch(record -> String.valueOf(record.get("message")).contains("LOGIN_SUCCEEDED"));
    }

    @Test
    @DisplayName("a failed login and a logout are recorded in the table only")
    void otherEventsAreOnlyInTheTable(CapturedOutput output) {
        int before = rows.count();

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            api.login(email, "まちがい");
            String cookie = AuthApi.cookieValue(api.login(email, PASSWORD));
            api.logout(cookie, api.origin());

            assertThat(logs.list()).isEmpty();
        }

        assertThat(rows.count()).isEqualTo(before + 3);
        assertThat(JsonLogRecords.parse(output.getOut()))
                .noneMatch(record -> String.valueOf(record.get("message")).contains("LOGGED_OUT"));
    }

    @Test
    @DisplayName("the audit table is the only store of the events, not the application log")
    void theTableIsTheOnlyStore() {
        int before = rows.count();

        api.login(email, PASSWORD);
        api.login(email, "まちがい");

        assertThat(rows.all()).hasSize(before + 2);
        assertThat(rows.all())
                .extracting(AuditRows.AuditRow::eventType)
                .containsSequence("LOGIN_SUCCEEDED", "LOGIN_FAILED");
    }
}
