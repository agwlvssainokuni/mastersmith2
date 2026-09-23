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
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 認証の出来事から監査イベントまでの結合テスト（FR9.1、FR9.2、BR1.1〜BR1.3、BR1.5、BR1.6、NFR1.3、NFR9.1）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.auth.password.bcrypt-cost=4",
            "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                    + "cherry.mastersmith.auth.testsupport.SqlStatementCounter"
        })
@Import(AuthApiTestConfig.class)
class AuditAuthenticationEventsIT {

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
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    private AuthApi api;

    private AuditRows rows;

    private String email;

    @BeforeEach
    void setUp() {
        api = new AuthApi(port);
        rows = new AuditRows(jdbc);
        clock.set(AuthApiTestConfig.START);
        email = "audit-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), false);
    }

    @Test
    @DisplayName("a successful login records LOGIN_SUCCEEDED with every required field")
    void loginSucceededIsRecorded() {
        int before = rows.count();

        assertThat(api.login(email, PASSWORD).statusCode()).isEqualTo(200);

        assertThat(rows.count()).isEqualTo(before + 1);
        AuditRows.AuditRow row = rows.last();
        assertThat(row.eventType()).isEqualTo("LOGIN_SUCCEEDED");
        assertThat(row.result()).isEqualTo("SUCCESS");
        assertThat(row.occurredAt()).isEqualTo(AuthApiTestConfig.START);
        assertThat(row.enteredEmail()).isEqualTo(email);
        assertThat(row.failureReason()).isNull();
        assertThat(row.sourceIp()).isNotBlank();
        assertThat(row.userAgent()).isNotNull();
        assertThat(row.traceId()).isNotBlank();
        assertThat(row.requestPath()).as("認証の出来事では要求のパスを記録しない").isNull();
    }

    @Test
    @DisplayName("a login with an unknown email records LOGIN_FAILED with USER_NOT_FOUND and the entered address")
    void unknownEmailIsRecorded() {
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";
        int before = rows.count();

        assertThat(api.login(unknown, PASSWORD).statusCode()).isEqualTo(401);

        assertThat(rows.count()).isEqualTo(before + 1);
        AuditRows.AuditRow row = rows.last();
        assertThat(row.eventType()).isEqualTo("LOGIN_FAILED");
        assertThat(row.result()).isEqualTo("FAILURE");
        assertThat(row.failureReason()).isEqualTo("USER_NOT_FOUND");
        assertThat(row.enteredEmail()).isEqualTo(unknown);
    }

    @Test
    @DisplayName("a wrong password records PASSWORD_MISMATCH and a locked account records ACCOUNT_LOCKED")
    void failureReasonsAreRecorded() {
        assertThat(api.login(email, "まちがい").statusCode()).isEqualTo(401);
        assertThat(rows.last().failureReason()).isEqualTo("PASSWORD_MISMATCH");

        for (int i = 0; i < 4; i++) {
            api.login(email, "まちがい");
        }
        int before = rows.count();

        assertThat(api.login(email, PASSWORD).statusCode()).isEqualTo(401);

        assertThat(rows.count()).isEqualTo(before + 1);
        assertThat(rows.last().failureReason()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("a logout records LOGGED_OUT while an unknown cookie records nothing")
    void logoutIsRecorded() {
        String cookie = AuthApi.cookieValue(api.login(email, PASSWORD));
        int before = rows.count();

        assertThat(api.logout(cookie, api.origin()).statusCode()).isEqualTo(204);

        assertThat(rows.count()).isEqualTo(before + 1);
        AuditRows.AuditRow row = rows.last();
        assertThat(row.eventType()).isEqualTo("LOGGED_OUT");
        assertThat(row.result()).isEqualTo("SUCCESS");
        assertThat(row.enteredEmail()).isEqualTo(email);

        int afterLogout = rows.count();
        assertThat(api.logout("unknown-token-value", api.origin()).statusCode()).isEqualTo(204);
        assertThat(rows.count()).isEqualTo(afterLogout);
    }

    @Test
    @DisplayName("the audit insert runs on the request thread and issues one insert only")
    void auditInsertRunsOnTheRequestThread() {
        SqlStatementCounter.start();
        api.login(email, PASSWORD);
        Map<String, List<String>> byThread = SqlStatementCounter.stop();

        assertThat(byThread).as("記録は要求と同じスレッドで行う").hasSize(1);
        assertThat(byThread.values().iterator().next())
                .filteredOn(kind -> kind.contains("audit_events"))
                .containsExactly("insert audit_events");
    }

    @Test
    @DisplayName("every audit row of this unit carries the trace id of its request")
    void everyRowCarriesATraceId() {
        api.login(email, PASSWORD);
        api.login(email, "まちがい");

        assertThat(rows.all())
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.traceId()).isNotBlank());
    }
}
