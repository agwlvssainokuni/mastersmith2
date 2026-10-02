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
package cherry.mastersmith.auth.testsupport;

import cherry.mastersmith.auth.service.LoginAttemptBarrier;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ロックの状態の行を排他した直後の同時の重なりを確実に作るテストの待ち合わせの口（Intent 260930-user-admin の U3、BR3.6、
 * {@code reliability-design.md} 3節・6節）。失敗回数を戻す操作の1段目と、ログインの判定の両方が通る口を差し替える。
 *
 * <p>{@link #whileLocked(long, Callable)} で決めた利用者の行を1つ目が排他した直後に、渡した2つ目の操作を別のスレッドで始め、2つ目が
 * ロックの状態の行の排他の待ちに入る（H2 のセッションの一覧で {@code from login_attempt_states ... for update} の文が実行中になる）か
 * 終わるまで待ってから、1つ目を続ける。1つ目を止めておく時間の上限は {@link #HOLD_LIMIT}（排他の待ちの上限 3000 ミリ秒より短い。R-03）。
 * sleep に頼らない。
 */
public class TestLoginAttemptBarrier implements LoginAttemptBarrier {

    /** 1つ目を止めておく時間の上限（排他の待ちの上限 3000 ミリ秒より短い）。 */
    public static final Duration HOLD_LIMIT = Duration.ofMillis(2000);

    private final AtomicReference<Armed> armed = new AtomicReference<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final JdbcTemplate jdbc;

    private volatile Boolean secondDoneAtRelease;

    /**
     * 作る。
     *
     * @param dataSource 内部DB（セッションの一覧を読むため）
     */
    public TestLoginAttemptBarrier(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 次に、指定した利用者のロックの状態の行を1つ目が排他した直後に、別のスレッドで2つ目の操作を始めるように決める。
     *
     * @param <T> 2つ目の結果の型
     * @param userId 1つ目が排他する行の利用者 ID
     * @param second 2つ目の操作
     * @return 2つ目の結果
     */
    public <T> CompletableFuture<T> whileLocked(long userId, Callable<T> second) {
        CompletableFuture<T> result = new CompletableFuture<>();
        secondDoneAtRelease = null;
        armed.set(new Armed(userId, () -> {
            try {
                result.complete(second.call());
            } catch (Exception | Error e) {
                result.completeExceptionally(e);
            }
            return null;
        }));
        return result;
    }

    /**
     * 1つ目を続けた時点で、2つ目がもう終わっていたかを返す。
     *
     * @return 終わっていたら真、待ちに入っていたら偽、待ち合わせが起きていなければ null
     */
    public Boolean secondDoneAtRelease() {
        return secondDoneAtRelease;
    }

    /** 待ち合わせの設定を消す。 */
    public void reset() {
        armed.set(null);
        secondDoneAtRelease = null;
    }

    @Override
    public void afterLock(long userId) {
        Armed current = armed.get();
        if (current == null || current.userId() != userId || !armed.compareAndSet(current, null)) {
            return;
        }
        Future<?> future = executor.submit(current.action());
        long deadline = System.nanoTime() + HOLD_LIMIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (future.isDone()) {
                secondDoneAtRelease = true;
                return;
            }
            if (waitingForAttemptRow()) {
                secondDoneAtRelease = false;
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("2つ目の操作が上限の時間のうちにロックの状態の行の排他の待ちに入らなかった（userId=" + userId + "）");
    }

    private boolean waitingForAttemptRow() {
        // 絞り込みは引数で渡す（文の中に書くと、この問い合わせ自身の文が絞り込みに当たってしまうため）。自分のセッションは除く。
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE LOWER(EXECUTING_STATEMENT) LIKE ?"
                        + " AND SESSION_ID <> SESSION_ID()",
                Integer.class,
                "%from login_attempt_states%for update%");
        return count != null && count > 0;
    }

    private record Armed(long userId, Callable<?> action) {}

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
        public TestLoginAttemptBarrier testLoginAttemptBarrier(DataSource dataSource) {
            return new TestLoginAttemptBarrier(dataSource);
        }
    }
}
