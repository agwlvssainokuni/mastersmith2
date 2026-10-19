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
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;

/**
 * 名前つきの書き込みの接続と管理者が、型で注入される内部DB の接続と管理者とぶつからないことの結合テスト（Intent
 * 261016-business-data-views の U2、BR9.2、NFR 設計のレビュー R-06、計画の Step 3）。書き込みの部品より先に通す。
 *
 * <p>内部DB は組み込みの H2。対象DB は届かない先（手元の閉じた番号）にし、一度も接続しないことで、内部DB の管理者が使われたことを
 * 確かめる（書き込みの管理者が使われたら接続に失敗する）。開発者の環境変数の影響を受けないよう、項目を起動の引数で明示する。
 */
class TargetWriteTransactionManagerInjectionIT {

    @TempDir
    Path tempDir;

    private ConfigurableApplicationContext start(String writeUsername, String writePassword) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--spring.datasource.url=" + TestDatabase.url(tempDir));
        args.add("--server.port=0");
        args.add("--mastersmith.auth.password.bcrypt-cost=4");
        args.add("--mastersmith.target-db.type=postgresql");
        args.add("--mastersmith.target-db.host=127.0.0.1");
        args.add("--mastersmith.target-db.port=" + TargetDbStartupIT.closedPort());
        args.add("--mastersmith.target-db.database=business");
        args.add("--mastersmith.target-db.schema=sales");
        args.add("--mastersmith.target-db.username=reader");
        args.add("--mastersmith.target-db.password=" + TestDatabase.randomSecret());
        args.add("--mastersmith.target-db.write.username=" + writeUsername);
        args.add("--mastersmith.target-db.write.password=" + writePassword);
        return new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new));
    }

    private static boolean defaultCandidate(ConfigurableApplicationContext context, String name) {
        return ((AbstractBeanDefinition) context.getBeanFactory().getBeanDefinition(name)).isDefaultCandidate();
    }

    private static String jdbcUrl(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(HikariDataSource.class).getJdbcUrl();
    }

    @Test
    @DisplayName("with write settings the managers and data sources found by type stay those of the internal database")
    void typeInjectionKeepsTheInternalDatabase() throws Exception {
        try (ConfigurableApplicationContext context = start("writer", TestDatabase.randomSecret())) {
            String internal = TestDatabase.url(tempDir);

            assertThat(context.getBeansOfType(PlatformTransactionManager.class))
                    .containsKey(TargetWriteDataSourceConfig.TARGET_WRITE_TRANSACTION_MANAGER);
            assertThat(context.getBeansOfType(DataSource.class))
                    .containsKeys(
                            TargetDataSourceConfig.TARGET_DATA_SOURCE,
                            TargetWriteDataSourceConfig.TARGET_WRITE_DATA_SOURCE);
            assertThat(context.getBean(PlatformTransactionManager.class)).isInstanceOf(JpaTransactionManager.class);
            assertThat(context.getBean(TransactionManager.class)).isInstanceOf(JpaTransactionManager.class);
            assertThat(jdbcUrl(context.getBean(JpaTransactionManager.class).getDataSource()))
                    .isEqualTo(internal);
            assertThat(jdbcUrl(context.getBean(DataSource.class))).isEqualTo(internal);
            assertThat(jdbcUrl(context.getBean(Flyway.class).getConfiguration().getDataSource()))
                    .isEqualTo(internal);
            assertThat(context.getBean(Flyway.class).info().applied()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("an existing @Transactional operation commits through the internal manager, not the write manager")
    void existingTransactionsUseTheInternalManager() throws Exception {
        try (ConfigurableApplicationContext context = start("writer", TestDatabase.randomSecret())) {
            String email = "member-" + UUID.randomUUID() + "@example.com";
            long userId = TestUserAccounts.create(
                    context.getBean(UserAccountService.class),
                    email,
                    "テスト用パスワード-" + TestDatabase.randomSecret(),
                    false);

            Integer count = new JdbcTemplate(context.getBean(DataSource.class))
                    .queryForObject("SELECT COUNT(*) FROM users WHERE user_id = ?", Integer.class, userId);
            assertThat(count).as("内部DB の管理者で確定した").isEqualTo(1);
            HikariDataSource write =
                    context.getBean(TargetWriteDataSourceConfig.TARGET_WRITE_DATA_SOURCE, HikariDataSource.class);
            assertThat(write.getHikariPoolMXBean())
                    .as("書き込みのプールは始まっていない（一度も接続していない）")
                    .isNull();
        }
    }

    @Test
    @DisplayName("the write data source and manager are reachable only by name and point at the target database")
    void writeBeansByName() throws Exception {
        try (ConfigurableApplicationContext context = start("writer", TestDatabase.randomSecret())) {
            DataSource write = context.getBean(TargetWriteDataSourceConfig.TARGET_WRITE_DATA_SOURCE, DataSource.class);
            PlatformTransactionManager manager = context.getBean(
                    TargetWriteDataSourceConfig.TARGET_WRITE_TRANSACTION_MANAGER, PlatformTransactionManager.class);

            assertThat(jdbcUrl(write)).startsWith("jdbc:postgresql://127.0.0.1:");
            assertThat(write.unwrap(HikariDataSource.class).getPoolName()).isEqualTo("mastersmith-target-db-write");
            assertThat(write.unwrap(HikariDataSource.class).isReadOnly()).isFalse();
            assertThat(manager).isNotInstanceOf(JpaTransactionManager.class);
            assertThat(defaultCandidate(context, TargetWriteDataSourceConfig.TARGET_WRITE_DATA_SOURCE))
                    .isFalse();
            assertThat(defaultCandidate(context, TargetWriteDataSourceConfig.TARGET_WRITE_TRANSACTION_MANAGER))
                    .isFalse();
        }
    }

    @Test
    @DisplayName("without write settings neither the write data source nor its manager is created")
    void noWriteBeansWithoutSettings() throws Exception {
        try (ConfigurableApplicationContext context = start("", "")) {
            assertThat(context.containsBean(TargetWriteDataSourceConfig.TARGET_WRITE_DATA_SOURCE))
                    .isFalse();
            assertThat(context.containsBean(TargetWriteDataSourceConfig.TARGET_WRITE_TRANSACTION_MANAGER))
                    .isFalse();
            assertThat(context.getBean(TargetDbWriteSettings.class))
                    .isInstanceOf(TargetDbWriteSettings.NotConfigured.class);
            assertThat(context.containsBean(TargetDataSourceConfig.TARGET_DATA_SOURCE))
                    .as("読み取りの接続は作られる")
                    .isTrue();
        }
    }
}
