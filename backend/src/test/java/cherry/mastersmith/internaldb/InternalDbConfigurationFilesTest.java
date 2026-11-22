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
package cherry.mastersmith.internaldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * {@code application.yaml} の、内部DB に関わる値の確かめ（BR1.6・BR3.5・NFR2.8・T10 の一部）。コメントの行は除いて確かめる。
 */
class InternalDbConfigurationFilesTest {

    private static List<String> yamlLines() throws IOException {
        String text =
                new String(new ClassPathResource("application.yaml").getContentAsByteArray(), StandardCharsets.UTF_8);
        return text.lines().filter(line -> !line.strip().startsWith("#")).toList();
    }

    @Test
    @DisplayName("the PostgreSQL driver and Hibernate JDBC error loggers stay OFF")
    void loggersStayOff() throws IOException {
        List<String> lines = yamlLines();

        assertThat(lines).anyMatch(line -> line.matches("\\s+org\\.postgresql:\\s*OFF\\s*"));
        assertThat(lines).anyMatch(line -> line.matches("\\s+org\\.hibernate\\.orm\\.jdbc\\.error:\\s*OFF\\s*"));
    }

    @Test
    @DisplayName("the spring.datasource environment variable names are unchanged")
    void datasourceEnvironmentVariablesAreUnchanged() throws IOException {
        String yaml = String.join("\n", yamlLines());

        assertThat(yaml)
                .contains("url: ${MASTERSMITH_DB_URL:jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE}")
                .contains("username: ${MASTERSMITH_DB_USERNAME:sa}")
                .contains("password: ${MASTERSMITH_DB_PASSWORD:}")
                .contains("maximum-pool-size: ${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}")
                .contains("pool-name: mastersmith-db");
    }

    @Test
    @DisplayName("the default Flyway location stays the H2 migrations")
    void defaultFlywayLocationIsH2() throws IOException {
        assertThat(yamlLines()).anyMatch(line -> line.matches("\\s+locations:\\s*classpath:db/migration\\s*"));
    }
}
