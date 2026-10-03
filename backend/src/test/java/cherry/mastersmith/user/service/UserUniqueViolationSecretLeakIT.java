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
package cherry.mastersmith.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.Theme;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 同じメールアドレスの利用者の作成が重なり、一意の制約（{@code uk_users_email}）の違反が起きても、重なったメールアドレスと H2 の
 * 違反の文がアプリのログと監査の行に出ないことの結合テスト（Intent 261003-user-admin-followup の FR8.1・FR8.2）。
 *
 * <p>重なりは {@link UserCreationIT} と同じ待ち合わせ（パスワードのハッシュの計算で {@link CyclicBarrier}）で確実に作る。1つの Spring の
 * 文脈の中で、既定の INFO の場合と {@code cherry.mastersmith} のロガーを TRACE にした場合の両方で確かめる。業務処理と Hibernate は
 * 差し替えない（漏えいは Hibernate の既定のログで起きるため）。
 */
@SpringBootTest
@Import(UserCreationIT.BarrierConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class UserUniqueViolationSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /** H2 の違反の文の目印（一意の制約の索引の名前）。 */
    private static final String VIOLATION_MARKER = "PUBLIC.UK_USERS_EMAIL";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserAccountService service;

    @Autowired
    UserCreationIT.BarrierPasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LoggingSystem loggingSystem;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        encoder.barrier(null);
        executor.shutdownNow();
    }

    /** 1回分の結果。 */
    private record Run(
            String logs, List<CreateUserResult> results, List<Map<String, Object>> auditRows, String email) {}

    private Run run(CapturedOutput output) throws Exception {
        String email = "uniq-leak-" + UUID.randomUUID() + "@example.com";
        long auditBefore = lastAuditEventId();
        int offset = output.getOut().length();
        encoder.barrier(new CyclicBarrier(2));

        Future<CreateUserResult> first = executor.submit(() -> service.createUser(newUser(email)));
        Future<CreateUserResult> second = executor.submit(() -> service.createUser(newUser(email)));
        List<CreateUserResult> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

        String logs = output.getOut().substring(offset) + output.getErr();
        List<Map<String, Object>> auditRows =
                jdbc.queryForList("SELECT * FROM audit_events WHERE audit_event_id > ?", auditBefore);
        return new Run(logs, results, auditRows, email);
    }

    private long lastAuditEventId() {
        Long id = jdbc.queryForObject("SELECT COALESCE(MAX(audit_event_id), 0) FROM audit_events", Long.class);
        return id == null ? 0 : id;
    }

    private static NewUser newUser(String email) {
        return new NewUser(
                email,
                "重なり の 確かめ",
                new Password(TestDatabase.randomSecret()),
                Language.JA,
                Theme.LIGHT,
                FontSize.MD,
                false);
    }

    private static void assertNoLeak(Run run) {
        assertThat(run.results())
                .as("重なりは1件の作成と1件の登録済みになる（違反が起きたことの確かめ）")
                .filteredOn(CreateUserResult.Created.class::isInstance)
                .hasSize(1);
        assertThat(run.results())
                .filteredOn(CreateUserResult.EmailAlreadyUsed.class::isInstance)
                .hasSize(1);
        String[] secrets = {run.email(), run.email().toUpperCase(Locale.ROOT), VIOLATION_MARKER};
        JsonLogRecords.assertContainsNoSecret(run.logs(), secrets);
        assertThat(run.logs()).doesNotContainIgnoringCase(run.email());
        for (Map<String, Object> row : run.auditRows()) {
            assertThat(String.valueOf(row.values()))
                    .doesNotContainIgnoringCase(run.email())
                    .doesNotContain(VIOLATION_MARKER);
        }
    }

    @Test
    @DisplayName("with the default INFO level, overlapping creations leak neither the email nor the violation text")
    void infoLevel(CapturedOutput output) throws Exception {
        Run run = run(output);

        assertNoLeak(run);
        assertThat(run.logs()).doesNotContain("ENTER ");
    }

    @Test
    @DisplayName("with TRACE, overlapping creations leak neither the email nor the violation text")
    void traceLevel(CapturedOutput output) throws Exception {
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        Run run;
        try {
            run = run(output);
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }

        assertNoLeak(run);
        assertThat(run.logs()).as("メソッドの呼び出しの追跡が有効").contains("ENTER UserAccountService#createUser");
    }
}
