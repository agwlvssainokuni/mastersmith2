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
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** 初期管理者の自動作成の結合テスト（FR3.1〜FR3.4。組み込みの H2、既定の cost 12）。 */
@ExtendWith(OutputCaptureExtension.class)
class InitialAdminIT {

    @TempDir
    Path tempDir;

    private static ConfigurableApplicationContext start(Path dir, String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--spring.datasource.url=" + TestDatabase.url(dir);
        all[1] = "--server.port=0";
        System.arraycopy(args, 0, all, 2, args.length);
        return new SpringApplicationBuilder(MastersmithApplication.class).run(all);
    }

    @Test
    @DisplayName("two startups create exactly one lower-cased administrator with a cost-12 bcrypt hash and a lock row")
    void createsOnce(CapturedOutput output) {
        Path dir = tempDir.resolve("admin");
        String password = "初期管理者-" + TestDatabase.randomSecret();
        String[] args = {
            "--mastersmith.auth.initial-admin.email=Admin@Example.COM",
            "--mastersmith.auth.initial-admin.password=" + password
        };
        try (ConfigurableApplicationContext context = start(dir, args)) {
            assertThat(context.isRunning()).isTrue();
        }
        try (ConfigurableApplicationContext context = start(dir, args)) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT user_id, email, password_hash, admin_flag FROM users WHERE LOWER(email) = 'admin@example.com'");
            assertThat(row).containsEntry("EMAIL", "admin@example.com").containsEntry("ADMIN_FLAG", true);
            assertThat((String) row.get("PASSWORD_HASH")).startsWith("$2a$12$");
            assertThat(jdbc.queryForObject(
                            "SELECT consecutive_failures FROM login_attempt_states WHERE subject_id = ?",
                            Integer.class,
                            row.get("USER_ID")))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                    .isEqualTo(1);
        }
        assertThat(output.getOut()).contains("初期管理者を作成しました").contains("初期管理者は既にいるため");
        JsonLogRecords.assertContainsNoSecret(output.getAll(), password);
    }

    @Test
    @DisplayName("the application starts without the initial administrator settings and creates nobody")
    void startsWithoutSettings(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start(tempDir.resolve("none"))) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                    .isZero();
        }
        assertThat(output.getOut()).contains("初期管理者を作成しませんでした");
    }

    @Test
    @DisplayName("an invalid password setting creates nobody and never appears in the log")
    void invalidPassword(CapturedOutput output) {
        String shortPassword = "t" + TestDatabase.randomSecret().substring(0, 9);
        try (ConfigurableApplicationContext context = start(
                tempDir.resolve("short"),
                "--mastersmith.auth.initial-admin.email=admin@example.com",
                "--mastersmith.auth.initial-admin.password=" + shortPassword)) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                    .isZero();
        }
        assertThat(output.getOut()).contains("12 文字未満です");
        JsonLogRecords.assertContainsNoSecret(output.getAll(), shortPassword);
    }
}
