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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

/** 対象DB の接続の組み立ての単体テスト（NFR4.5・NFR4.6、NFR7.2・NFR7.5〜NFR7.7、NFR 設計の接続の待ち 3 秒）。 */
class TargetDataSourceConfigTest {

    private static final String USERNAME = "reader" + TestDatabase.randomSecret();

    private static final String PASSWORD = TestDatabase.randomSecret();

    private static TargetDbSettings.Usable usable(String type, String port) {
        TargetDbProperties properties = new TargetDbProperties(
                type, "target-db", port, "business", "sales", USERNAME, PASSWORD, null, null, null);
        return (TargetDbSettings.Usable) TargetDbSettings.inspect(properties);
    }

    @Test
    @DisplayName("the pool is read-only, starts empty, holds at most 5 connections and waits 3 seconds to connect")
    void poolSettings() {
        HikariConfig config = TargetDataSourceConfig.hikariConfig(usable("postgresql", "5432"));

        assertThat(config.getPoolName()).isEqualTo("mastersmith-target-db");
        assertThat(config.isReadOnly()).isTrue();
        assertThat(config.getMinimumIdle()).isZero();
        assertThat(config.getMaximumPoolSize()).isEqualTo(5);
        assertThat(config.getConnectionTimeout()).isEqualTo(3_000L);
        assertThat(config.getValidationTimeout()).isLessThanOrEqualTo(config.getConnectionTimeout());
        assertThat(config.getIdleTimeout()).isEqualTo(60_000L);
        assertThat(config.getInitializationFailTimeout()).as("起動時に接続しない").isEqualTo(-1L);
        assertThat(config.getDriverClassName()).isEqualTo("org.postgresql.Driver");
    }

    @Test
    @DisplayName("the connection URL has no credentials, which are passed as separate user name and password")
    void urlWithoutCredentials() {
        HikariConfig config = TargetDataSourceConfig.hikariConfig(usable("mysql", "3306"));

        assertThat(config.getJdbcUrl())
                .isEqualTo("jdbc:mysql://target-db:3306/business")
                .doesNotContain(USERNAME)
                .doesNotContain(PASSWORD);
        assertThat(config.getUsername()).isEqualTo(USERNAME);
        assertThat(config.getPassword()).isEqualTo(PASSWORD);
        assertThat(config.getDataSourceProperties().values()).doesNotContain(USERNAME, PASSWORD);
    }

    @Test
    @DisplayName("each database type gets its own URL scheme and driver")
    void urlAndDriverByType() {
        assertThat(TargetDataSourceConfig.jdbcUrl(DatabaseProduct.MARIADB, "[::1]", 3307, "db"))
                .isEqualTo("jdbc:mariadb://[::1]:3307/db");
        assertThat(TargetDataSourceConfig.jdbcUrl(DatabaseProduct.POSTGRESQL, "pg", 5432, "db"))
                .isEqualTo("jdbc:postgresql://pg:5432/db");
        assertThat(TargetDataSourceConfig.driverClassName(DatabaseProduct.MYSQL))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(TargetDataSourceConfig.driverClassName(DatabaseProduct.MARIADB))
                .isEqualTo("org.mariadb.jdbc.Driver");
    }

    @Test
    @DisplayName("driver timeouts are longer than the pool wait and local file loading is turned off")
    void driverProperties() {
        TargetDbProperties properties = usable("mysql", "3306").properties();

        assertThat(TargetDataSourceConfig.driverProperties(DatabaseProduct.MYSQL, properties))
                .containsEntry("connectTimeout", "5000")
                .containsEntry("socketTimeout", "25000")
                .containsEntry("allowLoadLocalInfile", "false");
        assertThat(TargetDataSourceConfig.driverProperties(DatabaseProduct.MARIADB, properties))
                .containsEntry("connectTimeout", "5000")
                .containsEntry("allowLocalInfile", "false");
        assertThat(TargetDataSourceConfig.driverProperties(DatabaseProduct.POSTGRESQL, properties))
                .containsEntry("connectTimeout", "5")
                .containsEntry("loginTimeout", "5")
                .containsEntry("socketTimeout", "25");
    }

    @Test
    @DisplayName("creating the data source does not connect to the target database")
    void createDoesNotConnect() {
        try (HikariDataSource dataSource = new TargetDataSourceConfig().targetDataSource(usable("postgresql", "1"))) {
            // プールは初めて接続を借りるときに始まる。始まっていなければ、プールの管理の口は無い。
            assertThat(dataSource.getHikariPoolMXBean()).isNull();
        }
        assertThatThrownBy(() -> new TargetDataSourceConfig().targetDataSource(new TargetDbSettings.NotConfigured()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the condition matches only a usable setting in the environment")
    void condition() {
        TargetDbUsableCondition condition = new TargetDbUsableCondition();
        MockEnvironment usable = new MockEnvironment()
                .withProperty("mastersmith.target-db.type", "mariadb")
                .withProperty("mastersmith.target-db.host", "h")
                .withProperty("mastersmith.target-db.port", "3306")
                .withProperty("mastersmith.target-db.database", "d")
                .withProperty("mastersmith.target-db.schema", "d")
                .withProperty("mastersmith.target-db.username", "u")
                .withProperty("mastersmith.target-db.password", PASSWORD);
        MockEnvironment partial = new MockEnvironment().withProperty("mastersmith.target-db.schema", "d");

        assertThat(condition.matches(context(usable), null)).isTrue();
        assertThat(condition.matches(context(partial), null)).isFalse();
        assertThat(condition.matches(context(new MockEnvironment()), null)).isFalse();
    }

    private static ConditionContext context(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }
}
