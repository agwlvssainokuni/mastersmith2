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
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

/** 管理者のみの API の判定の結合テスト（FR8.1、FR8.2、BR2.1〜BR2.6、NFR1.2、NFR3.1、NFR3.4〜NFR3.6）。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.auth.password.bcrypt-cost=4",
            "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                    + "cherry.mastersmith.auth.testsupport.SqlStatementCounter"
        })
@Import(AdminAccessTestConfig.class)
class AdminAccessIT {

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
    JdbcTemplate jdbc;

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

    @Test
    @DisplayName("the check API answers 401 without login, 403 for a member and 204 for an administrator")
    void checkApiByRole() {
        String adminToken = users.accessToken(users.createAdmin());
        String memberToken = users.accessToken(users.createNonAdmin());

        HttpResponse<String> anonymous = get(AdminCheckController.PATH, null);
        HttpResponse<String> member = get(AdminCheckController.PATH, memberToken);
        HttpResponse<String> admin = get(AdminCheckController.PATH, adminToken);

        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(HttpTestClient.json(anonymous)).containsEntry("code", "AUTHENTICATION_REQUIRED");
        assertThat(member.statusCode()).isEqualTo(403);
        assertThat(HttpTestClient.json(member)).containsEntry("code", "ACCESS_DENIED");
        assertThat(admin.statusCode()).isEqualTo(204);
        assertThat(admin.body()).isEmpty();
    }

    @Test
    @DisplayName("calling the API directly without the screen gives the same answers")
    void directCallsAreJudgedTheSameWay() {
        String memberToken = users.accessToken(users.createNonAdmin());

        assertThat(get(AdminCheckController.PATH, memberToken).statusCode()).isEqualTo(403);
        assertThat(client.send(client.request(AdminCheckController.PATH)
                                .header("Authorization", "Bearer " + memberToken)
                                .header("Accept", "application/json")
                                .GET()
                                .build())
                        .statusCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("a member gets 403 for an admin API that does not exist while an administrator gets 404")
    void missingAdminApiHidesItsExistence() {
        String memberToken = users.accessToken(users.createNonAdmin());
        String adminToken = users.accessToken(users.createAdmin());

        HttpResponse<String> member = get("/api/admin/nothing", memberToken);
        HttpResponse<String> admin = get("/api/admin/nothing", adminToken);

        assertThat(member.statusCode()).isEqualTo(403);
        assertThat(HttpTestClient.json(member)).containsEntry("code", "ACCESS_DENIED");
        assertThat(admin.statusCode()).isEqualTo(404);
        assertThat(HttpTestClient.json(admin)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("removing the administrator flag in the database denies the very next request")
    void theFlagIsReadFromTheDatabaseOnEveryRequest() {
        AdminTestUsers.TestUser admin = users.createAdmin();
        String token = users.accessToken(admin);
        assertThat(get(AdminCheckController.PATH, token).statusCode()).isEqualTo(204);

        jdbc.update("UPDATE users SET admin_flag = FALSE WHERE user_id = ?", admin.userId());

        assertThat(get(AdminCheckController.PATH, token).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("the check API issues only the single user lookup of U2")
    void theCheckApiAddsNoDatabaseQuery() {
        String adminToken = users.accessToken(users.createAdmin());

        SqlStatementCounter.start();
        HttpResponse<String> response = get(AdminCheckController.PATH, adminToken);
        Map<String, List<String>> byThread = SqlStatementCounter.stop();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(byThread).hasSize(1);
        assertThat(byThread.values().iterator().next()).containsExactly("select users");
    }

    @Test
    @DisplayName("the 403 body has the common shape and reveals no internal detail")
    void theDeniedBodyHasTheCommonShape() {
        AdminTestUsers.TestUser member = users.createNonAdmin();
        String memberToken = users.accessToken(member);

        HttpResponse<String> response = get(AdminCheckController.PATH, memberToken);

        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsKeys("type", "code", "title", "status", "traceId");
        assertThat(body.get("type").toString()).endsWith("/api/problems/access-denied");
        assertThat(body).containsEntry("status", 403);
        assertThat(response.body())
                .doesNotContain(member.email())
                .doesNotContain(memberToken)
                .doesNotContain("AccessDeniedException")
                .doesNotContain("cherry.mastersmith");
    }
}
