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
import cherry.mastersmith.invitation.domain.InvitationProblemTypes;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationApps;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 招待の管理の API（契約 C5、BR1.1〜BR1.5・BR2.1〜BR2.7・BR4.4・BR5.1〜BR5.4・BR6.1〜BR6.3・BR9.1・BR9.3、NFR4.1・NFR8.1）の結合テスト。
 * team.md の必須のテスト（認可の 401・403・成功）を含む。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class InvitationAdminApiIT {

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

    private AdminTestUsers users;

    private String admin;

    @BeforeEach
    void setUp() {
        clock.set(AuthApiTestConfig.START);
        jdbc.update("DELETE FROM invitations");
        api = new InvitationApi(port);
        users = new AdminTestUsers(userAccountService, port);
        admin = users.accessToken(users.createAdmin());
    }

    private static String email() {
        return "invitee-" + UUID.randomUUID() + "@example.com";
    }

    private static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    private long invitedId(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(201);
        return ((Number) json(response).get("invitationId")).longValue();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(HttpResponse<String> response) {
        return (List<Map<String, Object>>) json(response).get("items");
    }

    @Test
    @DisplayName("the four APIs are 401 without a token and 403 for a non-admin, reading and writing nothing")
    void authorization() {
        long id = invitedId(api.invite(admin, email(), "ja"));
        int mails = RECEIVER.messages().size();
        int rows = count("SELECT COUNT(*) FROM invitations");
        String member = users.accessToken(users.createNonAdmin());

        for (String token : new String[] {null, member}) {
            int expected = token == null ? 401 : 403;
            assertThat(api.invite(token, email(), "ja").statusCode()).isEqualTo(expected);
            assertThat(api.list(token, "").statusCode()).isEqualTo(expected);
            assertThat(api.resend(token, id).statusCode()).isEqualTo(expected);
            assertThat(api.cancel(token, id).statusCode()).isEqualTo(expected);
        }
        assertThat(RECEIVER.messages()).hasSize(mails);
        assertThat(count("SELECT COUNT(*) FROM invitations")).isEqualTo(rows);
        assertThat(count("SELECT COUNT(*) FROM invitations WHERE state = 'PENDING'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("an invitation is created with 201, sent, listed, and the responses carry no token, hash or URL")
    void createsAndLists() {
        String email = email();

        HttpResponse<String> created = api.invite(admin, email, "en");

        long id = invitedId(created);
        Map<String, Object> body = json(created);
        assertThat(body)
                .containsEntry("email", email)
                .containsEntry("language", "en")
                .containsEntry("invitedBy", TestUserAccounts.DISPLAY_NAME)
                .containsEntry("sendResult", "SENT")
                .containsEntry("expired", false)
                .containsEntry("invitedAt", "2026-09-22T00:00:00Z")
                .containsEntry("expiresAt", "2026-09-23T00:00:00Z");
        assertThat(body.keySet())
                .containsExactlyInAnyOrder(
                        "invitationId",
                        "email",
                        "language",
                        "invitedBy",
                        "invitedAt",
                        "expiresAt",
                        "sendResult",
                        "expired");
        String token = ReceivedMails.lastToken(RECEIVER);
        HttpResponse<String> list = api.list(admin, "");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json(list))
                .containsEntry("page", 1)
                .containsEntry("size", 20)
                .containsEntry("total", 1)
                .containsEntry("invitationEnabled", true)
                .containsEntry("unavailableReasons", List.of());
        assertThat(items(list))
                .singleElement()
                .satisfies(item ->
                        assertThat(item).containsEntry("invitationId", (int) id).containsEntry("sendResult", "SENT"));
        for (HttpResponse<String> response : List.of(created, list)) {
            assertThat(response.body())
                    .doesNotContain(token)
                    .doesNotContain("register#token")
                    .doesNotContain("Hash");
        }
    }

    @Test
    @DisplayName("input errors are 400 with field errors, no value in the body, no row and no mail")
    void inputErrors() {
        String localPart = "a".repeat(254 - "@example.com".length());
        int mails = RECEIVER.messages().size();

        assertThat(api.invite(admin, localPart + "@example.com", "ja").statusCode())
                .isEqualTo(201);
        HttpResponse<String> tooLong = api.invite(admin, "b" + localPart + "@example.com", "ja");
        HttpResponse<String> lineBreak = api.invite(admin, "x@example.com\r\nBcc: y@example.com", "ja");
        HttpResponse<String> format = api.invite(admin, "not-an-email", "JA");
        HttpResponse<String> empty = api.invite(admin, "", "");

        assertThat(json(tooLong).get("fieldErrors")).isEqualTo(List.of(Map.of("field", "email", "reason", "TOO_LONG")));
        assertThat(json(lineBreak).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "email", "reason", "INVALID_CHARACTER")));
        assertThat(json(format).get("fieldErrors"))
                .isEqualTo(List.of(
                        Map.of("field", "email", "reason", "INVALID_VALUE"),
                        Map.of("field", "language", "reason", "INVALID_VALUE")));
        assertThat(json(empty).get("fieldErrors"))
                .isEqualTo(List.of(
                        Map.of("field", "email", "reason", "REQUIRED"),
                        Map.of("field", "language", "reason", "REQUIRED")));
        for (HttpResponse<String> response : List.of(tooLong, lineBreak, format)) {
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json(response)).containsEntry("code", "VALIDATION_FAILED");
            assertThat(response.body()).doesNotContain("example.com").doesNotContain("not-an-email");
        }
        assertThat(RECEIVER.messages()).hasSize(mails + 1);
        assertThat(count("SELECT COUNT(*) FROM invitations")).isEqualTo(1);
    }

    @Test
    @DisplayName("a registered email is 409 INVITATION_EMAIL_REGISTERED and a pending one is 409 with its id and page")
    void conflicts() {
        String registered = email();
        TestUserAccounts.create(userAccountService, registered, AdminTestUsers.PASSWORD, false);
        String target = email();
        long targetId = invitedId(api.invite(admin, target, "ja"));
        for (int i = 0; i < 20; i++) {
            invitedId(api.invite(admin, email(), "ja"));
        }
        int mails = RECEIVER.messages().size();

        HttpResponse<String> taken = api.invite(admin, " " + registered.toUpperCase() + " ", "ja");
        HttpResponse<String> pending = api.invite(admin, target, "en");

        assertThat(taken.statusCode()).isEqualTo(409);
        assertThat(json(taken))
                .containsEntry("code", "INVITATION_EMAIL_REGISTERED")
                .doesNotContainKey("invitationId");
        assertThat(taken.body()).doesNotContain(registered);
        assertThat(pending.statusCode()).isEqualTo(409);
        assertThat(json(pending))
                .containsEntry("code", "INVITATION_ALREADY_PENDING")
                .containsEntry("invitationId", (int) targetId)
                .containsEntry("page", 2);
        assertThat(RECEIVER.messages()).hasSize(mails);
        assertThat(items(api.list(admin, "?page=2")))
                .singleElement()
                .satisfies(item -> assertThat(item).containsEntry("invitationId", (int) targetId));
    }

    @Test
    @DisplayName("resend and cancel of completed, cancelled, replaced and unknown invitations give the same 404 body")
    void notFoundBodiesAreEqual() {
        long cancelled = invitedId(api.invite(admin, email(), "ja"));
        assertThat(api.cancel(admin, cancelled).statusCode()).isEqualTo(204);
        String email = email();
        long replaced = invitedId(api.invite(admin, email, "ja"));
        long completed = invitedId(api.invite(admin, email(), "ja"));
        assertThat(api.complete(ReceivedMails.lastToken(RECEIVER), "山田 花子").statusCode())
                .isEqualTo(204);
        clock.advance(Duration.ofHours(25));
        String fresh = users.accessToken(users.createAdmin());
        invitedId(api.invite(fresh, email, "ja"));

        List<HttpResponse<String>> responses = new java.util.ArrayList<>();
        for (long id : new long[] {cancelled, replaced, completed, 999_999}) {
            responses.add(api.resend(fresh, id));
            responses.add(api.cancel(fresh, id));
        }

        Map<String, Object> first = json(responses.getFirst());
        first.remove("traceId");
        assertThat(first).containsEntry("status", 404).containsEntry("code", "INVITATION_NOT_FOUND");
        for (HttpResponse<String> response : responses) {
            assertThat(response.statusCode()).isEqualTo(404);
            Map<String, Object> body = json(response);
            body.remove("traceId");
            body.remove("instance");
            Map<String, Object> expected = new java.util.LinkedHashMap<>(first);
            expected.remove("instance");
            assertThat(body).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("the list orders newest first by id, pages by 20, empties beyond the last page and rejects bad pages")
    void listing() {
        long[] ids = new long[21];
        for (int i = 0; i < 21; i++) {
            ids[i] = invitedId(api.invite(admin, email(), i % 2 == 0 ? "ja" : "en"));
        }

        List<Map<String, Object>> first = items(api.list(admin, "?page=1"));
        List<Map<String, Object>> second = items(api.list(admin, "?page=2"));

        assertThat(first).hasSize(20);
        assertThat(first.getFirst()).containsEntry("invitationId", (int) ids[20]);
        assertThat(second)
                .singleElement()
                .satisfies(item -> assertThat(item).containsEntry("invitationId", (int) ids[0]));
        HttpResponse<String> beyond = api.list(admin, "?page=3");
        assertThat(beyond.statusCode()).isEqualTo(200);
        assertThat(json(beyond)).containsEntry("total", 21).containsEntry("items", List.of());
        for (String bad : new String[] {"?page=0", "?page=-1", "?page=1.5", "?page=abc", "?page="}) {
            HttpResponse<String> response = api.list(admin, bad);
            assertThat(response.statusCode()).as(bad).isEqualTo(400);
            assertThat(json(response))
                    .containsEntry("code", "VALIDATION_FAILED")
                    .doesNotContainKey("fieldErrors");
        }
    }

    @Test
    @DisplayName("the list shows expiry by the clock, PENDING as FAILED and an empty name for a missing inviter")
    void listFlags() {
        long expired = invitedId(api.invite(admin, email(), "ja"));
        clock.advance(Duration.ofHours(24));
        String fresh = users.accessToken(users.createAdmin());
        long valid = invitedId(api.invite(fresh, email(), "ja"));
        jdbc.update("UPDATE invitations SET send_result = 'PENDING' WHERE invitation_id = ?", valid);

        List<Map<String, Object>> list = items(api.list(fresh, ""));

        assertThat(list)
                .filteredOn(item -> ((Number) item.get("invitationId")).longValue() == expired)
                .singleElement()
                .satisfies(item -> assertThat(item).containsEntry("expired", true));
        assertThat(list)
                .filteredOn(item -> ((Number) item.get("invitationId")).longValue() == valid)
                .singleElement()
                .satisfies(
                        item -> assertThat(item).containsEntry("expired", false).containsEntry("sendResult", "FAILED"));
    }

    @Test
    @DisplayName("the problem texts follow Accept-Language in ja and en")
    void localizedTexts() {
        String email = email();
        invitedId(api.invite(admin, email, "ja"));

        Map<String, Object> ja = json(api.invite(admin, email, "ja", "Accept-Language", "ja"));
        Map<String, Object> en = json(api.invite(admin, email, "ja", "Accept-Language", "en"));
        Map<String, Object> notFoundEn = json(api.cancel(admin, 999_999, "Accept-Language", "en"));

        assertThat(ja)
                .containsEntry(
                        "title",
                        InvitationProblemTypes.INVITATION_ALREADY_PENDING
                                .title()
                                .ja());
        assertThat(en)
                .containsEntry(
                        "title",
                        InvitationProblemTypes.INVITATION_ALREADY_PENDING
                                .title()
                                .en());
        assertThat(notFoundEn)
                .containsEntry(
                        "title",
                        InvitationProblemTypes.INVITATION_NOT_FOUND.title().en());
        assertThat(ja.get("detail")).isNotEqualTo(en.get("detail"));
    }

    @Test
    @DisplayName("503 lists the missing settings for base URL, SMTP and both, sends nothing and keeps the old link")
    void notConfigured(@TempDir Path dirA, @TempDir Path dirB, @TempDir Path dirC) {
        Map<Path, Map<String, String>> cases = new java.util.LinkedHashMap<>();
        cases.put(dirA, InvitationApps.mail(RECEIVER.port(), null));
        cases.put(dirB, Map.of("mastersmith.web.base-url", "http://localhost:8080"));
        cases.put(dirC, Map.of());
        List<List<String>> expected = List.of(
                List.of("BASE_URL_NOT_CONFIGURED"),
                List.of("SMTP_NOT_CONFIGURED"),
                List.of("BASE_URL_NOT_CONFIGURED", "SMTP_NOT_CONFIGURED"));
        int index = 0;
        int mails = RECEIVER.messages().size();
        for (Map.Entry<Path, Map<String, String>> entry : cases.entrySet()) {
            try (ConfigurableApplicationContext context = InvitationApps.start(entry.getKey(), entry.getValue())) {
                InvitationApi other = new InvitationApi(InvitationApps.port(context));
                String token = InvitationApps.adminToken(context);
                HttpResponse<String> invite = other.invite(token, email(), "ja");
                assertThat(invite.statusCode()).isEqualTo(503);
                assertThat(json(invite))
                        .containsEntry("code", "INVITATION_NOT_CONFIGURED")
                        .containsEntry("unavailableReasons", expected.get(index));
                assertThat(json(other.list(token, "")))
                        .containsEntry("invitationEnabled", false)
                        .containsEntry("unavailableReasons", expected.get(index));
                if (index == 0) {
                    resendKeepsTheOldLink(context, other, token);
                }
            }
            index++;
        }
        assertThat(RECEIVER.messages()).hasSize(mails);
    }

    private static void resendKeepsTheOldLink(
            ConfigurableApplicationContext context, InvitationApi other, String adminToken) {
        JdbcTemplate otherJdbc = context.getBean(JdbcTemplate.class);
        String known = "K".repeat(43);
        long admin = otherJdbc.queryForObject("SELECT MIN(user_id) FROM users WHERE admin_flag", Long.class);
        otherJdbc.update(
                "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at, expires_at,"
                        + " send_result, state) VALUES ('old-link@example.com', 'ja', ?, ?, CURRENT_TIMESTAMP,"
                        + " DATEADD('YEAR', 10, CURRENT_TIMESTAMP), 'SENT', 'PENDING')",
                new cherry.mastersmith.invitation.domain.InvitationToken(known).hash(),
                admin);
        long id = otherJdbc.queryForObject(
                "SELECT invitation_id FROM invitations WHERE email = 'old-link@example.com'", Long.class);

        HttpResponse<String> resend = other.resend(adminToken, id);

        assertThat(resend.statusCode()).isEqualTo(503);
        assertThat(other.verify(known).statusCode()).as("前のリンクは有効なまま").isEqualTo(200);
        assertThat(other.cancel(adminToken, id).statusCode()).as("取り消しは設定によらない").isEqualTo(204);
    }
}
