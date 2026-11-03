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
package cherry.mastersmith.user.repository;

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
 * V14（Intent 261102-timezone-ui-polish の U2、利用者の表の表示のタイムゾーンの列）の前の版との互換の結合テスト（H1・H2、
 * NFR4.1.1〜NFR4.1.4）。{@code AuditMigrationCompatibilityIT} と同じ形で、Spring を起動せず Flyway と移行のファイルだけを使う。
 *
 * <p>前の版のアプリは V1〜V13 だけを持つ。前の版と同じ Flyway の設定で V1〜V13 だけを置いた場所を読ませ、V14 まで当てた内部DB で止まら
 * ないこと、前の版が書いた利用者の行が V14 の後も読めて足した列が空であること、V14 の後に前の版の列だけで追記・更新できることを確かめる。
 */
class DisplayTimeZoneMigrationCompatibilityIT {

    /** 前の版が持つ最後の移行の番号。 */
    private static final int PREVIOUS_LAST_VERSION = 13;

    /** 今の版の最後の移行の番号。 */
    private static final int CURRENT_LAST_VERSION = 14;

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

    /** クラスパスの移行のうち、V1〜V13 だけを前の版の移行の場所に写す。 */
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
        assertThat(copied).as("前の版の移行 V1〜V13").isEqualTo(PREVIOUS_LAST_VERSION);
    }

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

    private void insertUserAsPreviousVersion(String email) {
        jdbc.update(
                "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name, language, theme,"
                        + " font_size, suspended) VALUES (?, 'h', FALSE, ?, ?, 'ja', 'system', 'md', FALSE)",
                email,
                OffsetDateTime.parse("2026-11-03T00:00:00Z"),
                email);
    }

    @Test
    @DisplayName("the previous version's Flyway with V1 to V13 does not stop on a database migrated to V14")
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
                .isEqualTo(CURRENT_LAST_VERSION);
    }

    @Test
    @DisplayName("a user row written by the previous version is read back after V14 with the new column null")
    void existingRowsSurviveTheMigration() {
        MigrateResult previous = previousVersion().migrate();
        assertThat(previous.migrationsExecuted).isEqualTo(PREVIOUS_LAST_VERSION);
        insertUserAsPreviousVersion("before-v14@example.com");

        MigrateResult current = currentVersion().migrate();

        assertThat(current.migrationsExecuted).isEqualTo(CURRENT_LAST_VERSION - PREVIOUS_LAST_VERSION);
        Map<String, Object> row =
                jdbc.queryForMap("SELECT email, language, theme, font_size, suspended, display_time_zone FROM users"
                        + " WHERE email = 'before-v14@example.com'");
        assertThat(row)
                .containsEntry("LANGUAGE", "ja")
                .containsEntry("THEME", "system")
                .containsEntry("FONT_SIZE", "md")
                .containsEntry("SUSPENDED", false)
                .containsEntry("DISPLAY_TIME_ZONE", null);
    }

    @Test
    @DisplayName("after V14 the previous version can still add a user with only its own columns")
    void previousVersionAddsAfterTheMigration() {
        currentVersion().migrate();

        insertUserAsPreviousVersion("after-v14@example.com");

        assertThat(jdbc.queryForObject(
                        "SELECT display_time_zone FROM users WHERE email = 'after-v14@example.com'", String.class))
                .isNull();
    }

    @Test
    @DisplayName("updating the four columns the previous version writes leaves the new column unchanged")
    void previousColumnsUpdateDoesNotTouchTheNewColumn() {
        currentVersion().migrate();
        insertUserAsPreviousVersion("keep-zone@example.com");
        jdbc.update("UPDATE users SET display_time_zone = 'Asia/Tokyo' WHERE email = 'keep-zone@example.com'");

        jdbc.update(
                "UPDATE users SET display_name = ?, language = 'en', theme = 'dark', font_size = 'lg'"
                        + " WHERE email = 'keep-zone@example.com'",
                "更新後の氏名");

        assertThat(jdbc.queryForObject(
                        "SELECT display_time_zone FROM users WHERE email = 'keep-zone@example.com'", String.class))
                .isEqualTo("Asia/Tokyo");
    }
}
