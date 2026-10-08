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
package cherry.mastersmith.role;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V11（Intent 261004-role-menu の U4、Bolt B4）の前の版との互換の結合テスト（計画の 13節 Q6: A、NFR3.6）。
 *
 * <p>前の版のアプリは V1〜V10 だけを持つ。前の版と同じ Flyway の設定（{@code validate-on-migrate: true}、既定の
 * {@code ignoreMigrationPatterns}）で、V1〜V10 だけを置いた場所を読ませ、V11 まで当てた内部DB で止まらないことを確かめる。あわせて、
 * V11 の前に前の版が書いた利用者（{@code users.admin_flag}）と監査の行が V11 の後も変わらずに読めること、V11 の後に前の版の列だけで
 * 監査の行を足せることを確かめる。role の移行は表を足すだけで {@code admin_flag} から行を移さない（計画の D-27）。前の版のイメージでの
 * 起動は配備の段（戻しの練習）が確かめる。
 *
 * <p>内部DB はテストごとの一時のディレクトリの組み込みの H2 で、Spring を起動しない（アプリの起動が V11 を当ててしまうため。
 * {@code AuditMigrationCompatibilityIT} と同じ形）。
 */
class RoleMigrationCompatibilityIT {

    /** 前の版が持つ最後の移行の番号。 */
    private static final int PREVIOUS_LAST_VERSION = 10;

    /** この Bolt が足す移行の番号。 */
    private static final int ROLE_VERSION = 11;

    private static final Pattern VERSIONED = Pattern.compile("V(\\d+)__.+\\.sql");

    @TempDir
    Path tempDir;

    private DriverManagerDataSource dataSource;

    private JdbcTemplate jdbc;

    private Path previousMigrations;

    private Path roleMigrations;

    @BeforeEach
    void setUp() throws IOException {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:file:" + tempDir.resolve("db").toAbsolutePath().resolve("mastersmith"), "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        previousMigrations = Files.createDirectories(tempDir.resolve("previous-migrations"));
        roleMigrations = Files.createDirectories(tempDir.resolve("role-migrations"));
        assertThat(copyMigrations(previousMigrations, PREVIOUS_LAST_VERSION))
                .as("前の版の移行 V1〜V10")
                .isEqualTo(PREVIOUS_LAST_VERSION);
        assertThat(copyMigrations(roleMigrations, ROLE_VERSION))
                .as("この Bolt の移行 V1〜V11")
                .isEqualTo(ROLE_VERSION);
    }

    /** クラスパスの移行のうち、番号が上限以下のものだけを写し、写した数を返す。 */
    private static int copyMigrations(Path target, int lastVersion) throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
        int copied = 0;
        for (Resource resource : resources) {
            String name = resource.getFilename();
            Matcher matcher = VERSIONED.matcher(name == null ? "" : name);
            if (matcher.matches() && Integer.parseInt(matcher.group(1)) <= lastVersion) {
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, target.resolve(name));
                }
                copied++;
            }
        }
        return copied;
    }

    /** アプリと同じ設定の Flyway（{@code validate-on-migrate: true}、それ以外は既定）。 */
    private Flyway flyway(Path location) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + location.toAbsolutePath())
                .validateOnMigrate(true)
                .load();
    }

    private Flyway previousVersion() {
        return flyway(previousMigrations);
    }

    private Flyway roleVersion() {
        return flyway(roleMigrations);
    }

    private void insertUser(String email, boolean admin) {
        jdbc.update(
                "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name) VALUES (?, ?, ?, ?, ?)",
                email,
                "$2a$04$rolemigrationhash",
                admin,
                OffsetDateTime.parse("2026-10-08T01:00:00Z"),
                "役割移行 太郎");
    }

    @Test
    @DisplayName("the previous version's Flyway with V1 to V10 does not stop on a database migrated to V11")
    void previousFlywayIgnoresTheFutureMigration() {
        roleVersion().migrate();

        ValidateResult validated = previousVersion().validateWithResult();
        MigrateResult migrated = previousVersion().migrate();

        assertThat(validated.validationSuccessful).isTrue();
        assertThat(migrated.success).isTrue();
        assertThat(migrated.migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT MAX(CAST(\"version\" AS INT)) FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL",
                        Integer.class))
                .isEqualTo(ROLE_VERSION);
    }

    @Test
    @DisplayName("users and audit rows written before V11 are read back unchanged, and admin_flag is kept as is")
    void existingRowsSurviveTheMigration() {
        assertThat(previousVersion().migrate().migrationsExecuted).isEqualTo(PREVIOUS_LAST_VERSION);
        insertUser("role-migration-admin@example.com", true);
        insertUser("role-migration-member@example.com", false);
        jdbc.update(
                "INSERT INTO audit_events (occurred_at, event_type, result, source_ip, trace_id, actor_user_id,"
                        + " target_group_id, detail) VALUES (?, 'GROUP_CREATED', 'SUCCESS', '192.0.2.60', 'trace-0060',"
                        + " 61, 71, '{\"name\":\"営業部\"}')",
                OffsetDateTime.parse("2026-10-08T03:00:00Z"));

        MigrateResult current = roleVersion().migrate();

        assertThat(current.migrationsExecuted).isEqualTo(1);
        assertThat(current.targetSchemaVersion).isEqualTo(String.valueOf(ROLE_VERSION));
        assertThat(jdbc.queryForList("SELECT email, admin_flag FROM users ORDER BY email"))
                .containsExactly(
                        Map.of("EMAIL", "role-migration-admin@example.com", "ADMIN_FLAG", true),
                        Map.of("EMAIL", "role-migration-member@example.com", "ADMIN_FLAG", false));
        assertThat(jdbc.queryForMap("SELECT event_type, target_role_id, target_group_id, detail FROM audit_events"
                        + " WHERE trace_id = 'trace-0060'"))
                .containsEntry("EVENT_TYPE", "GROUP_CREATED")
                .containsEntry("TARGET_ROLE_ID", null)
                .containsEntry("TARGET_GROUP_ID", 71L)
                .containsEntry("DETAIL", "{\"name\":\"営業部\"}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permission_settings", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("after V11 the previous version can still append users and audit rows with only its own columns")
    void previousVersionWritesAfterTheMigration() {
        roleVersion().migrate();

        insertUser("role-migration-after@example.com", true);
        int inserted = jdbc.update(
                "INSERT INTO audit_events (occurred_at, event_type, result, entered_email, failure_reason, source_ip,"
                        + " user_agent, request_path, trace_id, actor_user_id, dsl_hash, dsl_source, rejection_kind,"
                        + " target_user_id, target_invitation_id, target_role_id, target_group_id, detail)"
                        + " VALUES (?, 'GROUP_DELETED', 'SUCCESS', NULL, NULL, '192.0.2.61', NULL, NULL, 'trace-0061',"
                        + " 62, NULL, NULL, NULL, NULL, NULL, NULL, 72, '{\"name\":\"総務部\"}')",
                OffsetDateTime.parse("2026-10-08T04:00:00Z"));

        assertThat(inserted).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT admin_flag FROM users WHERE email = 'role-migration-after@example.com'", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForMap(
                        "SELECT target_role_id, target_group_id FROM audit_events" + " WHERE trace_id = 'trace-0061'"))
                .containsEntry("TARGET_ROLE_ID", null)
                .containsEntry("TARGET_GROUP_ID", 72L);
    }
}
