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
package cherry.mastersmith.dslmanage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.StatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** プレビューを置く文の数の確かめ（Intent 261114-internal-db-postgresql の U2、NFR1.2・NFR7。計画の Step 7）。 */
@SpringBootTest
@Import(StatementCounter.Config.class)
class DslPreviewStatementCountIT {

    private static final String HASH = "c".repeat(64);

    private static final Instant T0 = Instant.parse("2026-09-24T01:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    DslPreviewRepository previews;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    StatementCounter counter;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM dsl_previews");
    }

    private int place(UUID previewId, String body) {
        return tx.execute(status ->
                previews.place(previewId, body.getBytes(StandardCharsets.UTF_8), HASH, DslSource.UPLOAD, 1L, T0));
    }

    @Test
    @DisplayName(
            "the first place runs an update and an insert and a later place runs a single update on one connection")
    void statementCounts() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        counter.reset();
        assertThat(place(first, "version: 2\n")).isEqualTo(1);
        assertThat(counter.statements()).as("first place statements").isEqualTo(2);
        assertThat(counter.connections()).as("first place connections").isEqualTo(1);

        counter.reset();
        assertThat(place(second, "version: 2 # 2\n")).isEqualTo(1);
        assertThat(counter.statements()).as("later place statements").isEqualTo(1);
        assertThat(counter.connections()).as("later place connections").isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dsl_previews", Integer.class))
                .isOne();
        DslPreviewRef ref = tx.execute(status -> previews.findRef()).orElseThrow();
        assertThat(ref.previewId()).isEqualTo(second);
    }
}
