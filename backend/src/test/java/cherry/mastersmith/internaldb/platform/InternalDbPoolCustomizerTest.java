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
package cherry.mastersmith.internaldb.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.internaldb.testsupport.LogCapture;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/**
 * 内部DB の接続プールへの設定（接続を開かずに、設定の値だけで確かめる。T8・DD-1・NFR4.1・NFR4.10）。
 */
class InternalDbPoolCustomizerTest {

    private static final String POSTGRES_SQL = "SET lock_timeout = '2s'";

    private static InternalDbPoolCustomizer customizerFor(String url) {
        return new InternalDbPoolCustomizer(
                InternalDbSettings.from(new MockEnvironment().withProperty("spring.datasource.url", url)));
    }

    @Test
    @DisplayName(
            "sets the connection init statement only on the Hikari bean named dataSource when the kind is PostgreSQL")
    void postgresDataSourceGetsTheStatement() {
        HikariDataSource pool = new HikariDataSource();
        try (pool) {
            Object result = customizerFor("jdbc:postgresql://db.example.com/example")
                    .postProcessAfterInitialization(pool, "dataSource");

            assertThat(result).isSameAs(pool);
            assertThat(pool.getConnectionInitSql()).isEqualTo(POSTGRES_SQL);
            assertThat(pool.isRunning()).isFalse();
        }
    }

    @Test
    @DisplayName("leaves other beans (target DB pools, H2 pools, non data sources) untouched")
    void otherBeansAreUntouched() {
        try (HikariDataSource target = new HikariDataSource();
                HikariDataSource h2Pool = new HikariDataSource()) {
            InternalDbPoolCustomizer postgres = customizerFor("jdbc:postgresql://db.example.com/example");
            Object notADataSource = new Object();

            postgres.postProcessAfterInitialization(target, "targetDataSource");
            postgres.postProcessAfterInitialization(target, "targetWriteDataSource");
            assertThat(postgres.postProcessAfterInitialization(notADataSource, "somethingElse"))
                    .isSameAs(notADataSource);
            customizerFor("jdbc:h2:mem:example").postProcessAfterInitialization(h2Pool, "dataSource");

            assertThat(target.getConnectionInitSql()).isNull();
            assertThat(h2Pool.getConnectionInitSql()).isNull();
        }
    }

    @Test
    @DisplayName("replaces a user supplied connection-init-sql with the lock_timeout statement")
    void replacesTheUsersStatement() {
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setConnectionInitSql("SELECT 1");

            customizerFor("jdbc:postgresql://db.example.com/example")
                    .postProcessAfterInitialization(pool, "dataSource");

            assertThat(pool.getConnectionInitSql()).isEqualTo(POSTGRES_SQL);
        }
    }

    @Test
    @DisplayName("the setter takes effect before the first connection: the pool is not started and still configurable")
    void setterWorksBeforeTheFirstConnection() {
        try (HikariDataSource lazy = new HikariDataSource()) {
            lazy.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID());

            customizerFor("jdbc:postgresql://db.example.com/example")
                    .postProcessAfterInitialization(lazy, "dataSource");

            assertThat(lazy.getConnectionInitSql()).isEqualTo(POSTGRES_SQL);
            assertThat(lazy.isRunning()).isFalse();
        }
    }

    @Test
    @DisplayName("stops with UNKNOWN when the PostgreSQL dataSource is not a HikariDataSource")
    void nonHikariStops() {
        DataSource other = new NonHikariDataSource();

        try (LogCapture capture = LogCapture.start()) {
            assertThatThrownBy(() -> customizerFor("jdbc:postgresql://db.example.com/example")
                            .postProcessAfterInitialization(other, "dataSource"))
                    .isInstanceOfSatisfying(
                            InternalDbStartupException.class,
                            e -> assertThat(e.failureKind()).isEqualTo(StartupFailureKind.UNKNOWN))
                    .hasNoCause();

            List<ILoggingEvent> errors = capture.errorsWithMessage("Internal DB startup failed");
            assertThat(errors).hasSize(1);
            assertThat(LogCapture.text(errors.get(0)))
                    .contains("failureKind=UNKNOWN")
                    .doesNotContain("db.example.com");
        }
    }

    @Test
    @DisplayName("stops with UNKNOWN when the pool has already started")
    void alreadyStartedPoolStops() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID());
        try (HikariDataSource started = new HikariDataSource(config)) {
            assertThat(started.isRunning()).isTrue();

            assertThatThrownBy(() -> customizerFor("jdbc:postgresql://db.example.com/example")
                            .postProcessAfterInitialization(started, "dataSource"))
                    .isInstanceOfSatisfying(
                            InternalDbStartupException.class,
                            e -> assertThat(e.failureKind()).isEqualTo(StartupFailureKind.UNKNOWN));
            assertThat(started.getConnectionInitSql()).isNull();
        }
    }

    @Test
    @DisplayName("does nothing for H2 even when the bean is not a HikariDataSource")
    void h2DoesNothing() {
        DataSource other = new NonHikariDataSource();

        assertThat(customizerFor("jdbc:h2:mem:example").postProcessAfterInitialization(other, "dataSource"))
                .isSameAs(other);
    }

    @Test
    @DisplayName("does nothing when spring.datasource.url is absent from the environment")
    void absentPropertyDoesNothing() {
        InternalDbPoolCustomizer customizer =
                new InternalDbPoolCustomizer(InternalDbSettings.from(new MockEnvironment()));
        DataSource other = new NonHikariDataSource();

        assertThat(customizer.postProcessAfterInitialization(other, "dataSource"))
                .isSameAs(other);
    }

    @Test
    @DisplayName(
            "stops with EMPTY_URL or UNSUPPORTED_KIND, logging one ERROR with the setting name and supported kinds")
    void emptyAndUnsupportedStop() {
        for (String[] row : new String[][] {
            {"", "EMPTY_URL"}, {"   ", "EMPTY_URL"}, {"jdbc:mysql://user:pw@secret.example.com/db", "UNSUPPORTED_KIND"}
        }) {
            try (LogCapture capture = LogCapture.start()) {
                assertThatThrownBy(
                                () -> customizerFor(row[0]).postProcessAfterInitialization(new Object(), "dataSource"))
                        .isInstanceOfSatisfying(
                                InternalDbStartupException.class,
                                e -> assertThat(e.failureKind()).hasToString(row[1]))
                        .hasNoCause();

                List<ILoggingEvent> errors = capture.errorsWithMessage("Internal DB startup failed");
                assertThat(errors).hasSize(1);
                String text = LogCapture.text(errors.get(0));
                assertThat(text)
                        .contains("failureKind=" + row[1])
                        .contains("settingName=spring.datasource.url")
                        .contains("supportedKinds=H2,POSTGRESQL")
                        .doesNotContain("secret.example.com")
                        .doesNotContain("pw@");
            }
        }
    }

    @Test
    @DisplayName("logs one INFO line with internalDbKind when the kind is resolved")
    void logsTheResolvedKind() {
        try (LogCapture capture = LogCapture.start();
                HikariDataSource pool = new HikariDataSource()) {
            customizerFor("jdbc:postgresql://db.example.com/example")
                    .postProcessAfterInitialization(pool, "dataSource");
            customizerFor("jdbc:h2:mem:example").postProcessAfterInitialization(new Object(), "dataSource");

            List<ILoggingEvent> infos = capture.events().stream()
                    .filter(e -> "Internal DB kind resolved".equals(e.getFormattedMessage()))
                    .toList();
            assertThat(infos).hasSize(2);
            assertThat(infos).allMatch(e -> e.getLevel() == Level.INFO);
            assertThat(infos.stream().map(LogCapture::text).toList())
                    .anyMatch(t -> t.contains("internalDbKind=POSTGRESQL"))
                    .anyMatch(t -> t.contains("internalDbKind=H2"));
            assertThat(capture.allText()).doesNotContain("db.example.com");
        }
    }

    @Test
    @DisplayName("is registered by the configuration as a static post processor ordered first")
    void configurationRegistersTheProcessor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new org.springframework.core.env.MapPropertySource(
                            "test", java.util.Map.of("spring.datasource.url", "jdbc:h2:mem:example")));
            context.register(InternalDbPlatformConfiguration.class);
            context.refresh();

            assertThat(context.getBean(InternalDbPoolCustomizer.class).getOrder())
                    .isEqualTo(org.springframework.core.PriorityOrdered.HIGHEST_PRECEDENCE);
            assertThat(context.getBean(InternalDbSettings.class).kind()).isEqualTo(Optional.of(InternalDbKind.H2));
            assertThat(context.getBean(InternalDbMigrationStrategy.class)).isNotNull();
            assertThat(context.getBean(InternalDbFlywayCustomizer.class)).isNotNull();
        }
    }

    /** Hikari ではない {@link DataSource}（接続は開けない）。 */
    private static final class NonHikariDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
