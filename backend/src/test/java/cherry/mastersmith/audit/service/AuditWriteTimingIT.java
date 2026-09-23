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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.audit.testsupport.SlowAuditWriteConfig;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
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

/** 遅い書き込みの WARN の結合テスト（{@code infrastructure-design/monitoring-design.md} 1章）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, SlowAuditWriteConfig.class})
class AuditWriteTimingIT {

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
        email = "timing-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), false);
    }

    @Test
    @DisplayName("a write beyond the threshold logs exactly one warning with the elapsed time")
    void slowWriteLogsOneWarning() {
        int before = rows.count();

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThat(api.login(email, PASSWORD).statusCode()).isEqualTo(200);

            assertThat(logs.list()).hasSize(1);
            ILoggingEvent warning = logs.list().getFirst();
            assertThat(warning.getLevel()).isEqualTo(Level.WARN);
            assertThat(warning.getMessage()).isEqualTo(AuditEventListener.SLOW_WRITE_MESSAGE);
            assertThat(warning.getKeyValuePairs())
                    .anySatisfy(pair -> assertThat(pair.key).isEqualTo("elapsedMs"));
        }

        assertThat(rows.count()).as("遅れても記録そのものは行われる").isEqualTo(before + 1);
    }

    @Test
    @DisplayName("a slow write changes neither the login nor the logout response")
    void slowWriteDoesNotChangeTheResponses() {
        String cookie = AuthApi.cookieValue(api.login(email, PASSWORD));

        assertThat(api.logout(cookie, api.origin()).statusCode()).isEqualTo(204);
        assertThat(api.login(email, "まちがい").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("each slow write logs its own single warning")
    void eachSlowWriteLogsItsOwnWarning() {
        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            api.login(email, PASSWORD);
            api.login(email, "まちがい");

            assertThat(logs.list())
                    .hasSize(2)
                    .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        }
    }
}
