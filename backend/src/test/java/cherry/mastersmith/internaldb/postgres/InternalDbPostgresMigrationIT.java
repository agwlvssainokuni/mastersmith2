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
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresDatabase;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.MigrationObjectNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 内部DB を実際の PostgreSQL（コンテナ）で動かす経路の移行・起動の結合テスト（FR2.1・FR2.3・FR2.5・FR3.2・FR3.3・FR3.6・NFR4.11・
 * BR2.11）。専用の空のデータベースに、本番と同じ設定でアプリを起動する。
 */
@InternalDbPostgresIT
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InternalDbPostgresMigrationIT {

    private InternalDbPostgresDatabase database;

    private ConfigurableApplicationContext context;

    private JdbcTemplate jdbc;

    @BeforeAll
    void startApplication() {
        database = InternalDbPostgresDatabase.create();
        context = new SpringApplicationBuilder(MastersmithApplication.class).run(database.applicationArgs());
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    }

    @AfterAll
    void stopApplication() {
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    @Test
    @DisplayName("V1 to V14 are recorded as successful in flyway_schema_history")
    void allMigrationsAreRecorded() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL"
                        + " ORDER BY installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
    }

    @Test
    @DisplayName("every table, constraint and index name written in the H2 migrations exists in the PostgreSQL catalog")
    void namesFromTheH2MigrationsExist() {
        Set<String> expected = new TreeSet<>();
        MigrationObjectNames.load("db/migration")
                .values()
                .forEach(file -> expected.addAll(MigrationObjectNames.objectNames(file.sql())));
        Set<String> actual = new TreeSet<>();
        actual.addAll(jdbc.queryForList(
                "SELECT lower(relname) FROM pg_class WHERE relnamespace = current_schema()::regnamespace",
                String.class));
        actual.addAll(jdbc.queryForList(
                "SELECT lower(conname) FROM pg_constraint WHERE connamespace = current_schema()::regnamespace",
                String.class));

        List<String> missing = new ArrayList<>(expected);
        missing.removeAll(actual);

        assertThat(expected).isNotEmpty();
        assertThat(missing)
                .as("names written in the H2 migrations but missing in PostgreSQL")
                .isEmpty();
    }

    @Test
    @DisplayName("the application starts with ddl-auto validate on the migrated schema")
    void startsWithValidate() {
        assertThat(context.isRunning()).isTrue();
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");
    }

    @Test
    @DisplayName("the four binary columns are bytea")
    void binaryColumnsAreBytea() {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT table_name, column_name, data_type FROM information_schema.columns"
                        + " WHERE table_schema = current_schema() AND ("
                        + "(table_name = 'refresh_tokens' AND column_name = 'token_hash')"
                        + " OR (table_name = 'invitations' AND column_name = 'token_hash')"
                        + " OR (table_name = 'dsl_previews' AND column_name = 'yaml_bytes')"
                        + " OR (table_name = 'dsl_applied_revisions' AND column_name = 'yaml_bytes'))");

        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(row -> assertThat(row).containsEntry("data_type", "bytea"));
    }

    @Test
    @DisplayName("every pooled connection of the internal DB has lock_timeout set to 2 seconds")
    void lockTimeoutIsTwoSeconds() {
        assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("2s");
    }

    @Test
    @DisplayName("the Hikari pool of the internal DB exposes hikaricp.connections with the pool name tag")
    void poolMetricsAreExposed() {
        MeterRegistry registry = context.getBean(MeterRegistry.class);

        assertThat(registry.find("hikaricp.connections")
                        .tag("pool", "mastersmith-db")
                        .meters())
                .isNotEmpty();
    }

    @Test
    @DisplayName("the initial administrator is created once and a second startup on the same database adds none")
    void initialAdministratorIsCreatedOnce(CapturedOutput output) {
        String password = "初期管理者-" + TestDatabase.randomSecret();
        try (InternalDbPostgresDatabase own = InternalDbPostgresDatabase.create()) {
            List<String> args = new ArrayList<>(List.of(own.applicationArgs()));
            args.add("--mastersmith.auth.initial-admin.email=admin@example.com");
            args.add("--mastersmith.auth.initial-admin.password=" + password);
            try (ConfigurableApplicationContext first =
                    new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new))) {
                assertThat(first.isRunning()).isTrue();
            }
            try (ConfigurableApplicationContext second =
                    new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new))) {
                JdbcTemplate secondJdbc = new JdbcTemplate(second.getBean(DataSource.class));
                assertThat(secondJdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                        .isEqualTo(1);
                assertThat(secondJdbc.queryForObject(
                                "SELECT COUNT(*) FROM users WHERE email = 'admin@example.com' AND admin_flag",
                                Integer.class))
                        .isEqualTo(1);
            }
            assertThat(output.getAll()).doesNotContain(password);
            assertThat(JsonLogRecords.parse(output.getOut()))
                    .filteredOn(record -> "Internal DB kind resolved".equals(record.get("message")))
                    .isNotEmpty()
                    .allSatisfy(record -> assertThat(record).containsEntry("internalDbKind", "POSTGRESQL"));
        }
    }
}
