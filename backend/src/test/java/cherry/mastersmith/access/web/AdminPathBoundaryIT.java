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

import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** パスの境界と、正規化されていないパスの拒否の結合テスト（BR1.6、BR6.1、NFR3.3、NFR3.6、NFR10.3、NFR10.4）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AdminAccessTestConfig.class)
class AdminPathBoundaryIT {

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

    private HttpTestClient client;

    private AdminTestUsers users;

    @BeforeEach
    void setUp() {
        client = new HttpTestClient(port);
        users = new AdminTestUsers(userAccountService, port);
    }

    private HttpResponse<String> get(String path, String accessToken) {
        return accessToken == null ? client.get(path) : client.get(path, "Authorization", "Bearer " + accessToken);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("the admin root with and without the trailing slash is denied to a member")
    @ValueSource(strings = {"/api/admin", "/api/admin/"})
    void theAdminRootIsAdminOnly(String path) {
        String memberToken = users.accessToken(users.createNonAdmin());

        HttpResponse<String> response = get(path, memberToken);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "ACCESS_DENIED");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("a path that is not normalized is rejected with 400 REQUEST_REJECTED before any decision")
    @ValueSource(strings = {"/api/admin/..;/secret", "/api/admin/./check", "/api//admin/check", "/api/admin;x/check"})
    void craftedPathsAreRejected(String path) {
        HttpResponse<String> response = client.get(path);

        assertThat(response.statusCode()).isEqualTo(400);
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsEntry("code", "REQUEST_REJECTED").containsEntry("status", 400);
        assertThat(body).containsKeys("type", "title", "traceId");
        assertThat(body.get("type").toString()).endsWith("/api/problems/request-rejected");
    }

    @Test
    @DisplayName("the rejection response carries the security headers of U1 and no-store")
    void theRejectionCarriesTheHeadersOfU1() {
        HttpResponse<String> response = client.get("/api/admin/..;/secret");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(value -> assertThat(value).contains("frame-ancestors 'none'"));
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("same-origin");
        assertThat(response.headers().allValues("Cache-Control")).containsExactly("no-store");
    }

    @Test
    @DisplayName("the rejection log carries the same trace id as the response and never the rejected path")
    void theRejectionLogSharesTheTraceIdAndHidesThePath() {
        String craftedPath = "/api/admin/..;/secret";

        HttpResponse<String> response;
        Map<String, Object> pairs;
        String mdcTraceId;
        try (LogEvents events = LogEvents.capture(AccessRequestRejectedHandler.class)) {
            response = client.get(craftedPath);
            assertThat(events.list()).hasSize(1);
            pairs = events.list().getFirst().getKeyValuePairs().stream()
                    .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
            mdcTraceId = events.list().getFirst().getMDCPropertyMap().get("traceId");
            assertThat(events.list().getFirst().getFormattedMessage()).doesNotContain("..;");
        }
        assertThat(mdcTraceId).isNotBlank();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(pairs).containsOnlyKeys("code");
        assertThat(pairs).containsEntry("code", "REQUEST_REJECTED");
        // トレースIDは U1 のログの仕組みが同じ行に付ける。応答の traceId と同じであることを確かめる。
        assertThat(mdcTraceId).isEqualTo(HttpTestClient.json(response).get("traceId"));
    }

    @Test
    @DisplayName("an encoded separator is rejected by the container and never reaches the admin API")
    void encodedSeparatorIsRejected() {
        String memberToken = users.accessToken(users.createNonAdmin());

        HttpResponse<String> anonymous = client.get("/api/admin%2Fcheck");
        HttpResponse<String> member = get("/api/admin%2Fcheck", memberToken);

        assertThat(anonymous.statusCode()).isEqualTo(400);
        assertThat(member.statusCode()).isEqualTo(400);
        assertThat(member.body()).doesNotContain("ACCESS_DENIED").doesNotContain("cherry.mastersmith");
    }

    @Test
    @DisplayName("an upper case path is never treated as the admin area: it is served as a screen URL")
    void upperCasePathIsNotAdminOnly() {
        String adminToken = users.accessToken(users.createAdmin());
        String memberToken = users.accessToken(users.createNonAdmin());

        HttpResponse<String> admin = get("/API/admin/check", adminToken);
        HttpResponse<String> member = get("/API/admin/check", memberToken);
        HttpResponse<String> anonymous = get("/API/admin/check", null);

        // /api/ の外であるため、U1 の画面の配信（公開）に当たる。管理者のみの API には決して届かない（BR1.6）。
        assertThat(admin.statusCode()).isEqualTo(200);
        assertThat(member.statusCode()).isEqualTo(200);
        assertThat(anonymous.statusCode()).isEqualTo(200);
        assertThat(anonymous.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
        assertThat(anonymous.body()).doesNotContain("ACCESS_DENIED");
    }
}
