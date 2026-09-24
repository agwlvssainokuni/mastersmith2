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
package cherry.mastersmith.targetdb.config;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.health.TimeBoundedDbHealthIndicator;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.service.TargetSchemaReader;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 対象DB の設定と起動の結合テスト（US6.1 の AC6.1.1・AC6.1.2・AC6.1.5、BR1.2・BR1.3・BR1.5・BR1.7、NFR7.1〜NFR7.4、ADR-006）。
 *
 * <p>内部DB は組み込みの H2 を使い、コンテナは使わない。対象DB は、届かない先（手元の閉じた番号）を設定して確かめる。
 * 開発者の環境変数に対象DB の設定があっても影響しないよう、7つの項目を起動の引数で明示する。
 */
@ExtendWith(OutputCaptureExtension.class)
class TargetDbStartupIT {

    private static final String CONFIG_LOGGER = TargetDataSourceConfig.class.getName();

    @TempDir
    Path tempDir;

    /** 7つの項目を指定して起動する（null の項目は空の値にする）。 */
    private static ConfigurableApplicationContext start(Path dir, Map<String, String> target) {
        List<String> args = new ArrayList<>();
        args.add("--spring.datasource.url=" + TestDatabase.url(dir));
        args.add("--server.port=0");
        args.add("--mastersmith.auth.password.bcrypt-cost=4");
        for (String item : List.of("type", "host", "port", "database", "schema", "username", "password")) {
            args.add("--mastersmith.target-db." + item + "=" + target.getOrDefault(item, ""));
        }
        return new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new));
    }

    /** 閉じた（何も待ち受けていない）手元の番号を返す。 */
    static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
    }

    private static void assertHealthIsOnlyUp(ConfigurableApplicationContext context) {
        HttpResponse<String> health = new HttpTestClient(port(context)).get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(health)).containsOnlyKeys("status").containsEntry("status", "UP");
    }

    private static String jdbcUrl(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(HikariDataSource.class).getJdbcUrl();
    }

    private static List<Map<String, Object>> configWarnings(CapturedOutput output) {
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> CONFIG_LOGGER.equals(record.get("logger")))
                .toList();
    }

    @Test
    @DisplayName("without any target database setting the application starts, answers health and accepts a login")
    void startsWithoutSetting(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start(tempDir, Map.of())) {
            assertThat(context.containsBean(TargetDataSourceConfig.TARGET_DATA_SOURCE))
                    .isFalse();
            assertThat(context.getBean(TargetDbSettings.class)).isInstanceOf(TargetDbSettings.NotConfigured.class);
            assertThat(context.getBean(TargetSchemaReader.class).readSchema(ReadPurpose.GENERATE))
                    .isEqualTo(TargetSchemaResult.unconfigured());
            assertHealthIsOnlyUp(context);

            String email = "member-" + UUID.randomUUID() + "@example.com";
            String password = "テスト用パスワード-" + TestDatabase.randomSecret();
            context.getBean(UserAccountService.class).createUser(email, new Password(password), false);
            assertThat(new AuthApi(port(context)).login(email, password).statusCode())
                    .isEqualTo(200);
        }
        assertThat(configWarnings(output)).as("設定が無いときは WARN を出さない").isEmpty();
    }

    @Test
    @DisplayName("with an unreachable target database the application starts and never connects to it")
    void startsWithUnreachableTarget(CapturedOutput output) throws IOException {
        String password = TestDatabase.randomSecret();
        Map<String, String> target = Map.of(
                "type", "postgresql",
                "host", "127.0.0.1",
                "port", Integer.toString(closedPort()),
                "database", "business",
                "schema", "public",
                "username", "reader",
                "password", password);
        try (ConfigurableApplicationContext context = start(tempDir, target)) {
            HikariDataSource targetDataSource =
                    context.getBean(TargetDataSourceConfig.TARGET_DATA_SOURCE, HikariDataSource.class);
            assertThat(context.getBean(TargetDbSettings.class)).isInstanceOf(TargetDbSettings.Usable.class);

            assertHealthIsOnlyUp(context);

            assertThat(targetDataSource.getHikariPoolMXBean())
                    .as("対象DB のプールは始まっていない（一度も接続していない）")
                    .isNull();
        }
        assertThat(configWarnings(output)).isEmpty();
        assertThat(output.getOut()).doesNotContain("mastersmith-target-db - Start");
        JsonLogRecords.assertContainsNoSecret(output.getAll(), password);
    }

    @Test
    @DisplayName("a missing schema setting logs one warning naming the item and the application starts")
    void missingItemWarnsOnce(CapturedOutput output) {
        String host = "h" + TestDatabase.randomSecret();
        String username = "u" + TestDatabase.randomSecret();
        String password = TestDatabase.randomSecret();
        Map<String, String> target = Map.of(
                "type",
                "mysql",
                "host",
                host,
                "port",
                "3306",
                "database",
                "business",
                "username",
                username,
                "password",
                password);
        try (ConfigurableApplicationContext context = start(tempDir, target)) {
            assertThat(context.containsBean(TargetDataSourceConfig.TARGET_DATA_SOURCE))
                    .isFalse();
            assertThat(context.getBean(TargetSchemaReader.class).readSchema(ReadPurpose.COMPARE))
                    .isEqualTo(TargetSchemaResult.unconfigured());
            assertHealthIsOnlyUp(context);
        }
        assertThat(configWarnings(output)).singleElement().satisfies(record -> {
            assertThat(record).containsEntry("level", "WARN").containsEntry("items", "mastersmith.target-db.schema");
        });
        JsonLogRecords.assertContainsNoSecret(output.getAll(), host, username, password);
    }

    @Test
    @DisplayName("an unsupported database type logs one warning naming the item and the application starts")
    void unsupportedTypeWarnsOnce(CapturedOutput output) {
        String host = "h" + TestDatabase.randomSecret();
        String password = TestDatabase.randomSecret();
        Map<String, String> target = Map.of(
                "type", "oracle",
                "host", host,
                "port", "1521",
                "database", "business",
                "schema", "sales",
                "username", "reader",
                "password", password);
        try (ConfigurableApplicationContext context = start(tempDir, target)) {
            assertThat(context.containsBean(TargetDataSourceConfig.TARGET_DATA_SOURCE))
                    .isFalse();
            assertThat(context.getBean(TargetDbSettings.class)).isInstanceOf(TargetDbSettings.Invalid.class);
        }
        assertThat(configWarnings(output))
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("level", "WARN")
                        .containsEntry("items", "mastersmith.target-db.type"));
        JsonLogRecords.assertContainsNoSecret(output.getAll(), host, password);
    }

    @Test
    @DisplayName("the injected DataSource, Flyway, JPA transactions and health all keep using the internal database")
    void internalDatabaseStaysDefault() throws Exception {
        Map<String, String> target = Map.of(
                "type", "mariadb",
                "host", "127.0.0.1",
                "port", Integer.toString(closedPort()),
                "database", "business",
                "schema", "business",
                "username", "reader",
                "password", TestDatabase.randomSecret());
        try (ConfigurableApplicationContext context = start(tempDir, target)) {
            String internal = TestDatabase.url(tempDir);

            assertThat(context.getBeansOfType(DataSource.class)).containsKey(TargetDataSourceConfig.TARGET_DATA_SOURCE);
            assertThat(jdbcUrl(context.getBean(DataSource.class))).isEqualTo(internal);
            assertThat(jdbcUrl(context.getBean(Flyway.class).getConfiguration().getDataSource()))
                    .isEqualTo(internal);
            assertThat(jdbcUrl(context.getBean(JpaTransactionManager.class).getDataSource()))
                    .isEqualTo(internal);
            DataSource healthDataSource = (DataSource)
                    ReflectionTestUtils.getField(context.getBean(TimeBoundedDbHealthIndicator.class), "dataSource");
            assertThat(jdbcUrl(healthDataSource)).isEqualTo(internal);
            assertThat(jdbcUrl(context.getBean(TargetDataSourceConfig.TARGET_DATA_SOURCE, DataSource.class)))
                    .startsWith("jdbc:mariadb://127.0.0.1:");
            assertThat(context.getBean(Flyway.class).info().applied()).isNotEmpty();
        }
    }
}
