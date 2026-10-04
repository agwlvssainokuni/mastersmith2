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
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 初期管理者の作成と救済で、ログ（既定の INFO と TRACE）と監査の行に、メールアドレス・パスワード・リフレッシュトークン・パスワードの
 * ハッシュが出ないことの結合テスト（Intent 261004-safety-carryover の FR1.5・FR1.7・NFR1）。
 *
 * <p>確かめる区間は、1回目の起動（作成）と2回目の起動（3つの条件の救済）のログ。1回目の起動の後のログイン（リフレッシュトークンを
 * 得るための準備）の区間は、この Intent の変更の外のため数えない。
 */
@ExtendWith(OutputCaptureExtension.class)
class InitialAdminSecretLeakIT {

    private static final String CONFIGURED_EMAIL = "Leak.Rescue@Example.com";

    private static final String NORMALIZED_EMAIL = "leak.rescue@example.com";

    @TempDir
    Path tempDir;

    private static ConfigurableApplicationContext start(Path dir, String password, String level) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.datasource.url=" + TestDatabase.url(dir),
                "--server.port=0",
                "--mastersmith.auth.password.bcrypt-cost=4",
                "--mastersmith.auth.initial-admin.email=" + CONFIGURED_EMAIL,
                "--mastersmith.auth.initial-admin.password=" + password));
        if (level != null) {
            args.add("--logging.level.cherry.mastersmith=" + level);
        }
        return new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new));
    }

    @ParameterizedTest(name = "[{index}] logging level {0}")
    @DisplayName("creation and rescue write no email, password, refresh token or hash to the log or the audit rows")
    @ValueSource(strings = {"INFO", "TRACE"})
    void creationAndRescueLeakNoSecret(String level, CapturedOutput output) {
        Path dir = tempDir.resolve(level);
        String password = "初期管理者-" + TestDatabase.randomSecret();
        String previousPassword = "起動の前のパスワード-" + TestDatabase.randomSecret();
        String cookie;
        String creationLog;
        try (ConfigurableApplicationContext context = start(dir, password, "TRACE".equals(level) ? level : null)) {
            creationLog = output.getOut();
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            HttpResponse<String> login = new AuthApi(
                            Integer.parseInt(context.getEnvironment().getProperty("local.server.port")))
                    .login(NORMALIZED_EMAIL, password);
            assertThat(login.statusCode()).isEqualTo(200);
            cookie = AuthApi.cookieValue(login);
            jdbc.update(
                    "UPDATE users SET suspended = TRUE, admin_flag = FALSE, password_hash = ? WHERE email = ?",
                    new BCryptPasswordEncoder(4).encode(previousPassword),
                    NORMALIZED_EMAIL);
        }
        int before = output.getOut().length();
        List<Map<String, Object>> auditRows;
        try (ConfigurableApplicationContext context = start(dir, password, "TRACE".equals(level) ? level : null)) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            assertThat(jdbc.queryForObject(
                            "SELECT suspended FROM users WHERE email = ?", Boolean.class, NORMALIZED_EMAIL))
                    .as("救済が動いたこと（確かめになっていること）")
                    .isFalse();
            auditRows = jdbc.queryForList("SELECT * FROM audit_events WHERE event_type IN"
                    + " ('INITIAL_ADMIN_CREATED', 'INITIAL_ADMIN_RESCUED')");
        }
        String rescueLog = output.getOut().substring(before);
        String checked = creationLog + rescueLog;

        assertThat(checked).contains("初期管理者を作成しました").contains("初期管理者を救済しました");
        if ("TRACE".equals(level)) {
            assertThat(rescueLog)
                    .as("TRACE の追跡の行が出ている（確かめになっている）")
                    .contains("ENTER UserAccountService#rescueInitialAdmin")
                    .contains("ENTER InitialAdminRescueListener#onInitialAdminRescued");
        }
        String[] secrets = {
            CONFIGURED_EMAIL,
            NORMALIZED_EMAIL,
            NORMALIZED_EMAIL.toUpperCase(Locale.ROOT),
            password,
            previousPassword,
            cookie,
            "$2a$"
        };
        JsonLogRecords.assertContainsNoSecret(checked, secrets);
        assertThat(auditRows).hasSize(2);
        String auditText = auditRows.stream()
                .flatMap(row -> row.values().stream())
                .map(Objects::toString)
                .reduce("", (left, right) -> left + "|" + right);
        JsonLogRecords.assertContainsNoSecret(auditText, secrets);
    }
}
