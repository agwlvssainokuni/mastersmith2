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
package cherry.mastersmith.appearance.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.appearance.testsupport.ConnectionAcquireCounter;
import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.auth.testsupport.AuthTestTokens;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code GET /api/appearance} の公開の範囲・応答・ヘッダー・監査なし・接続を借りないことの結合テスト（BR3.1〜BR3.6、NFR4.1〜
 * NFR4.5・NFR4.8・NFR5.2・NFR9.5・NFR9.6、security-design.md の 3節の表）。
 *
 * <p>本物の {@code SecurityConfig} と差し込み口（auth の 110・access の 210・U8 の 410）で起動した文脈に、実際の HTTP で要求を送る。
 * {@code /api/**} を公開にするテスト用の決まり（{@code mastersmith.test-fixture.public-api}）は有効にしない。見た目の設定は空に
 * して、開発者の環境変数に影響されないようにする。定期の削除（{@code RefreshTokenCleanupJob}）は止め、接続の回数の比べに混ざらない
 * ようにする。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.auth.password.bcrypt-cost=4",
            "mastersmith.auth.refresh-token-cleanup.cron=-",
            "mastersmith.appearance.brand-color=",
            "mastersmith.appearance.font-family="
        })
class AppearanceApiIT {

    private static final String PATH = "/api/appearance";

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
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    DataSource dataSource;

    private HttpTestClient client;

    private AdminTestUsers users;

    @BeforeEach
    void setUp() {
        client = new HttpTestClient(port);
        users = new AdminTestUsers(userAccountService, port);
    }

    private HttpResponse<String> send(String method, String path, String accessToken) {
        HttpRequest.Builder builder = client.request(path).method(method, HttpRequest.BodyPublishers.noBody());
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return client.send(builder.build());
    }

    private String validToken() {
        return users.accessToken(users.createNonAdmin());
    }

    private static void assertAuthenticationRequired(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "AUTHENTICATION_REQUIRED");
    }

    /** 使用中・待ちの接続が 0 になるまで待つ（起動の直後の処理や、前の要求の後始末が終わったことの待ち合わせ）。 */
    private void awaitPoolIdle() {
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> {
                    Gauge active = meterRegistry
                            .find("hikaricp.connections.active")
                            .tag("pool", ConnectionAcquireCounter.POOL)
                            .gauge();
                    Gauge pending = meterRegistry
                            .find("hikaricp.connections.pending")
                            .tag("pool", ConnectionAcquireCounter.POOL)
                            .gauge();
                    return active != null && pending != null && active.value() == 0 && pending.value() == 0;
                });
    }

    @Test
    @DisplayName("GET without a token is 200 with exactly the two items of C7 and the defaults")
    void anonymousGetReturnsTheDefaults() {
        HttpResponse<String> response = client.get(PATH);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(HttpTestClient.json(response))
                .containsOnlyKeys("brandColor", "fontFamily")
                .containsEntry("brandColor", "blue")
                .containsEntry("fontFamily", "sans");
    }

    @Test
    @DisplayName("POST without a token is 401 AUTHENTICATION_REQUIRED")
    void anonymousPostIsUnauthorized() {
        assertAuthenticationRequired(send("POST", PATH, null));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("other methods with a valid token are 405 METHOD_NOT_ALLOWED with GET in Allow")
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void otherMethodsWithValidTokenAreNotAllowed(String method) {
        HttpResponse<String> response = send(method, PATH, validToken());

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "METHOD_NOT_ALLOWED");
        assertThat(response.headers().allValues("Allow"))
                .anySatisfy(allow -> assertThat(allow).containsIgnoringCase("GET"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("other methods without a token stay behind login")
    @ValueSource(strings = {"PUT", "PATCH", "DELETE", "OPTIONS"})
    void otherMethodsWithoutTokenAreUnauthorized(String method) {
        assertAuthenticationRequired(send(method, PATH, null));
    }

    @Test
    @DisplayName("HEAD without a token is 401 and HEAD with a valid token answers like GET without a body")
    void headIsNotPublic() {
        HttpResponse<String> anonymous = send("HEAD", PATH, null);
        HttpResponse<String> authenticated = send("HEAD", PATH, validToken());

        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(authenticated.statusCode()).isEqualTo(200);
        assertThat(authenticated.body()).isEmpty();
    }

    @Test
    @DisplayName("GET with an expired or a tampered token is 401 while a valid token gets 200")
    void tokensAreJudgedByTheExistingAuthentication() {
        AdminTestUsers.TestUser user = users.createNonAdmin();
        String valid = users.accessToken(user);
        Instant past = Instant.now().minus(Duration.ofHours(2));
        String expired = new AuthTestTokens(signingKey).hs256(user.userId(), past, past.plus(Duration.ofMinutes(5)));

        assertAuthenticationRequired(send("GET", PATH, expired));
        assertAuthenticationRequired(send("GET", PATH, AuthTestTokens.tamper(valid)));
        HttpResponse<String> ok = send("GET", PATH, valid);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(ok)).containsOnlyKeys("brandColor", "fontFamily");
    }

    @Test
    @DisplayName("the path with a trailing slash is not public")
    void trailingSlashIsNotPublic() {
        assertAuthenticationRequired(client.get(PATH + "/"));
        assertAuthenticationRequired(client.get(PATH + "/extra"));
    }

    @Test
    @DisplayName("the anonymous answer carries no-store and the common security headers")
    void anonymousAnswerCarriesTheCommonHeaders() {
        HttpResponse<String> response = client.get(PATH);

        assertThat(response.headers().allValues("Cache-Control")).containsExactly("no-store");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(csp -> assertThat(csp).contains("font-src 'self'"));
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("same-origin");
    }

    @Test
    @DisplayName("reading the appearance leaves no audit event")
    void noAuditEvent() {
        String token = validToken();
        AuditRows rows = new AuditRows(jdbc);
        int before = rows.count();

        assertThat(client.get(PATH).statusCode()).isEqualTo(200);
        assertThat(send("GET", PATH, token).statusCode()).isEqualTo(200);

        assertThat(rows.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("GET without a token borrows no connection of the internal database")
    void anonymousGetBorrowsNoConnection() throws SQLException {
        ConnectionAcquireCounter counter = new ConnectionAcquireCounter(meterRegistry, dataSource);
        awaitPoolIdle();
        counter.assertCountingWorks();
        awaitPoolIdle();

        long before = counter.count();
        for (int i = 0; i < 3; i++) {
            assertThat(client.get(PATH).statusCode()).isEqualTo(200);
        }
        awaitPoolIdle();

        assertThat(counter.count()).as("トークンなしの GET で接続を借りない").isEqualTo(before);
    }

    @Test
    @DisplayName("the health check stays as before with only its status")
    void healthStaysUnchanged() {
        HttpResponse<String> health = client.get("/actuator/health");

        assertThat(health.statusCode()).isEqualTo(200);
        Map<String, Object> body = HttpTestClient.json(health);
        assertThat(body).containsOnlyKeys("status").containsEntry("status", "UP");
    }
}
