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
import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.access.web.AdminCheckController;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.Mode;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 監査の書き込みの失敗が元の操作に影響しないことの結合テスト（FR9.4、BR3.1、NFR10.1、NFR10.3）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AdminAccessTestConfig.class, FailingAuditEventRepositoryConfig.class})
class AuditWriteFailureIT {

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

    private HttpTestClient client;

    private AuthApi api;

    private AdminTestUsers users;

    @BeforeEach
    void setUp() {
        client = new HttpTestClient(port);
        api = new AuthApi(port);
        users = new AdminTestUsers(userAccountService, port);
        repository.mode(Mode.NONE);
        repository.takeSaveCalls();
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        return pairs == null
                ? Map.of()
                : pairs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    /** 応答を比べられる形にする（要求ごとに変わるトレースIDだけを除く）。 */
    private static String comparable(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return response.statusCode() + " ";
        }
        Map<String, Object> json = new java.util.TreeMap<>(HttpTestClient.json(response));
        // 要求のたびに変わる値は、項目があることだけを比べる。
        for (String volatileKey : List.of("traceId", "accessToken", "expiresAt")) {
            json.computeIfPresent(volatileKey, (key, value) -> "<present>");
        }
        return response.statusCode() + " " + json;
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"APPEND_FAILURE", "CONNECTION_FAILURE"})
    @DisplayName("a failing audit write changes neither the login, the refresh nor the logout response")
    void authenticationResponsesAreUnchanged(Mode mode) {
        AdminTestUsers.TestUser user = users.createNonAdmin();
        String loginBefore = comparable(api.login(user.email(), AdminTestUsers.PASSWORD));
        String failedBefore = comparable(api.login(user.email(), "まちがい"));
        String cookieBefore = AuthApi.cookieValue(api.login(user.email(), AdminTestUsers.PASSWORD));
        String logoutBefore = comparable(api.logout(cookieBefore, api.origin()));

        repository.mode(mode);

        assertThat(comparable(api.login(user.email(), AdminTestUsers.PASSWORD))).isEqualTo(loginBefore);
        assertThat(comparable(api.login(user.email(), "まちがい"))).isEqualTo(failedBefore);
        String cookieAfter = AuthApi.cookieValue(api.login(user.email(), AdminTestUsers.PASSWORD));
        assertThat(comparable(api.logout(cookieAfter, api.origin()))).isEqualTo(logoutBefore);
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"APPEND_FAILURE", "CONNECTION_FAILURE"})
    @DisplayName("a failing audit write changes neither the 401 nor the 403 response")
    void accessDeniedResponsesAreUnchanged(Mode mode) {
        String memberToken = users.accessToken(users.createNonAdmin());
        String deniedBefore =
                comparable(client.get(AdminCheckController.PATH, "Authorization", "Bearer " + memberToken));
        String unauthenticatedBefore = comparable(client.get(AdminCheckController.PATH));

        repository.mode(mode);

        assertThat(comparable(client.get(AdminCheckController.PATH, "Authorization", "Bearer " + memberToken)))
                .isEqualTo(deniedBefore);
        assertThat(comparable(client.get(AdminCheckController.PATH))).isEqualTo(unauthenticatedBefore);
    }

    @Test
    @DisplayName("one failing write logs exactly one error with every field, including the email address")
    void oneFailureLogsOneErrorWithEveryField() {
        AdminTestUsers.TestUser user = users.createNonAdmin();
        repository.mode(Mode.APPEND_FAILURE);
        repository.takeSaveCalls();

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            api.login(user.email(), AdminTestUsers.PASSWORD);

            assertThat(logs.list()).hasSize(1);
            ILoggingEvent error = logs.list().getFirst();
            assertThat(error.getLevel()).isEqualTo(Level.ERROR);
            assertThat(error.getMessage()).isEqualTo(AuditEventListener.FAILURE_MESSAGE);
            assertThat(keyValues(error))
                    .containsEntry("auditEventType", "LOGIN_SUCCEEDED")
                    .containsEntry("result", "SUCCESS")
                    .containsEntry("enteredEmail", user.email())
                    .containsEntry("exceptionType", "org.springframework.dao.DataIntegrityViolationException")
                    .containsKeys(
                            "occurredAt", "failureReason", "sourceIp", "userAgent", "requestPath", "auditTraceId");
            assertThat(error.getThrowableProxy()).isNotNull();
        }
        assertThat(repository.takeSaveCalls()).as("再試行はしない").isEqualTo(1);
    }

    @Test
    @DisplayName("a failing write of an access denial also logs one error with its request path")
    void accessDenialFailureLogsOneError() {
        String memberToken = users.accessToken(users.createNonAdmin());
        repository.mode(Mode.CONNECTION_FAILURE);
        repository.takeSaveCalls();

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            assertThat(client.get(AdminCheckController.PATH, "Authorization", "Bearer " + memberToken)
                            .statusCode())
                    .isEqualTo(403);

            assertThat(logs.list()).hasSize(1);
            assertThat(keyValues(logs.list().getFirst()))
                    .containsEntry("auditEventType", "ACCESS_DENIED")
                    .containsEntry("requestPath", AdminCheckController.PATH)
                    .containsEntry("exceptionType", "org.springframework.transaction.CannotCreateTransactionException");
        }
        assertThat(repository.takeSaveCalls()).as("再試行はしない").isEqualTo(1);
    }
}
