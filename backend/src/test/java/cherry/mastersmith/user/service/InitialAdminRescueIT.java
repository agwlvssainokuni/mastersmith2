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

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.testsupport.FailingInitialAdminRescueConfig;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 初期管理者の救済の結合テスト（Intent 261004-safety-carryover の FR1.1〜FR1.8・FR1.2a・NFR2・R-08）。組み込みの H2 で起動を2回
 * 行う。1回目の起動で初期管理者を作り、状態を整えてから閉じ、2回目の起動で救済を確かめる。
 */
@ExtendWith(OutputCaptureExtension.class)
class InitialAdminRescueIT {

    private static final String EMAIL = "rescue@example.com";

    private static final String INITIALIZER = InitialAdminInitializer.class.getName();

    @TempDir
    Path tempDir;

    /** 1回目の起動で整えた状態。 */
    private record Prepared(long userId, String cookie, String previousPassword, String previousHash, int auditRows) {}

    private static ConfigurableApplicationContext start(Path dir, List<Class<?>> extraSources, String... args) {
        List<String> all = new ArrayList<>();
        all.add("--spring.datasource.url=" + TestDatabase.url(dir));
        all.add("--server.port=0");
        all.add("--mastersmith.auth.password.bcrypt-cost=4");
        all.addAll(List.of(args));
        List<Class<?>> sources = new ArrayList<>();
        sources.add(MastersmithApplication.class);
        sources.addAll(extraSources);
        return new SpringApplicationBuilder(sources.toArray(Class<?>[]::new)).run(all.toArray(String[]::new));
    }

    private static String[] settings(String password) {
        return new String[] {
            "--mastersmith.auth.initial-admin.email=" + EMAIL, "--mastersmith.auth.initial-admin.password=" + password
        };
    }

    private static JdbcTemplate jdbc(ConfigurableApplicationContext context) {
        return new JdbcTemplate(context.getBean(DataSource.class));
    }

    private static AuthApi api(ConfigurableApplicationContext context) {
        return new AuthApi(Integer.parseInt(context.getEnvironment().getProperty("local.server.port")));
    }

    /**
     * 1回目の起動で初期管理者を作り、ログインしてリフレッシュトークンを得てから、指定の状態に書き換える（テストの準備に限る SQL）。
     */
    private Prepared prepare(
            Path dir, String password, boolean suspend, boolean revokeAdmin, boolean changePassword, boolean lock) {
        try (ConfigurableApplicationContext context = start(dir, List.of(), settings(password))) {
            JdbcTemplate jdbc = jdbc(context);
            long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);
            HttpResponse<String> login = api(context).login(EMAIL, password);
            assertThat(login.statusCode()).isEqualTo(200);
            String cookie = AuthApi.cookieValue(login);
            String previousPassword = "起動の前のパスワード-" + TestDatabase.randomSecret();
            if (suspend) {
                jdbc.update("UPDATE users SET suspended = TRUE WHERE user_id = ?", userId);
            }
            if (revokeAdmin) {
                jdbc.update("UPDATE users SET admin_flag = FALSE WHERE user_id = ?", userId);
            }
            if (changePassword) {
                jdbc.update(
                        "UPDATE users SET password_hash = ? WHERE user_id = ?",
                        new BCryptPasswordEncoder(4).encode(previousPassword),
                        userId);
            }
            if (lock) {
                jdbc.update(
                        "UPDATE login_attempt_states SET consecutive_failures = 5, locked_until = ? WHERE subject_id = ?",
                        OffsetDateTime.ofInstant(Instant.now().plus(1, ChronoUnit.HOURS), ZoneOffset.UTC),
                        userId);
            }
            String previousHash =
                    jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, userId);
            return new Prepared(userId, cookie, previousPassword, previousHash, auditCount(jdbc));
        }
    }

    private static int auditCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
    }

    private static Map<String, Object> user(JdbcTemplate jdbc, long userId) {
        return jdbc.queryForMap("SELECT suspended, admin_flag, password_hash FROM users WHERE user_id = ?", userId);
    }

    private static Map<String, Object> lockState(JdbcTemplate jdbc, long userId) {
        return jdbc.queryForMap(
                "SELECT consecutive_failures, locked_until FROM login_attempt_states WHERE subject_id = ?", userId);
    }

    private static int activeTokens(JdbcTemplate jdbc, long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class, userId);
    }

    private static List<Map<String, Object>> rescueRows(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT event_type, result, target_user_id, actor_user_id, source_ip, rejection_kind,"
                + " entered_email, failure_reason, user_agent, request_path, trace_id, dsl_hash, dsl_source,"
                + " target_invitation_id FROM audit_events WHERE event_type = 'INITIAL_ADMIN_RESCUED'");
    }

    private static List<Map<String, Object>> initializerLogs(String output, String level, String message) {
        return JsonLogRecords.parse(output).stream()
                .filter(record -> INITIALIZER.equals(record.get("logger")))
                .filter(record -> level.equals(record.get("level")))
                .filter(record -> message.equals(record.get("message")))
                .toList();
    }

    @Test
    @DisplayName(
            "a suspended, non-admin, locked initial admin with another password and live refresh tokens is rescued")
    void rescuesAllThreeConditions(CapturedOutput output) {
        String password = "初期管理者-" + TestDatabase.randomSecret();
        Prepared prepared = prepare(tempDir.resolve("all"), password, true, true, true, true);
        int before = output.getOut().length();

        try (ConfigurableApplicationContext context = start(tempDir.resolve("all"), List.of(), settings(password))) {
            JdbcTemplate jdbc = jdbc(context);
            Map<String, Object> user = user(jdbc, prepared.userId());
            assertThat(user).containsEntry("SUSPENDED", false).containsEntry("ADMIN_FLAG", true);
            assertThat((String) user.get("PASSWORD_HASH")).isNotEqualTo(prepared.previousHash());
            assertThat(new BCryptPasswordEncoder().matches(password, (String) user.get("PASSWORD_HASH")))
                    .isTrue();
            assertThat(lockState(jdbc, prepared.userId()))
                    .containsEntry("CONSECUTIVE_FAILURES", 0)
                    .containsEntry("LOCKED_UNTIL", null);
            assertThat(activeTokens(jdbc, prepared.userId()))
                    .as("起動の前のリフレッシュトークンはすべて無効")
                    .isZero();
            assertThat(rescueRows(jdbc))
                    .singleElement()
                    .satisfies(row -> assertThat(row)
                            .containsEntry("EVENT_TYPE", "INITIAL_ADMIN_RESCUED")
                            .containsEntry("RESULT", "SUCCESS")
                            .containsEntry("TARGET_USER_ID", prepared.userId())
                            .containsEntry("ACTOR_USER_ID", null)
                            .containsEntry("SOURCE_IP", "system")
                            .containsEntry("REJECTION_KIND", "SUSPENDED+NO_ADMIN+PASSWORD")
                            .containsEntry("ENTERED_EMAIL", null)
                            .containsEntry("FAILURE_REASON", null)
                            .containsEntry("USER_AGENT", null)
                            .containsEntry("REQUEST_PATH", null)
                            .containsEntry("TRACE_ID", null)
                            .containsEntry("DSL_HASH", null)
                            .containsEntry("DSL_SOURCE", null)
                            .containsEntry("TARGET_INVITATION_ID", null));

            AuthApi api = api(context);
            assertThat(api.refresh(prepared.cookie(), api.origin()).statusCode())
                    .as("起動の前のリフレッシュトークンは更新に使えない")
                    .isEqualTo(401);
            assertThat(api.login(EMAIL, password).statusCode())
                    .as("設定のパスワードでログインできる")
                    .isEqualTo(200);
            assertThat(api.login(EMAIL, prepared.previousPassword()).statusCode())
                    .isEqualTo(401);
        }
        String startup = output.getOut().substring(before);
        assertThat(initializerLogs(startup, "WARN", "初期管理者を救済しました"))
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("maskedEmail", "r***@example.com")
                        .containsEntry("conditions", "SUSPENDED+NO_ADMIN+PASSWORD")
                        .doesNotContainKey("email"));
        JsonLogRecords.assertContainsNoSecret(output.getAll(), password, prepared.previousPassword(), EMAIL);
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @DisplayName("each single condition alone is rescued and recorded with its own value")
    @CsvSource({"true,false,false,SUSPENDED", "false,true,false,NO_ADMIN", "false,false,true,PASSWORD"})
    void rescuesSingleCondition(boolean suspend, boolean revokeAdmin, boolean changePassword, String expected) {
        String password = "初期管理者-" + TestDatabase.randomSecret();
        Path dir = tempDir.resolve(expected);
        Prepared prepared = prepare(dir, password, suspend, revokeAdmin, changePassword, false);

        try (ConfigurableApplicationContext context = start(dir, List.of(), settings(password))) {
            JdbcTemplate jdbc = jdbc(context);
            assertThat(user(jdbc, prepared.userId()))
                    .containsEntry("SUSPENDED", false)
                    .containsEntry("ADMIN_FLAG", true);
            assertThat(activeTokens(jdbc, prepared.userId())).isZero();
            assertThat(rescueRows(jdbc))
                    .singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("REJECTION_KIND", expected));
            assertThat(api(context).login(EMAIL, password).statusCode()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("an active admin whose password matches is left untouched and no audit row is added")
    void notNeeded(CapturedOutput output) {
        String password = "初期管理者-" + TestDatabase.randomSecret();
        Path dir = tempDir.resolve("none-needed");
        Prepared prepared = prepare(dir, password, false, false, false, false);
        int before = output.getOut().length();

        try (ConfigurableApplicationContext context = start(dir, List.of(), settings(password))) {
            JdbcTemplate jdbc = jdbc(context);
            assertThat(user(jdbc, prepared.userId()))
                    .containsEntry("SUSPENDED", false)
                    .containsEntry("ADMIN_FLAG", true)
                    .containsEntry("PASSWORD_HASH", prepared.previousHash());
            assertThat(activeTokens(jdbc, prepared.userId()))
                    .as("リフレッシュトークンは無効にしない")
                    .isEqualTo(1);
            assertThat(auditCount(jdbc)).isEqualTo(prepared.auditRows());
        }
        String startup = output.getOut().substring(before);
        assertThat(initializerLogs(startup, "INFO", "初期管理者は既にいるため、作成しませんでした")).hasSize(1);
        assertThat(initializerLogs(startup, "WARN", "初期管理者を救済しました")).isEmpty();
    }

    @Test
    @DisplayName("a failure in the middle of the rescue rolls everything back, adds no audit row,"
            + " logs one ERROR and keeps the application running")
    void failureRollsBack(CapturedOutput output) {
        String password = "初期管理者-" + TestDatabase.randomSecret();
        Path dir = tempDir.resolve("failing");
        Prepared prepared = prepare(dir, password, true, true, true, true);
        int before = output.getOut().length();

        try (ConfigurableApplicationContext context =
                start(dir, List.of(FailingInitialAdminRescueConfig.class), settings(password))) {
            assertThat(context.isRunning()).isTrue();
            FailingInitialAdminRescueConfig.Probe probe = context.getBean(FailingInitialAdminRescueConfig.Probe.class);
            assertThat(probe.calls()).isEqualTo(1);
            assertThat(probe.failuresSeen()).as("投げる前は auth の受け手が失敗回数を 0 にしていた").isZero();
            assertThat(probe.activeTokensSeen())
                    .as("投げる前は auth の受け手がトークンを無効にしていた")
                    .isZero();

            JdbcTemplate jdbc = jdbc(context);
            assertThat(user(jdbc, prepared.userId()))
                    .containsEntry("SUSPENDED", true)
                    .containsEntry("ADMIN_FLAG", false)
                    .containsEntry("PASSWORD_HASH", prepared.previousHash());
            assertThat(lockState(jdbc, prepared.userId())).containsEntry("CONSECUTIVE_FAILURES", 5);
            assertThat(lockState(jdbc, prepared.userId()).get("LOCKED_UNTIL")).isNotNull();
            assertThat(activeTokens(jdbc, prepared.userId())).isEqualTo(1);
            assertThat(rescueRows(jdbc)).isEmpty();
            assertThat(auditCount(jdbc)).isEqualTo(prepared.auditRows());
        }
        String startup = output.getOut().substring(before);
        assertThat(initializerLogs(startup, "ERROR", "初期管理者の救済に失敗しました"))
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("maskedEmail", "r***@example.com")
                        .containsEntry("exceptionClass", IllegalStateException.class.getName()));
        assertThat(initializerLogs(startup, "WARN", "初期管理者を救済しました")).isEmpty();
        assertThat(startup).doesNotContain(FailingInitialAdminRescueConfig.FAILURE_MESSAGE);
        JsonLogRecords.assertContainsNoSecret(output.getAll(), password, prepared.previousPassword(), EMAIL);
    }

    @Test
    @DisplayName("a failing audit write keeps the committed rescue, logs one audit ERROR and keeps running")
    void auditFailureKeepsTheRescue(CapturedOutput output) {
        String password = "初期管理者-" + TestDatabase.randomSecret();
        Path dir = tempDir.resolve("audit-failing");
        Prepared prepared = prepare(dir, password, true, false, false, false);
        int before = output.getOut().length();

        try (ConfigurableApplicationContext context =
                start(dir, List.of(FailingAuditEventRepositoryConfig.class), settings(password))) {
            assertThat(context.isRunning()).isTrue();
            JdbcTemplate jdbc = jdbc(context);
            assertThat(user(jdbc, prepared.userId())).containsEntry("SUSPENDED", false);
            assertThat(rescueRows(jdbc)).isEmpty();
        }
        String startup = output.getOut().substring(before);
        List<Map<String, Object>> auditErrors = JsonLogRecords.parse(startup).stream()
                .filter(record -> "cherry.mastersmith.audit.service.AuditEventListener".equals(record.get("logger")))
                .filter(record -> "ERROR".equals(record.get("level")))
                .toList();
        assertThat(auditErrors)
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("auditEventType", "INITIAL_ADMIN_RESCUED")
                        .containsEntry("sourceIp", "system"));
        assertThat(initializerLogs(startup, "WARN", "初期管理者を救済しました")).hasSize(1);
    }

    @Test
    @DisplayName("a configured password shorter than 12 characters rescues nobody and warns (R-08)")
    void invalidPasswordRescuesNobody(CapturedOutput output) {
        String password = "初期管理者-" + TestDatabase.randomSecret();
        Path dir = tempDir.resolve("short");
        Prepared prepared = prepare(dir, password, true, true, false, false);
        String shortPassword = "t" + TestDatabase.randomSecret().substring(0, 9);
        int before = output.getOut().length();

        try (ConfigurableApplicationContext context = start(dir, List.of(), settings(shortPassword))) {
            JdbcTemplate jdbc = jdbc(context);
            assertThat(user(jdbc, prepared.userId()))
                    .containsEntry("SUSPENDED", true)
                    .containsEntry("ADMIN_FLAG", false)
                    .containsEntry("PASSWORD_HASH", prepared.previousHash());
            assertThat(activeTokens(jdbc, prepared.userId())).isEqualTo(1);
            assertThat(auditCount(jdbc)).isEqualTo(prepared.auditRows());
        }
        String startup = output.getOut().substring(before);
        assertThat(initializerLogs(startup, "WARN", "初期管理者を作成しませんでした")).hasSize(1);
        assertThat(initializerLogs(startup, "WARN", "初期管理者を救済しました")).isEmpty();
        JsonLogRecords.assertContainsNoSecret(output.getAll(), shortPassword);
    }

    @Test
    @DisplayName("without the settings a suspended existing user is left untouched")
    void withoutSettingsNothingHappens() {
        String password = "初期管理者-" + TestDatabase.randomSecret();
        Path dir = tempDir.resolve("no-settings");
        Prepared prepared = prepare(dir, password, true, false, false, false);

        try (ConfigurableApplicationContext context = start(dir, List.of())) {
            JdbcTemplate jdbc = jdbc(context);
            assertThat(user(jdbc, prepared.userId())).containsEntry("SUSPENDED", true);
            assertThat(activeTokens(jdbc, prepared.userId())).isEqualTo(1);
            assertThat(auditCount(jdbc)).isEqualTo(prepared.auditRows());
        }
    }
}
