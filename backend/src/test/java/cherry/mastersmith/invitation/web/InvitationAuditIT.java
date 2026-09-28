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
package cherry.mastersmith.invitation.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * 招待と登録の監査の結合テスト（BR8.1〜BR8.4・BR8.6、NFR9.4・NFR9.5、C8、{@code security-design.md} 2節、team.md の監査ログの必須のテスト）。
 * 5つの出来事の必須の項目、実際のアクセストークンの管理者が actor になること、送り手の情報がログインの監査と同じ取り方であること、
 * 記録しない場合を確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class InvitationAuditIT {

    private static final String LONG_USER_AGENT = "UA-" + "x".repeat(510);

    private static final SmtpTestServer RECEIVER = SmtpTestServer.builder().start();

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        InvitationTestProperties.register(registry, tempDir, RECEIVER.port(), "http://localhost:8080");
    }

    @AfterAll
    static void stop() {
        RECEIVER.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    private InvitationApi api;

    private AdminTestUsers.TestUser admin;

    private String adminToken;

    @BeforeEach
    void setUp() {
        clock.set(AuthApiTestConfig.START);
        api = new InvitationApi(port);
        AdminTestUsers users = new AdminTestUsers(userAccountService, port);
        admin = users.createAdmin();
        adminToken = loginWithUserAgent(admin.email());
    }

    /** User-Agent を付けてログインし、アクセストークンを返す（ログインの監査の送り手の情報と比べるため）。 */
    private String loginWithUserAgent(String email) {
        HttpTestClient client = new HttpTestClient(port);
        String body = JsonMapper.builder()
                .build()
                .writeValueAsString(Map.of("email", email, "password", AdminTestUsers.PASSWORD));
        HttpResponse<String> login = client.send(client.request("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("User-Agent", LONG_USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        return (String) HttpTestClient.json(login).get("accessToken");
    }

    private long invite(String email) {
        HttpResponse<String> response = api.invite(adminToken, email, "ja", "User-Agent", LONG_USER_AGENT);
        assertThat(response.statusCode()).isEqualTo(201);
        return ((Number) HttpTestClient.json(response).get("invitationId")).longValue();
    }

    private List<Map<String, Object>> rows(long invitationId) {
        return jdbc.queryForList(
                "SELECT * FROM audit_events WHERE target_invitation_id = ? ORDER BY audit_event_id", invitationId);
    }

    private int count() {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return value == null ? 0 : value;
    }

    @Test
    @DisplayName("invite, resend and cancel record the admin of the access token, the invitation and SUCCESS")
    void adminEvents() {
        long id = invite("audit-" + UUID.randomUUID() + "@example.com");
        assertThat(api.resend(adminToken, id, "User-Agent", LONG_USER_AGENT).statusCode())
                .isEqualTo(200);
        assertThat(api.cancel(adminToken, id, "User-Agent", LONG_USER_AGENT).statusCode())
                .isEqualTo(204);

        List<Map<String, Object>> rows = rows(id);

        assertThat(rows)
                .extracting(row -> row.get("EVENT_TYPE"))
                .containsExactly("INVITATION_ISSUED", "INVITATION_RESENT", "INVITATION_CANCELLED");
        Map<String, Object> login =
                jdbc.queryForMap("SELECT source_ip, user_agent FROM audit_events WHERE event_type = 'LOGIN_SUCCEEDED'"
                        + " ORDER BY audit_event_id DESC FETCH FIRST 1 ROWS ONLY");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row)
                    .containsEntry("RESULT", "SUCCESS")
                    .containsEntry("ACTOR_USER_ID", admin.userId())
                    .containsEntry("TARGET_USER_ID", null)
                    .containsEntry("FAILURE_REASON", null)
                    .containsEntry("ENTERED_EMAIL", null)
                    .containsEntry("SOURCE_IP", login.get("SOURCE_IP"))
                    .containsEntry("USER_AGENT", login.get("USER_AGENT"));
            assertThat((String) row.get("USER_AGENT")).hasSize(512);
            assertThat((String) row.get("TRACE_ID")).matches("[0-9a-f]{32}");
            assertThat(row.get("OCCURRED_AT")).isNotNull();
        });
    }

    @Test
    @DisplayName("a completion records no actor, the invitation and the user; a refused one records the reason")
    void registrationEvents() {
        String email = "audit-" + UUID.randomUUID() + "@example.com";
        long id = invite(email);
        String token = ReceivedMails.lastToken(RECEIVER);

        assertThat(api.complete(token, "山田 花子", "User-Agent", LONG_USER_AGENT).statusCode())
                .isEqualTo(204);
        assertThat(api.complete(token, "山田 花子").statusCode()).isEqualTo(404);
        assertThat(api.complete("A".repeat(43), "山田 花子").statusCode()).isEqualTo(404);

        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
        List<Map<String, Object>> rows = rows(id);
        assertThat(rows)
                .extracting(row -> row.get("EVENT_TYPE") + ":" + row.get("RESULT") + ":" + row.get("FAILURE_REASON"))
                .containsExactly(
                        "INVITATION_ISSUED:SUCCESS:null",
                        "REGISTRATION_COMPLETED:SUCCESS:null",
                        "REGISTRATION_FAILED:FAILURE:INVITATION_ALREADY_USED");
        assertThat(rows.get(1))
                .containsEntry("ACTOR_USER_ID", null)
                .containsEntry("TARGET_USER_ID", userId)
                .containsEntry("ENTERED_EMAIL", null);
        assertThat((String) rows.get(1).get("USER_AGENT")).hasSize(512);
        assertThat(rows.get(2)).containsEntry("ACTOR_USER_ID", null).containsEntry("TARGET_USER_ID", null);
        Map<String, Object> notFound = jdbc.queryForMap("SELECT * FROM audit_events WHERE event_type ="
                + " 'REGISTRATION_FAILED' ORDER BY audit_event_id DESC FETCH FIRST 1 ROWS ONLY");
        assertThat(notFound)
                .containsEntry("FAILURE_REASON", "INVITATION_NOT_FOUND")
                .containsEntry("TARGET_INVITATION_ID", null);
    }

    @Test
    @DisplayName("refusals, send failures are not audited; verify, input errors and replacement are not audited")
    void notRecorded() {
        String email = "audit-" + UUID.randomUUID() + "@example.com";
        long id = invite(email);
        String token = ReceivedMails.lastToken(RECEIVER);
        int before = count();

        api.invite(adminToken, "bad", "ja");
        api.invite(adminToken, email, "ja");
        api.resend(adminToken, 999_999);
        api.cancel(adminToken, 999_999);
        api.list(adminToken, "?page=0");
        api.verify(token);
        api.verify("A".repeat(43));
        api.complete(token, " ");

        assertThat(count()).isEqualTo(before);
        clock.advance(java.time.Duration.ofHours(25));
        adminToken = loginWithUserAgent(admin.email());
        before = count();
        long replacing = invite(email);
        assertThat(count()).as("置き換えは新しい招待の1件だけ（ログインの監査を除く）").isEqualTo(before + 1);
        assertThat(rows(id)).extracting(row -> row.get("EVENT_TYPE")).containsExactly("INVITATION_ISSUED");
        assertThat(rows(replacing)).extracting(row -> row.get("EVENT_TYPE")).containsExactly("INVITATION_ISSUED");
    }
}
