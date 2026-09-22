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

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 制限時間付きの内部DBの確認（BR1.1〜BR1.3、NFR1.1、NFR1.2）。Actuator の既定の DB の確認を置き換える。
 *
 * <p>確認は専用のスレッド1本で行い、コネクションプールから接続を借りて {@code SELECT 1} を1回行う。呼び出し側は制限時間だけ待ち、
 * 時間内に終わらなければ確認を中断して DOWN とする。前の確認がまだ終わっていなければ、新しい確認を始めずに DOWN とする
 * （内部DBが応答しない間に要求が続いても、スレッドや接続を使い果たさない）。接続を借りる待ちも制限時間に含まれる。応答は状態だけ。
 */
@Component
public class TimeBoundedDbHealthIndicator implements HealthIndicator, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeBoundedDbHealthIndicator.class);

    private final DataSource dataSource;

    private final Duration timeout;

    private final ExecutorService executor;

    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    /**
     * 確認の仕組みを作る。
     *
     * @param dataSource 内部DBの接続
     * @param properties ヘルスチェックの設定
     */
    @Autowired
    public TimeBoundedDbHealthIndicator(DataSource dataSource, HealthProperties properties) {
        this(dataSource, properties, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "health-db-check");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /**
     * 実行の枠を指定して確認の仕組みを作る（テストで確認の終わりを待つために使う）。
     *
     * @param dataSource 内部DBの接続
     * @param properties ヘルスチェックの設定
     * @param executor 確認を行う実行の枠（スレッド1本）
     */
    TimeBoundedDbHealthIndicator(DataSource dataSource, HealthProperties properties, ExecutorService executor) {
        this.dataSource = dataSource;
        this.timeout = properties.dbTimeout();
        this.executor = executor;
    }

    @Override
    public Health health() {
        if (!inFlight.compareAndSet(false, true)) {
            LOGGER.atWarn().addKeyValue("reason", "previous-check-running").log("内部DBの確認を行えませんでした");
            return Health.down().build();
        }
        Future<?> future;
        try {
            future = executor.submit(this::query);
        } catch (RuntimeException e) {
            inFlight.set(false);
            LOGGER.atWarn()
                    .addKeyValue("reason", "submit-failed")
                    .addKeyValue("errorType", e.getClass().getName())
                    .log("内部DBの確認を行えませんでした");
            return Health.down().build();
        }
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return Health.up().build();
        } catch (TimeoutException e) {
            future.cancel(true);
            LOGGER.atWarn()
                    .addKeyValue("reason", "timeout")
                    .addKeyValue("timeoutMillis", timeout.toMillis())
                    .log("内部DBの確認が制限時間内に終わりませんでした");
            return Health.down().build();
        } catch (ExecutionException e) {
            LOGGER.atWarn()
                    .addKeyValue("reason", "error")
                    .addKeyValue("errorType", e.getCause().getClass().getName())
                    .log("内部DBの確認に失敗しました");
            return Health.down().build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return Health.down().build();
        }
    }

    /** 接続を1本借りて確認の問い合わせを1回行う。終わったら（失敗・中断を含む）次の確認を許す。 */
    private void query() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout((int) Math.max(1, Math.ceilDiv(timeout.toMillis(), 1000)));
            statement.execute("SELECT 1");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("内部DBの確認の問い合わせに失敗しました", e);
        } finally {
            inFlight.set(false);
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
