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
package cherry.mastersmith.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
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
 * V10（Intent 261004-role-menu の U3）の前の版との互換の結合テスト（計画の 11節 Q4: A、D-14、NFR3.6・BR8.8、基盤の設計の読み直しの
 * R-02・R-03）。
 *
 * <p>前の版のアプリは V1〜V9 だけを持つ。前の版と同じ Flyway の設定（{@code validate-on-migrate: true}、既定の
 * {@code ignoreMigrationPatterns}）で、V1〜V9 だけを置いた場所を読ませ、V10 まで当てた内部DB で止まらないことを確かめる。あわせて、
 * V10 の前に前の版が書いた監査の行が V10 の後も読め、足した3列が空であること、V10 の後に前の版の列だけで追記できることを確かめる。
 * 前の版のイメージでの起動は配備の段（戻しの練習）が確かめる。
 *
 * <p>内部DB はテストごとの一時のディレクトリの組み込みの H2 で、Spring を起動しない（アプリの起動が V10 を当ててしまうため）。
 */
class AuditMigrationCompatibilityIT {

    /** 前の版が持つ最後の移行の番号。 */
    private static final int PREVIOUS_LAST_VERSION = 9;

    private static final Pattern VERSIONED = Pattern.compile("V(\\d+)__.+\\.sql");

    @TempDir
    Path tempDir;

    private DriverManagerDataSource dataSource;

    private JdbcTemplate jdbc;

    private Path previousMigrations;

    @BeforeEach
    void setUp() throws IOException {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:file:" + tempDir.resolve("db").toAbsolutePath().resolve("mastersmith"), "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        previousMigrations = Files.createDirectories(tempDir.resolve("previous-migrations"));
        copyPreviousMigrations(previousMigrations);
    }

    /** クラスパスの移行のうち、V1〜V9 だけを前の版の移行の場所に写す。 */
    private static void copyPreviousMigrations(Path target) throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
        int copied = 0;
        for (Resource resource : resources) {
            String name = resource.getFilename();
            Matcher matcher = VERSIONED.matcher(name == null ? "" : name);
            if (matcher.matches() && Integer.parseInt(matcher.group(1)) <= PREVIOUS_LAST_VERSION) {
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, target.resolve(name));
                }
                copied++;
            }
        }
        assertThat(copied).as("前の版の移行 V1〜V9").isEqualTo(PREVIOUS_LAST_VERSION);
    }

    /** アプリと同じ設定の Flyway（{@code validate-on-migrate: true}、それ以外は既定）。 */
    private Flyway flyway(String location) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .validateOnMigrate(true)
                .load();
    }

    private Flyway previousVersion() {
        return flyway("filesystem:" + previousMigrations.toAbsolutePath());
    }

    private Flyway currentVersion() {
        return flyway("classpath:db/migration");
    }

    @Test
    @DisplayName("the previous version's Flyway with V1 to V9 does not stop on a database migrated to V10")
    void previousFlywayIgnoresTheFutureMigration() {
        currentVersion().migrate();

        ValidateResult validated = previousVersion().validateWithResult();
        MigrateResult migrated = previousVersion().migrate();

        assertThat(validated.validationSuccessful).isTrue();
        assertThat(migrated.success).isTrue();
        assertThat(migrated.migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT MAX(CAST(\"version\" AS INT)) FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL",
                        Integer.class))
                .isEqualTo(10);
    }

    @Test
    @DisplayName("an audit row written before V10 is read back after V10 with the three new columns empty")
    void existingRowsSurviveTheMigration() {
        MigrateResult previous = previousVersion().migrate();
        assertThat(previous.migrationsExecuted).isEqualTo(PREVIOUS_LAST_VERSION);
        jdbc.update(
                "INSERT INTO audit_events (occurred_at, event_type, result, source_ip, trace_id, actor_user_id,"
                        + " target_user_id) VALUES (?, 'USER_RESUMED', 'SUCCESS', '192.0.2.50', 'trace-0050', 61, 62)",
                OffsetDateTime.parse("2026-10-08T03:00:00Z"));

        MigrateResult current = currentVersion().migrate();

        assertThat(current.migrationsExecuted).isEqualTo(1);
        assertThat(current.targetSchemaVersion).isEqualTo("10");
        Map<String, Object> row =
                jdbc.queryForMap("SELECT event_type, result, source_ip, actor_user_id, target_user_id,"
                        + " target_role_id, target_group_id, detail FROM audit_events WHERE trace_id = 'trace-0050'");
        assertThat(row)
                .containsEntry("EVENT_TYPE", "USER_RESUMED")
                .containsEntry("RESULT", "SUCCESS")
                .containsEntry("SOURCE_IP", "192.0.2.50")
                .containsEntry("ACTOR_USER_ID", 61L)
                .containsEntry("TARGET_USER_ID", 62L)
                .containsEntry("TARGET_ROLE_ID", null)
                .containsEntry("TARGET_GROUP_ID", null)
                .containsEntry("DETAIL", null);
    }

    @Test
    @DisplayName("after V10 the previous version can still append a row with only its own columns")
    void previousVersionAppendsAfterTheMigration() {
        currentVersion().migrate();

        int inserted = jdbc.update(
                "INSERT INTO audit_events (occurred_at, event_type, result, entered_email, failure_reason, source_ip,"
                        + " user_agent, request_path, trace_id, actor_user_id, dsl_hash, dsl_source, rejection_kind,"
                        + " target_user_id, target_invitation_id)"
                        + " VALUES (?, 'LOGIN_FAILED', 'FAILURE', 'member@example.com', 'PASSWORD_MISMATCH',"
                        + " '192.0.2.51', NULL, NULL, 'trace-0051', NULL, NULL, NULL, NULL, NULL, NULL)",
                OffsetDateTime.parse("2026-10-08T04:00:00Z"));

        assertThat(inserted).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT target_role_id, target_group_id, detail FROM audit_events"
                        + " WHERE trace_id = 'trace-0051'"))
                .containsEntry("TARGET_ROLE_ID", null)
                .containsEntry("TARGET_GROUP_ID", null)
                .containsEntry("DETAIL", null);
    }

    @Test
    @DisplayName("V10 creates the group tables with the named constraints, the user index and the column lengths")
    void groupTablesAreCreated() {
        currentVersion().migrate();

        List<String> constraints = jdbc.queryForList(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
                        + " WHERE TABLE_NAME IN ('GROUPS', 'GROUP_MEMBERS') ORDER BY CONSTRAINT_NAME",
                String.class);
        List<String> indexColumns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS"
                        + " WHERE TABLE_NAME = 'GROUP_MEMBERS' AND INDEX_NAME = 'IX_GROUP_MEMBERS_USER_ID'",
                String.class);
        List<Map<String, Object>> lengths =
                jdbc.queryForList("SELECT COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS"
                        + " WHERE TABLE_NAME = 'GROUPS' AND COLUMN_NAME IN ('NAME', 'NAME_KEY') ORDER BY COLUMN_NAME");

        assertThat(constraints)
                .contains("FK_GROUP_MEMBERS_GROUP", "FK_GROUP_MEMBERS_USER", "PK_GROUP_MEMBERS", "UK_GROUPS_NAME_KEY");
        assertThat(indexColumns).containsExactly("USER_ID");
        assertThat(lengths)
                .containsExactly(
                        Map.of("COLUMN_NAME", "NAME", "CHARACTER_MAXIMUM_LENGTH", 128L),
                        Map.of("COLUMN_NAME", "NAME_KEY", "CHARACTER_MAXIMUM_LENGTH", 256L));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM groups", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_members", Integer.class))
                .isZero();
    }
}
