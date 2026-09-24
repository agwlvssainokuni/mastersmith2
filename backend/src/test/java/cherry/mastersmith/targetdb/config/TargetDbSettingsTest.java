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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 対象DB の設定の点検の単体テスト（BR1.2・BR1.3、NFR4.3、NFR7.1）。 */
class TargetDbSettingsTest {

    private static final String PASSWORD = TestDatabase.randomSecret();

    private static TargetDbProperties properties(
            String type, String host, String port, String database, String schema, String username, String password) {
        return new TargetDbProperties(type, host, port, database, schema, username, password, null, null, null);
    }

    private static TargetDbProperties valid() {
        return properties("PostgreSQL", "target-db.internal", "5432", "business", "Sales", "reader", PASSWORD);
    }

    private static List<String> problems(TargetDbProperties properties) {
        TargetDbSettings settings = TargetDbSettings.inspect(properties);
        assertThat(settings).isInstanceOf(TargetDbSettings.Invalid.class);
        return ((TargetDbSettings.Invalid) settings).problemItems();
    }

    @Test
    @DisplayName("no setting at all, including empty values from environment variables, means not configured")
    void notConfigured() {
        assertThat(TargetDbSettings.inspect(properties(null, null, null, null, null, null, null)))
                .isInstanceOf(TargetDbSettings.NotConfigured.class);
        assertThat(TargetDbSettings.inspect(properties("", "", "", "", "", "", "")))
                .isInstanceOf(TargetDbSettings.NotConfigured.class);
    }

    @Test
    @DisplayName("a complete setting is usable with the product, port and schema read from it")
    void usable() {
        TargetDbSettings settings = TargetDbSettings.inspect(valid());

        assertThat(settings).isInstanceOfSatisfying(TargetDbSettings.Usable.class, usable -> {
            assertThat(usable.product()).isEqualTo(DatabaseProduct.POSTGRESQL);
            assertThat(usable.port()).isEqualTo(5432);
            assertThat(usable.schema()).isEqualTo("Sales");
            assertThat(usable.queryTimeout(ReadPurpose.GENERATE)).isEqualTo(Duration.ofSeconds(20));
            assertThat(usable.queryTimeout(ReadPurpose.COMPARE)).isEqualTo(Duration.ofSeconds(5));
            assertThat(usable.toString()).doesNotContain(PASSWORD).doesNotContain("target-db.internal");
        });
        assertThat(TargetDbSettings.inspect(
                        properties("mysql", "[::1]", "3306", "app_db", "app_db", "reader", PASSWORD)))
                .isInstanceOf(TargetDbSettings.Usable.class);
        assertThat(TargetDbSettings.inspect(properties("MARIADB", "10.0.0.5", "65535", "業務", "業務", "reader", PASSWORD)))
                .isInstanceOf(TargetDbSettings.Usable.class);
    }

    @Test
    @DisplayName("each missing item is named by its setting key and all problems are listed together")
    void missingItems() {
        assertThat(problems(properties("postgresql", "h", "5432", "d", "", "u", PASSWORD)))
                .containsExactly("mastersmith.target-db.schema");
        assertThat(problems(properties("", "h", "5432", "d", "s", "u", PASSWORD)))
                .containsExactly("mastersmith.target-db.type");
        assertThat(problems(properties("postgresql", null, "5432", "d", "s", "u", PASSWORD)))
                .containsExactly("mastersmith.target-db.host");
        assertThat(problems(properties("postgresql", "h", "5432", " ", "s", "u", PASSWORD)))
                .containsExactly("mastersmith.target-db.database");
        assertThat(problems(properties("postgresql", "h", "5432", "d", "s", "", "")))
                .containsExactly("mastersmith.target-db.username", "mastersmith.target-db.password");
        assertThat(problems(properties(null, null, null, null, "s", null, null)))
                .containsExactly(
                        "mastersmith.target-db.type",
                        "mastersmith.target-db.host",
                        "mastersmith.target-db.port",
                        "mastersmith.target-db.database",
                        "mastersmith.target-db.username",
                        "mastersmith.target-db.password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"oracle", "postgres", "h2", "sqlserver"})
    @DisplayName("a database type other than MySQL, MariaDB and PostgreSQL is not supported")
    void unsupportedType(String type) {
        assertThat(problems(properties(type, "h", "5432", "d", "s", "u", PASSWORD)))
                .containsExactly("mastersmith.target-db.type");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "65536", "-1", "5432 ", "1e3", "99999999999"})
    @DisplayName("a port that is not a number from 1 to 65535 is invalid")
    void invalidPort(String port) {
        assertThat(problems(properties("mysql", "h", port, "d", "s", "u", PASSWORD)))
                .containsExactly("mastersmith.target-db.port");
    }

    @ParameterizedTest
    @ValueSource(strings = {"h?user=root", "h/x", "user@h", "h:1", "h h", "h;x", "h#x"})
    @DisplayName("a host with characters that change the meaning of the connection URL is invalid")
    void invalidHost(String host) {
        assertThat(problems(properties("mysql", host, "3306", "d", "s", "u", PASSWORD)))
                .containsExactly("mastersmith.target-db.host");
    }

    @ParameterizedTest
    @ValueSource(strings = {"d?allowLoadLocalInfile=true", "d&user=x", "d/x", "d;x", "d x", "d'x", "d=x"})
    @DisplayName("a database name with characters that change the meaning of the connection URL is invalid")
    void invalidDatabase(String database) {
        assertThat(problems(properties("mariadb", "h", "3306", database, "s", "u", PASSWORD)))
                .containsExactly("mastersmith.target-db.database");
    }

    @Test
    @DisplayName("timeouts and pool values outside what the connection pool accepts are invalid")
    void invalidTimings() {
        TargetDbProperties properties = new TargetDbProperties(
                "mysql",
                "h",
                "3306",
                "d",
                "s",
                "u",
                PASSWORD,
                Duration.ofMillis(100),
                new TargetDbProperties.QueryTimeout(Duration.ofMillis(500), Duration.ZERO),
                new TargetDbProperties.Pool(0, Duration.ofSeconds(5)));

        assertThat(problems(properties))
                .containsExactly(
                        "mastersmith.target-db.connect-timeout",
                        "mastersmith.target-db.query-timeout.generate",
                        "mastersmith.target-db.query-timeout.compare",
                        "mastersmith.target-db.pool.maximum-size",
                        "mastersmith.target-db.pool.idle-timeout");
    }

    @Test
    @DisplayName("an invalid setting logs exactly one warning with item names only and no value")
    void oneWarningWithNamesOnly() {
        String host = "h" + TestDatabase.randomSecret();
        String username = "u" + TestDatabase.randomSecret();
        TargetDbProperties properties = properties("oracle", host, "5432", "d", "", username, PASSWORD);

        try (LogEvents events = LogEvents.capture(TargetDataSourceConfig.class)) {
            TargetDbSettings settings = new TargetDataSourceConfig().targetDbSettings(properties);

            assertThat(settings).isInstanceOf(TargetDbSettings.Invalid.class);
            List<ILoggingEvent> logs = events.list();
            assertThat(logs).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getKeyValuePairs()).singleElement().satisfies(pair -> {
                    assertThat(pair.key).isEqualTo("items");
                    assertThat(pair.value).isEqualTo("mastersmith.target-db.type, mastersmith.target-db.schema");
                });
                assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                        .doesNotContain(host)
                        .doesNotContain(username)
                        .doesNotContain(PASSWORD);
            });
        }
    }

    @Test
    @DisplayName("no warning is logged when the target database is not configured or usable")
    void noWarningOtherwise() {
        try (LogEvents events = LogEvents.capture(TargetDataSourceConfig.class)) {
            new TargetDataSourceConfig().targetDbSettings(properties(null, null, null, null, null, null, null));
            new TargetDataSourceConfig().targetDbSettings(valid());

            assertThat(events.list()).isEmpty();
        }
    }
}
