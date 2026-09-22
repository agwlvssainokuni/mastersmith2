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

import cherry.mastersmith.auth.domain.LoginAttemptState;
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** ロックの状態の DB アクセスの結合テスト（行の排他、待ちの上限、ダミーの行、明示の更新）。 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class LoginAttemptStateRepositoryIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    LoginAttemptStateRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private long newSubject() {
        String email = "lock-" + UUID.randomUUID() + "@example.com";
        jdbc.update(
                "INSERT INTO users (email, password_hash, admin_flag, created_at) VALUES (?, 'x', FALSE, ?)",
                email,
                OffsetDateTime.now());
        long id = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
        tx.executeWithoutResult(status -> repository.createIfAbsent(id));
        return id;
    }

    /** 行の排他を持ったまま、release が下りるまで待つ処理を別のスレッドで始める。 */
    private Future<?> holdLock(long subjectId, int failures, CountDownLatch locked, CountDownLatch release) {
        return executor.submit(() -> tx.executeWithoutResult(status -> {
            repository.lockForUpdate(subjectId).orElseThrow();
            repository.update(subjectId, failures, null);
            locked.countDown();
            await(release);
        }));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("合図が来なかった");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("a second locked read of the same row sees the first transaction's committed value")
    void secondReadWaitsForCommit() throws Exception {
        long subject = newSubject();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> first = holdLock(subject, 3, locked, release);
        await(locked);

        Future<Integer> second = executor.submit(() -> tx.execute(
                status -> repository.lockForUpdate(subject).orElseThrow().getConsecutiveFailures()));
        release.countDown();
        first.get(30, TimeUnit.SECONDS);

        assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(3);
    }

    @Test
    @DisplayName("waiting for a locked row fails after the three-second limit")
    void lockTimeout() throws Exception {
        long subject = newSubject();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> first = holdLock(subject, 1, locked, release);
        await(locked);

        long start = System.nanoTime();
        Future<Throwable> second = executor.submit(() -> {
            try {
                tx.executeWithoutResult(status -> repository.lockForUpdate(subject));
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        });
        Throwable failure = second.get(30, TimeUnit.SECONDS);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        release.countDown();
        first.get(30, TimeUnit.SECONDS);

        assertThat(failure).isInstanceOf(DataAccessException.class);
        assertThat(elapsedMillis).isBetween(2500L, 9000L);
    }

    @Test
    @DisplayName("a lock on one user's row does not block another user's row")
    void otherRowsDoNotWait() throws Exception {
        long held = newSubject();
        long other = newSubject();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> first = holdLock(held, 1, locked, release);
        await(locked);

        Integer failures = tx.execute(
                status -> repository.lockForUpdate(other).orElseThrow().getConsecutiveFailures());
        release.countDown();
        first.get(30, TimeUnit.SECONDS);

        assertThat(failures).isZero();
    }

    @Test
    @DisplayName("locked reads of dummy rows skip rows held by other attempts instead of waiting")
    void dummyRowsDoNotWait() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<Long> first = executor.submit(() -> tx.execute(status -> {
            long id = repository.lockDummyForUpdate().getSubjectId();
            locked.countDown();
            await(release);
            return id;
        }));
        await(locked);

        long start = System.nanoTime();
        Long secondId = tx.execute(status -> repository.lockDummyForUpdate().getSubjectId());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        release.countDown();
        Long firstId = first.get(30, TimeUnit.SECONDS);

        assertThat(firstId).isNegative();
        assertThat(secondId).isNegative().isNotEqualTo(firstId);
        assertThat(elapsedMillis).isLessThan(2500L);
    }

    @Test
    @DisplayName("the explicit update is issued once even when the values do not change")
    void explicitUpdateAlwaysIssued() {
        long subject = newSubject();

        SqlStatementCounter.start();
        Integer updated = tx.execute(status -> {
            repository.lockForUpdate(subject).orElseThrow();
            return repository.update(subject, 0, null);
        });
        Map<String, List<String>> statements = SqlStatementCounter.stop();

        assertThat(updated).isEqualTo(1);
        assertThat(statements.values())
                .singleElement()
                .isEqualTo(List.of("select login_attempt_states for update", "update login_attempt_states"));
    }

    @Test
    @DisplayName("creating a row that already exists does nothing and keeps the counts")
    void createIfAbsentIsIdempotent() {
        long subject = newSubject();
        Instant lockedUntil = Instant.parse("2026-09-22T00:30:00Z");
        tx.executeWithoutResult(status -> repository.update(subject, 5, lockedUntil));

        tx.executeWithoutResult(status -> repository.createIfAbsent(subject));
        LoginAttemptState state =
                tx.execute(status -> repository.lockForUpdate(subject).orElseThrow());

        assertThat(state.getConsecutiveFailures()).isEqualTo(5);
        assertThat(state.getLockedUntil()).isEqualTo(lockedUntil);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM login_attempt_states WHERE subject_id = ?", Integer.class, subject))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a missing row reads as empty")
    void missingRow() {
        Optional<LoginAttemptState> missing = tx.execute(status -> repository.lockForUpdate(Long.MAX_VALUE));

        assertThat(missing).isEmpty();
    }
}
