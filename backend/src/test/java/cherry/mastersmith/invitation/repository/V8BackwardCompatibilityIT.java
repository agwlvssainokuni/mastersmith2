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
package cherry.mastersmith.invitation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V8 の後方互換の自動の確かめ（NFR10.2、{@code reliability-design.md} 8節の (1)）。
 *
 * <p>今の Flyway で V8 まで当てた内部DB に対して、V7 までしか知らない Flyway（1つ前の版のアプリの設定と同じく、移行の置き場は V1〜V7
 * の複写、{@code validate-on-migrate} は有効）が失敗しないことと、1つ前の版のアプリが使う表の追記の形がそのまま通ることを固定する。
 * Hibernate の {@code validate} が余分な表を見ないことは、1つ前の版のエンティティを持てないため、戻しの練習で確かめる。
 */
class V8BackwardCompatibilityIT {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateToV8() {
        MigrateResult result =
                V8MigrationIT.flyway(tempDir, V8MigrationIT.CURRENT).migrate();
        assertThat(result.targetSchemaVersion).isEqualTo("8");
        jdbc = V8MigrationIT.jdbc(tempDir);
    }

    @Test
    @DisplayName("a Flyway that knows only V1 to V7 validates and migrates the V8 database without failing")
    void previousFlywayIgnoresV8() {
        Flyway previous = V8MigrationIT.flyway(tempDir, V8MigrationIT.THROUGH_V7);

        assertThatCode(previous::validate).doesNotThrowAnyException();
        MigrateResult result = previous.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '8'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the previous version's users and audit inserts still succeed after V8")
    void previousInsertsSucceed() {
        jdbc.update("INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)"
                + " VALUES ('old@example.com', '$2a$04$h', FALSE, CURRENT_TIMESTAMP, 'old@example.com')");
        jdbc.update("INSERT INTO audit_events (occurred_at, event_type, result, source_ip)"
                + " VALUES (CURRENT_TIMESTAMP, 'LOGIN_SUCCEEDED', 'SUCCESS', '192.0.2.1')");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("V8 changes no column of the existing tables")
    void existingTablesUnchanged() {
        Path before = tempDir.resolve("v7");
        V8MigrationIT.flyway(before, V8MigrationIT.THROUGH_V7).migrate();
        String columns = "SELECT TABLE_NAME || '.' || COLUMN_NAME || ':' || DATA_TYPE || ':' || IS_NULLABLE"
                + " FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME <> 'INVITATIONS'"
                + " AND TABLE_NAME <> 'flyway_schema_history' ORDER BY 1";

        assertThat(jdbc.queryForList(columns, String.class))
                .isEqualTo(V8MigrationIT.jdbc(before).queryForList(columns, String.class));
    }
}
