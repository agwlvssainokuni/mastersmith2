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
package cherry.mastersmith.access.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AccessEventType;
import cherry.mastersmith.access.domain.AccessResult;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.access.testsupport.CapturedAccessDeniedEvents;
import cherry.mastersmith.auth.testsupport.AuthTestTokens;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** アクセス拒否の出来事の結合テスト（FR9.1、BR3.1〜BR3.6、NFR10.1、NFR10.2）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AdminAccessTestConfig.class)
class AccessDeniedEventsIT {

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
    CapturedAccessDeniedEvents captured;

    private HttpTestClient client;

    private AdminTestUsers users;

    private AuthTestTokens tokens;

    @BeforeEach
    void setUp() {
        client = new HttpTestClient(port);
        users = new AdminTestUsers(userAccountService, port);
        tokens = new AuthTestTokens(signingKey);
        clock.set(AdminAccessTestConfig.START);
        captured.clear();
    }

    private HttpResponse<String> call(String path, String accessToken) {
        return accessToken == null ? client.get(path) : client.get(path, "Authorization", "Bearer " + accessToken);
    }

    private List<AdminAccessDeniedEvent> events() {
        return captured.ofType(AdminAccessDeniedEvent.class);
    }

    @Test
    @DisplayName("a denied member produces one NOT_ADMIN event with every field")
    void deniedMemberProducesAnEvent() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        captured.clear();

        assertThat(call(AdminCheckController.PATH, token).statusCode()).isEqualTo(403);

        assertThat(events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo(AccessEventType.ACCESS_DENIED);
            assertThat(event.result()).isEqualTo(AccessResult.FAILURE);
            assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.NOT_ADMIN);
            assertThat(event.enteredEmail()).isEqualTo(member.email());
            assertThat(event.occurredAt()).isEqualTo(AdminAccessTestConfig.START);
            assertThat(event.requestPath()).isEqualTo(AdminCheckController.PATH);
            assertThat(event.sourceIp()).isNotBlank();
            assertThat(event.traceId()).isNotBlank();
        });
    }

    @Test
    @DisplayName("a request without a token produces a TOKEN_MISSING event")
    void missingTokenProducesAnEvent() {
        assertThat(call(AdminCheckController.PATH, null).statusCode()).isEqualTo(401);

        assertThat(events()).singleElement().satisfies(event -> {
            assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.TOKEN_MISSING);
            assertThat(event.enteredEmail()).isNull();
            assertThat(event.requestPath()).isEqualTo(AdminCheckController.PATH);
        });
    }

    @Test
    @DisplayName("a malformed token produces a TOKEN_MALFORMED event")
    void malformedTokenProducesAnEvent() {
        assertThat(call(AdminCheckController.PATH, "not-a-json-web-token").statusCode())
                .isEqualTo(401);

        assertThat(events())
                .singleElement()
                .satisfies(event -> assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.TOKEN_MALFORMED));
    }

    @Test
    @DisplayName("a token signed with another key produces a TOKEN_INVALID event")
    void tamperedTokenProducesAnEvent() {
        AdminTestUsers.TestUser admin = users.createAdmin();
        captured.clear();
        String tampered = tokens.hs256WithOtherKey(
                admin.userId(), AdminAccessTestConfig.START, AdminAccessTestConfig.START.plus(Duration.ofMinutes(5)));

        assertThat(call(AdminCheckController.PATH, tampered).statusCode()).isEqualTo(401);

        assertThat(events())
                .singleElement()
                .satisfies(event -> assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.TOKEN_INVALID));
    }

    @Test
    @DisplayName("a token whose algorithm is none produces a TOKEN_INVALID event")
    void algNoneProducesAnEvent() {
        AdminTestUsers.TestUser admin = users.createAdmin();
        captured.clear();
        String unsigned = AuthTestTokens.algNone(
                admin.userId(), AdminAccessTestConfig.START, AdminAccessTestConfig.START.plus(Duration.ofMinutes(5)));

        assertThat(call(AdminCheckController.PATH, unsigned).statusCode()).isEqualTo(401);

        assertThat(events())
                .singleElement()
                .satisfies(event -> assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.TOKEN_INVALID));
    }

    @Test
    @DisplayName("a valid token of a user that is not in the database produces a USER_NOT_FOUND event")
    void unknownUserProducesAnEvent() {
        String token = tokens.hs256(
                987_654_321L, AdminAccessTestConfig.START, AdminAccessTestConfig.START.plus(Duration.ofMinutes(5)));

        assertThat(call(AdminCheckController.PATH, token).statusCode()).isEqualTo(401);

        assertThat(events())
                .singleElement()
                .satisfies(event -> assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("an expired token produces no event because it happens in normal use")
    void expiredTokenProducesNoEvent() {
        String token = users.accessToken(users.createAdmin());
        clock.advance(Duration.ofHours(1));
        captured.clear();

        assertThat(call(AdminCheckController.PATH, token).statusCode()).isEqualTo(401);

        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("a 401 outside the admin-only area produces no event")
    void otherAreaProducesNoEvent() {
        assertThat(call("/api/no-such-api", null).statusCode()).isEqualTo(401);
        assertThat(call("/api/test-fixture/number", "not-a-json-web-token").statusCode())
                .isEqualTo(401);

        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("a failing listener changes neither the 401 nor the 403 response")
    void aFailingListenerDoesNotChangeTheResponse() {
        String memberToken = users.accessToken(users.createNonAdmin());
        HttpResponse<String> deniedBefore = call(AdminCheckController.PATH, memberToken);
        HttpResponse<String> unauthenticatedBefore = call(AdminCheckController.PATH, null);
        captured.failOnNextEvents();

        HttpResponse<String> deniedAfter = call(AdminCheckController.PATH, memberToken);
        HttpResponse<String> unauthenticatedAfter = call(AdminCheckController.PATH, null);

        assertThat(deniedAfter.statusCode())
                .isEqualTo(deniedBefore.statusCode())
                .isEqualTo(403);
        assertThat(HttpTestClient.json(deniedAfter)).containsEntry("code", "ACCESS_DENIED");
        assertThat(unauthenticatedAfter.statusCode())
                .isEqualTo(unauthenticatedBefore.statusCode())
                .isEqualTo(401);
        assertThat(HttpTestClient.json(unauthenticatedAfter)).containsEntry("code", "AUTHENTICATION_REQUIRED");
    }
}
