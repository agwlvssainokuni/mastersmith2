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
import cherry.mastersmith.audit.service.AuditEventListener;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.Mode;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.ReceivedMails;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 招待と登録の監査の書き込みの失敗の結合テスト（BR8.5、NFR9.4）。既存の {@code FailingAuditEventRepositoryConfig} の形で、5つの出来事
 * それぞれの記録を失敗させ、応答が変わらず、ERROR に秘密（メールアドレス・トークン）が無いことを確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AuthApiTestConfig.class, FailingAuditEventRepositoryConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class InvitationAuditWriteFailureIT {

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
    FailingAuditEventRepository repository;

    private InvitationApi api;

    private String admin;

    @BeforeEach
    void setUp() {
        repository.mode(Mode.NONE);
        api = new InvitationApi(port);
        AdminTestUsers users = new AdminTestUsers(userAccountService, port);
        admin = users.accessToken(users.createAdmin());
    }

    @AfterEach
    void reset() {
        repository.mode(Mode.NONE);
    }

    private static String email() {
        return "leak-check-audit-" + UUID.randomUUID() + "@example.com";
    }

    private long invite(String email) {
        HttpResponse<String> response = api.invite(admin, email, "ja");
        assertThat(response.statusCode()).isEqualTo(201);
        return ((Number) HttpTestClient.json(response).get("invitationId")).longValue();
    }

    /** 記録を失敗させて操作し、状態コードと、ERROR の種類と秘密の無いことを確かめる。 */
    private void assertContained(
            CapturedOutput output,
            String eventType,
            int status,
            Supplier<HttpResponse<String>> action,
            String... secrets) {
        int offset = output.getOut().length();
        repository.mode(Mode.APPEND_FAILURE);
        HttpResponse<String> response = action.get();
        repository.mode(Mode.NONE);

        assertThat(response.statusCode()).as(eventType).isEqualTo(status);
        String logs = output.getOut().substring(offset);
        List<Map<String, Object>> errors = JsonLogRecords.parse(logs).stream()
                .filter(record -> AuditEventListener.class.getName().equals(record.get("logger")))
                .filter(record -> "ERROR".equals(record.get("level")))
                .toList();
        assertThat(errors)
                .as(eventType)
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("auditEventType", eventType)
                        .containsKey("targetInvitationId"));
        JsonLogRecords.assertContainsNoSecret(logs, secrets);
    }

    @Test
    @DisplayName("each of the five events keeps its response when the audit append fails, without secrets in the ERROR")
    void everyEventIsContained(CapturedOutput output) {
        String email = email();
        assertContained(output, "INVITATION_ISSUED", 201, () -> api.invite(admin, email, "ja"), email);
        String token = ReceivedMails.lastToken(RECEIVER);
        long id = ((Number) HttpTestClient.json(api.list(admin, "")).get("total")).longValue();
        assertThat(id).isPositive();
        long invitationId = invite(email());
        assertContained(output, "INVITATION_RESENT", 200, () -> api.resend(admin, invitationId), email);
        assertContained(output, "INVITATION_CANCELLED", 204, () -> api.cancel(admin, invitationId), email);
        assertContained(output, "REGISTRATION_COMPLETED", 204, () -> api.complete(token, "山田 花子"), email, token);
        assertContained(output, "REGISTRATION_FAILED", 404, () -> api.complete(token, "山田 花子"), email, token);
    }
}
