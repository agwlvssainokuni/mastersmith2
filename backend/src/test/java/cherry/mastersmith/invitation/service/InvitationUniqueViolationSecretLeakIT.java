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
package cherry.mastersmith.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import cherry.mastersmith.invitation.testsupport.TestInvitationBarrier;
import cherry.mastersmith.mail.testsupport.SmtpTestServer;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 同じメールアドレスへの招待が重なり、招待中の一意の制約（{@code uk_invitations_pending_email}）の違反が起きても、重なったメール
 * アドレスと H2 の違反の文がアプリのログと監査の行に出ないことの結合テスト（Intent 261003-user-admin-followup の FR8.1・FR8.2）。
 *
 * <p>重なりは {@link InvitationConcurrencyIT} と同じ待ち合わせ（{@link TestInvitationBarrier#insertTogether(int)}）で確実に作る。1つの
 * Spring の文脈の中で、既定の INFO の場合と {@code cherry.mastersmith} のロガーを TRACE にした場合の両方で確かめる。業務処理と
 * Hibernate は差し替えない。メールは JVM の中の SMTP の受け手で受け、実在の宛先へは送らない。
 */
@SpringBootTest
@Import({AuthApiTestConfig.class, TestInvitationBarrier.Config.class})
@ExtendWith(OutputCaptureExtension.class)
class InvitationUniqueViolationSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /** H2 の違反の文の目印（招待中の一意の制約の索引の名前）。 */
    private static final String VIOLATION_MARKER = "PUBLIC.UK_INVITATIONS_PENDING_EMAIL";

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.1", "agent", null);

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

    @Autowired
    InvitationService service;

    @Autowired
    TestInvitationBarrier barrier;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LoggingSystem loggingSystem;

    @AfterEach
    void tearDown() {
        barrier.reset();
    }

    /** 1回分の結果。 */
    private record Run(String logs, List<InviteResult> results, List<Map<String, Object>> auditRows, String email) {}

    private Run run(CapturedOutput output) throws Exception {
        long admin = TestUserAccounts.create(
                userAccountService,
                "uniq-leak-admin-" + UUID.randomUUID() + "@example.com",
                TestDatabase.randomSecret(),
                true);
        String email = "uniq-leak-invitee-" + UUID.randomUUID() + "@example.com";
        long auditBefore = lastAuditEventId();
        int offset = output.getOut().length();
        barrier.insertTogether(2);

        CompletableFuture<InviteResult> first =
                CompletableFuture.supplyAsync(() -> service.invite(admin, ORIGIN, new InviteCommand(email, "ja")));
        CompletableFuture<InviteResult> second =
                CompletableFuture.supplyAsync(() -> service.invite(admin, ORIGIN, new InviteCommand(email, "en")));
        List<InviteResult> results = List.of(
                first.get(TestInvitationBarrier.TIMEOUT_SECONDS, TimeUnit.SECONDS),
                second.get(TestInvitationBarrier.TIMEOUT_SECONDS, TimeUnit.SECONDS));

        String logs = output.getOut().substring(offset) + output.getErr();
        List<Map<String, Object>> auditRows =
                jdbc.queryForList("SELECT * FROM audit_events WHERE audit_event_id > ?", auditBefore);
        return new Run(logs, results, auditRows, email);
    }

    private long lastAuditEventId() {
        Long id = jdbc.queryForObject("SELECT COALESCE(MAX(audit_event_id), 0) FROM audit_events", Long.class);
        return id == null ? 0 : id;
    }

    private static void assertNoLeak(Run run) {
        assertThat(run.results())
                .as("重なりは1件の招待と1件の招待中になる（違反が起きたことの確かめ）")
                .filteredOn(InviteResult.Invited.class::isInstance)
                .hasSize(1);
        assertThat(run.results())
                .filteredOn(InviteResult.AlreadyPending.class::isInstance)
                .hasSize(1);
        String[] secrets = {run.email(), run.email().toUpperCase(Locale.ROOT), VIOLATION_MARKER};
        JsonLogRecords.assertContainsNoSecret(run.logs(), secrets);
        assertThat(run.logs()).doesNotContainIgnoringCase(run.email());
        assertThat(run.auditRows()).as("勝った側の招待の監査の行がある").isNotEmpty();
        for (Map<String, Object> row : run.auditRows()) {
            assertThat(String.valueOf(row.values()))
                    .doesNotContainIgnoringCase(run.email())
                    .doesNotContain(VIOLATION_MARKER);
        }
    }

    @Test
    @DisplayName("with the default INFO level, overlapping invitations leak neither the email nor the violation text")
    void infoLevel(CapturedOutput output) throws Exception {
        Run run = run(output);

        assertNoLeak(run);
        assertThat(run.logs()).doesNotContain("ENTER ");
    }

    @Test
    @DisplayName("with TRACE, overlapping invitations leak neither the email nor the violation text")
    void traceLevel(CapturedOutput output) throws Exception {
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        Run run;
        try {
            run = run(output);
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }

        assertNoLeak(run);
        assertThat(run.logs()).as("メソッドの呼び出しの追跡が有効").contains("ENTER InvitationService#invite");
    }
}
