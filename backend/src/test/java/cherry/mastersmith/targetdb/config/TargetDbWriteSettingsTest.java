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
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 対象DB の書き込みの設定の点検の単体テスト（Intent 261016-business-data-views の U2、BR9.1・NFR4.11）。 */
class TargetDbWriteSettingsTest {

    private static final String WRITE_PASSWORD = TestDatabase.randomSecret();

    private static TargetDbProperties properties(
            String type, String writeUsername, String writePassword, Duration writeTimeout, Integer poolSize) {
        return new TargetDbProperties(
                type,
                type == null ? null : "target-db",
                type == null ? null : "5432",
                type == null ? null : "business",
                type == null ? null : "sales",
                type == null ? null : "reader",
                type == null ? null : "reader-password",
                null,
                new TargetDbProperties.QueryTimeout(null, null, null, null, null, writeTimeout),
                null,
                null,
                new TargetDbProperties.Write(writeUsername, writePassword, new TargetDbProperties.WritePool(poolSize)));
    }

    private static TargetDbWriteSettings inspect(TargetDbProperties properties) {
        return TargetDbWriteSettings.inspect(TargetDbSettings.inspect(properties), properties);
    }

    @Test
    @DisplayName("both write credentials on a usable read setting make the write setting usable")
    void bothCredentials() {
        TargetDbWriteSettings settings = inspect(properties("postgresql", "writer", WRITE_PASSWORD, null, null));

        assertThat(settings).isInstanceOfSatisfying(TargetDbWriteSettings.Usable.class, usable -> {
            assertThat(usable.product()).isEqualTo(DatabaseProduct.POSTGRESQL);
            assertThat(usable.queryTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(usable.write().username()).isEqualTo("writer");
            assertThat(usable.toString()).doesNotContain(WRITE_PASSWORD).doesNotContain("writer");
        });
    }

    @Test
    @DisplayName("without any write credential the write setting is not configured")
    void noCredentials() {
        assertThat(inspect(properties("mysql", null, null, null, null)))
                .isInstanceOf(TargetDbWriteSettings.NotConfigured.class);
        assertThat(inspect(properties("mysql", " ", "", null, null)))
                .isInstanceOf(TargetDbWriteSettings.NotConfigured.class);
    }

    @Test
    @DisplayName("only one of the write credentials names the missing item and holds no value")
    void oneCredentialOnly() {
        assertThat(inspect(properties("mariadb", "writer", null, null, null)))
                .isEqualTo(new TargetDbWriteSettings.Incomplete(List.of(TargetDbWriteSettings.WRITE_PASSWORD)));
        TargetDbWriteSettings missingUser = inspect(properties("mariadb", null, WRITE_PASSWORD, null, null));
        assertThat(missingUser)
                .isEqualTo(new TargetDbWriteSettings.Incomplete(List.of(TargetDbWriteSettings.WRITE_USERNAME)));
        assertThat(missingUser.toString()).doesNotContain(WRITE_PASSWORD);
    }

    @Test
    @DisplayName("a write limit under one second or a pool under one names the item")
    void outOfRangeValues() {
        assertThat(inspect(properties("postgresql", "w", WRITE_PASSWORD, Duration.ofMillis(999), null)))
                .isEqualTo(new TargetDbWriteSettings.Incomplete(List.of(TargetDbWriteSettings.QUERY_TIMEOUT_WRITE)));
        assertThat(inspect(properties("postgresql", "w", WRITE_PASSWORD, Duration.ofSeconds(1), 0)))
                .isEqualTo(
                        new TargetDbWriteSettings.Incomplete(List.of(TargetDbWriteSettings.WRITE_POOL_MAXIMUM_SIZE)));
        assertThat(inspect(properties("postgresql", "w", WRITE_PASSWORD, Duration.ofSeconds(1), 1)))
                .isInstanceOf(TargetDbWriteSettings.Usable.class);
    }

    @Test
    @DisplayName("write credentials without a usable read setting do not make writing usable")
    void readSettingRequired() {
        assertThat(inspect(properties(null, "writer", WRITE_PASSWORD, null, null)))
                .isInstanceOf(TargetDbWriteSettings.NotConfigured.class);
        assertThat(inspect(properties("oracle", "writer", WRITE_PASSWORD, null, null)))
                .isInstanceOf(TargetDbWriteSettings.NotConfigured.class);
    }

    @Test
    @DisplayName("an incomplete write setting logs exactly one warning with item names only")
    void oneWarningForIncomplete() {
        TargetDbProperties properties = properties("postgresql", "writer-x", null, null, null);
        TargetDataSourceConfig config = new TargetDataSourceConfig();

        try (LogEvents events = LogEvents.capture(TargetDataSourceConfig.class)) {
            TargetDbWriteSettings settings =
                    config.targetDbWriteSettings(config.targetDbSettings(properties), properties);

            assertThat(settings).isInstanceOf(TargetDbWriteSettings.Incomplete.class);
            List<ILoggingEvent> logs = events.list();
            assertThat(logs).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getKeyValuePairs()).singleElement().satisfies(pair -> {
                    assertThat(pair.key).isEqualTo("items");
                    assertThat(pair.value).isEqualTo("mastersmith.target-db.write.password");
                });
                assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                        .doesNotContain("writer-x");
            });
        }
    }

    @Test
    @DisplayName("no write warning is logged when writing is usable or not configured")
    void noWarningOtherwise() {
        TargetDataSourceConfig config = new TargetDataSourceConfig();
        TargetDbProperties usable = properties("postgresql", "w", WRITE_PASSWORD, null, null);
        TargetDbProperties none = properties("postgresql", null, null, null, null);
        try (LogEvents events = LogEvents.capture(TargetDataSourceConfig.class)) {
            config.targetDbWriteSettings(config.targetDbSettings(usable), usable);
            config.targetDbWriteSettings(config.targetDbSettings(none), none);

            assertThat(events.list()).isEmpty();
        }
    }
}
