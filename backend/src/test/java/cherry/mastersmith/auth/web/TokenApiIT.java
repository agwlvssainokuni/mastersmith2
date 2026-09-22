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
import cherry.mastersmith.common.testsupport.HttpTestClient;
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

/** トークンの更新の API の結合テスト（FR5.1、FR5.2、BR5.3〜BR5.7、NFR5.3、NFR5.4）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class TokenApiIT {

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
        email = "token-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), false);
    }

    private String loginCookie() {
        return AuthApi.cookieValue(api.login(email, PASSWORD));
    }

    @Test
    @DisplayName("a refresh returns a new token and a new cookie while the used cookie is rejected afterwards")
    void refreshRotates() {
        String cookie = loginCookie();

        HttpResponse<String> refreshed = api.refresh(cookie, api.origin());

        assertThat(refreshed.statusCode()).isEqualTo(200);
        String newCookie = AuthApi.cookieValue(refreshed);
        assertThat(newCookie).isNotEqualTo(cookie);
        assertThat(HttpTestClient.json(refreshed)).containsKeys("accessToken", "expiresAt", "user");

        HttpResponse<String> reused = api.refresh(cookie, api.origin());

        assertThat(reused.statusCode()).isEqualTo(401);
        assertThat(HttpTestClient.json(reused)).containsEntry("code", "REFRESH_FAILED");
        assertThat(AuthApi.setCookie(reused))
                .hasValueSatisfying(
                        line -> assertThat(line).contains("Max-Age=0").contains("Path=/api/auth/session"));
        assertThat(api.refresh(newCookie, api.origin()).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("a refresh without a cookie is rejected with 401 REFRESH_FAILED")
    void withoutCookie() {
        HttpResponse<String> response = api.refresh(null, api.origin());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "REFRESH_FAILED");
        assertThat(AuthApi.setCookie(response))
                .hasValueSatisfying(line -> assertThat(line).contains("Max-Age=0"));
    }

    @Test
    @DisplayName("a refresh token expires exactly 24 hours after it was issued")
    void expiry() {
        String cookie = loginCookie();

        clock.advance(Duration.ofHours(24).minusSeconds(1));
        HttpResponse<String> justBefore = api.refresh(cookie, api.origin());
        String rotated = AuthApi.cookieValue(justBefore);
        clock.advance(Duration.ofHours(24));

        assertThat(justBefore.statusCode()).isEqualTo(200);
        assertThat(api.refresh(rotated, api.origin()).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("refreshing one browser's token leaves another browser's token usable")
    void otherBrowserKeepsWorking() {
        String first = loginCookie();
        String second = loginCookie();

        api.refresh(first, api.origin());

        assertThat(api.refresh(second, api.origin()).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("refresh and logout reject a missing or different origin with 403 ORIGIN_NOT_ALLOWED")
    void originChecked() {
        String cookie = loginCookie();

        HttpResponse<String> noOrigin = api.refresh(cookie, null);
        HttpResponse<String> otherOrigin = api.refresh(cookie, "http://evil.example.com");
        HttpResponse<String> logoutNoOrigin = api.logout(cookie, null);

        assertThat(noOrigin.statusCode()).isEqualTo(403);
        assertThat(HttpTestClient.json(noOrigin)).containsEntry("code", "ORIGIN_NOT_ALLOWED");
        assertThat(otherOrigin.statusCode()).isEqualTo(403);
        assertThat(logoutNoOrigin.statusCode()).isEqualTo(403);
        assertThat(api.refresh(cookie, api.origin()).statusCode()).isEqualTo(200);
    }
}
