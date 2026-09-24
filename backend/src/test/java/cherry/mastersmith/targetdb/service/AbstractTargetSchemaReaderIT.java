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
package cherry.mastersmith.targetdb.service;

import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER_VIEW;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.ORDER_ITEMS;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.SYMBOL_TABLE;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.config.TargetDataSourceConfig;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.domain.TargetTable;
import cherry.mastersmith.targetdb.domain.UnavailableReason;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.SilentServer;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 読み取りの口の結合テストの共通の確かめ（US6.1 の AC6.1.3、US1.1 の AC1.1.7・AC1.1.8・AC1.1.10、US1.2 の AC1.2.1、
 * BR1.5・BR1.6・BR1.8・BR1.9、NFR1.1、NFR4.4・NFR4.6、NFR7.4）。3種類の DB の子のクラスで、同じ内容を確かめる。
 *
 * <p>アプリを起動し（内部DB は組み込みの H2）、読み取りの権限だけのアカウントでコンテナの対象DB を読む。誤ったパスワード・
 * 応答しない先はアプリを別に起動して確かめ、止めた DB は最後にコンテナを止めて確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
// 拡張の登録の順は、ログの取り込み（OutputCaptureExtension）を先、コンテナの実行環境の確かめ（ContainerRuntimeCheck）を後に
// しなければならない。JUnit 5 は前処理を登録の順、後処理を逆の順に呼ぶため、確かめを先にすると、コンテナの実行環境に届かずに
// テストを飛ばしたとき、ログの取り込みが始まらないまま後片付けの OutputCapture.pop が空の待ち行列で失敗し、飛ばしたはずのクラスが
// initializationError になる（Build and Test からの戻し Loop-back 1）。順は ExtensionOrderArchitectureTest で確かめる。
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
abstract class AbstractTargetSchemaReaderIT {

    private final TargetDbTestDatabase database = TargetDbTestDatabase.of(product());

    private final String schema = TargetDbTestDatabase.uniqueName("Sales_");

    private final String otherSchema = TargetDbTestDatabase.uniqueName("other_");

    private final String reader = TargetDbTestDatabase.uniqueName("reader_");

    private final String readerPassword = TestDatabase.randomSecret();

    /** 内部DB（H2）のファイルを置く一時ディレクトリ（クラスの始めに受け取る）。 */
    private Path tempDir;

    private ConfigurableApplicationContext app;

    private boolean stopped;

    /**
     * 確かめる DB の種類を返す。
     *
     * @return 種類
     */
    abstract DatabaseProduct product();

    @BeforeAll
    void startDatabaseAndApp(@TempDir Path dir) {
        tempDir = dir;
        database.start();
        database.createSchema(otherSchema);
        database.createSchema(schema);
        database.createStandardFixture(schema, otherSchema);
        database.createReadOnlyAccount(reader, readerPassword, schema);
        app = startApp(tempDir.resolve("main"), database.host(), database.port(), reader, readerPassword);
    }

    @AfterAll
    void stopAll() {
        try {
            if (app != null) {
                app.close();
            }
            if (!stopped) {
                database.dropAccount(reader);
                database.dropSchema(schema);
                database.dropSchema(otherSchema);
            }
        } finally {
            database.stop();
        }
    }

    private ConfigurableApplicationContext startApp(Path dir, String host, int port, String user, String password) {
        return new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        "--spring.datasource.url=" + TestDatabase.url(dir),
                        "--server.port=0",
                        "--mastersmith.auth.password.bcrypt-cost=4",
                        "--mastersmith.target-db.type=" + product().name().toLowerCase(),
                        "--mastersmith.target-db.host=" + host,
                        "--mastersmith.target-db.port=" + port,
                        "--mastersmith.target-db.database=" + database.connectDatabase(schema),
                        "--mastersmith.target-db.schema=" + schema,
                        "--mastersmith.target-db.username=" + user,
                        "--mastersmith.target-db.password=" + password);
    }

    private static TargetSchemaReader reader(ConfigurableApplicationContext context) {
        return context.getBean(TargetSchemaReader.class);
    }

    /**
     * アプリのログ（1行1件の JSON）だけを取り出す。テストの道具（Testcontainers・Docker の部品）のログは、コンテナの接続先を
     * 出すため除く。
     */
    private static String appLog(CapturedOutput output) {
        StringBuilder log = new StringBuilder();
        for (Map<String, Object> record : JsonLogRecords.parse(output.getAll())) {
            String logger = String.valueOf(record.get("logger"));
            if (logger.startsWith("tc.")
                    || logger.startsWith("org.testcontainers")
                    || logger.startsWith("com.github.dockerjava")) {
                continue;
            }
            log.append(record).append('\n');
        }
        return log.toString();
    }

    /** 結果とアプリのログに、接続先・ユーザー名・パスワード・例外が無いことを確かめる。 */
    private static void assertNoConnectionInfo(
            TargetSchemaResult result, CapturedOutput output, int port, String user, String password) {
        assertThat(result.toString())
                .doesNotContain(user)
                .doesNotContain(password)
                .doesNotContain(":" + port);
        String log = appLog(output);
        assertThat(log).as("アプリのログが捕まえられている").contains(JdbcTargetSchemaReader.class.getName());
        JsonLogRecords.assertContainsNoSecret(log, "127.0.0.1:" + port, "localhost:" + port, user, password);
        assertThat(targetDbRecords(output))
                .as("対象DB に関わるログに例外（文言とスタックトレース）が無い")
                .noneMatch(record -> record.containsKey("exception"));
    }

    /**
     * 対象DB に関わるログ（U1 のパッケージ・3つのドライバー・対象DB のプール）を返す。ほかのテストのクラスが残した後片付けの
     * ログ（外部エクスポートの送信の失敗など）は含めない。
     */
    private static List<Map<String, Object>> targetDbRecords(CapturedOutput output) {
        return JsonLogRecords.parse(output.getAll()).stream()
                .filter(record -> {
                    String logger = String.valueOf(record.get("logger"));
                    return logger.startsWith("cherry.mastersmith.targetdb")
                            || logger.startsWith("org.mariadb")
                            || logger.startsWith("org.postgresql")
                            || logger.startsWith("com.mysql")
                            || (logger.startsWith("com.zaxxer.hikari")
                                    && String.valueOf(record.get("message")).contains("mastersmith-target-db"));
                })
                .toList();
    }

    /** アプリのログのうち、読み取りの口の WARN を返す。 */
    private static List<Map<String, Object>> readerWarnings(CapturedOutput output) {
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> JdbcTargetSchemaReader.class.getName().equals(record.get("logger")))
                .toList();
    }

    @Test
    @Order(1)
    @DisplayName("both purposes read the configured schema with an account that only has read privileges")
    void readsWithReadOnlyAccount() {
        for (ReadPurpose purpose : ReadPurpose.values()) {
            TargetSchemaResult result = reader(app).readSchema(purpose);

            assertThat(result).isInstanceOfSatisfying(TargetSchemaResult.Success.class, success -> {
                assertThat(success.schema().databaseProduct()).isEqualTo(product());
                assertThat(success.schema().schemaName()).isEqualTo(schema);
                assertThat(success.schema().tables())
                        .extracting(TargetTable::name)
                        .containsExactlyInAnyOrder(CUSTOMER, ORDER_ITEMS, SYMBOL_TABLE, CUSTOMER_VIEW);
            });
        }
    }

    @Test
    @Order(2)
    @DisplayName("the target connection is read-only, pooled apart from the internal one and at most 5 connections")
    void connectionIsReadOnly() throws SQLException {
        HikariDataSource target = app.getBean(TargetDataSourceConfig.TARGET_DATA_SOURCE, HikariDataSource.class);
        try (Connection connection = target.getConnection()) {
            assertThat(connection.isReadOnly()).isTrue();
        }
        assertThat(target.getPoolName()).isEqualTo("mastersmith-target-db");
        assertThat(target.getMaximumPoolSize()).isEqualTo(5);
        assertThat(app.getBean(DataSource.class).unwrap(HikariDataSource.class).getPoolName())
                .isEqualTo("mastersmith-db");
    }

    @Test
    @Order(3)
    @DisplayName("login and audit use the internal database and no application table is created in the target database")
    void nothingIsWrittenToTheTarget() throws SQLException {
        String email = "member-" + UUID.randomUUID() + "@example.com";
        String password = "テスト用パスワード-" + TestDatabase.randomSecret();
        app.getBean(UserAccountService.class).createUser(email, new Password(password), false);
        int port = Integer.parseInt(app.getEnvironment().getRequiredProperty("local.server.port"));

        assertThat(new AuthApi(port).login(email, password).statusCode()).isEqualTo(200);
        assertThat(new JdbcTemplate(app.getBean(DataSource.class))
                        .queryForObject(
                                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'LOGIN_SUCCEEDED'",
                                Integer.class))
                .as("監査の行は内部DB にある")
                .isPositive();
        try (Connection connection = database.admin();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE LOWER(table_name) IN ('flyway_schema_history', 'users', 'audit_events',"
                        + " 'refresh_tokens', 'login_attempt_states')"
                        // DB の持つ仕組みの表（MySQL・MariaDB の performance_schema.users など）は除く。
                        + " AND LOWER(table_schema) NOT IN"
                        + " ('mysql', 'performance_schema', 'information_schema', 'sys', 'pg_catalog')")) {
            rs.next();
            assertThat(rs.getInt(1)).as("対象DB に Flyway の履歴の表もアプリの表も無い").isZero();
        }
    }

    @Test
    @Order(4)
    @DisplayName("a wrong password gives CONNECTION_FAILED without the host, user name, password or driver message")
    void wrongPassword(CapturedOutput output) {
        String wrong = TestDatabase.randomSecret();
        TargetSchemaResult result;
        try (ConfigurableApplicationContext other =
                startApp(tempDir.resolve("wrong"), "127.0.0.1", database.port(), reader, wrong)) {
            result = reader(other).readSchema(ReadPurpose.GENERATE);
        }

        assertThat(result).isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.CONNECTION_FAILED));
        assertNoConnectionInfo(result, output, database.port(), reader, wrong);
        assertThat(appLog(output)).doesNotContain(authenticationFailureText());
        assertThat(readerWarnings(output))
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("level", "WARN")
                        .containsEntry("reason", "CONNECTION_FAILED")
                        .doesNotContainKey("exception"));
    }

    /** 認証の失敗のときにドライバーが例外の文言に入れる語（ログに出ないことを確かめる）。 */
    abstract String authenticationFailureText();

    @Test
    @Order(5)
    @DisplayName("a server that accepts but never answers gives TIMEOUT after the 3 second connection wait")
    void unresponsiveServer(CapturedOutput output) {
        try (SilentServer silent = SilentServer.start();
                ConfigurableApplicationContext other =
                        startApp(tempDir.resolve("silent"), "127.0.0.1", silent.port(), reader, readerPassword)) {
            long start = System.nanoTime();
            TargetSchemaResult result = reader(other).readSchema(ReadPurpose.COMPARE);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(result).isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.TIMEOUT));
            assertThat(elapsed).as("接続の待ち 3 秒で打ち切る").isBetween(2_900L, 8_000L);
            assertNoConnectionInfo(result, output, silent.port(), reader, readerPassword);
        }
    }

    @Test
    @Order(6)
    @DisplayName("after the database stops the same application gives CONNECTION_FAILED")
    void stoppedDatabase(CapturedOutput output) {
        int port = database.port();
        stopped = true;
        database.stop();

        TargetSchemaResult result = reader(app).readSchema(ReadPurpose.GENERATE);

        assertThat(result).isEqualTo(TargetSchemaResult.unavailable(UnavailableReason.CONNECTION_FAILED));
        assertNoConnectionInfo(result, output, port, reader, readerPassword);
    }
}
