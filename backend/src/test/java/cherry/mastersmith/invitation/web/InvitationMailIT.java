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
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationApps;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.RawHttp;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.service.UserAccountService;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 招待メールの結合テスト（BR3.4・BR4.2・BR10.1〜BR10.3、NFR1.4・NFR8.2・NFR9.2、AC3.1.2・AC3.1.3・AC3.1.8・AC3.1.9）。JVM の中の
 * SubEtha SMTP で本番のテンプレートのメールを実際に受ける（team.md のメールの必須のテスト）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class InvitationMailIT {

    private static final String BASE_URL = "https://mastersmith.example.com/app";

    private static final SmtpTestServer RECEIVER = SmtpTestServer.builder().start();

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        InvitationTestProperties.register(registry, tempDir, RECEIVER.port(), BASE_URL + "/");
    }

    @AfterAll
    static void stop() {
        RECEIVER.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    private InvitationApi api;

    private String admin;

    @BeforeEach
    void setUp() {
        api = new InvitationApi(port);
        AdminTestUsers users = new AdminTestUsers(userAccountService, port);
        admin = users.accessToken(users.createAdmin());
    }

    private static String email() {
        return "invitee-" + UUID.randomUUID() + "@example.com";
    }

    private static String recipient(MimeMessage message) throws Exception {
        return message.getRecipients(Message.RecipientType.TO)[0].toString();
    }

    @Test
    @DisplayName("ja and en invitations reach the recipient with the subject, lang, body and a link from the base URL")
    void bothLanguages() throws Exception {
        for (String language : new String[] {"ja", "en"}) {
            String email = email();
            assertThat(api.invite(admin, email, language).statusCode()).isEqualTo(201);

            MimeMessage message = ReceivedMails.last(RECEIVER);
            String body = ReceivedMails.body(message);
            assertThat(recipient(message)).isEqualTo(email);
            assertThat(message.getSubject())
                    .isEqualTo(language.equals("ja") ? "MasterSmith への招待" : "Invitation to MasterSmith");
            assertThat(body).contains("<html lang=\"" + language + "\">");
            assertThat(ReceivedMails.link(body)).startsWith(BASE_URL + "/register#token=");
            assertThat(body).contains(language.equals("ja") ? "このリンクは 24 時間有効です" : "This link is valid for 24 hours.");
            assertThat(body.split(java.util.regex.Pattern.quote(ReceivedMails.link(body)), -1))
                    .as("リンクと URL の文字の両方")
                    .hasSize(3);
        }
    }

    @Test
    @DisplayName("a changed Host header never changes the link, which is built from the base URL only")
    void hostHeaderIsIgnored() {
        String email = email();
        String json = "{\"email\":\"" + email + "\",\"language\":\"ja\"}";

        RawHttp.Response response = RawHttp.post(port, InvitationApi.ADMIN, "evil.example.net", admin, json);

        assertThat(response.status()).isEqualTo(201);
        String body = ReceivedMails.body(ReceivedMails.last(RECEIVER));
        assertThat(ReceivedMails.link(body)).startsWith(BASE_URL + "/register#token=");
        assertThat(body).doesNotContain("evil.example.net");
    }

    @Test
    @DisplayName("a recipient with CR or LF is refused with 400 and no mail, so no header is added")
    void lineBreakRecipient() {
        int mails = RECEIVER.messages().size();

        HttpResponse<String> response = api.invite(admin, "victim@example.com\r\nBcc: other@example.com", "ja");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(RECEIVER.messages()).hasSize(mails);
    }

    @Test
    @DisplayName("without a base URL a changed Host header gives 503 and no mail")
    void noBaseUrlWithHost(@TempDir Path dir) {
        int mails = RECEIVER.messages().size();
        try (ConfigurableApplicationContext context =
                InvitationApps.start(dir, InvitationApps.mail(RECEIVER.port(), null))) {
            String json = "{\"email\":\"" + email() + "\",\"language\":\"ja\"}";

            RawHttp.Response response = RawHttp.post(
                    InvitationApps.port(context),
                    InvitationApi.ADMIN,
                    "evil.example.net",
                    InvitationApps.adminToken(context),
                    json);

            assertThat(response.status()).isEqualTo(503);
            assertThat(response.raw()).contains("BASE_URL_NOT_CONFIGURED").doesNotContain("register#token");
        }
        assertThat(RECEIVER.messages()).hasSize(mails);
    }

    @Test
    @DisplayName(
            "with a 48-hour validity the invitation and resend bodies show 48 and never 24, and expiresAt is +48 hours")
    void validity48Hours(@TempDir Path dir) {
        Map<String, String> settings = new java.util.LinkedHashMap<>(InvitationApps.mail(RECEIVER.port(), BASE_URL));
        settings.put("mastersmith.invitation.validity", "48h");
        try (ConfigurableApplicationContext context = InvitationApps.start(dir, settings)) {
            InvitationApi other = new InvitationApi(InvitationApps.port(context));
            String token = InvitationApps.adminToken(context);
            for (String language : new String[] {"ja", "en"}) {
                HttpResponse<String> created = other.invite(token, email(), language);
                assertThat(created.statusCode()).isEqualTo(201);
                Map<String, Object> body = HttpTestClient.json(created);
                assertThat(Duration.between(Instant.parse((String) body.get("invitedAt")), Instant.parse((String)
                                body.get("expiresAt"))))
                        .isEqualTo(Duration.ofHours(48));
                assertBody48(ReceivedMails.body(ReceivedMails.last(RECEIVER)), language);

                Instant before = Instant.now();
                HttpResponse<String> resent = other.resend(token, ((Number) body.get("invitationId")).longValue());
                assertThat(resent.statusCode()).isEqualTo(200);
                Instant expiresAt =
                        Instant.parse((String) HttpTestClient.json(resent).get("expiresAt"));
                assertThat(expiresAt)
                        .isAfterOrEqualTo(before.plus(Duration.ofHours(48)).minusSeconds(1))
                        .isBeforeOrEqualTo(Instant.now().plus(Duration.ofHours(48)));
                assertBody48(ReceivedMails.body(ReceivedMails.last(RECEIVER)), language);
            }
        }
    }

    private static void assertBody48(String body, String language) {
        // リンク（トークン）に偶然 24 が入りうるため、リンクを除いた本文で確かめる。
        String withoutLink = body.replace(ReceivedMails.link(body), "");
        assertThat(withoutLink)
                .contains(language.equals("ja") ? "このリンクは 48 時間有効です" : "This link is valid for 48 hours.")
                .doesNotContain("24");
    }
}
