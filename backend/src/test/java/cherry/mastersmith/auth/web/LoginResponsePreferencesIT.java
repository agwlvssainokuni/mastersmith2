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
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpResponse;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ログインと更新の応答の広げ（契約 C3、BR6.1、NFR6.5、{@code performance-design.md} 4節）の結合テスト。
 *
 * <p>4つの値は既存の利用者の読み取りの値から写すため、内部DB への問い合わせは増えない。ログインと更新の処理が発行する SQL の種類と数を
 * 数え、利用者の表の読み取りが1回だけで、ほかの表の問い合わせの形も変わらないことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                    + "cherry.mastersmith.auth.testsupport.SqlStatementCounter"
        })
@Import(AuthApiTestConfig.class)
class LoginResponsePreferencesIT {

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

    private AuthApi auth;

    private String email;

    @BeforeEach
    void setUp() {
        auth = new AuthApi(port);
        email = "response-" + UUID.randomUUID() + "@example.com";
        TestUserAccounts.create(userAccountService, email, PASSWORD, false);
        auth.login(email, PASSWORD);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> user(HttpResponse<String> response) {
        return (Map<String, Object>) HttpTestClient.json(response).get("user");
    }

    @Test
    @DisplayName("the login and refresh responses carry email, admin and the four values with theme system kept")
    void responsesCarryTheFourValues() {
        HttpResponse<String> login = auth.login(email, PASSWORD);
        HttpResponse<String> refreshed = auth.refresh(AuthApi.cookieValue(login), auth.origin());

        Map<String, Object> expected = Map.of(
                "email",
                email,
                "admin",
                false,
                "displayName",
                TestUserAccounts.DISPLAY_NAME,
                "language",
                "ja",
                "theme",
                "system",
                "fontSize",
                "md");
        assertThat(user(login)).isEqualTo(expected);
        assertThat(user(refreshed)).isEqualTo(expected);
    }

    @Test
    @DisplayName("after saving, the refresh response returns the new values")
    void refreshReturnsSavedValues() {
        HttpResponse<String> login = auth.login(email, PASSWORD);
        new MeApi(port).putPreferences(AuthApi.accessToken(login), "保存した 名前", "en", "dark", "lg");

        HttpResponse<String> refreshed = auth.refresh(AuthApi.cookieValue(login), auth.origin());

        assertThat(user(refreshed))
                .containsEntry("displayName", "保存した 名前")
                .containsEntry("language", "en")
                .containsEntry("theme", "dark")
                .containsEntry("fontSize", "lg");
    }

    @Test
    @DisplayName("login and refresh read the users table once each and issue the same kinds and number of statements")
    void statementsAreNotIncreased() {
        SqlStatementCounter.start();
        HttpResponse<String> login = auth.login(email, PASSWORD);
        Map<String, List<String>> loginStatements = SqlStatementCounter.stop();
        SqlStatementCounter.start();
        HttpResponse<String> refreshed = auth.refresh(AuthApi.cookieValue(login), auth.origin());
        Map<String, List<String>> refreshStatements = SqlStatementCounter.stop();

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(loginStatements).hasSize(1);
        assertThat(refreshStatements).hasSize(1);
        assertThat(loginStatements.values().iterator().next())
                .containsExactly(
                        "select users",
                        "select login_attempt_states for update",
                        "update login_attempt_states",
                        "insert refresh_tokens",
                        "insert audit_events");
        assertThat(refreshStatements.values().iterator().next())
                .containsExactly(
                        "select refresh_tokens", "update refresh_tokens", "select users", "insert refresh_tokens");
    }
}
