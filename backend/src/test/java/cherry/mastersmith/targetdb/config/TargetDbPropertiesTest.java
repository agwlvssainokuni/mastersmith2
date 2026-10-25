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

import cherry.mastersmith.common.testsupport.TestDatabase;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 対象DB の設定の型の単体テスト（BR1.4、NFR4.2）。 */
class TargetDbPropertiesTest {

    @Test
    @DisplayName("toString shows only whether the host, port, database, user name and password are set")
    void toStringMasksConnectionTarget() {
        String host = "db-" + TestDatabase.randomSecret() + ".example.com";
        String database = "db" + TestDatabase.randomSecret();
        String username = "user" + TestDatabase.randomSecret();
        String password = TestDatabase.randomSecret();
        TargetDbProperties properties = new TargetDbProperties(
                "postgresql", host, "15432", database, "app", username, password, null, null, null, null);

        String text = properties.toString();

        assertThat(text)
                .doesNotContain(host)
                .doesNotContain("15432")
                .doesNotContain(database)
                .doesNotContain(username)
                .doesNotContain(password)
                .contains("type=postgresql")
                .contains("schema=app")
                .contains("password=***")
                .contains("host=***");
    }

    @Test
    @DisplayName("toString marks an empty password as empty without printing any value")
    void toStringShowsEmpty() {
        TargetDbProperties properties =
                new TargetDbProperties(null, "", null, null, null, null, "", null, null, null, null);

        assertThat(properties.toString()).contains("password=(empty)").contains("host=(empty)");
    }

    @Test
    @DisplayName("timeouts and the pool fall back to the defaults when the values are empty")
    void defaults() {
        TargetDbProperties properties =
                new TargetDbProperties(null, null, null, null, null, null, null, null, null, null, null);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.queryTimeout().generate()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.queryTimeout().compare()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.queryTimeout().list()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.queryTimeout().detail()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.queryTimeout().longest()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.pool().maximumSize()).isEqualTo(10);
        assertThat(properties.pool().idleTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("the list and detail limits fall back to 5 seconds one by one and count for the longest limit")
    void listAndDetailDefaults() {
        TargetDbProperties.QueryTimeout partial =
                new TargetDbProperties.QueryTimeout(null, null, Duration.ofSeconds(7), null, null);

        assertThat(partial.list()).isEqualTo(Duration.ofSeconds(7));
        assertThat(partial.detail()).isEqualTo(Duration.ofSeconds(5));
        assertThat(new TargetDbProperties.QueryTimeout(
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(3),
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(4),
                                null)
                        .longest())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(new TargetDbProperties.QueryTimeout(
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(3),
                                Duration.ofSeconds(4),
                                Duration.ofSeconds(21),
                                null)
                        .longest())
                .isEqualTo(Duration.ofSeconds(21));
    }

    @Test
    @DisplayName("the write user name and password are shown only as present or empty")
    void toStringMasksWriteCredentials() {
        String username = "writer" + TestDatabase.randomSecret();
        String password = TestDatabase.randomSecret();
        TargetDbProperties properties = new TargetDbProperties(
                "mysql",
                "h",
                "3306",
                "d",
                "d",
                "reader",
                "x",
                null,
                null,
                null,
                new TargetDbProperties.Write(username, password, null));

        assertThat(properties.toString())
                .doesNotContain(username)
                .doesNotContain(password)
                .contains("write=Write[username=***, password=***, pool=WritePool[maximumSize=5]]");
        assertThat(properties.write().toString()).doesNotContain(username).doesNotContain(password);
        assertThat(new TargetDbProperties.Write("", null, null).toString())
                .contains("username=(empty)")
                .contains("password=(empty)");
    }

    @Test
    @DisplayName("the write settings fall back to empty credentials, a pool of 5 and a 5 second statement limit")
    void writeDefaults() {
        TargetDbProperties properties =
                new TargetDbProperties(null, null, null, null, null, null, null, null, null, null, null);

        assertThat(properties.write().username()).isNull();
        assertThat(properties.write().password()).isNull();
        assertThat(properties.write().pool().maximumSize()).isEqualTo(5);
        assertThat(properties.queryTimeout().write()).isEqualTo(Duration.ofSeconds(5));
        assertThat(new TargetDbProperties.WritePool(null).maximumSize()).isEqualTo(5);
        assertThat(new TargetDbProperties.WritePool(2).maximumSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("the longest limit covers only the four read purposes and ignores the write limit")
    void longestIgnoresTheWriteLimit() {
        TargetDbProperties.QueryTimeout timeouts = new TargetDbProperties.QueryTimeout(
                Duration.ofSeconds(20),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                Duration.ofSeconds(60));

        assertThat(timeouts.write()).isEqualTo(Duration.ofSeconds(60));
        assertThat(timeouts.longest()).isEqualTo(Duration.ofSeconds(20));
    }
}
