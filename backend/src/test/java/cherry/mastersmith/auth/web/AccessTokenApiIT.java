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
import cherry.mastersmith.auth.testsupport.AuthTestTokens;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** アクセストークンの検証の結合テスト（FR4.2〜FR4.5、BR4.2〜BR4.5、NFR3.2、NFR10.5）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.auth.password.bcrypt-cost=4",
            "mastersmith.test-fixture.auth-protected=true",
            "logging.level.cherry.mastersmith.auth.web.TokenAuthenticationEntryPoint=DEBUG"
        })
@Import(AuthApiTestConfig.class)
class AccessTokenApiIT {

    private static final String PASSWORD = "正しいパスワード-1234";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Value("${mastersmith.auth.signing-key}")
    String signingKey;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    private AuthApi api;

    private String email;

    private long userId;

    @BeforeEach
    void setUp() {
        api = new AuthApi(port);
        clock.set(AuthApiTestConfig.START);
        email = "access-" + UUID.randomUUID() + "@example.com";
        userId = userAccountService
                .createUser(email, new Password(PASSWORD), false)
                .userId();
    }

    private String token() {
        return AuthApi.accessToken(api.login(email, PASSWORD));
    }

    /** 保護された窓口を呼び、入口の処理が出した区分（DEBUG）を返す。 */
    private String reasonOf(String accessToken, int expectedStatus) {
        try (LogEvents events = LogEvents.capture(TokenAuthenticationEntryPoint.class)) {
            HttpResponse<String> response = api.me(accessToken);
            assertThat(response.statusCode()).isEqualTo(expectedStatus);
            assertThat(HttpTestClient.json(response)).containsEntry("code", "AUTHENTICATION_REQUIRED");
            assertThat(response.body()).doesNotContain(accessToken == null ? "Bearer" : accessToken);
            List<String> reasons = events.list().stream()
                    .map(event -> event.getKeyValuePairs().toString())
                    .toList();
            assertThat(reasons).hasSize(1);
            return reasons.getFirst();
        }
    }

    @Test
    @DisplayName("a valid token opens the protected API and carries the user from the database")
    void validToken() {
        HttpResponse<String> response = api.me(token());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(response))
                .containsEntry("email", email)
                .containsEntry("admin", false)
                .containsEntry("userId", (int) userId);
    }

    @Test
    @DisplayName("a request without a token is 401 with the reason TOKEN_MISSING")
    void missingToken() {
        assertThat(reasonOf(null, 401)).contains("TOKEN_MISSING");
    }

    @Test
    @DisplayName("broken, tampered, alg none and other algorithms are 401 with their reasons")
    void invalidTokens() {
        AuthTestTokens tokens = new AuthTestTokens(signingKey);
        Instant exp = AuthApiTestConfig.START.plus(Duration.ofMinutes(5));

        assertThat(reasonOf("not-a-jwt", 401)).contains("TOKEN_MALFORMED");
        assertThat(reasonOf(tokens.tamperedSignature(userId, AuthApiTestConfig.START, exp), 401))
                .contains("TOKEN_INVALID");
        assertThat(reasonOf(AuthTestTokens.algNone(userId, AuthApiTestConfig.START, exp), 401))
                .contains("TOKEN_INVALID");
        assertThat(reasonOf(AuthTestTokens.hs512(userId, AuthApiTestConfig.START, exp), 401))
                .contains("TOKEN_INVALID");
    }

    @Test
    @DisplayName("a token is accepted at 4 minutes 59 seconds and expired at exactly 5 minutes")
    void expiryBoundary() {
        String accessToken = token();

        clock.advance(Duration.ofSeconds(299));
        assertThat(api.me(accessToken).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofSeconds(1));

        assertThat(reasonOf(accessToken, 401)).contains("TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("a token of a user that no longer exists is 401 with the reason USER_NOT_FOUND")
    void userGone() {
        String accessToken = token();
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM login_attempt_states WHERE subject_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);

        assertThat(reasonOf(accessToken, 401)).contains("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("the admin flag comes from the database on every request")
    void adminFlagFromDatabase() {
        String accessToken = token();

        jdbc.update("UPDATE users SET admin_flag = TRUE WHERE user_id = ?", userId);

        Map<String, Object> body = HttpTestClient.json(api.me(accessToken));
        assertThat(body).containsEntry("admin", true);
    }
}
