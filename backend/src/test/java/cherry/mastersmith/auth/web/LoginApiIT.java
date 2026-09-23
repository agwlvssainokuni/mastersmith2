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
package cherry.mastersmith.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.CountingPasswordEncoder;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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

/** ログインの API の結合テスト（FR4.1、FR4.5、FR7.1〜FR7.5、BR2.2〜BR2.8、BR3.1〜BR3.6、NFR4.1、NFR4.2）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.test-fixture.auth-protected=true",
            "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                    + "cherry.mastersmith.auth.testsupport.SqlStatementCounter"
        })
@Import(AuthApiTestConfig.class)
class LoginApiIT {

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
    CountingPasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    private AuthApi api;

    private String email;

    @BeforeEach
    void setUp() {
        api = new AuthApi(port);
        clock.set(AuthApiTestConfig.START);
        email = "login-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), true);
        encoder.takeMatchCount();
    }

    private int failures() {
        return jdbc.queryForObject(
                "SELECT s.consecutive_failures FROM login_attempt_states s JOIN users u ON u.user_id = s.subject_id"
                        + " WHERE u.email = ?",
                Integer.class,
                email);
    }

    private Map<String, Object> comparable(HttpResponse<String> response) {
        Map<String, Object> body = new HashMap<>(HttpTestClient.json(response));
        body.remove("traceId");
        body.remove("instance");
        body.put("statusCode", response.statusCode());
        return body;
    }

    @Test
    @DisplayName(
            "a successful login returns the token, the user and the refresh cookie and the token opens a protected API")
    void success() {
        HttpResponse<String> response = api.login(email, PASSWORD);

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsOnlyKeys("accessToken", "expiresAt", "user");
        assertThat(body).extractingByKey("user").isEqualTo(Map.of("email", email, "admin", true));
        assertThat(body.get("expiresAt").toString()).startsWith("2026-09-22T00:05:00");
        assertThat(response.body()).doesNotContain(PASSWORD);
        assertThat(AuthApi.setCookie(response))
                .hasValueSatisfying(cookie -> assertThat(cookie)
                        .contains("Path=/api/auth/session")
                        .contains("HttpOnly")
                        .contains("Secure")
                        .contains("SameSite=Strict")
                        .contains("Max-Age=86400"));
        assertThat(api.me(AuthApi.accessToken(response)).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("an empty email or password is rejected with 400 VALIDATION_FAILED without echoing the password")
    void validation() {
        HttpResponse<String> emptyEmail = api.login("", PASSWORD);
        HttpResponse<String> emptyPassword = api.login(email, "");

        assertThat(emptyEmail.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(emptyEmail)).containsEntry("code", "VALIDATION_FAILED");
        assertThat(emptyPassword.statusCode()).isEqualTo(400);
        assertThat(emptyPassword.body()).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("the four failure paths return the same response and use the same statements and password checks")
    void failuresAreIndistinguishable() {
        List<Map<String, Object>> bodies = new ArrayList<>();
        List<List<String>> statements = new ArrayList<>();
        List<Integer> matches = new ArrayList<>();
        String lockedEmail = "locked-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(lockedEmail, new Password(PASSWORD), false);
        for (int i = 0; i < 5; i++) {
            api.login(lockedEmail, "まちがい");
        }
        encoder.takeMatchCount();

        List<Runnable> attempts = List.of(
                () -> record(bodies, statements, matches, "nobody-" + UUID.randomUUID() + "@example.com", PASSWORD),
                () -> record(bodies, statements, matches, email, "まちがったパスワード"),
                () -> record(bodies, statements, matches, lockedEmail, PASSWORD),
                () -> record(bodies, statements, matches, email, "あ".repeat(24) + "a"));
        attempts.forEach(Runnable::run);

        assertThat(bodies).allSatisfy(body -> assertThat(body).isEqualTo(bodies.getFirst()));
        assertThat(bodies.getFirst())
                .containsEntry("code", "AUTHENTICATION_FAILED")
                .containsEntry("statusCode", 401);
        assertThat(statements).allSatisfy(kinds -> assertThat(kinds).isEqualTo(statements.getFirst()));
        // 4つの失敗の経路で並びが同じであること（利用者の存在を推測できないこと）を確かめる。
        // 最後の insert audit_events は、確定の後に同じスレッドで行う U4 の監査の追記である。
        assertThat(statements.getFirst())
                .containsExactly(
                        "select users",
                        "select login_attempt_states for update",
                        "update login_attempt_states",
                        "insert audit_events");
        assertThat(matches).containsExactly(1, 1, 1, 1);
    }

    private void record(
            List<Map<String, Object>> bodies,
            List<List<String>> statements,
            List<Integer> matches,
            String loginEmail,
            String password) {
        SqlStatementCounter.start();
        HttpResponse<String> response = api.login(loginEmail, password);
        Map<String, List<String>> byThread = SqlStatementCounter.stop();
        bodies.add(comparable(response));
        assertThat(byThread).hasSize(1);
        statements.add(byThread.values().iterator().next());
        matches.add(encoder.takeMatchCount());
    }

    @Test
    @DisplayName("four failures do not lock, the fifth locks, and a correct password is then rejected")
    void lockThreshold() {
        for (int i = 0; i < 4; i++) {
            assertThat(api.login(email, "まちがい").statusCode()).isEqualTo(401);
        }
        assertThat(failures()).isEqualTo(4);
        assertThat(api.login(email, PASSWORD).statusCode()).isEqualTo(200);
        assertThat(failures()).isZero();

        for (int i = 0; i < 5; i++) {
            api.login(email, "まちがい");
        }

        assertThat(failures()).isEqualTo(5);
        assertThat(api.login(email, PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("after the lock duration a correct password succeeds again and resets the failures")
    void unlockAfterDuration() {
        for (int i = 0; i < 5; i++) {
            api.login(email, "まちがい");
        }

        clock.advance(Duration.ofMinutes(30));

        assertThat(api.login(email, PASSWORD).statusCode()).isEqualTo(200);
        assertThat(failures()).isZero();
    }
}
