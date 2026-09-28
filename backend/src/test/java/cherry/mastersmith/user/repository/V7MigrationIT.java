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
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * スキーマの変更 V7 の結合テスト（BR9.1・BR9.2、NFR10.1・NFR10.2、{@code reliability-design.md} 6.2 の (1)）。
 *
 * <p>Spring を起動せず、組み込みの H2 の一時のファイルに Flyway の API で当てる。V6 までの状態は、テストの資源に置いた V1〜V6 の複写
 * （{@code db/migration-through-v6}）で作る。複写が本番の V1〜V6 と1バイトも違わないことも確かめる（片方だけの変更を見逃さないため）。
 */
class V7MigrationIT {

    /** 本番の移行の置き場。 */
    static final String CURRENT = "classpath:db/migration";

    /** V6 までの移行の複写の置き場（1つ前の版のアプリが知る移行）。 */
    static final String THROUGH_V6 = "classpath:db/migration-through-v6";

    static final String[] V1_TO_V6 = {
        "V1__u1_baseline.sql",
        "V2__u2_user_account.sql",
        "V3__u2_authentication.sql",
        "V4__u4_audit_event.sql",
        "V5__u4_dsl_management.sql",
        "V6__u4_dsl_audit_columns.sql"
    };

    @TempDir
    Path tempDir;

    static String url(Path dir) {
        return "jdbc:h2:file:" + dir.toAbsolutePath().resolve("mastersmith");
    }

    static Flyway flyway(Path dir, String location) {
        return configure(dir, location).load();
    }

    /**
     * 移行の設定を作る（版の上限を足すときに使う）。
     *
     * @param dir 内部DB の置き場
     * @param location 移行の置き場
     * @return 移行の設定
     */
    static FluentConfiguration configure(Path dir, String location) {
        return Flyway.configure()
                .dataSource(url(dir), "sa", "")
                .locations(location)
                .validateOnMigrate(true);
    }

    static JdbcTemplate jdbc(Path dir) {
        return new JdbcTemplate(new DriverManagerDataSource(url(dir), "sa", ""));
    }

    private static byte[] resource(String path) {
        try (InputStream in = V7MigrationIT.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("資源 %s が無い", path).isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("the copies of V1 to V6 in the test resources are byte-for-byte identical to the production files")
    void copiesAreIdentical() {
        for (String file : V1_TO_V6) {
            assertThat(resource("db/migration-through-v6/" + file))
                    .as(file)
                    .isEqualTo(resource("db/migration/" + file));
        }
        assertThat(flyway(tempDir, THROUGH_V6).info().all()).hasSize(V1_TO_V6.length);
    }

    @Test
    @DisplayName(
            "V7 gives existing users the email as display name with ja, system and md, and leaves audit targets empty")
    void existingRowsGetInitialValues() {
        flyway(tempDir, THROUGH_V6).migrate();
        JdbcTemplate jdbc = jdbc(tempDir);
        jdbc.update("INSERT INTO users (email, password_hash, admin_flag, created_at)"
                + " VALUES ('admin@example.com', '$2a$04$h', TRUE, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO users (email, password_hash, admin_flag, created_at)"
                + " VALUES ('member@example.com', '$2a$04$h', FALSE, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO audit_events (occurred_at, event_type, result, source_ip)"
                + " VALUES (CURRENT_TIMESTAMP, 'LOGIN_SUCCEEDED', 'SUCCESS', '192.0.2.1')");

        // V8 以降（Intent 260925-user-management の U3）が足されても V7 だけを当てて確かめるため、版を 7 までに止める。
        MigrateResult result = configure(tempDir, CURRENT).target("7").load().migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(result.targetSchemaVersion).isEqualTo("7");
        assertThat(jdbc.queryForList(
                        "SELECT email, display_name, language, theme, font_size FROM users ORDER BY user_id"))
                .extracting(row -> row.get("EMAIL") + "|" + row.get("DISPLAY_NAME") + "|" + row.get("LANGUAGE") + "|"
                        + row.get("THEME") + "|" + row.get("FONT_SIZE"))
                .containsExactly(
                        "admin@example.com|admin@example.com|ja|system|md",
                        "member@example.com|member@example.com|ja|system|md");
        Map<String, Object> audit = jdbc.queryForMap("SELECT target_user_id, target_invitation_id FROM audit_events");
        assertThat(audit.get("TARGET_USER_ID")).isNull();
        assertThat(audit.get("TARGET_INVITATION_ID")).isNull();
    }

    @Test
    @DisplayName("migrating again applies V7 only once")
    void appliedOnlyOnce() {
        flyway(tempDir, CURRENT).migrate();

        MigrateResult again = flyway(tempDir, CURRENT).migrate();

        assertThat(again.migrationsExecuted).isZero();
        assertThat(jdbc(tempDir)
                        .queryForObject(
                                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '7' AND \"success\"",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the display name column is required without a default while the other three have defaults")
    void columnDefinitions() {
        flyway(tempDir, CURRENT).migrate();

        Map<String, Map<String, Object>> columns = new java.util.HashMap<>();
        for (Map<String, Object> row : jdbc(tempDir)
                .queryForList("SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT, CHARACTER_MAXIMUM_LENGTH"
                        + " FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'USERS'")) {
            columns.put((String) row.get("COLUMN_NAME"), row);
        }
        assertThat(columns.get("DISPLAY_NAME"))
                .containsEntry("IS_NULLABLE", "NO")
                .containsEntry("CHARACTER_MAXIMUM_LENGTH", 508L);
        assertThat(columns.get("DISPLAY_NAME").get("COLUMN_DEFAULT")).isNull();
        assertThat(columns.get("LANGUAGE")).containsEntry("IS_NULLABLE", "NO").containsEntry("COLUMN_DEFAULT", "'ja'");
        assertThat(columns.get("THEME")).containsEntry("IS_NULLABLE", "NO").containsEntry("COLUMN_DEFAULT", "'system'");
        assertThat(columns.get("FONT_SIZE")).containsEntry("IS_NULLABLE", "NO").containsEntry("COLUMN_DEFAULT", "'md'");
    }
}
