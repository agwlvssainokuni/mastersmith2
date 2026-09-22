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

import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.LoginFailureReason;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.CapturedAuthenticationEvents;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 認証の出来事の結合テスト（BR7.1〜BR7.3、NFR10.1、NFR10.2）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, AuthEventsIT.FailingListenerConfig.class})
class AuthEventsIT {

    private static final String PASSWORD = "正しいパスワード-1234";

    /** 確定の後に必ず例外を起こす受け取り（U4 の記録の失敗を真似る）。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingListenerConfig {

        /** 例外を起こす受け取りを置く。 */
        static class FailingListener {

            /**
             * 出来事を受け取って例外を起こす。
             *
             * @param event 出来事
             */
            @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
            public void onEvent(AuthenticationEvent event) {
                throw new IllegalStateException("監査の記録に失敗しました");
            }
        }

        /**
         * 受け取りを作る。
         *
         * @return 受け取り
         */
        @Bean
        public FailingListener failingListener() {
            return new FailingListener();
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
    CapturedAuthenticationEvents events;

    @Autowired
    MutableClock clock;

    private AuthApi api;

    private String email;

    @BeforeEach
    void setUp() {
        api = new AuthApi(port);
        clock.set(AuthApiTestConfig.START);
        events.clear();
        email = "events-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), false);
        events.clear();
    }

    private List<AuthenticationEvent> captured() {
        return events.ofType(AuthenticationEvent.class);
    }

    @Test
    @DisplayName("a successful login publishes LOGIN_SUCCEEDED with every audit field")
    void loginSucceeded() {
        api.login(email, PASSWORD);

        assertThat(captured()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo(AuthenticationEventType.LOGIN_SUCCEEDED);
            assertThat(event.occurredAt()).isEqualTo(AuthApiTestConfig.START);
            assertThat(event.enteredEmail()).isEqualTo(email);
            assertThat(event.userId()).isNotNull();
            assertThat(event.failureReason()).isNull();
            assertThat(event.sourceIp()).isNotBlank();
            assertThat(event.userAgent()).isNotNull();
            assertThat(event.traceId()).isNotBlank();
        });
    }

    @Test
    @DisplayName("the three failure reasons are published, including one for an unknown email address")
    void loginFailed() {
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";

        api.login(unknown, PASSWORD);
        api.login(email, "まちがい");
        for (int i = 0; i < 4; i++) {
            api.login(email, "まちがい");
        }
        events.clear();
        api.login(email, PASSWORD);

        assertThat(captured())
                .singleElement()
                .satisfies(event -> assertThat(event.failureReason()).isEqualTo(LoginFailureReason.ACCOUNT_LOCKED));
    }

    @Test
    @DisplayName("an unknown email publishes LOGIN_FAILED with USER_NOT_FOUND and no user id")
    void unknownEmail() {
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";

        api.login(unknown, PASSWORD);

        assertThat(captured()).singleElement().satisfies(event -> {
            assertThat(event.failureReason()).isEqualTo(LoginFailureReason.USER_NOT_FOUND);
            assertThat(event.enteredEmail()).isEqualTo(unknown);
            assertThat(event.userId()).isNull();
        });
    }

    @Test
    @DisplayName("logout publishes LOGGED_OUT while an invalid cookie publishes nothing")
    void logout() {
        String cookie = AuthApi.cookieValue(api.login(email, PASSWORD));
        events.clear();

        api.logout(cookie, api.origin());
        assertThat(captured()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo(AuthenticationEventType.LOGGED_OUT);
            assertThat(event.enteredEmail()).isEqualTo(email);
        });

        events.clear();
        api.logout("unknown-token-value", api.origin());
        assertThat(captured()).isEmpty();
    }

    @Test
    @DisplayName("a listener that fails after the commit does not change the login, refresh or logout responses")
    void listenerFailureDoesNotChangeResponses() {
        var login = api.login(email, PASSWORD);
        String cookie = AuthApi.cookieValue(login);

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(api.refresh(cookie, api.origin()).statusCode()).isEqualTo(200);
        assertThat(api.login(email, "まちがい").statusCode()).isEqualTo(401);
        assertThat(api.logout(AuthApi.cookieValue(api.login(email, PASSWORD)), api.origin())
                        .statusCode())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("published events never carry a password, a hash or a token")
    void noSecrets() {
        String cookie = AuthApi.cookieValue(api.login(email, PASSWORD));
        api.logout(cookie, api.origin());

        assertThat(captured())
                .allSatisfy(event -> assertThat(event.toString())
                        .doesNotContain(PASSWORD)
                        .doesNotContain(cookie)
                        .doesNotContain("$2a$"));
    }
}
