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
package cherry.mastersmith.useradmin.testsupport;

import cherry.mastersmith.useradmin.domain.AdminOperation;
import cherry.mastersmith.useradmin.service.UserAdminBarrier;
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
 * 印を付ける・外す・止めるの同時の重なりを確実に作るテストの待ち合わせの口（Intent 260930-user-admin の U3、BR3.6、
 * {@code reliability-design.md} 6節）。スレッドの数や実時刻の sleep に頼らない。
 *
 * <p>{@link #whileCounting(AdminOperation, long, Callable)} で決めた操作と対象の1つ目の操作が、行を排他して数える直前に来たら、渡した
 * 2つ目の操作を別のスレッドで始め、2つ目が利用者の行の排他の待ちに入る（H2 のセッションの一覧で {@code from users ... for update} の文が
 * 実行中になる）か終わるまで待ってから、1つ目を続ける。1つ目を止めておく時間の上限は {@link #HOLD_LIMIT}（排他の待ちの上限
 * 3000 ミリ秒より短い。超えると2つ目が上限切れになり、意図した重なりにならないため。R-03）。上限を過ぎたら、何を待っていたか（操作の
 * 区分と利用者 ID だけ）を書いて失敗にする。
 */
public class TestUserAdminBarrier implements UserAdminBarrier {

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
    public TestUserAdminBarrier(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 次に、指定した操作と対象の1つ目が数える直前に来たら、別のスレッドで2つ目の操作を始めるように決める。
     *
     * @param <T> 2つ目の結果の型
     * @param operation 1つ目の操作の区分
     * @param targetUserId 1つ目の対象の利用者 ID
     * @param second 2つ目の操作
     * @return 2つ目の結果
     */
    public <T> CompletableFuture<T> whileCounting(AdminOperation operation, long targetUserId, Callable<T> second) {
        CompletableFuture<T> result = new CompletableFuture<>();
        secondDoneAtRelease = null;
        armed.set(new Armed(operation, targetUserId, () -> {
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
     * 1つ目を続けた時点で、2つ目がもう終わっていたかを返す（待たずに通ったことの確かめに使う）。
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
    public void beforeCount(AdminOperation operation, long targetUserId) {
        Armed current = armed.get();
        if (current == null || current.operation() != operation || current.targetUserId() != targetUserId) {
            return;
        }
        if (!armed.compareAndSet(current, null)) {
            return;
        }
        Future<?> future = executor.submit(current.action());
        long deadline = System.nanoTime() + HOLD_LIMIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (future.isDone()) {
                secondDoneAtRelease = true;
                return;
            }
            if (waitingForUserRows()) {
                secondDoneAtRelease = false;
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException(
                "2つ目の操作が上限の時間のうちに利用者の行の排他の待ちに入らなかった（operation=" + operation + ", targetUserId=" + targetUserId + "）");
    }

    private boolean waitingForUserRows() {
        // 絞り込みは引数で渡す（文の中に書くと、この問い合わせ自身の文が絞り込みに当たってしまうため）。自分のセッションは除く。
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE LOWER(EXECUTING_STATEMENT) LIKE ?"
                        + " AND SESSION_ID <> SESSION_ID()",
                Integer.class,
                "%from users%for update%");
        return count != null && count > 0;
    }

    private record Armed(AdminOperation operation, long targetUserId, Callable<?> action) {}

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
        public TestUserAdminBarrier testUserAdminBarrier(DataSource dataSource) {
            return new TestUserAdminBarrier(dataSource);
        }
    }
}
