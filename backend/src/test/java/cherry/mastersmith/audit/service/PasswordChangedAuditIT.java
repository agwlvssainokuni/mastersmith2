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
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.Mode;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.PasswordChangedEvent;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * パスワードの変更の監査（契約 C8 の PASSWORD_CHANGED、BR7.1〜BR7.4、NFR9.4・NFR9.5・NFR9.8）の結合テスト。
 *
 * <p>監査の行は {@code JdbcTemplate} で表を直接読む。書き込みの失敗は既存の失敗する保存の部品で、巻き戻しは確定の直前に例外を投げる
 * テストの受け取りで起こす。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AuthApiTestConfig.class, FailingAuditEventRepositoryConfig.class, PasswordChangedAuditIT.RollbackConfig.class})
class PasswordChangedAuditIT {

    private static final String NEW_PASSWORD = "新しいパスワード-000001";

    private static final String WRONG = "まちがったパスワード-0009";

    private static final String LONG_USER_AGENT = "UA-" + "x".repeat(510);

    /** パスワードの変更の確定を失敗させ、元のトランザクションを取り消す。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackConfig {

        /** 確定の直前に受け取り、指定されていれば例外を投げる。 */
        static class RollbackTrigger {

            private final AtomicBoolean failing = new AtomicBoolean(false);

            @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
            public void onEvent(PasswordChangedEvent event) {
                if (failing.get()) {
                    throw new IllegalStateException("テスト用の、確定を失敗させる例外");
                }
            }

            void failing(boolean value) {
                failing.set(value);
            }
        }

        @Bean
        RollbackTrigger passwordRollbackTrigger() {
            return new RollbackTrigger();
        }
    }

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

    @Autowired
    RollbackConfig.RollbackTrigger trigger;

    private MeApi me;

    private AdminTestUsers users;

    @BeforeEach
    void setUp() {
        me = new MeApi(port);
        users = new AdminTestUsers(userAccountService, port);
        repository.mode(Mode.NONE);
        trigger.failing(false);
    }

    private List<Map<String, Object>> passwordRows(long userId) {
        return jdbc.queryForList(
                "SELECT * FROM audit_events WHERE event_type = 'PASSWORD_CHANGED' AND actor_user_id = ?"
                        + " ORDER BY audit_event_id",
                userId);
    }

    private Map<String, Object> loginRow(String traceId) {
        return jdbc.queryForMap(
                "SELECT * FROM audit_events WHERE event_type = 'LOGIN_SUCCEEDED' AND trace_id = ?", traceId);
    }

    /** User-Agent を指定してログインし、アクセストークンと監査のトレースIDの比べの材料を返す。 */
    private HttpResponse<String> loginWithUserAgent(String email) {
        HttpTestClient client = new HttpTestClient(port);
        String body = JsonMapper.builder()
                .build()
                .writeValueAsString(Map.of("email", email, "password", AdminTestUsers.PASSWORD));
        return client.send(client.request("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("User-Agent", LONG_USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private static String accessToken(HttpResponse<String> login) {
        return (String) HttpTestClient.json(login).get("accessToken");
    }

    @Test
    @DisplayName("a success and a mismatch are recorded once each with every required field")
    void recordsSuccessAndMismatch() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);

        me.changePassword(token, WRONG, NEW_PASSWORD, NEW_PASSWORD, "User-Agent", LONG_USER_AGENT);
        me.changePassword(token, AdminTestUsers.PASSWORD, NEW_PASSWORD, NEW_PASSWORD, "User-Agent", LONG_USER_AGENT);

        List<Map<String, Object>> rows = passwordRows(member.userId());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0))
                .containsEntry("RESULT", "FAILURE")
                .containsEntry("FAILURE_REASON", "CURRENT_PASSWORD_MISMATCH");
        assertThat(rows.get(1)).containsEntry("RESULT", "SUCCESS");
        assertThat(rows.get(1).get("FAILURE_REASON")).isNull();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("ACTOR_USER_ID")).isEqualTo(member.userId());
            assertThat(row.get("TARGET_USER_ID")).isEqualTo(member.userId());
            assertThat(row.get("TARGET_INVITATION_ID")).isNull();
            assertThat(row.get("ENTERED_EMAIL")).isNull();
            assertThat(row.get("REQUEST_PATH")).isNull();
            assertThat(row.get("DSL_HASH")).isNull();
            assertThat(row.get("SOURCE_IP")).isEqualTo("127.0.0.1");
            assertThat((String) row.get("USER_AGENT")).hasSize(512);
            assertThat((String) row.get("TRACE_ID")).matches("[0-9a-f]{32}");
            assertThat(row.get("OCCURRED_AT")).isNotNull();
        });
    }

    @Test
    @DisplayName("the source IP, User-Agent and trace id are taken the same way as the login audit")
    void originIsTakenLikeTheLoginAudit() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        HttpResponse<String> login = loginWithUserAgent(member.email());
        String loginTrace = (String) jdbc.queryForObject(
                "SELECT trace_id FROM audit_events WHERE event_type = 'LOGIN_SUCCEEDED' ORDER BY audit_event_id DESC"
                        + " LIMIT 1",
                String.class);

        me.changePassword(accessToken(login), WRONG, NEW_PASSWORD, NEW_PASSWORD, "User-Agent", LONG_USER_AGENT);

        Map<String, Object> password = passwordRows(member.userId()).getFirst();
        Map<String, Object> loginRow = loginRow(loginTrace);
        assertThat(password.get("SOURCE_IP")).isEqualTo(loginRow.get("SOURCE_IP"));
        assertThat(password.get("USER_AGENT")).isEqualTo(loginRow.get("USER_AGENT"));
        assertThat((String) password.get("TRACE_ID")).matches("[0-9a-f]{32}").isNotEqualTo(loginTrace);
    }

    @Test
    @DisplayName("input errors are never recorded")
    void inputErrorsAreNotRecorded() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);

        me.changePassword(token, "", NEW_PASSWORD, NEW_PASSWORD);
        me.changePassword(token, AdminTestUsers.PASSWORD, "short", "short");
        me.changePassword(token, AdminTestUsers.PASSWORD, NEW_PASSWORD, "other");

        assertThat(passwordRows(member.userId())).isEmpty();
    }

    @Test
    @DisplayName("a failing audit write changes neither the 204 nor the 400 and logs one error each (NFR9.4)")
    void failingWriteKeepsTheResponses() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        repository.mode(Mode.APPEND_FAILURE);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            HttpResponse<String> mismatch = me.changePassword(token, WRONG, NEW_PASSWORD, NEW_PASSWORD);
            HttpResponse<String> success =
                    me.changePassword(token, AdminTestUsers.PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

            assertThat(mismatch.statusCode()).isEqualTo(400);
            assertThat(HttpTestClient.json(mismatch)).containsEntry("code", "PASSWORD_CURRENT_MISMATCH");
            assertThat(success.statusCode()).isEqualTo(204);
            List<ILoggingEvent> errors = logs.list();
            assertThat(errors).hasSize(2).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage()).isEqualTo(AuditEventListener.FAILURE_MESSAGE);
                assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                        .doesNotContain(AdminTestUsers.PASSWORD)
                        .doesNotContain(NEW_PASSWORD)
                        .doesNotContain(member.email());
            });
        }
        repository.mode(Mode.NONE);
        assertThat(passwordRows(member.userId())).isEmpty();
    }

    @Test
    @DisplayName("a rolled-back change records no success and leaves the password unchanged (NFR9.8)")
    void rolledBackChangeIsNotRecorded() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        String before =
                jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, member.userId());
        trigger.failing(true);

        HttpResponse<String> response = me.changePassword(token, AdminTestUsers.PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        trigger.failing(false);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(passwordRows(member.userId())).isEmpty();
        assertThat(jdbc.queryForObject(
                        "SELECT password_hash FROM users WHERE user_id = ?", String.class, member.userId()))
                .isEqualTo(before);
    }
}
