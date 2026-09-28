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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V7 の後方互換の自動の確かめ（NFR10.3・NFR10.4、{@code reliability-design.md} 6.2 の (1)）。
 *
 * <p>今の Flyway で V7 まで当てた内部DB に対して、V6 までしか知らない Flyway（1つ前の版のアプリの設定と同じく、移行の置き場は V1〜V6
 * の複写、{@code validate-on-migrate} は有効）が失敗しないことと、1つ前の版のアプリの追記の形の既知の限界を固定する。Hibernate の
 * {@code validate} が余分な列を許すことは、1つ前の版のエンティティを持てないため、ここでは確かめない（戻しの練習で確かめる）。
 */
class V7BackwardCompatibilityIT {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateToV7() {
        // V8 以降（Intent 260925-user-management の U3）が足されても V7 の後の状態で確かめるため、版を 7 までに止める。
        MigrateResult result = V7MigrationIT.configure(tempDir, V7MigrationIT.CURRENT)
                .target("7")
                .load()
                .migrate();
        assertThat(result.targetSchemaVersion).isEqualTo("7");
        jdbc = V7MigrationIT.jdbc(tempDir);
    }

    @Test
    @DisplayName("a Flyway that knows only V1 to V6 validates and migrates the V7 database without failing")
    void previousFlywayIgnoresV7() {
        Flyway previous = V7MigrationIT.flyway(tempDir, V7MigrationIT.THROUGH_V6);

        assertThatCode(previous::validate).doesNotThrowAnyException();
        MigrateResult result = previous.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '7'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "a users insert without the four columns (the previous version's shape) fails because display_name is missing")
    void previousUserInsertFails() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users (email, password_hash, admin_flag, created_at)"
                        + " VALUES ('old@example.com', '$2a$04$h', TRUE, CURRENT_TIMESTAMP)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("a users insert with only the display name gets ja, system and md from the defaults")
    void defaultsFillTheThreeColumns() {
        jdbc.update("INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)"
                + " VALUES ('new@example.com', '$2a$04$h', FALSE, CURRENT_TIMESTAMP, 'new@example.com')");

        Map<String, Object> row = jdbc.queryForMap("SELECT language, theme, font_size FROM users");

        assertThat(row)
                .containsEntry("LANGUAGE", "ja")
                .containsEntry("THEME", "system")
                .containsEntry("FONT_SIZE", "md");
    }

    @Test
    @DisplayName("an audit insert without the two target columns (the previous version's shape) still succeeds")
    void previousAuditInsertSucceeds() {
        jdbc.update("INSERT INTO audit_events (occurred_at, event_type, result, entered_email, source_ip)"
                + " VALUES (CURRENT_TIMESTAMP, 'LOGIN_FAILED', 'FAILURE', 'x@example.com', '192.0.2.1')");

        Map<String, Object> row = jdbc.queryForMap("SELECT target_user_id, target_invitation_id FROM audit_events");

        assertThat(row.get("TARGET_USER_ID")).isNull();
        assertThat(row.get("TARGET_INVITATION_ID")).isNull();
    }
}
