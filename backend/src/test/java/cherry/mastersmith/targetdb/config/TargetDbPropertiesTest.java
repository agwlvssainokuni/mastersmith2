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
                "postgresql", host, "15432", database, "app", username, password, null, null, null);

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
        TargetDbProperties properties = new TargetDbProperties(null, "", null, null, null, null, "", null, null, null);

        assertThat(properties.toString()).contains("password=(empty)").contains("host=(empty)");
    }

    @Test
    @DisplayName("timeouts and the pool fall back to the defaults when the values are empty")
    void defaults() {
        TargetDbProperties properties =
                new TargetDbProperties(null, null, null, null, null, null, null, null, null, null);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.queryTimeout().generate()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.queryTimeout().compare()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.pool().maximumSize()).isEqualTo(5);
        assertThat(properties.pool().idleTimeout()).isEqualTo(Duration.ofSeconds(60));
    }
}
