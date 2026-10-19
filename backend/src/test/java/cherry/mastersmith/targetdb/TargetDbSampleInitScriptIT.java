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
package cherry.mastersmith.targetdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.TargetDbImages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 見本の対象DB の初期化の台本（{@code docker/targetdb/<種類>/}）の結合テスト（Intent 261016-business-data-views の U2、計画の Step 26、
 * 基盤の設計 Q6: A、BR13.4、NFR4.3・NFR4.16、基盤の設計のレビューの R-02・R-03）。3種類の DB を、compose と同じ版のイメージで起動する。
 *
 * <ul>
 *   <li>(a) リポジトリの台本（01・02・03）を初期化の場所に写して起動し、書き込みのアカウント {@code mastersmith_writer} は見本の表の
 *       行の操作が通り、DDL（CREATE・DROP・ALTER・TRUNCATE・INDEX・VIEW・SCHEMA）と一時の表は拒まれ、読み取りのアカウントでは書けない
 *   <li>(b) 01・02 だけで起動し、03 を {@code exec} で2回流して同じ結果になり、パスワードを替えて流し直すと新しいパスワードで入れて
 *       古いパスワードでは入れない（既にあるボリュームへの足し方。README）
 * </ul>
 *
 * <p>パスワードはテストの中の乱数。値は環境変数で渡し、コマンド行に出さない。
 */
@ExtendWith(ContainerRuntimeCheck.class)
class TargetDbSampleInitScriptIT {

    private static final String WRITER = "mastersmith_writer";

    private static final String READER = "mastersmith_reader";

    private static final String INIT_DIR = "/docker-entrypoint-initdb.d/";

    /** (b) で 03 を置く、初期化の場所の外の置き場。 */
    private static final String LATER_DIR = "/mastersmith-later/";

    /** リポジトリの台本の置き場を、テストの作業ディレクトリ（backend）から上へたどって探す。 */
    private static Path scripts(DatabaseProduct product) {
        String kind =
                switch (product) {
                    case POSTGRESQL -> "postgres";
                    case MYSQL -> "mysql";
                    case MARIADB -> "mariadb";
                };
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("docker").resolve("targetdb").resolve(kind);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("docker/targetdb が見つかりません");
    }

    private static JdbcDatabaseContainer<?> container(
            DatabaseProduct product, String admin, String reader, String writer, boolean withWriterScript) {
        JdbcDatabaseContainer<?> container =
                switch (product) {
                    case POSTGRESQL ->
                        new PostgreSQLContainer(TargetDbImages.postgres())
                                .withDatabaseName("business")
                                .withUsername("target_admin")
                                .withPassword(admin);
                    case MYSQL ->
                        new MySQLContainer(TargetDbImages.mysql())
                                .withDatabaseName("business")
                                .withPassword(admin);
                    case MARIADB ->
                        new MariaDBContainer(TargetDbImages.mariadb())
                                .withDatabaseName("business")
                                .withPassword(admin);
                };
        Path dir = scripts(product);
        String schema = "01-sample-schema.sql";
        container
                .withEnv("MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD", admin)
                .withEnv("MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD", reader)
                .withEnv("MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD", writer)
                .withCopyFileToContainer(MountableFile.forHostPath(dir.resolve(schema), 0644), INIT_DIR + schema)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(dir.resolve("02-reader-account.sh"), 0755),
                        INIT_DIR + "02-reader-account.sh")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(dir.resolve("03-writer-account.sh"), 0755),
                        (withWriterScript ? INIT_DIR : LATER_DIR) + "03-writer-account.sh");
        return container;
    }

    private static Connection connect(
            JdbcDatabaseContainer<?> container, DatabaseProduct product, String user, String password)
            throws SQLException {
        String scheme =
                switch (product) {
                    case POSTGRESQL -> "postgresql";
                    case MYSQL -> "mysql";
                    case MARIADB -> "mariadb";
                };
        int port = container.getMappedPort(product == DatabaseProduct.POSTGRESQL ? 5432 : 3306);
        return DriverManager.getConnection(
                "jdbc:" + scheme + "://" + container.getHost() + ":" + port + "/business", user, password);
    }

    private static String table(DatabaseProduct product, String name) {
        return product == DatabaseProduct.POSTGRESQL ? "sales." + name : "business." + name;
    }

    /** 書き込みのアカウントで、見本の表の行の操作が通り、DDL と一時の表が拒まれることを確かめる。 */
    private static void assertWriterRights(JdbcDatabaseContainer<?> container, DatabaseProduct product, String password)
            throws SQLException {
        String customers = table(product, "customers");
        try (Connection connection = connect(container, product, WRITER, password);
                Statement statement = connection.createStatement()) {
            assertThat(statement.executeUpdate("INSERT INTO " + customers + " (customer_id, name) VALUES (90, '書込')"))
                    .isEqualTo(1);
            assertThat(statement.executeUpdate("UPDATE " + customers + " SET note = 'x' WHERE customer_id = 90"))
                    .isEqualTo(1);
            assertThat(statement.executeUpdate("DELETE FROM " + customers + " WHERE customer_id = 90"))
                    .isEqualTo(1);
            List<String> refused = List.of(
                    "CREATE TABLE " + table(product, "w_new") + " (id INTEGER)",
                    "DROP TABLE " + table(product, "order_items"),
                    "ALTER TABLE " + customers + " ADD COLUMN extra INTEGER",
                    "TRUNCATE TABLE " + table(product, "order_items"),
                    "CREATE INDEX ix_w_name ON " + customers + " (name)",
                    "CREATE VIEW " + table(product, "w_view") + " AS SELECT customer_id FROM " + customers,
                    product == DatabaseProduct.POSTGRESQL ? "CREATE SCHEMA w_schema" : "CREATE DATABASE w_schema",
                    product == DatabaseProduct.POSTGRESQL
                            ? "CREATE TEMPORARY TABLE w_temp (id INTEGER)"
                            : "CREATE TEMPORARY TABLE " + table(product, "w_temp") + " (id INTEGER)");
            for (String sql : refused) {
                assertThatThrownBy(() -> statement.execute(sql)).as(sql).isInstanceOf(SQLException.class);
            }
        }
    }

    private static void assertReaderCannotWrite(
            JdbcDatabaseContainer<?> container, DatabaseProduct product, String password) throws SQLException {
        try (Connection connection = connect(container, product, READER, password);
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO " + table(product, "customers") + " (customer_id, name) VALUES (91, '読取')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName(
            "the init scripts create a writer that can change rows but cannot run DDL, and the reader cannot write")
    void initScripts(DatabaseProduct product) throws Exception {
        String admin = TestDatabase.randomSecret();
        String reader = TestDatabase.randomSecret();
        String writer = TestDatabase.randomSecret();
        try (JdbcDatabaseContainer<?> container = container(product, admin, reader, writer, true)) {
            container.start();

            assertWriterRights(container, product, writer);
            assertReaderCannotWrite(container, product, reader);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("running the writer script twice on an existing database gives the same result and a new password")
    void rerunOnExistingDatabase(DatabaseProduct product) throws Exception {
        String admin = TestDatabase.randomSecret();
        String reader = TestDatabase.randomSecret();
        String writer = TestDatabase.randomSecret();
        String renewed = TestDatabase.randomSecret();
        try (JdbcDatabaseContainer<?> container = container(product, admin, reader, writer, false)) {
            container.start();
            assertThatThrownBy(() -> connect(container, product, WRITER, writer).close())
                    .as("01・02 だけでは書き込みのアカウントが無い")
                    .isInstanceOf(SQLException.class);

            ExecResult first = run(container, writer);
            ExecResult second = run(container, writer);
            assertThat(first.getExitCode()).as(first.getStderr()).isZero();
            assertThat(second.getExitCode()).as(second.getStderr()).isZero();
            assertWriterRights(container, product, writer);

            ExecResult third = run(container, renewed);
            assertThat(third.getExitCode()).as(third.getStderr()).isZero();
            assertWriterRights(container, product, renewed);
            assertThatThrownBy(() -> connect(container, product, WRITER, writer).close())
                    .as("古いパスワードでは入れない")
                    .isInstanceOf(SQLException.class);
            ExecResult empty = run(container, "");
            assertThat(empty.getExitCode()).as("空のパスワードでは止まる").isEqualTo(1);
            assertThat(first.getStdout() + first.getStderr() + third.getStdout() + third.getStderr())
                    .as("台本はパスワードを出さない")
                    .doesNotContain(writer)
                    .doesNotContain(renewed);
        }
    }

    private static ExecResult run(JdbcDatabaseContainer<?> container, String password) throws Exception {
        return container.execInContainer(ExecConfig.builder()
                .command(new String[] {"bash", LATER_DIR + "03-writer-account.sh"})
                .envVars(Map.of("MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD", password))
                .build());
    }
}
