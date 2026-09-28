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

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationApps;
import cherry.mastersmith.mail.testsupport.MailTestApplication;
import cherry.mastersmith.mail.testsupport.SilentSmtpServer;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 招待メールの送信の失敗の結合テスト（BR4.5・BR8.1、NFR2.2・NFR2.3・NFR9.9、{@code observability-design.md} 2.1、team.md のメールの
 * 必須のテストの送信の失敗）。受け手が接続を拒む（閉じた番号）・応答しない（受け付けて何も返さない受け手）・宛先を拒む（SubEtha SMTP の
 * 拒否の設定）の3つで確かめる。
 */
@ExtendWith(OutputCaptureExtension.class)
class InvitationSendFailureIT {

    private static final String DISPATCHER = "cherry.mastersmith.invitation.service.InvitationMailDispatcher";

    private static String email() {
        return "leak-check-failure-" + UUID.randomUUID() + "@example.com";
    }

    /** 送信の失敗で、招待は 201・送り直しは 200 で FAILED、応答に失敗の種類が無く、ログに宛先が無く、U3 の INFO が1件ずつで WARN が無いことを確かめる。 */
    private static void assertFailure(Path dir, int port, IntSupplier connections, String kind, CapturedOutput output) {
        String email = email();
        try (ConfigurableApplicationContext context =
                InvitationApps.start(dir, InvitationApps.mail(port, "http://localhost:8080"))) {
            InvitationApi api = new InvitationApi(InvitationApps.port(context));
            String admin = InvitationApps.adminToken(context);
            int offset = output.getOut().length();
            int connectionsBefore = connections.getAsInt();

            HttpResponse<String> created = api.invite(admin, email, "ja");

            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(HttpTestClient.json(created)).containsEntry("sendResult", "FAILED");
            long id = ((Number) HttpTestClient.json(created).get("invitationId")).longValue();
            assertThat(connections.getAsInt() - connectionsBefore)
                    .as("受け手への接続は1回だけ")
                    .isLessThanOrEqualTo(1);

            HttpResponse<String> resent = api.resend(admin, id);

            assertThat(resent.statusCode()).isEqualTo(200);
            assertThat(HttpTestClient.json(resent)).containsEntry("sendResult", "FAILED");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject(
                            "SELECT send_result FROM invitations WHERE invitation_id = ?", String.class, id))
                    .isEqualTo("FAILED");
            assertThat(jdbc.queryForList(
                            "SELECT event_type, result FROM audit_events WHERE target_invitation_id = ?"
                                    + " ORDER BY audit_event_id",
                            id))
                    .extracting(row -> row.get("EVENT_TYPE") + ":" + row.get("RESULT"))
                    .containsExactly("INVITATION_ISSUED:SUCCESS", "INVITATION_RESENT:SUCCESS");

            String logs = output.getOut().substring(offset);
            // 正常の応答（管理者だけが見る C5）の email は除き、失敗の種類の細部・SMTP の応答は応答に載せない（NFR2.2）。
            for (HttpResponse<String> response : List.of(created, resent)) {
                assertThat(response.body()).doesNotContain(kind).doesNotContain("failureKind");
            }
            JsonLogRecords.assertContainsNoSecret(logs, email);
            List<Map<String, Object>> records = JsonLogRecords.parse(logs);
            assertThat(records)
                    .filteredOn(record -> DISPATCHER.equals(record.get("logger")))
                    .hasSize(2)
                    .allSatisfy(record -> assertThat(record)
                            .containsEntry("level", "INFO")
                            .containsEntry("failureKind", kind)
                            .containsEntry("invitationId", (int) id))
                    .extracting(record -> record.get("operation"))
                    .containsExactly("INVITE", "RESEND");
            assertThat(records)
                    .filteredOn(
                            record -> String.valueOf(record.get("logger")).startsWith("cherry.mastersmith.invitation"))
                    .noneMatch(record -> "WARN".equals(record.get("level")));
        }
    }

    @Test
    @DisplayName("a refused connection gives 201 and 200 with FAILED and one INFO each, without the recipient")
    void connectionRefused(@TempDir Path dir, CapturedOutput output) throws Exception {
        assertFailure(dir, MailTestApplication.closedPort(), () -> 0, "CONNECTION_FAILED", output);
    }

    @Test
    @DisplayName("a silent receiver times out once per send and gives FAILED")
    void silentReceiver(@TempDir Path dir, CapturedOutput output) throws Exception {
        try (SilentSmtpServer silent = new SilentSmtpServer()) {
            assertFailure(dir, silent.port(), silent::connections, "TIMEOUT", output);
            assertThat(silent.connections()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("a receiver that rejects the recipient gives FAILED and the SMTP reply is not exposed")
    void rejectedRecipient(@TempDir Path dir, CapturedOutput output) {
        try (SmtpTestServer rejecting =
                SmtpTestServer.builder().rejectRecipients().start()) {
            assertFailure(dir, rejecting.port(), rejecting::connections, "REJECTED", output);
            assertThat(rejecting.connections()).isEqualTo(2);
            assertThat(rejecting.messages()).isEmpty();
            // SMTP の応答の形（3桁の番号と空白・ハイフン）が、U3・U1 とメールの部品のログに出ていない。出力の全体の文字列で
            // "553" を探すと、同じ出力に混ざる無関係のログ（例: 無作為の番号 127.0.0.1:55366）にたまたま当たるため、
            // B1 の MailSecretLeakIT と同じくロガーを絞って応答の形で確かめる（Intent 260925-user-management の B4 で直した）。
            assertThat(JsonLogRecords.parse(output.getOut()).stream()
                            .filter(record -> {
                                String logger = String.valueOf(record.get("logger"));
                                return logger.startsWith("cherry.mastersmith.invitation")
                                        || logger.startsWith("cherry.mastersmith.mail")
                                        || logger.startsWith("org.springframework.mail")
                                        || logger.startsWith("jakarta.mail")
                                        || logger.startsWith("org.eclipse.angus");
                            })
                            .map(Object::toString))
                    .isNotEmpty()
                    .noneMatch(text -> text.matches("(?s).*(?<![0-9a-fA-F])5[0-9]{2}[ -].*"));
        }
    }
}
