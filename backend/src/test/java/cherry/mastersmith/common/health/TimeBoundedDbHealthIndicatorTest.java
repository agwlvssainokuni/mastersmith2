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
package cherry.mastersmith.common.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class TimeBoundedDbHealthIndicatorTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(50);

    private final CountDownLatch release = new CountDownLatch(1);

    private final CountDownLatch finished = new CountDownLatch(1);

    private TimeBoundedDbHealthIndicator indicator;

    @AfterEach
    void tearDown() {
        release.countDown();
        if (indicator != null) {
            indicator.destroy();
        }
    }

    private TimeBoundedDbHealthIndicator create(DataSource dataSource, Duration timeout) {
        indicator = new TimeBoundedDbHealthIndicator(dataSource, new HealthProperties(timeout));
        return indicator;
    }

    private static Connection healthyConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);
        return connection;
    }

    /** 中断されても待ち続ける（応答しない JDBC の呼び出しの代わり）。 */
    private void awaitReleaseUninterruptibly() {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    release.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            finished.countDown();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("successful probe query reports UP without details")
    void upWhenQuerySucceeds() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = healthyConnection();
        when(dataSource.getConnection()).thenReturn(connection);

        Health health = create(dataSource, Duration.ofSeconds(2)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEmpty();
        verify(connection).close();
    }

    @Test
    @DisplayName("failing connection reports DOWN without details")
    void downWhenConnectionFails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("接続できません"));

        Health health = create(dataSource, Duration.ofSeconds(2)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("failing probe query reports DOWN")
    void downWhenQueryFails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenThrow(new SQLException("問い合わせに失敗"));

        assertThat(create(dataSource, Duration.ofSeconds(2)).health().getStatus())
                .isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("waiting to borrow a connection counts toward the time limit and reports DOWN")
    void downWhenBorrowingTimesOut() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            release.await();
            return healthyConnection();
        });

        long start = System.nanoTime();
        Health health = create(dataSource, SHORT_TIMEOUT).health();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(elapsedMillis).isLessThan(5_000);
    }

    @Test
    @DisplayName("a probe query that does not finish within the time limit reports DOWN")
    void downWhenQueryTimesOut() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenAnswer(invocation -> {
            release.await();
            return true;
        });

        assertThat(create(dataSource, SHORT_TIMEOUT).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("while the previous probe is still running no new probe starts and DOWN is reported")
    void noNewProbeWhilePreviousRuns() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = healthyConnection();
        when(dataSource.getConnection())
                .thenAnswer(invocation -> {
                    awaitReleaseUninterruptibly();
                    return connection;
                })
                .thenReturn(connection);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        indicator = new TimeBoundedDbHealthIndicator(dataSource, new HealthProperties(SHORT_TIMEOUT), executor);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        verify(dataSource, times(1)).getConnection();

        release.countDown();
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        // 確認のスレッドは1本のため、後ろに並べた空の処理が終われば、止めていた確認は後始末まで終わっている。
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        verify(dataSource, times(2)).getConnection();
    }

    @Test
    @DisplayName("a stopped executor that rejects the probe reports DOWN and lets the next probe be attempted")
    void downWhenExecutorRejectsProbe() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        SubmitCountingExecutor executor = new SubmitCountingExecutor();
        executor.shutdown();
        indicator = new TimeBoundedDbHealthIndicator(dataSource, new HealthProperties(SHORT_TIMEOUT), executor);

        Health first = indicator.health();
        Health second = indicator.health();

        assertThat(first.getStatus()).isEqualTo(Status.DOWN);
        assertThat(first.getDetails()).isEmpty();
        assertThat(second.getStatus()).isEqualTo(Status.DOWN);
        // 1回目で受け付けられなかった後も次の確認を許す印が戻っていれば、2回目も実行の枠へ渡そうとする
        // （戻っていなければ「前の確認が終わっていない」として、実行の枠へ渡さずに DOWN を返す）。
        assertThat(executor.submits()).isEqualTo(2);
        verify(dataSource, never()).getConnection();
    }

    @Test
    @DisplayName("an interrupt while waiting for the probe reports DOWN and keeps the interrupt flag")
    void downWhenWaitingIsInterrupted() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            awaitReleaseUninterruptibly();
            return healthyConnection();
        });
        TimeBoundedDbHealthIndicator target = create(dataSource, Duration.ofSeconds(30));

        // 確認は release まで終わらないため、割り込みの印を立てて呼ぶと、結果の待ちは制限時間を待たずに割り込まれる
        Health health;
        boolean interruptKept;
        Thread.currentThread().interrupt();
        try {
            health = target.health();
        } finally {
            // 印を消して確かめる（後のテストに印を残さない）
            interruptKept = Thread.interrupted();
        }

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).isEmpty();
        assertThat(interruptKept).isTrue();
    }

    /** 確認を実行の枠へ渡した回数を数える実行の枠（スレッド1本）。止めた後は渡された確認を受け付けない。 */
    private static final class SubmitCountingExecutor extends ThreadPoolExecutor {

        private final AtomicInteger submits = new AtomicInteger();

        SubmitCountingExecutor() {
            super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }

        @Override
        public Future<?> submit(Runnable task) {
            submits.incrementAndGet();
            return super.submit(task);
        }

        int submits() {
            return submits.get();
        }
    }
}
