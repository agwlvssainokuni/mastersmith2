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
import static org.awaitility.Awaitility.await;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.common.testsupport.TestHikariMbeansEnvironmentPostProcessor;
import cherry.mastersmith.dslmanage.domain.DslAppliedRef;
import cherry.mastersmith.dslmanage.domain.DslContent;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.service.DslRecordStore;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 内部DB（組み込みの H2）のファイルが動いている間に伸び続ける不具合の再現と、HikariCP の標準の MBean の操作だけで
 * アプリを止めずに詰め直せることの確かめ（Intent 260925-storage-memory-fixes の FR1.1・FR1.2・FR1.4・FR1.5・FR1.8・FR1.11）。
 *
 * <p>H2 は接続が1本でも開いている間は消した行の場所を詰め直さず、DSL の投入と適用を重ねるたびにファイルが伸びる。
 * 運用の道具（{@code docker/hikari-pool.sh} の compact）と同じ順に、プールの MBean で一時停止 → 接続の破棄 → 0 本を待つ →
 * 再開 を行うと、最後の接続が閉じたときに接続先の {@code DEFRAG_ALWAYS=TRUE} に従って H2 が詰め直す。
 *
 * <p>MBean の登録と一時停止の許可は、本番の設定（{@code application.yaml}）の値をそのまま使う（テストの既定で登録を無効にする
 * 補助を、このクラスだけ切る）。そのため、設定が消えるとこのテストは失敗する。プールの名前は、同じ JVM のほかの Spring の文脈と
 * 重ならない名前にする。
 *
 * <p>本文は、圧縮の効かない固定の種の乱数（2MiB）とする。10MB にしないのは、テストの JVM のヒープ（1g）と時間を抑えるため。
 */
class H2CompactionByPoolSuspensionIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2CompactionByPoolSuspensionIT.class);

    /** このクラスだけのプールの名前（MBean の名前に入る）。 */
    private static final String POOL_NAME = "h2-compaction-it";

    private static final long MIB = 1024L * 1024;

    /** 1件の本文の大きさ（2MiB）。 */
    private static final int BODY_BYTES = (int) (2 * MIB);

    /** 投入（プレビューを置く）と適用を重ねる回数。 */
    private static final int ROUNDS = 24;

    /** 履歴の件数の上限（このテストでの値。超えた古い版は消える）。 */
    private static final int HISTORY_LIMIT = 3;

    /** 残る本文の件数（履歴 3 件とプレビュー 1 件）。 */
    private static final int LIVE_BODIES = HISTORY_LIMIT + 1;

    /** 残る本文の合計。 */
    private static final long LIVE_BYTES = (long) BODY_BYTES * LIVE_BODIES;

    /**
     * 詰め直しの直後の大きさの上限。計画 D6（本文の論理的な最大 約 210MB に約 40MiB、2割ほどの余裕）の考え方を、このテストの
     * 本文の大きさに当てはめ、残る本文の 2 割に、ほかの表・索引・移行の記録の分として 4MiB を足した値とする。
     */
    private static final long COMPACTED_LIMIT_BYTES = LIVE_BYTES + LIVE_BYTES / 5 + 4 * MIB;

    /** 待ちの上限（0 本になるまで・詰め直しが終わるまで・再開の後の処理）。道具の既定（10 秒・30 秒）に合わせる。 */
    private static final Duration ZERO_CONNECTIONS_WAIT = Duration.ofSeconds(10);

    private static final Duration SHRINK_WAIT = Duration.ofSeconds(30);

    private static final long ACTOR_USER_ID = 1L;

    private static final Instant T0 = Instant.parse("2026-09-25T01:00:00Z");

    @TempDir
    static Path tempDir;

    private static ConfigurableApplicationContext context;

    private static Path dbFile;

    private static HikariPoolMXBean pool;

    private static DslRecordStore recordStore;

    private static JdbcTemplate jdbc;

    private static DataSource dataSource;

    @BeforeAll
    static void start() throws MalformedObjectNameException {
        Path dir = tempDir.resolve("db");
        dbFile = dir.resolve("mastersmith.mv.db");
        context = new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        // 今のテストの既定の接続先には DEFRAG_ALWAYS が付いていないため、本番の既定と同じく付ける。
                        "--spring.datasource.url=" + TestDatabase.url(dir) + ";DEFRAG_ALWAYS=TRUE",
                        "--spring.datasource.hikari.pool-name=" + POOL_NAME,
                        "--" + TestHikariMbeansEnvironmentPostProcessor.DISABLE + "=false",
                        "--server.port=0");
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("com.zaxxer.hikari:type=Pool (" + POOL_NAME + ")");
        assertThat(server.isRegistered(name))
                .as("application.yaml の register-mbeans: true でプールの MBean が登録される")
                .isTrue();
        pool = JMX.newMXBeanProxy(server, name, HikariPoolMXBean.class);
        recordStore = context.getBean(DslRecordStore.class);
        jdbc = context.getBean(JdbcTemplate.class);
        dataSource = context.getBean(DataSource.class);
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            if (pool != null) {
                pool.resumePool();
            }
            context.close();
        }
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM dsl_previews");
        jdbc.update("DELETE FROM dsl_applied_revisions");
    }

    /** テストが途中で失敗しても、プールが止まったままにならないように必ず再開する。 */
    @AfterEach
    void resume() {
        pool.resumePool();
    }

    @Test
    @DisplayName(
            "the H2 file grows while connections are open and shrinks after suspend, evict and resume via the pool MBean")
    void growsWhileOpenAndShrinksAfterPoolCompaction() throws Exception {
        List<String> storedHashes = submitAndApply(ROUNDS);
        long grown = Files.size(dbFile);

        // 再現: 接続が開いている間は、消した本文の場所が残り、詰め直しの上限を大きく超える。
        assertThat(grown).as("伸びた大きさ %d", grown).isGreaterThan(COMPACTED_LIMIT_BYTES * 2);

        // 操作しなければ縮まない（FR1.2）。書き出しを確定させても変わらない。
        jdbc.execute("CHECKPOINT SYNC");
        assertThat(Files.size(dbFile)).isGreaterThanOrEqualTo(grown);

        // 運用の道具と同じ順の、HikariCP の標準の操作だけ。
        pool.suspendPool();
        pool.softEvictConnections();
        await().atMost(ZERO_CONNECTIONS_WAIT).until(() -> pool.getTotalConnections() == 0);
        await().atMost(SHRINK_WAIT).until(() -> Files.size(dbFile) <= COMPACTED_LIMIT_BYTES);
        long compacted = Files.size(dbFile);
        pool.resumePool();

        LOGGER.atInfo()
                .addKeyValue("h2.grownBytes", grown)
                .addKeyValue("h2.compactedBytes", compacted)
                .addKeyValue("h2.liveBytes", LIVE_BYTES)
                .addKeyValue("h2.limitBytes", COMPACTED_LIMIT_BYTES)
                .log("詰め直しの前と後の大きさ");
        assertThat(compacted).isLessThanOrEqualTo(COMPACTED_LIMIT_BYTES);

        // 再開の後に使え、本文が壊れていない（FR1.8）。
        List<String> readHashes = readAllBodiesVerifyingHashes();
        assertThat(readHashes).containsExactlyInAnyOrderElementsOf(storedHashes);
    }

    @Test
    @DisplayName("a request that needs the database while the pool is suspended waits until resume and then succeeds")
    void requestWaitsWhileSuspendedAndSucceedsAfterResume() throws Exception {
        submitAndApply(1);
        pool.suspendPool();

        AtomicReference<Thread> worker = new AtomicReference<>();
        CompletableFuture<Integer> request = new CompletableFuture<>();
        Thread thread = new Thread(
                () -> {
                    try {
                        request.complete(recordStore.findRevisionsNewestFirst().size());
                    } catch (RuntimeException e) {
                        request.completeExceptionally(e);
                    }
                },
                "suspended-request");
        worker.set(thread);
        thread.start();

        // 一時停止の間は、接続を借りる入口（HikariCP の SuspendResumeLock）で止まって待つ。上限（5 秒）で失敗しない。
        await().atMost(ZERO_CONNECTIONS_WAIT).until(() -> isWaitingForResume(worker.get()));
        assertThat(request).isNotDone();

        pool.resumePool();
        assertThat(request.get(10, TimeUnit.SECONDS)).isEqualTo(1);
    }

    @Test
    @DisplayName("a connection borrowed before suspension stays usable and is closed when it is returned")
    void borrowedConnectionIsClosedOnReturn() throws SQLException {
        Connection borrowed = dataSource.getConnection();
        try {
            pool.suspendPool();
            pool.softEvictConnections();

            // 待機中の接続は閉じ、借りている1本だけが残る。
            await().atMost(ZERO_CONNECTIONS_WAIT)
                    .until(() -> pool.getIdleConnections() == 0 && pool.getTotalConnections() == 1);
            assertThat(pool.getActiveConnections()).isEqualTo(1);
            try (Statement statement = borrowed.createStatement();
                    ResultSet result = statement.executeQuery("SELECT 1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        } finally {
            borrowed.close();
        }

        // 返した時に閉じ、0 本になる（道具が 0 本を待つ前提）。
        await().atMost(ZERO_CONNECTIONS_WAIT).until(() -> pool.getTotalConnections() == 0);
        pool.resumePool();
        assertThat(recordStore.findCurrentRevision()).isEmpty();
    }

    /** 投入（プレビューを置く）と適用を重ね、最後にプレビューを1件置く。残る本文の識別を返す。 */
    private static List<String> submitAndApply(int rounds) {
        Random random = new Random(20260925L);
        List<String> hashes = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            byte[] body = randomBody(random);
            String hash = sha256(body);
            DslPreviewRef preview =
                    recordStore.placePreview(body, hash, DslSource.UPLOAD, ACTOR_USER_ID, T0.plusSeconds(round * 2L));
            recordStore.apply(preview.previewId(), ACTOR_USER_ID, T0.plusSeconds(round * 2L + 1), HISTORY_LIMIT);
            hashes.add(hash);
        }
        List<String> live = new ArrayList<>(hashes.subList(Math.max(0, hashes.size() - HISTORY_LIMIT), hashes.size()));
        byte[] body = randomBody(random);
        String hash = sha256(body);
        recordStore.placePreview(body, hash, DslSource.UPLOAD, ACTOR_USER_ID, T0.plusSeconds(rounds * 2L));
        live.add(hash);
        return live;
    }

    /** 残っている履歴とプレビューの本文を読み、SHA-256 が記録の識別と一致することを確かめ、識別の一覧を返す。 */
    private static List<String> readAllBodiesVerifyingHashes() {
        List<String> hashes = new ArrayList<>();
        for (DslAppliedRef ref : recordStore.findRevisionsNewestFirst()) {
            DslContent<DslAppliedRef> content =
                    recordStore.findRevisionContent(ref.revisionId()).orElseThrow();
            assertThat(sha256(content.yamlBytes())).isEqualTo(ref.dslHash());
            hashes.add(ref.dslHash());
        }
        DslContent<DslPreviewRef> preview = recordStore.findPreviewContent().orElseThrow();
        assertThat(sha256(preview.yamlBytes())).isEqualTo(preview.ref().dslHash());
        hashes.add(preview.ref().dslHash());
        return hashes;
    }

    private static boolean isWaitingForResume(Thread thread) {
        return thread != null
                && thread.getState() == Thread.State.WAITING
                && Arrays.stream(thread.getStackTrace())
                        .anyMatch(frame -> frame.getClassName().endsWith("SuspendResumeLock"));
    }

    private static byte[] randomBody(Random random) {
        byte[] body = new byte[BODY_BYTES];
        random.nextBytes(body);
        return body;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えません", e);
        }
    }
}
