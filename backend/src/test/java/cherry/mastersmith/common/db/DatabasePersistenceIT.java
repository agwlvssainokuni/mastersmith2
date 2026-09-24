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
package cherry.mastersmith.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

/** 内部DB（組み込みの H2）の接続とスキーマの変更の結合テスト（FR1.2、BR2.1〜BR2.3）。 */
@ExtendWith(OutputCaptureExtension.class)
class DatabasePersistenceIT {

    @TempDir
    Path tempDir;

    private static ConfigurableApplicationContext start(String url, String... extraArgs) {
        String[] args = new String[extraArgs.length + 2];
        args[0] = "--spring.datasource.url=" + url;
        args[1] = "--server.port=0";
        System.arraycopy(extraArgs, 0, args, 2, extraArgs.length);
        return new SpringApplicationBuilder(MastersmithApplication.class).run(args);
    }

    @Test
    @DisplayName("default datasource is an embedded H2 database stored in a file without network access")
    void defaultDatasourceIsEmbeddedFileDatabase() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        String url = environment.getProperty("spring.datasource.url");

        // 停止時の詰め直し（DEFRAG_ALWAYS=TRUE）は Build and Test からの戻し Loop-back 1（U4-STORAGE）で足した。
        assertThat(url).isEqualTo("jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE");
        assertThat(url).doesNotContainIgnoringCase("AUTO_SERVER").doesNotContainIgnoringCase("tcp");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(environment.getProperty("spring.h2.console.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("data written before a restart is still present after the context is recreated")
    void dataSurvivesRestart() {
        String url = TestDatabase.url(tempDir.resolve("persist"));

        try (ConfigurableApplicationContext context = start(url)) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE test_persistence (id INT PRIMARY KEY, label VARCHAR(50))");
            jdbc.update("INSERT INTO test_persistence (id, label) VALUES (?, ?)", 1, "再起動の前に書いた値");
        }

        try (ConfigurableApplicationContext context = start(url)) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            String label = jdbc.queryForObject("SELECT label FROM test_persistence WHERE id = 1", String.class);
            assertThat(label).isEqualTo("再起動の前に書いた値");
            jdbc.execute("DROP TABLE test_persistence");
        }
    }

    @Test
    @DisplayName("switching the connection setting alone points the application to another H2 database")
    void connectionSettingSwitchesDatabase() {
        Path first = tempDir.resolve("first");
        Path second = tempDir.resolve("second");

        try (ConfigurableApplicationContext context = start(TestDatabase.url(first))) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.execute("CREATE TABLE test_switch (id INT PRIMARY KEY)");
        }

        try (ConfigurableApplicationContext context = start(TestDatabase.url(second))) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'TEST_SWITCH'", Integer.class);
            assertThat(count).isZero();
        }

        assertThat(Files.exists(first.resolve("mastersmith.mv.db"))).isTrue();
        assertThat(Files.exists(second.resolve("mastersmith.mv.db"))).isTrue();
    }

    @Test
    @DisplayName("Flyway applies the U1 baseline migration at startup")
    void flywayAppliesBaseline() {
        try (ConfigurableApplicationContext context = start(TestDatabase.url(tempDir.resolve("flyway")))) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT \"version\", \"success\" FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL");
            // 後の単位のスキーマの変更（V2 以降）も並ぶため、件数ではなく U1 の基準線が成功で入っていることを確かめる。
            assertThat(rows).contains(Map.of("version", "1", "success", true));
            Integer failed = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE NOT \"success\"", Integer.class);
            assertThat(failed).isZero();
        }
    }

    @Test
    @DisplayName("application starts with Hibernate schema validation instead of schema generation")
    void startsWithHibernateValidation() {
        try (ConfigurableApplicationContext context = start(TestDatabase.url(tempDir.resolve("validate")))) {
            assertThat(context.getBean(EntityManagerFactory.class).isOpen()).isTrue();
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                    .isEqualTo("validate");
        }
    }

    @Test
    @DisplayName("configured database password never appears in the log output")
    void passwordIsNotLogged(CapturedOutput output) {
        String password = TestDatabase.randomSecret();

        try (ConfigurableApplicationContext context =
                start(TestDatabase.url(tempDir.resolve("secret")), "--spring.datasource.password=" + password)) {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        }

        // 起動の途中（DB の接続の開始）のログまで捕まえたうえで、パスワードが出ていないことを確かめる。
        assertThat(output.getOut()).contains("mastersmith-db - Start completed");
        JsonLogRecords.assertContainsNoSecret(output.getAll(), password);
    }
}
