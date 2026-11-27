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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.persistence.RowLockFailures;
import cherry.mastersmith.common.testsupport.CommittableWrite;
import cherry.mastersmith.common.testsupport.H2BlockedSessions;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * プレビューを置く同時の実行の確かめ（Intent 261114-internal-db-postgresql の U2、BR1.4・NFR4.x。計画の Step 8）。
 *
 * <p>固定の鍵（スロット 1）の行を別の接続が未確定のまま持つ間に置くと、先の確定の後に鍵の重なりを受けて更新をやり直し、後の側の
 * プレビューが残る。待ちの上限（H2 は約 4 秒）を超えて持つと、置く側は上限切れで失敗する。別の接続は待ち合わせ（
 * {@code INFORMATION_SCHEMA.SESSIONS} の待ちの印）で作り、実時刻の {@code sleep} は合否に使わない。
 */
@SpringBootTest
class DslPreviewPlaceConcurrencyIT {

    private static final String INSERT = "INSERT INTO dsl_previews (preview_slot, preview_id, yaml_bytes, dsl_hash,"
            + " source, placed_by_user_id, placed_at) VALUES (1, ?, ?, ?, 'UPLOAD', 1, ?)";

    private static final String HASH_EARLIER = "d".repeat(64);

    private static final String HASH_LATER = "e".repeat(64);

    private static final Instant T0 = Instant.parse("2026-09-24T01:00:00Z");

    private static final Duration AWAIT = Duration.ofSeconds(30);

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

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM dsl_previews");
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static byte[] yaml(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private int place(UUID previewId, String body, String hash) {
        return tx.execute(status -> previews.place(previewId, yaml(body), hash, DslSource.PASTE, 2L, T0));
    }

    @Test
    @DisplayName("placing while another transaction holds an uncommitted preview converges to the later one")
    void laterPlaceWinsAfterTheEarlierCommits() throws Exception {
        UUID earlier = UUID.randomUUID();
        UUID later = UUID.randomUUID();

        int written;
        try (CommittableWrite held = CommittableWrite.hold(
                TestDatabase.url(tempDir),
                INSERT,
                earlier,
                yaml("version: 2 # earlier\n"),
                HASH_EARLIER,
                OffsetDateTime.ofInstant(T0, ZoneOffset.UTC))) {
            Future<Integer> placing = executor.submit(() -> place(later, "version: 2 # later\n", HASH_LATER));
            H2BlockedSessions.awaitBlocked(TestDatabase.url(tempDir), AWAIT);
            held.commit();
            written = placing.get(AWAIT.toSeconds(), TimeUnit.SECONDS);
        }

        assertThat(written).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dsl_previews", Integer.class))
                .isOne();
        DslPreviewRef ref = tx.execute(status -> previews.findRef()).orElseThrow();
        assertThat(ref.previewId()).as("後に確定した方が残る").isEqualTo(later);
        assertThat(ref.dslHash()).isEqualTo(HASH_LATER);
        assertThat(ref.source()).isEqualTo(DslSource.PASTE);
        assertThat(ref.placedByUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName(
            "placing past the lock wait limit fails as a lock failure and leaves the other transaction's row alone")
    void waitTimeoutIsALockFailure(TestReporter reporter) throws Exception {
        UUID earlier = UUID.randomUUID();
        long elapsedNanos;

        try (CommittableWrite held = CommittableWrite.hold(
                TestDatabase.url(tempDir),
                INSERT,
                earlier,
                yaml("version: 2 # earlier\n"),
                HASH_EARLIER,
                OffsetDateTime.ofInstant(T0, ZoneOffset.UTC))) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> place(UUID.randomUUID(), "version: 2 # later\n", HASH_LATER))
                    .satisfies(thrown -> assertThat(RowLockFailures.isLockFailure(thrown))
                            .as("待ちの上限切れとして分類できる")
                            .isTrue());
            elapsedNanos = System.nanoTime() - start;
            held.commit();
        }

        reporter.publishEntry("lockWaitMillis", Long.toString(TimeUnit.NANOSECONDS.toMillis(elapsedNanos)));
        assertThat(tx.execute(status -> previews.findRef()).orElseThrow().previewId())
                .isEqualTo(earlier);
    }
}
