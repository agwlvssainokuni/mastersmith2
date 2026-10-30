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
package cherry.mastersmith.targetdb.service;

import cherry.mastersmith.targetdb.config.TargetDataSourceConfig;
import cherry.mastersmith.targetdb.config.TargetDbSettings;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import cherry.mastersmith.targetdb.domain.TargetReadUnexpectedException;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * JDBC で業務データの読み取りの接続を貸す {@link TargetReadAccess}（BR8.1・BR8.2・BR10.2、reliability-design.md の 2節・5節、
 * NFR 設計の F2: A・レビュー R-02・R-04）。
 *
 * <p>内部DB のトランザクションに乗せないため、{@code @Transactional} は付けない。文は作らない（文を作るのは
 * {@code businessdata.repository} の {@code RowSqlExecutor} だけ）。
 *
 * <ul>
 *   <li>設定が使えない・接続が無い → {@link TargetReadOutcome.NotConfigured}（処理を呼ばない）
 *   <li>借りる失敗・通信の待ちを付ける失敗 → {@link TargetReadOutcome.Unavailable}（分けない。Q3: A）
 *   <li>処理の非検査の例外 → メッセージも原因も持たない {@link TargetReadUnexpectedException} にして投げ直す（R-02）
 *   <li>返すとき: {@code finally} の中で元の通信の待ちに戻し、戻す処理が失敗したらプールから明示して捨ててから閉じる（R-04）。
 *       戻す・閉じる処理の失敗は、例外のクラスの名前だけを WARN で出す
 * </ul>
 */
@Service
public class JdbcTargetReadAccess implements TargetReadAccess {

    /** 目的の上限より、貸すときの通信の待ちを長くする分（F2: A）。 */
    static final Duration NETWORK_TIMEOUT_MARGIN = Duration.ofSeconds(5);

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcTargetReadAccess.class);

    private final TargetDbSettings settings;

    private final Optional<DataSource> dataSource;

    /** {@code setNetworkTimeout} に渡す1つのスレッドの実行の口（MySQL・MariaDB のドライバーは同期で呼ぶため、ほぼ使われない）。 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mastersmith-target-read-timeout");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 作る。
     *
     * @param settings 起動時に点検した対象DB の設定
     * @param dataSource 対象DB の読み取り専用の接続（設定が「使える」ときだけある）
     */
    public JdbcTargetReadAccess(
            TargetDbSettings settings,
            @Qualifier(TargetDataSourceConfig.TARGET_DATA_SOURCE) Optional<DataSource> dataSource) {
        this.settings = settings;
        this.dataSource = dataSource;
    }

    @Override
    public <T> TargetReadOutcome<T> withRead(ReadPurpose purpose, Function<TargetReadSession, T> work) {
        Objects.requireNonNull(purpose, "purpose は必須です");
        Objects.requireNonNull(work, "work は必須です");
        if (purpose != ReadPurpose.LIST && purpose != ReadPurpose.DETAIL && purpose != ReadPurpose.REFERENCE) {
            throw new IllegalArgumentException("業務データの読み取りの目的ではありません");
        }
        if (!(settings instanceof TargetDbSettings.Usable usable) || dataSource.isEmpty()) {
            return new TargetReadOutcome.NotConfigured<>();
        }
        Duration queryTimeout = usable.queryTimeout(purpose);
        int queryTimeoutSeconds = JdbcTargetSchemaReader.toQueryTimeoutSeconds(queryTimeout);
        Connection connection;
        try {
            connection = dataSource.get().getConnection();
        } catch (SQLException e) {
            return unavailable(e);
        } catch (RuntimeException e) {
            return new TargetReadOutcome.Unavailable<>(null, null, e.getClass().getName());
        }
        Integer original = null;
        try {
            original = connection.getNetworkTimeout();
            connection.setNetworkTimeout(executor, toMillis(queryTimeout.plus(NETWORK_TIMEOUT_MARGIN)));
        } catch (SQLException e) {
            giveBack(connection, original, true);
            return unavailable(e);
        }
        TargetReadSession session = new TargetReadSession(usable.product(), connection, queryTimeoutSeconds);
        try {
            T value;
            try {
                value = work.apply(session);
            } catch (RuntimeException e) {
                throw new TargetReadUnexpectedException(e.getClass().getSimpleName());
            }
            return new TargetReadOutcome.Done<>(value);
        } finally {
            // 時間切れ・断絶の後の接続は、壊れた状態でプールへ戻さず捨てる（次の要求が同じ接続を借りて失敗しないため）
            giveBack(connection, original, session.isBroken());
        }
    }

    @Override
    public Optional<DatabaseProduct> product() {
        return settings instanceof TargetDbSettings.Usable usable ? Optional.of(usable.product()) : Optional.empty();
    }

    @Override
    public Duration referenceReadTotal() {
        if (!(settings instanceof TargetDbSettings.Usable usable)) {
            throw new IllegalStateException("対象DB の設定が使える状態ではありません");
        }
        return usable.referenceReadTotal();
    }

    /** Bean を閉じるときに、通信の待ちの実行の口を止める。 */
    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    /**
     * 通信の待ちを元に戻して閉じる。戻す処理が失敗した接続は、プールから明示して捨ててから閉じる（R-04）。
     *
     * @param connection 借りた接続
     * @param original 元の通信の待ち（読めなかったら null）
     * @param evict 戻すかによらず捨てるなら true
     */
    private void giveBack(Connection connection, Integer original, boolean evict) {
        boolean discard = evict || original == null;
        if (!discard) {
            try {
                connection.setNetworkTimeout(executor, original);
            } catch (SQLException | RuntimeException e) {
                discard = true;
                LOGGER.atWarn()
                        .addKeyValue("exceptionClass", e.getClass().getName())
                        .log("対象DB の接続の通信の待ちを元に戻せなかったため、接続を捨てます");
            }
        }
        if (discard && dataSource.isPresent() && dataSource.get() instanceof HikariDataSource hikari) {
            try {
                hikari.evictConnection(connection);
            } catch (RuntimeException e) {
                LOGGER.atWarn()
                        .addKeyValue("exceptionClass", e.getClass().getName())
                        .log("対象DB の接続をプールから捨てられませんでした");
            }
        }
        try {
            connection.close();
        } catch (SQLException | RuntimeException e) {
            LOGGER.atWarn()
                    .addKeyValue("exceptionClass", e.getClass().getName())
                    .log("対象DB の接続を閉じられませんでした");
        }
    }

    private static <T> TargetReadOutcome<T> unavailable(SQLException e) {
        return new TargetReadOutcome.Unavailable<>(
                e.getSQLState(), e.getErrorCode(), e.getClass().getName());
    }

    private static int toMillis(Duration duration) {
        return (int) Math.min(duration.toMillis(), Integer.MAX_VALUE);
    }
}
