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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.internaldb.platform.StartupFailureKind;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresDatabase;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.InternalDbStartupProbe;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 内部DB を PostgreSQL で動かす経路の、移行の失敗の結合テスト（FR2.4・FR3.3・BR2.10・NFR4.x）。起動が止まり、ERROR がちょうど 1 件で、
 * 失敗の区分と版の番号が出て、接続先・利用者・パスワードが出ない。外の接続が表を握ったときは、接続ごとの {@code lock_timeout}（2 秒）で
 * 切れる（無限に待たない）。切れるまでの時間は、ログの時刻から測る。
 */
@InternalDbPostgresIT
class InternalDbPostgresMigrationFailureIT {

    private static final String MESSAGE = "Internal DB startup failed";

    /** 前もって行う移行（このテスト自身の出力）と、アプリの起動の出力を分ける印。 */
    private static final String APP_MARKER = "INTERNAL_DB_PG_APP_START_MARKER";

    private static List<Map<String, Object>> startupErrors(CapturedOutput output) {
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> "ERROR".equals(record.get("level")) && MESSAGE.equals(record.get("message")))
                .toList();
    }

    /** 指定の版まで、PostgreSQL 用の移行を当てておき、これ以降がアプリの起動の出力であることを印で分ける。 */
    private static void migrateUpTo(InternalDbPostgresDatabase database, String version) {
        Flyway.configure()
                .dataSource(database.jdbcUrl(), database.user(), database.password())
                .locations("classpath:db/migration-postgresql")
                .target(version)
                .load()
                .migrate();
        System.out.println(APP_MARKER);
    }

    private static String appOutput(CapturedOutput output) {
        String all = output.getAll();
        int at = all.indexOf(APP_MARKER);
        return at < 0 ? all : all.substring(at);
    }

    private static void assertNoSecrets(InternalDbPostgresDatabase database, CapturedOutput output, Throwable failure) {
        // 利用者名とパスワードは、どのロガーの出力にも出ない。
        for (String secret : List.of(database.user(), database.password())) {
            assertThat(appOutput(output)).as("log output of every logger").doesNotContain(secret);
        }
        // 接続先の URL とデータベース名は、Flyway の起動時の INFO 1 行（org.flywaydb.core.FlywayExecutor の
        // "Database: <URL> (<製品と版>)"。H2 でも同じ。報告に書く）を除いて、ログにも例外の連なりにも出ない。
        String withoutFlywayBanner = appOutput(output)
                .lines()
                .filter(line -> !line.contains("\"logger\":\"org.flywaydb.core.FlywayExecutor\""))
                .reduce("", (a, b) -> a + b + "\n");
        for (String value : List.of(database.name(), database.jdbcUrl())) {
            assertThat(withoutFlywayBanner)
                    .as("log output except the Flyway banner")
                    .doesNotContain(value);
        }
        for (String value : List.of(database.name(), database.user(), database.password(), database.jdbcUrl())) {
            assertThat(InternalDbStartupProbe.chainText(failure))
                    .as("exception chain")
                    .doesNotContain(value);
        }
    }

    /** ログの時刻の差（最初に当たった「始め」の記録から、最初に当たった「終わり」の記録まで）。 */
    private static Duration elapsedBetween(
            CapturedOutput output, Predicate<Map<String, Object>> from, Predicate<Map<String, Object>> to) {
        List<Map<String, Object>> records = JsonLogRecords.parse(appOutput(output));
        Map<String, Object> start = records.stream().filter(from).findFirst().orElseThrow();
        Map<String, Object> end = records.stream().filter(to).findFirst().orElseThrow();
        return Duration.between(OffsetDateTime.parse((String) start.get("timestamp")), OffsetDateTime.parse((String)
                end.get("timestamp")));
    }

    private static boolean messageContains(Map<String, Object> record, String logger, String text) {
        return String.valueOf(record.get("logger")).contains(logger)
                && String.valueOf(record.get("message")).contains(text);
    }

    @Test
    @DisplayName(
            "a table that a migration creates already existing stops the start with MIGRATION_FAILED and its version")
    void existingTableStopsTheMigration(CapturedOutput output) throws SQLException {
        try (InternalDbPostgresDatabase database = InternalDbPostgresDatabase.create()) {
            migrateUpTo(database, "2");
            try (Connection connection = database.connect();
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE refresh_tokens (id INTEGER)");
            }

            Throwable failure = InternalDbStartupProbe.startExpectingFailure(database.datasourceArgs());

            assertThat(InternalDbStartupProbe.findStartupException(failure))
                    .get()
                    .satisfies(e -> assertThat(e.failureKind()).isEqualTo(StartupFailureKind.MIGRATION_FAILED));
            List<Map<String, Object>> errors = startupErrors(output);
            assertThat(errors).as("exactly one startup ERROR").hasSize(1);
            assertThat(errors.get(0))
                    .containsEntry("failureKind", "MIGRATION_FAILED")
                    .containsEntry("migrationVersion", 3)
                    .containsEntry("sqlState", "42P07");
            assertNoSecrets(database, output, failure);
        }
    }

    @Test
    @DisplayName(
            "a table locked by another connection fails the migration statement in the 2 second range with its version")
    void lockedTargetTableFailsTheMigrationStatement(CapturedOutput output) throws SQLException {
        try (InternalDbPostgresDatabase database = InternalDbPostgresDatabase.create()) {
            migrateUpTo(database, "13");
            Throwable failure;
            try (Connection holder = database.connect()) {
                hold(holder, "users");
                failure = InternalDbStartupProbe.startExpectingFailure(database.datasourceArgs());
                holder.rollback();
            }

            assertThat(InternalDbStartupProbe.findStartupException(failure))
                    .get()
                    .satisfies(e -> assertThat(e.failureKind()).isEqualTo(StartupFailureKind.MIGRATION_FAILED));
            List<Map<String, Object>> errors = startupErrors(output);
            assertThat(errors).as("exactly one startup ERROR").hasSize(1);
            assertThat(errors.get(0))
                    .containsEntry("failureKind", "MIGRATION_FAILED")
                    .containsEntry("migrationVersion", 14)
                    .containsEntry("sqlState", "55P03");
            Duration waited = elapsedBetween(
                    output,
                    record -> messageContains(record, "DbMigrate", "Migrating schema \"public\" to version \"14"),
                    record -> messageContains(record, "StartupFailureReporter", MESSAGE));
            System.out.println("INTERNAL_DB_PG_LOCK_TARGET migrationStatementToFailureMs=" + waited.toMillis());
            assertThat(waited)
                    .as("from the start of V14 to the failure (lock_timeout is 2 seconds)")
                    .isBetween(Duration.ofMillis(1900), Duration.ofMillis(3900));
            assertNoSecrets(database, output, failure);

            // 握りを解くと、次の起動は通り、V14 まで当たる。
            try (ConfigurableApplicationContext context =
                    new SpringApplicationBuilder(MastersmithApplication.class).run(database.applicationArgs())) {
                JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                assertThat(jdbc.queryForObject(
                                "SELECT COUNT(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL",
                                Integer.class))
                        .isEqualTo(14);
            }
        }
    }

    @Test
    @DisplayName("the schema history table locked by another connection fails in the 2 second range without a version")
    void lockedHistoryTableFailsWithoutAVersion(CapturedOutput output) throws SQLException {
        try (InternalDbPostgresDatabase database = InternalDbPostgresDatabase.create()) {
            migrateUpTo(database, "13");
            Throwable failure;
            try (Connection holder = database.connect()) {
                hold(holder, "flyway_schema_history");
                failure = InternalDbStartupProbe.startExpectingFailure(database.datasourceArgs());
                holder.rollback();
            }

            assertThat(InternalDbStartupProbe.findStartupException(failure))
                    .get()
                    .satisfies(e -> assertThat(e.failureKind()).isEqualTo(StartupFailureKind.MIGRATION_FAILED));
            List<Map<String, Object>> errors = startupErrors(output);
            assertThat(errors).as("exactly one startup ERROR").hasSize(1);
            assertThat(errors.get(0))
                    .containsEntry("failureKind", "MIGRATION_FAILED")
                    .containsEntry("sqlState", "55P03")
                    .doesNotContainKey("migrationVersion");
            Duration waited = elapsedBetween(
                    output,
                    record -> messageContains(record, "FlywayExecutor", "Database: "),
                    record -> messageContains(record, "StartupFailureReporter", MESSAGE));
            System.out.println("INTERNAL_DB_PG_LOCK_HISTORY flywayStartToFailureMs=" + waited.toMillis());
            assertThat(waited)
                    .as("from the start of Flyway to the failure (lock_timeout is 2 seconds)")
                    .isBetween(Duration.ofMillis(1900), Duration.ofMillis(3900));
            assertNoSecrets(database, output, failure);
        }
    }

    /** 外の接続が、開いたトランザクションの中で表を排他で握る。 */
    private static void hold(Connection holder, String table) throws SQLException {
        holder.setAutoCommit(false);
        try (Statement statement = holder.createStatement()) {
            statement.execute("LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE");
        }
    }
}
