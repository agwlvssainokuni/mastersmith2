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
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.access.testsupport.AdminTestUsers.TestUser;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.Mode;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 利用者の管理の操作の監査の書き込みが失敗しても、操作の応答と状態が変わらないことの結合テスト（Intent 260930-user-admin の U3、
 * BR6.4、NFR9.4。既存の {@code AuditWriteFailureIT} と同じ形）。アプリのログに ERROR が1件出て、メールアドレス・氏名を含まない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, FailingAuditEventRepositoryConfig.class})
class UserAdminAuditWriteFailureIT {

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
    FailingAuditEventRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private HttpTestClient client;

    private AdminTestUsers users;

    private TestUser admin;

    private String adminToken;

    @BeforeEach
    void setUp() {
        client = new HttpTestClient(port);
        users = new AdminTestUsers(userAccountService, port);
        repository.mode(Mode.NONE);
        admin = users.createAdmin();
        adminToken = users.accessToken(admin);
        repository.takeSaveCalls();
    }

    private HttpResponse<String> operate(long userId, String action) {
        return client.send(client.request("/api/admin/users/" + userId + "/" + action)
                .header("Authorization", "Bearer " + adminToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    }

    private Map<String, Object> columns(long userId) {
        return jdbc.queryForMap("SELECT admin_flag, suspended FROM users WHERE user_id = ?", userId);
    }

    private String emailOf(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, userId);
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        return pairs == null
                ? Map.of()
                : pairs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    private void assertOneErrorWithoutPersonalValues(LogEvents logs, long target, String operation, String result) {
        assertThat(logs.list()).hasSize(1);
        ILoggingEvent error = logs.list().getFirst();
        assertThat(error.getLevel()).isEqualTo(Level.ERROR);
        Map<String, Object> fields = keyValues(error);
        assertThat(fields)
                .containsEntry("auditEventType", operation)
                .containsEntry("result", result)
                .containsEntry("actorUserId", String.valueOf(admin.userId()))
                .containsEntry("targetUserId", String.valueOf(target));
        String text = error.getFormattedMessage() + " " + fields;
        assertThat(text)
                .doesNotContain(emailOf(target))
                .doesNotContain(admin.email())
                .doesNotContain(TestUserAccounts.DISPLAY_NAME);
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"APPEND_FAILURE", "CONNECTION_FAILURE"})
    @DisplayName("a failing audit write keeps a successful suspend at 204 with the user suspended")
    void successIsKept(Mode mode) {
        long target = users.createNonAdmin().userId();
        repository.mode(mode);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            HttpResponse<String> response = operate(target, "suspend");

            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(columns(target)).containsEntry("SUSPENDED", true);
            assertOneErrorWithoutPersonalValues(logs, target, "USER_SUSPENDED", "SUCCESS");
        }
        assertThat(repository.takeSaveCalls()).as("再試行はしない").isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"APPEND_FAILURE", "CONNECTION_FAILURE"})
    @DisplayName("a failing audit write keeps a rejection at 409 with nothing changed")
    void rejectionIsKept(Mode mode) {
        long target = users.createAdmin().userId();
        Map<String, Object> before = columns(target);
        repository.mode(mode);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            HttpResponse<String> response = operate(target, "grant-admin");

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(HttpTestClient.json(response)).containsEntry("code", "USER_ADMIN_NO_CHANGE");
            assertThat(columns(target)).isEqualTo(before);
            assertOneErrorWithoutPersonalValues(logs, target, "USER_ADMIN_GRANTED", "FAILURE");
        }
    }
}
