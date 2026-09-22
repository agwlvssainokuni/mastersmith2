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
package cherry.mastersmith.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.domain.RefreshToken;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** リフレッシュトークンの DB アクセスの結合テスト（ハッシュでの探索、条件付きの無効化、件数ごとの削除）。 */
@SpringBootTest
class RefreshTokenRepositoryIT {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RefreshTokenRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private long userId;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM refresh_tokens");
        String email = "token-" + UUID.randomUUID() + "@example.com";
        jdbc.update(
                "INSERT INTO users (email, password_hash, admin_flag, created_at) VALUES (?, 'x', FALSE, ?)",
                email,
                OffsetDateTime.now());
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static byte[] randomHash() {
        byte[] hash = new byte[32];
        RANDOM.nextBytes(hash);
        return hash;
    }

    private RefreshToken save(byte[] hash, Instant expiresAt) {
        return repository.save(new RefreshToken(userId, hash, expiresAt.minus(Duration.ofHours(24)), expiresAt));
    }

    @Test
    @DisplayName("a token is found by its hash and another hash finds nothing")
    void findByHash() {
        byte[] hash = randomHash();
        RefreshToken saved = save(hash, NOW.plus(Duration.ofHours(24)));

        assertThat(repository.findByTokenHash(hash))
                .hasValueSatisfying(found -> assertThat(found.getTokenId()).isEqualTo(saved.getTokenId()));
        assertThat(repository.findByTokenHash(randomHash())).isEmpty();
    }

    @Test
    @DisplayName("conditional revoke succeeds once and then updates nothing")
    void revokeOnce() {
        RefreshToken saved = save(randomHash(), NOW.plus(Duration.ofHours(24)));

        Integer first = tx.execute(status -> repository.revokeIfActive(saved.getTokenId(), NOW));
        Integer second = tx.execute(status -> repository.revokeIfActive(saved.getTokenId(), NOW.plusSeconds(1)));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(repository.findById(saved.getTokenId()).orElseThrow().getRevokedAt())
                .isEqualTo(NOW);
    }

    @Test
    @DisplayName("of two simultaneous revokes of the same token only one succeeds")
    void concurrentRevoke() throws Exception {
        RefreshToken saved = save(randomHash(), NOW.plus(Duration.ofHours(24)));
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> revoke = () -> {
            start.await(30, TimeUnit.SECONDS);
            return tx.execute(status -> repository.revokeIfActive(saved.getTokenId(), NOW));
        };
        List<Future<Integer>> results = new ArrayList<>();
        results.add(executor.submit(revoke));
        results.add(executor.submit(revoke));
        start.countDown();

        int total = 0;
        for (Future<Integer> result : results) {
            total += result.get(30, TimeUnit.SECONDS);
        }
        assertThat(total).isEqualTo(1);
    }

    @Test
    @DisplayName("cleanup deletes only rows expired before the cutoff, in batches of the limit")
    void deleteInBatches() {
        Instant cutoff = NOW.minus(Duration.ofDays(7));
        for (int i = 0; i < 5; i++) {
            save(randomHash(), cutoff.minus(Duration.ofMinutes(i + 1)));
        }
        RefreshToken recent = save(randomHash(), cutoff.plusSeconds(1));
        RefreshToken active = save(randomHash(), NOW.plus(Duration.ofHours(1)));

        List<Integer> batches = new ArrayList<>();
        int deleted;
        do {
            deleted = tx.execute(status -> repository.deleteExpiredBefore(cutoff, 2));
            batches.add(deleted);
        } while (deleted > 0);

        assertThat(batches).containsExactly(2, 2, 1, 0);
        assertThat(repository.findAll())
                .extracting(RefreshToken::getTokenId)
                .containsExactlyInAnyOrder(recent.getTokenId(), active.getTokenId());
    }

    @Test
    @DisplayName("the batch limit constant is one thousand rows")
    void batchLimit() {
        assertThat(RefreshTokenRepository.DELETE_BATCH_SIZE).isEqualTo(1000);
    }
}
