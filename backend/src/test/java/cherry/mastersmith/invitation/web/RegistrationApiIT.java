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
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.invitation.service.InvitationCleanupJob;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

/**
 * 登録の完了の API（契約 C6、BR3.2・BR3.3・BR7.1〜BR7.6・BR11.2、NFR1.3・NFR3.1・NFR4.4、AC3.2.2〜AC3.2.13）の結合テスト。team.md の
 * 必須のテスト（有効期限の境界、使用済み・取り消し済みの再使用の拒否、改ざん・存在しない招待の拒否と応答の同一）を含む。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class RegistrationApiIT {

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

    @Autowired
    InvitationCleanupJob cleanupJob;

    private InvitationApi api;

    private AdminTestUsers users;

    @BeforeEach
    void setUp() {
        clock.set(AuthApiTestConfig.START);
        api = new InvitationApi(port);
        users = new AdminTestUsers(userAccountService, port);
    }

    private record Invited(long invitationId, String email, String token) {}

    private String admin() {
        return users.accessToken(users.createAdmin());
    }

    private Invited invite(String email) {
        HttpResponse<String> response = api.invite(admin(), email, "en");
        assertThat(response.statusCode()).isEqualTo(201);
        long id = ((Number) HttpTestClient.json(response).get("invitationId")).longValue();
        return new Invited(id, email, ReceivedMails.lastToken(RECEIVER));
    }

    private Invited invite() {
        return invite("invitee-" + UUID.randomUUID() + "@example.com");
    }

    private int audits() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    private String lastFailure() {
        return jdbc.queryForObject(
                "SELECT failure_reason FROM audit_events WHERE event_type = 'REGISTRATION_FAILED'"
                        + " ORDER BY audit_event_id DESC FETCH FIRST 1 ROWS ONLY",
                String.class);
    }

    private static Map<String, Object> comparable(HttpResponse<String> response) {
        Map<String, Object> body = new LinkedHashMap<>(HttpTestClient.json(response));
        body.remove("traceId");
        body.remove("instance");
        return body;
    }

    private static List<String> keys(HttpResponse<String> response) {
        return new ArrayList<>(HttpTestClient.json(response).keySet());
    }

    @Test
    @DisplayName("verify returns only the email and language, changes nothing and is not audited")
    void verifyValid() {
        Invited invited = invite();
        int audits = audits();

        HttpResponse<String> response = api.verify(invited.token());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(response)).isEqualTo(Map.of("email", invited.email(), "language", "en"));
        assertThat(audits()).isEqualTo(audits);
        assertThat(jdbc.queryForObject(
                        "SELECT state FROM invitations WHERE invitation_id = ?", String.class, invited.invitationId()))
                .isEqualTo("PENDING");
        assertThat(api.verify("A".repeat(43)).statusCode()).isEqualTo(404);
        assertThat(audits()).isEqualTo(audits);
    }

    @Test
    @DisplayName("completion is 204 without tokens, the new user can log in and is not an admin")
    void completes() {
        Invited invited = invite();

        HttpResponse<String> response = api.complete(invited.token(), "山田 花子");

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        HttpResponse<String> login = new AuthApi(port).login(invited.email(), InvitationApi.PASSWORD);
        assertThat(login.statusCode()).isEqualTo(200);
        String userToken = AuthApi.accessToken(login);
        assertThat(api.list(userToken, "").statusCode()).as("登録した利用者は管理者でない").isEqualTo(403);
        assertThat(jdbc.queryForMap(
                        "SELECT display_name, admin_flag, language FROM users WHERE email = ?", invited.email()))
                .containsEntry("DISPLAY_NAME", "山田 花子")
                .containsEntry("ADMIN_FLAG", false)
                .containsEntry("LANGUAGE", "ja");
        assertThat(jdbc.queryForObject(
                        "SELECT state FROM invitations WHERE invitation_id = ?", String.class, invited.invitationId()))
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("input errors are 400 with field errors, do not consume the invitation and are not audited")
    void inputErrors() {
        Invited invited = invite();
        int audits = audits();

        HttpResponse<String> response = api.completeWith(invited.token(), " ", "短い", "違う", "JA");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(response).get("fieldErrors"))
                .isEqualTo(List.of(
                        Map.of("field", "displayName", "reason", "REQUIRED"),
                        Map.of("field", "password", "reason", "TOO_SHORT"),
                        Map.of("field", "passwordConfirmation", "reason", "MISMATCH"),
                        Map.of("field", "language", "reason", "INVALID_VALUE")));
        assertThat(response.body()).doesNotContain("短い").doesNotContain("違う");
        HttpResponse<String> badTokenAndInput = api.complete("bad", " ");
        assertThat(badTokenAndInput.statusCode()).as("入力の誤りが先").isEqualTo(400);
        assertThat(audits()).isEqualTo(audits);
        assertThat(api.complete(invited.token(), "山田 花子").statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("the expiry boundary: one microsecond before passes, exactly at and after are refused as expired")
    void expiryBoundary() {
        Invited before = invite();
        Invited at = invite();
        Invited after = invite();

        clock.set(AuthApiTestConfig.START.plus(Duration.ofHours(24)).minusNanos(1000));
        assertThat(api.verify(before.token()).statusCode()).isEqualTo(200);
        assertThat(api.complete(before.token(), "直前").statusCode()).isEqualTo(204);
        clock.set(AuthApiTestConfig.START.plus(Duration.ofHours(24)));
        assertThat(api.verify(at.token()).statusCode()).isEqualTo(404);
        assertThat(api.complete(at.token(), "ちょうど").statusCode()).isEqualTo(404);
        assertThat(lastFailure()).isEqualTo("INVITATION_EXPIRED");
        clock.advance(Duration.ofNanos(1000));
        assertThat(api.complete(after.token(), "直後").statusCode()).isEqualTo(404);
        assertThat(lastFailure()).isEqualTo("INVITATION_EXPIRED");
    }

    @Test
    @DisplayName("every rejection reason gives the same 404 body and the audit records the reason of BR7.6")
    void rejectionsAreIdentical() {
        List<HttpResponse<String>> responses = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        Invited used = invite();
        assertThat(api.complete(used.token(), "使用済み").statusCode()).isEqualTo(204);
        responses.add(api.complete(used.token(), "もう一度"));
        reasons.add(lastFailure());

        Invited cancelled = invite();
        assertThat(api.cancel(admin(), cancelled.invitationId()).statusCode()).isEqualTo(204);
        responses.add(api.complete(cancelled.token(), "取り消し済み"));
        reasons.add(lastFailure());

        Invited resent = invite();
        assertThat(api.resend(admin(), resent.invitationId()).statusCode()).isEqualTo(200);
        responses.add(api.complete(resent.token(), "送り直しの前"));
        reasons.add(lastFailure());

        char first = used.token().charAt(0);
        String tampered = (first == 'A' ? 'B' : 'A') + used.token().substring(1);
        responses.add(api.complete(tampered, "改ざん"));
        reasons.add(lastFailure());
        responses.add(api.complete("A".repeat(42), "42 文字"));
        reasons.add(lastFailure());
        responses.add(api.complete("A".repeat(44), "44 文字"));
        reasons.add(lastFailure());
        responses.add(api.complete("A".repeat(42) + "+", "使えない文字"));
        reasons.add(lastFailure());

        String sameEmail = "replaced-" + UUID.randomUUID() + "@example.com";
        Invited replaced = invite(sameEmail);
        clock.advance(Duration.ofHours(25));
        invite(sameEmail);
        responses.add(api.complete(replaced.token(), "置き換え済み"));
        reasons.add(lastFailure());

        Invited deleted = invite();
        assertThat(api.cancel(admin(), deleted.invitationId()).statusCode()).isEqualTo(204);
        clock.advance(Duration.ofDays(91));
        assertThat(cleanupJob.run()).isPositive();
        responses.add(api.complete(deleted.token(), "消えた招待"));
        reasons.add(lastFailure());
        responses.add(api.verify(deleted.token()));

        assertThat(reasons)
                .containsExactly(
                        "INVITATION_ALREADY_USED",
                        "INVITATION_CANCELLED",
                        "INVITATION_NOT_FOUND",
                        "INVITATION_NOT_FOUND",
                        "INVITATION_NOT_FOUND",
                        "INVITATION_NOT_FOUND",
                        "INVITATION_NOT_FOUND",
                        "INVITATION_EXPIRED",
                        "INVITATION_NOT_FOUND");
        Map<String, Object> expected = comparable(responses.getFirst());
        assertThat(expected).containsEntry("status", 404).containsEntry("code", "REGISTRATION_LINK_INVALID");
        List<String> expectedKeys = keys(responses.getFirst());
        for (HttpResponse<String> response : responses) {
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(comparable(response)).isEqualTo(expected);
            assertThat(keys(response)).isEqualTo(expectedKeys);
        }
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'REGISTRATION_FAILED'"
                                + " AND failure_reason = 'INVITATION_NOT_FOUND' AND target_invitation_id IS NOT NULL",
                        Integer.class))
                .as("見つからない招待の失敗は対象の招待を持たない")
                .isZero();
    }
}
