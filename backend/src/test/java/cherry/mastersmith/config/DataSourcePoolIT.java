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
package cherry.mastersmith.config;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.testsupport.TestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 内部DBの接続プールの上限の結合テスト（F2 の直し方。FR1.1、FR1.2）。
 *
 * <p>上限は {@code application.yaml} の {@code ${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}} で決まる。環境変数の名前での上書きは、
 * 起動の引数で同じ名前の値を渡して確かめる（Spring は環境変数と起動の引数を同じ名前で引く）。本物の環境変数での上書きは、
 * {@code MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10} を付けて {@code ConcurrentLoginAuditIT} を実行する試しで確かめる。
 * このテストは、その環境変数を設定せずに実行する。
 */
class DataSourcePoolIT {

    /** 既定の上限。 */
    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 30;

    @TempDir
    Path tempDir;

    private static ConfigurableApplicationContext start(Path dir, String... extraArgs) {
        List<String> args = new ArrayList<>();
        args.add("--spring.datasource.url=" + TestDatabase.url(dir));
        args.add("--server.port=0");
        args.addAll(List.of(extraArgs));
        return new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new));
    }

    private static HikariDataSource hikari(ConfigurableApplicationContext context) throws SQLException {
        return context.getBean(DataSource.class).unwrap(HikariDataSource.class);
    }

    @Test
    @DisplayName("the pool allows 30 connections by default")
    void defaultMaximumPoolSizeIs30() throws SQLException {
        try (ConfigurableApplicationContext context = start(tempDir.resolve("default"))) {
            HikariDataSource dataSource = hikari(context);

            assertThat(dataSource.getPoolName()).isEqualTo("mastersmith-db");
            assertThat(dataSource.getMaximumPoolSize())
                    .as("環境変数 MASTERSMITH_DB_MAXIMUM_POOL_SIZE を設定せずに実行する")
                    .isEqualTo(DEFAULT_MAXIMUM_POOL_SIZE);
            assertThat(dataSource.getConnectionTimeout()).as("接続を借りる待ちの上限は変えない").isEqualTo(5000L);
        }
    }

    @Test
    @DisplayName("MASTERSMITH_DB_MAXIMUM_POOL_SIZE overrides the maximum pool size")
    void maximumPoolSizeCanBeOverridden() throws SQLException {
        try (ConfigurableApplicationContext context =
                start(tempDir.resolve("override"), "--MASTERSMITH_DB_MAXIMUM_POOL_SIZE=12")) {
            assertThat(hikari(context).getMaximumPoolSize()).isEqualTo(12);
        }
    }

    @Test
    @DisplayName("30 connections can be held at the same time with the default setting")
    void defaultPoolHands30ConnectionsAtOnce() throws SQLException {
        try (ConfigurableApplicationContext context = start(tempDir.resolve("hold"))) {
            HikariDataSource dataSource = hikari(context);
            List<Connection> held = new ArrayList<>();
            try {
                for (int i = 0; i < DEFAULT_MAXIMUM_POOL_SIZE; i++) {
                    held.add(dataSource.getConnection());
                }

                assertThat(held)
                        .hasSize(DEFAULT_MAXIMUM_POOL_SIZE)
                        .allSatisfy(
                                connection -> assertThat(connection.isValid(1)).isTrue());
                assertThat(dataSource.getHikariPoolMXBean().getActiveConnections())
                        .isEqualTo(DEFAULT_MAXIMUM_POOL_SIZE);
            } finally {
                for (Connection connection : held) {
                    connection.close();
                }
            }
        }
    }
}
