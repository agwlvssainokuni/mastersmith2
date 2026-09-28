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
package cherry.mastersmith.invitation.testsupport;

import cherry.mastersmith.invitation.service.InvitationBarrier;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 招待と登録の完了の同時の操作を確実に重ねるテストの待ち合わせの口（{@code reliability-design.md} 2.3・3節）。上限の時間つきで待ち、
 * 実時刻の sleep に頼らない。
 *
 * <ul>
 *   <li>{@link #insertTogether(int)}: 次の招待の追記の直前で、指定した数の要求がそろうまで待たせる（同時の招待）
 *   <li>{@link #whileLocked(Callable)}: 次に行の排他を得た直後に、渡した操作を別のスレッドで始め、その操作が行の排他の待ちに入る
 *       （H2 のセッションの一覧で排他の読み取りの文が実行中になる）か終わるまで待ってから、元の操作を続ける
 * </ul>
 */
public class TestInvitationBarrier implements InvitationBarrier {

    /** 待ち合わせの上限（秒）。 */
    public static final long TIMEOUT_SECONDS = 20;

    private final AtomicReference<CyclicBarrier> insertBarrier = new AtomicReference<>();

    private final AtomicReference<Callable<?>> lockedAction = new AtomicReference<>();

    private final AtomicReference<Future<?>> lockedFuture = new AtomicReference<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final JdbcTemplate jdbc;

    /**
     * 作る。
     *
     * @param dataSource 内部DB（セッションの一覧を読むため）
     */
    public TestInvitationBarrier(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 次の招待の追記の直前で、指定した数の要求をそろえる。
     *
     * @param parties そろえる要求の数
     */
    public void insertTogether(int parties) {
        insertBarrier.set(new CyclicBarrier(parties));
    }

    /**
     * 次に行の排他を得た直後に、別のスレッドで行う操作を決める。
     *
     * @param <T> 操作の結果の型
     * @param action 操作
     * @return 操作の結果（元の操作の後に得る）
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> whileLocked(Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        lockedAction.set(() -> {
            try {
                result.complete(action.call());
            } catch (Exception | Error e) {
                result.completeExceptionally(e);
            }
            return null;
        });
        return result;
    }

    /** 待ち合わせの設定を消す。 */
    public void reset() {
        insertBarrier.set(null);
        lockedAction.set(null);
    }

    @Override
    public void beforeInsert() {
        CyclicBarrier barrier = insertBarrier.get();
        if (barrier == null) {
            return;
        }
        try {
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (BrokenBarrierException | TimeoutException e) {
            throw new IllegalStateException("同時の招待がそろわなかった", e);
        } finally {
            insertBarrier.compareAndSet(barrier, null);
        }
    }

    @Override
    public void afterLock(long invitationId) {
        Callable<?> action = lockedAction.getAndSet(null);
        if (action == null) {
            return;
        }
        Future<?> future = executor.submit(action);
        lockedFuture.set(future);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (future.isDone() || waitingForLock()) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("別の操作が行の排他の待ちに入らなかった");
    }

    private boolean waitingForLock() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE LOWER(EXECUTING_STATEMENT) LIKE"
                        + " '%from invitations%for update%'",
                Integer.class);
        return count != null && count > 0;
    }

    /** テストで差し替える設定。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /**
         * 本番の何もしない待ち合わせの口の代わりに使う。
         *
         * @param dataSource 内部DB
         * @return 待ち合わせの口
         */
        @Bean
        @Primary
        public TestInvitationBarrier testInvitationBarrier(DataSource dataSource) {
            return new TestInvitationBarrier(dataSource);
        }
    }
}
