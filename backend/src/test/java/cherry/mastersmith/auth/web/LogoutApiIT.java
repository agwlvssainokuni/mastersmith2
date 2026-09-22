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
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ログアウトの API の結合テスト（FR6.1、FR6.2、BR6.1、BR6.2、BR4.6）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mastersmith.auth.password.bcrypt-cost=4", "mastersmith.test-fixture.auth-protected=true"})
@Import(AuthApiTestConfig.class)
class LogoutApiIT {

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

    private AuthApi api;

    private String email;

    @BeforeEach
    void setUp() {
        api = new AuthApi(port);
        clock.set(AuthApiTestConfig.START);
        email = "logout-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), false);
    }

    @Test
    @DisplayName("logout answers 204, clears the cookie and makes the refresh token unusable")
    void logsOut() {
        HttpResponse<String> login = api.login(email, PASSWORD);
        String cookie = AuthApi.cookieValue(login);

        HttpResponse<String> response = api.logout(cookie, api.origin());

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(AuthApi.setCookie(response))
                .hasValueSatisfying(
                        line -> assertThat(line).contains("Max-Age=0").contains("Path=/api/auth/session"));
        assertThat(api.refresh(cookie, api.origin()).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("after logout the issued access token still works until it expires (decided behaviour, BR4.6)")
    void accessTokenStaysValid() {
        HttpResponse<String> login = api.login(email, PASSWORD);
        String accessToken = AuthApi.accessToken(login);

        api.logout(AuthApi.cookieValue(login), api.origin());

        assertThat(api.me(accessToken).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(5));
        assertThat(api.me(accessToken).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("logout without a cookie or with an unknown one still answers 204")
    void withoutCookie() {
        assertThat(api.logout(null, api.origin()).statusCode()).isEqualTo(204);
        assertThat(api.logout("unknown-token-value", api.origin()).statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("logging out one browser leaves another browser's refresh token valid")
    void otherBrowserKeepsWorking() {
        String first = AuthApi.cookieValue(api.login(email, PASSWORD));
        String second = AuthApi.cookieValue(api.login(email, PASSWORD));

        api.logout(first, api.origin());

        assertThat(api.refresh(second, api.origin()).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("a logged out refresh token cannot be used twice")
    void repeatedLogout() {
        String cookie = AuthApi.cookieValue(api.login(email, PASSWORD));

        assertThat(api.logout(cookie, api.origin()).statusCode()).isEqualTo(204);
        assertThat(api.logout(cookie, api.origin()).statusCode()).isEqualTo(204);
        assertThat(api.refresh(cookie, api.origin()).statusCode()).isEqualTo(401);
    }
}
