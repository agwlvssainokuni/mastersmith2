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
package cherry.mastersmith.internaldb.testsupport;

import static org.assertj.core.api.Assertions.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 待ち・行き詰まりのテストの待ち合わせの道具（NFR 設計 DD-4）。保持側の接続（確定しない）・待つ側を別のスレッドで走らせる部品・
 * {@code pg_stat_activity} の観測（{@code wait_event_type = 'Lock'}。50 ms の間隔で最大 10 秒）を持つ。実時間の {@code sleep} を合否に使わない。
 * 観測が 10 秒で終わらなければ失敗にする。失敗の文は件数だけを出す。
 *
 * <p>{@link #close()} で、保持側の接続を {@code ROLLBACK} して閉じ、待つ側のスレッドを止める。クラスごとの {@code @AfterEach} で
 * {@link #awaitNoWaiters()} を呼び、待ちが後のテストに残らないことを確かめる。
 */
public final class ConflictProbe implements AutoCloseable {

    /** 観測の間隔（ミリ秒）。 */
    static final long INTERVAL_MILLIS = 50L;

    /** 観測の上限（ミリ秒）。 */
    static final long LIMIT_MILLIS = 10_000L;

    private static final String WAITERS_SQL = "SELECT count(*) FROM pg_stat_activity"
            + " WHERE datname = current_database() AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()";

    private final InternalDbPostgresDatabase database;

    private final List<Connection> holders = new ArrayList<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "pg-conflict-probe");
        thread.setDaemon(true);
        return thread;
    });

    private Connection observer;

    /**
     * 作る。
     *
     * @param database 観測する専用のデータベース
     */
    public ConflictProbe(InternalDbPostgresDatabase database) {
        this.database = database;
    }

    /**
     * 保持側の接続を開く（自動確定でない。{@link #release(Connection)} か {@link #close()} で {@code ROLLBACK} して閉じる）。
     *
     * @return 保持側の接続
     * @throws SQLException 接続の失敗
     */
    public Connection openHolder() throws SQLException {
        Connection connection = database.connect();
        connection.setAutoCommit(false);
        holders.add(connection);
        return connection;
    }

    /**
     * 保持側の接続で文を流す（失敗は例外で出る）。
     *
     * @param holder 保持側の接続
     * @param sql 文（行の排他の読み取り・更新など）
     * @throws SQLException 文の失敗
     */
    public static void execute(Connection holder, String sql) throws SQLException {
        try (Statement statement = holder.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * 保持側の接続で文を流し、失敗したときは SQLState を返す（行き詰まりの犠牲側の確かめ用）。
     *
     * @param holder 保持側の接続
     * @param sql 文
     * @return 成功なら null、失敗なら SQLState
     */
    public static String tryExecute(Connection holder, String sql) {
        try {
            execute(holder, sql);
            return null;
        } catch (SQLException e) {
            return e.getSQLState();
        }
    }

    /**
     * 保持側の接続を {@code ROLLBACK} して閉じる。
     *
     * @param holder 保持側の接続
     */
    public void release(Connection holder) {
        holders.remove(holder);
        try {
            holder.rollback();
        } catch (SQLException e) {
            // 閉じられない接続は、閉じる側で捨てる。
        } finally {
            try {
                holder.close();
            } catch (SQLException e) {
                // 何もしない。
            }
        }
    }

    /** 開いている保持側の接続をすべて {@code ROLLBACK} して閉じる。 */
    public void releaseHolders() {
        for (Connection holder : List.copyOf(holders)) {
            release(holder);
        }
    }

    /**
     * 待つ側の呼び出しを別のスレッドで走らせる。
     *
     * @param call 呼び出し
     * @param <T> 結果の型
     * @return 結果
     */
    public <T> Future<T> async(Callable<T> call) {
        return executor.submit(call);
    }

    /**
     * 待つ側の呼び出しを別のスレッドで走らせ、呼び出しの開始からの時間も測る（DD-1）。
     *
     * @param call 呼び出し
     * @param <T> 結果の型
     * @return 結果と時間
     */
    public <T> Future<WaitWindow.Timed<T>> asyncTimed(Callable<T> call) {
        return executor.submit(() -> WaitWindow.measure(call));
    }

    /**
     * 排他の待ち（{@code wait_event_type = 'Lock'}）の数。
     *
     * @return 待っているセッションの数
     */
    public int waiterCount() {
        try (Statement statement = observer().createStatement();
                ResultSet rs = statement.executeQuery(WAITERS_SQL)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("the wait observation failed: SQLState=" + e.getSQLState());
        }
    }

    /**
     * 排他を待つセッションが、指定の数以上になるまで観測する（50 ms の間隔で最大 10 秒）。
     *
     * @param expected 待ちの数
     */
    public void awaitWaiters(int expected) {
        long deadline = System.nanoTime() + LIMIT_MILLIS * 1_000_000L;
        int seen = waiterCount();
        while (seen < expected) {
            if (System.nanoTime() >= deadline) {
                fail("the expected lock waiters were not observed within 10 seconds: expected=" + expected
                        + " observed=" + seen);
            }
            pause();
            seen = waiterCount();
        }
    }

    /** 排他を待つセッションが 1 つ以上になるまで観測する。 */
    public void awaitWaiting() {
        awaitWaiters(1);
    }

    /** 排他を待つセッションが無くなるまで観測する（後のテストに待ちを残さないための確かめ）。 */
    public void awaitNoWaiters() {
        long deadline = System.nanoTime() + LIMIT_MILLIS * 1_000_000L;
        int seen = waiterCount();
        while (seen > 0) {
            if (System.nanoTime() >= deadline) {
                fail("lock waiters remain after the test: observed=" + seen);
            }
            pause();
            seen = waiterCount();
        }
    }

    private synchronized Connection observer() throws SQLException {
        if (observer == null) {
            observer = database.connect();
        }
        return observer;
    }

    private static void pause() {
        try {
            Thread.sleep(INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("the wait observation was interrupted");
        }
    }

    @Override
    public synchronized void close() {
        releaseHolders();
        executor.shutdownNow();
        if (observer != null) {
            try {
                observer.close();
            } catch (SQLException e) {
                // 何もしない。
            }
            observer = null;
        }
    }
}
