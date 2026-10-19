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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;

/**
 * 対象DB の書き込みの接続の組み立ての単体テスト（Intent 261016-business-data-views の U2、BR8.2・BR9.1・BR9.2、NFR4.3、
 * infrastructure-specification.md の 2.1）。
 */
class TargetWriteDataSourceConfigTest {

    private static final String READ_USERNAME = "reader" + TestDatabase.randomSecret();

    private static final String WRITE_USERNAME = "writer" + TestDatabase.randomSecret();

    private static final String WRITE_PASSWORD = TestDatabase.randomSecret();

    private static TargetDbWriteSettings.Usable usable(String type, Duration writeTimeout, Integer poolSize) {
        TargetDbProperties properties = new TargetDbProperties(
                type,
                "target-db",
                "3306",
                "business",
                "sales",
                READ_USERNAME,
                "reader-password",
                null,
                new TargetDbProperties.QueryTimeout(null, null, null, null, writeTimeout),
                null,
                new TargetDbProperties.Write(
                        WRITE_USERNAME, WRITE_PASSWORD, new TargetDbProperties.WritePool(poolSize)));
        return (TargetDbWriteSettings.Usable)
                TargetDbWriteSettings.inspect(TargetDbSettings.inspect(properties), properties);
    }

    @Test
    @DisplayName("the write pool is not read-only, starts empty, holds at most 5 connections and has its own name")
    void poolSettings() {
        HikariConfig config = TargetWriteDataSourceConfig.hikariConfig(usable("postgresql", null, null));

        assertThat(config.getPoolName()).isEqualTo("mastersmith-target-db-write");
        assertThat(config.isReadOnly()).isFalse();
        assertThat(config.getMinimumIdle()).isZero();
        assertThat(config.getMaximumPoolSize()).isEqualTo(5);
        assertThat(config.getConnectionTimeout()).isEqualTo(3_000L);
        assertThat(config.getValidationTimeout()).isEqualTo(3_000L);
        assertThat(config.getIdleTimeout()).as("読み取りの idle-timeout を共用").isEqualTo(60_000L);
        assertThat(config.getInitializationFailTimeout()).as("起動時に接続しない").isEqualTo(-1L);
        assertThat(config.getConnectionInitSql()).as("PostgreSQL は初期化の文を持たない").isNull();
        assertThat(TargetWriteDataSourceConfig.hikariConfig(usable("mysql", null, 2))
                        .getMaximumPoolSize())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("the URL is the read connection target and the write credentials are passed separately")
    void writeCredentialsOutsideTheUrl() {
        HikariConfig config = TargetWriteDataSourceConfig.hikariConfig(usable("mariadb", null, null));

        assertThat(config.getJdbcUrl())
                .isEqualTo("jdbc:mariadb://target-db:3306/business")
                .doesNotContain(WRITE_USERNAME)
                .doesNotContain(WRITE_PASSWORD);
        assertThat(config.getUsername()).isEqualTo(WRITE_USERNAME).isNotEqualTo(READ_USERNAME);
        assertThat(config.getPassword()).isEqualTo(WRITE_PASSWORD);
        assertThat(config.getDataSourceProperties().values()).doesNotContain(WRITE_USERNAME, WRITE_PASSWORD);
        assertThat(config.getDriverClassName()).isEqualTo("org.mariadb.jdbc.Driver");
    }

    @Test
    @DisplayName("MySQL and MariaDB set the time zone and add STRICT_ALL_TABLES when a connection is made")
    void connectionInitSql() {
        String expected =
                "SET time_zone = '+00:00', sql_mode = CONCAT_WS(',', NULLIF(@@sql_mode, ''), 'STRICT_ALL_TABLES')";

        assertThat(TargetWriteDataSourceConfig.hikariConfig(usable("mysql", null, null))
                        .getConnectionInitSql())
                .isEqualTo(expected)
                .isEqualTo(TargetDataSourceConfig.WRITE_CONNECTION_INIT_SQL);
        assertThat(TargetWriteDataSourceConfig.hikariConfig(usable("mariadb", null, null))
                        .getConnectionInitSql())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("the socket timeout is the write limit plus five seconds and follows a changed limit")
    void socketTimeoutFollowsTheWriteLimit() {
        assertThat(TargetWriteDataSourceConfig.hikariConfig(usable("mysql", null, null))
                        .getDataSourceProperties())
                .containsEntry("socketTimeout", "10000")
                .containsEntry("connectTimeout", "5000");
        assertThat(TargetWriteDataSourceConfig.driverProperties(
                        DatabaseProduct.MARIADB, Duration.ofSeconds(3), Duration.ofSeconds(12)))
                .containsEntry("socketTimeout", "17000");
        assertThat(TargetWriteDataSourceConfig.driverProperties(
                        DatabaseProduct.POSTGRESQL, Duration.ofSeconds(3), Duration.ofSeconds(12)))
                .as("PostgreSQL のドライバーは秒の単位")
                .containsEntry("socketTimeout", "17")
                .containsEntry("connectTimeout", "5")
                .containsEntry("loginTimeout", "5");
    }

    @Test
    @DisplayName("the row count is fixed to matched rows and local file loading is turned off")
    void matchedRowsAndNoLocalFiles() {
        assertThat(TargetWriteDataSourceConfig.driverProperties(
                        DatabaseProduct.MYSQL, Duration.ofSeconds(3), Duration.ofSeconds(5)))
                .containsEntry("useAffectedRows", "false")
                .containsEntry("allowLoadLocalInfile", "false")
                .containsEntry("allowUrlInLocalInfile", "false");
        assertThat(TargetWriteDataSourceConfig.driverProperties(
                        DatabaseProduct.MARIADB, Duration.ofSeconds(3), Duration.ofSeconds(5)))
                .containsEntry("useAffectedRows", "false")
                .containsEntry("allowLocalInfile", "false");
    }

    @Test
    @DisplayName("creating the data source does not connect and the manager uses only that data source")
    void createDoesNotConnect() {
        TargetWriteDataSourceConfig config = new TargetWriteDataSourceConfig();
        try (HikariDataSource dataSource = config.targetWriteDataSource(usable("postgresql", null, null))) {
            assertThat(dataSource.getHikariPoolMXBean()).isNull();
            DataSourceTransactionManager manager = config.targetWriteTransactionManager(dataSource);
            assertThat(manager.getDataSource()).isSameAs(dataSource);
        }
        assertThatThrownBy(() -> config.targetWriteDataSource(new TargetDbWriteSettings.NotConfigured()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the condition matches only when the read setting is usable and both write credentials are set")
    void condition() {
        TargetDbWriteUsableCondition condition = new TargetDbWriteUsableCondition();
        MockEnvironment read = readEnvironment();
        MockEnvironment both = readEnvironment()
                .withProperty("mastersmith.target-db.write.username", "w")
                .withProperty("mastersmith.target-db.write.password", WRITE_PASSWORD);
        MockEnvironment one = readEnvironment().withProperty("mastersmith.target-db.write.username", "w");
        MockEnvironment writeOnly = new MockEnvironment()
                .withProperty("mastersmith.target-db.write.username", "w")
                .withProperty("mastersmith.target-db.write.password", WRITE_PASSWORD);

        assertThat(condition.matches(context(both), null)).isTrue();
        assertThat(condition.matches(context(read), null)).isFalse();
        assertThat(condition.matches(context(one), null)).isFalse();
        assertThat(condition.matches(context(writeOnly), null)).isFalse();
    }

    private static MockEnvironment readEnvironment() {
        return new MockEnvironment()
                .withProperty("mastersmith.target-db.type", "mysql")
                .withProperty("mastersmith.target-db.host", "h")
                .withProperty("mastersmith.target-db.port", "3306")
                .withProperty("mastersmith.target-db.database", "d")
                .withProperty("mastersmith.target-db.schema", "d")
                .withProperty("mastersmith.target-db.username", "u")
                .withProperty("mastersmith.target-db.password", "p");
    }

    private static ConditionContext context(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }
}
