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
import cherry.mastersmith.auth.testsupport.AuthTestTokens;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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

/**
 * 招待中の人の認証の結合テスト（BR7.7、NFR3.3、AC3.2.8・AC3.2.14、team.md の必須のテスト）。招待は利用者を作らないため、既存の
 * ログイン・トークンの更新・アクセストークンの認証のどれでも拒否されることを確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class InvitedPersonAuthenticationIT {

    /** どの利用者の ID とも重ならない招待の ID。 */
    private static final long INVITATION_ID = 910_000;

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
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Value("${mastersmith.auth.signing-key}")
    String signingKey;

    private String email;

    @BeforeEach
    void setUp() {
        clock.set(AuthApiTestConfig.START);
        AdminTestUsers users = new AdminTestUsers(userAccountService, port);
        AdminTestUsers.TestUser admin = users.createAdmin();
        email = "invited-" + UUID.randomUUID() + "@example.com";
        assertThat(new InvitationApi(port)
                        .invite(users.accessToken(admin), email, "ja")
                        .statusCode())
                .isEqualTo(201);
        jdbc.update(
                "UPDATE invitations SET invitation_id = ? WHERE email = ?",
                INVITATION_ID + Math.abs(email.hashCode() % 1000),
                email);
    }

    @Test
    @DisplayName("logging in with the invited email is the same 401 as an unknown user")
    void loginIsRefused() {
        AuthApi auth = new AuthApi(port);

        HttpResponse<String> invited = auth.login(email, InvitationApi.PASSWORD);
        HttpResponse<String> unknown =
                auth.login("nobody-" + UUID.randomUUID() + "@example.com", InvitationApi.PASSWORD);

        assertThat(invited.statusCode()).isEqualTo(401);
        Map<String, Object> invitedBody = HttpTestClient.json(invited);
        Map<String, Object> unknownBody = HttpTestClient.json(unknown);
        invitedBody.remove("traceId");
        unknownBody.remove("traceId");
        assertThat(invitedBody).isEqualTo(unknownBody);
    }

    @Test
    @DisplayName("correctly signed access tokens for the invitation id or the invited email are 401")
    void accessTokensAreRefused() throws Exception {
        long invitationId =
                jdbc.queryForObject("SELECT invitation_id FROM invitations WHERE email = ?", Long.class, email);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id = ?", Integer.class, invitationId))
                .isZero();
        String byId = new AuthTestTokens(signingKey)
                .hs256(invitationId, AuthApiTestConfig.START, AuthApiTestConfig.START.plus(Duration.ofMinutes(5)));
        SignedJWT byEmail = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject(email)
                        .issueTime(Date.from(AuthApiTestConfig.START))
                        .expirationTime(Date.from(AuthApiTestConfig.START.plus(Duration.ofMinutes(5))))
                        .build());
        byEmail.sign(new MACSigner(Base64.getDecoder().decode(signingKey)));
        MeApi me = new MeApi(port);

        assertThat(me.getPreferences(byId).statusCode()).isEqualTo(401);
        assertThat(me.getPreferences(byEmail.serialize()).statusCode()).isEqualTo(401);
        assertThat(new InvitationApi(port).list(byId, "").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("there is no refresh token for an invited person and a made-up one is 401")
    void refreshIsRefused() {
        AuthApi auth = new AuthApi(port);

        // ベース URL（mastersmith.web.base-url）を設定すると Origin の確かめもその値で行うため、ベース URL と同じ Origin で送る。
        assertThat(auth.refresh("A".repeat(43), "http://localhost:8080").statusCode())
                .isEqualTo(401);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON rt.user_id = u.user_id"
                                + " WHERE u.email = ?",
                        Integer.class,
                        email))
                .isZero();
    }
}
