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
package cherry.mastersmith.invitation.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 招待と登録の完了の代表の流れ（BR1.1 の 4。期待は {@code InvitationFlowIT}・{@code RegistrationApiIT}）。招待の API はメールの送信の設定が要るため、
 * H2 の参照のテストと同じく、実際に受けるだけの SMTP の受け手（{@code SmtpTestServer}）を起動して、クラスごとのアプリの設定に渡す。
 */
@InternalDbPostgresIT
class PgInvitationFlowIT {

    private static final String PASSWORD = "登録用のテストパスワード-01";

    private static SmtpTestServer receiver;

    private static PgApp app;

    private static PortableFixture fixture;

    @BeforeAll
    static void start() {
        receiver = SmtpTestServer.builder().start();
        app = PgApp.startWithMail(receiver.port());
        fixture = new PortableFixture(app);
    }

    @AfterAll
    static void stop() {
        try {
            app.close();
        } finally {
            receiver.close();
        }
    }

    private static String completeBody(String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("displayName", "招待 された人");
        body.put("password", PASSWORD);
        body.put("passwordConfirmation", PASSWORD);
        body.put("language", "ja");
        body.put("theme", "system");
        body.put("fontSize", "md");
        return PortableFixture.json(body);
    }

    @Test
    @DisplayName("an admin invites, the invitee receives the mail, verifies, completes and logs in")
    void invitesVerifiesCompletesAndLogsIn() {
        String email = "pg-flow-" + UUID.randomUUID() + "@example.com";

        HttpResponse<String> invite = fixture.adminCall(
                "POST", "/api/admin/invitations", PortableFixture.json(Map.of("email", email, "language", "ja")));
        assertThat(invite.statusCode()).isEqualTo(201);
        String token = ReceivedMails.lastToken(receiver);

        HttpResponse<String> verified =
                fixture.send("POST", "/api/registration/verify", null, PortableFixture.json(Map.of("token", token)));
        assertThat(HttpTestClient.json(verified)).isEqualTo(Map.of("email", email, "language", "ja"));
        assertThat(fixture.send("POST", "/api/registration/complete", null, completeBody(token))
                        .statusCode())
                .isEqualTo(204);

        HttpResponse<String> login = fixture.loginResponse(email, PASSWORD);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(PortableFixture.accessToken(login)).isNotBlank();
        assertThat(HttpTestClient.json(fixture.adminCall("GET", "/api/admin/invitations", null)))
                .containsEntry("total", 0);
    }

    @Test
    @DisplayName("a token that matches no invitation is 404 REGISTRATION_LINK_INVALID for verify and complete")
    void unknownTokenIsRejected() {
        String unknown = "A".repeat(43);

        PortableFixture.assertProblem(
                fixture.send("POST", "/api/registration/verify", null, PortableFixture.json(Map.of("token", unknown))),
                404,
                "REGISTRATION_LINK_INVALID");
        PortableFixture.assertProblem(
                fixture.send("POST", "/api/registration/complete", null, completeBody(unknown)),
                404,
                "REGISTRATION_LINK_INVALID");
    }
}
