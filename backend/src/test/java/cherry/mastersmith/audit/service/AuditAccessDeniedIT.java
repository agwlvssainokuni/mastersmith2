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

import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.access.web.AdminCheckController;
import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.auth.testsupport.AuthTestTokens;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** アクセス拒否の出来事から監査イベントまでの結合テスト（FR9.1、FR9.2、BR1.1〜BR1.3、NFR9.1、`security-design.md` 6章）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AdminAccessTestConfig.class)
class AuditAccessDeniedIT {

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

    private HttpTestClient client;

    private AdminTestUsers users;

    private AuthTestTokens tokens;

    private AuditRows rows;

    @BeforeEach
    void setUp() {
        client = new HttpTestClient(port);
        users = new AdminTestUsers(userAccountService, port);
        tokens = new AuthTestTokens(signingKey);
        rows = new AuditRows(jdbc);
        clock.set(AdminAccessTestConfig.START);
    }

    private HttpResponse<String> call(String path, String accessToken) {
        return accessToken == null ? client.get(path) : client.get(path, "Authorization", "Bearer " + accessToken);
    }

    private String validTokenOf(long userId) {
        return tokens.hs256(
                userId, AdminAccessTestConfig.START, AdminAccessTestConfig.START.plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("a denied member is recorded as ACCESS_DENIED with NOT_ADMIN, the email and the request path")
    void deniedMemberIsRecorded() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String token = users.accessToken(member);
        int before = rows.count();

        assertThat(call(AdminCheckController.PATH, token).statusCode()).isEqualTo(403);

        // 応答が返った時点で行が存在する（応答を書く前に記録している）。
        assertThat(rows.count()).isEqualTo(before + 1);
        AuditRows.AuditRow row = rows.last();
        assertThat(row.eventType()).isEqualTo("ACCESS_DENIED");
        assertThat(row.result()).isEqualTo("FAILURE");
        assertThat(row.failureReason()).isEqualTo("NOT_ADMIN");
        assertThat(row.enteredEmail()).isEqualTo(member.email());
        assertThat(row.occurredAt()).isEqualTo(AdminAccessTestConfig.START);
        assertThat(row.requestPath()).isEqualTo(AdminCheckController.PATH);
        assertThat(row.sourceIp()).isNotBlank();
        assertThat(row.traceId()).isNotBlank();
    }

    @Test
    @DisplayName("a request without a token is recorded as TOKEN_MISSING with no email address")
    void missingTokenIsRecorded() {
        int before = rows.count();

        assertThat(call(AdminCheckController.PATH, null).statusCode()).isEqualTo(401);

        assertThat(rows.count()).isEqualTo(before + 1);
        AuditRows.AuditRow row = rows.last();
        assertThat(row.failureReason()).isEqualTo("TOKEN_MISSING");
        assertThat(row.enteredEmail()).isNull();
        assertThat(row.requestPath()).isEqualTo(AdminCheckController.PATH);
    }

    @Test
    @DisplayName("a malformed, a tampered and an unsigned token are recorded with their own reasons")
    void brokenTokensAreRecorded() {
        AdminTestUsers.TestUser admin = users.createAdmin();

        assertThat(call(AdminCheckController.PATH, "not-a-json-web-token").statusCode())
                .isEqualTo(401);
        assertThat(rows.last().failureReason()).isEqualTo("TOKEN_MALFORMED");

        String otherKey = tokens.hs256WithOtherKey(
                admin.userId(), AdminAccessTestConfig.START, AdminAccessTestConfig.START.plus(Duration.ofMinutes(5)));
        assertThat(call(AdminCheckController.PATH, otherKey).statusCode()).isEqualTo(401);
        assertThat(rows.last().failureReason()).isEqualTo("TOKEN_INVALID");

        String unsigned = AuthTestTokens.algNone(
                admin.userId(), AdminAccessTestConfig.START, AdminAccessTestConfig.START.plus(Duration.ofMinutes(5)));
        assertThat(call(AdminCheckController.PATH, unsigned).statusCode()).isEqualTo(401);
        assertThat(rows.last().failureReason()).isEqualTo("TOKEN_INVALID");

        assertThat(rows.last().enteredEmail()).as("401 ではメールアドレスを記録しない").isNull();
    }

    @Test
    @DisplayName("a valid token of a user that is not in the database is recorded as USER_NOT_FOUND")
    void unknownUserIsRecorded() {
        int before = rows.count();

        assertThat(call(AdminCheckController.PATH, validTokenOf(987_654_321L)).statusCode())
                .isEqualTo(401);

        assertThat(rows.count()).isEqualTo(before + 1);
        assertThat(rows.last().failureReason()).isEqualTo("USER_NOT_FOUND");
        assertThat(rows.last().enteredEmail()).isNull();
    }

    @Test
    @DisplayName("an expired token records nothing because U3 publishes no event for it")
    void expiredTokenIsNotRecorded() {
        String token = users.accessToken(users.createAdmin());
        clock.advance(Duration.ofHours(1));
        int before = rows.count();

        assertThat(call(AdminCheckController.PATH, token).statusCode()).isEqualTo(401);

        assertThat(rows.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("the recorded request path is normalized and carries no query part")
    void requestPathIsNormalizedWithoutItsQuery() {
        int before = rows.count();

        assertThat(call(AdminCheckController.PATH + "?token=" + UUID.randomUUID(), null)
                        .statusCode())
                .isEqualTo(401);

        assertThat(rows.count()).isEqualTo(before + 1);
        assertThat(rows.last().requestPath())
                .isEqualTo(AdminCheckController.PATH)
                .doesNotContain("?");
    }

    @Test
    @DisplayName("a successful admin request records nothing")
    void successfulAdminRequestIsNotRecorded() {
        String adminToken = users.accessToken(users.createAdmin());
        int before = rows.count();

        assertThat(call(AdminCheckController.PATH, adminToken).statusCode()).isEqualTo(204);

        assertThat(rows.count()).isEqualTo(before);
    }
}
