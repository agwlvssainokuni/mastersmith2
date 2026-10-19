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

import cherry.mastersmith.targetdb.config.TargetDbWriteSettings;
import cherry.mastersmith.targetdb.config.TargetWriteDataSourceConfig;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetWriteFailure;
import cherry.mastersmith.targetdb.domain.TargetWriteOutcome;
import cherry.mastersmith.targetdb.domain.TargetWriteSession;
import cherry.mastersmith.targetdb.domain.TargetWriteStage;
import cherry.mastersmith.targetdb.domain.TargetWriteUnexpectedException;
import cherry.mastersmith.targetdb.domain.WriteStep;
import com.zaxxer.hikari.HikariDataSource;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC で業務データの書き込みの接続を貸す {@link TargetWriteAccess}（Intent 261016-business-data-views の U2、BR9.3・BR10.1、
 * reliability-design.md の 2節・3節・7節、logical-components.md の L9）。
 *
 * <p>内部DB のトランザクションに乗せないため、{@code @Transactional} は付けない。名前つきの書き込みの管理者の
 * {@link TransactionTemplate}（READ COMMITTED）を使う。文は作らない（文を作るのは {@code businessdata.repository} の
 * {@code RowSqlExecutor} だけ）。
 *
 * <ul>
 *   <li>設定が使えない・接続が無い → {@link TargetWriteOutcome.NotConfigured}（処理を呼ばない）
 *   <li>借りる失敗（トランザクションを始められない） → {@link TargetWriteOutcome.Unavailable}
 *   <li>処理の非検査の例外 → メッセージも原因も持たない {@link TargetWriteUnexpectedException} にして巻き戻し、段
 *       {@link TargetWriteStage#WORK} の {@link TargetWriteOutcome.Failed}
 *   <li>答えが確定で、確定が失敗 → 段 {@link TargetWriteStage#COMMIT} の {@link TargetWriteOutcome.Failed}（確定したか分からない）
 *   <li>答えが巻き戻しで、巻き戻しが失敗 → 処理の結果のまま {@link TargetWriteOutcome.Done}（保存されていないことは確か）
 *   <li>答えがセーブポイントへ戻して確かめるで、戻し・確かめ・最後の巻き戻しのどれかが失敗 → その答えの {@code onFailure} の結果
 * </ul>
 *
 * <p>{@link TransactionTemplate} の外側で、{@link TransactionSystemException} を含むすべての例外を受け、口のメソッドから例外を出さない
 * （targetdb.service は TraceAspect の対象のため）。確定・巻き戻しが失敗した接続は、管理者が返した後に
 * {@link HikariDataSource#evictConnection} で捨てる（開いているトランザクションの中では捨てない。試しの W12）。捨てる処理の失敗は、例外の
 * クラスの名前だけを WARN で出す。
 *
 * <p>捨てる前の短い間に、同じ接続がほかの要求に貸されうる（NFR 設計のレビュー R-05）。その場合は Hikari の借りるときの生存確認に頼り、
 * 壊れた接続ならその要求が類 08 の失敗（借りる失敗・{@code UNAVAILABLE}）になるだけで、保存の結果は取り違えない。
 */
@Service
public class JdbcTargetWriteAccess implements TargetWriteAccess {

    /** PostgreSQL の、問い合わせの取り消し（待ちの上限による打ち切りを含む）の SQLState。 */
    static final String QUERY_CANCELED = "57014";

    /** MySQL・MariaDB の、問い合わせの中断（待ちの上限による打ち切り）の SQLState。 */
    static final String QUERY_INTERRUPTED = "70100";

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcTargetWriteAccess.class);

    private final TargetDbWriteSettings settings;

    private final Optional<DataSource> dataSource;

    private final Optional<TransactionTemplate> template;

    /**
     * 作る。
     *
     * @param settings 起動時に点検した書き込みの設定
     * @param dataSource 対象DB の書き込みの接続（書き込みの設定が「使える」ときだけある）
     * @param transactionManager 書き込みの接続だけを扱う管理者（書き込みの設定が「使える」ときだけある）
     */
    public JdbcTargetWriteAccess(
            TargetDbWriteSettings settings,
            @Qualifier(TargetWriteDataSourceConfig.TARGET_WRITE_DATA_SOURCE) Optional<DataSource> dataSource,
            @Qualifier(TargetWriteDataSourceConfig.TARGET_WRITE_TRANSACTION_MANAGER)
                    Optional<PlatformTransactionManager> transactionManager) {
        this.settings = settings;
        this.dataSource = dataSource;
        this.template = transactionManager.map(manager -> {
            TransactionTemplate t = new TransactionTemplate(manager);
            t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return t;
        });
    }

    @Override
    public <T> TargetWriteOutcome<T> withWrite(Function<TargetWriteSession, WriteStep<T>> work) {
        Objects.requireNonNull(work, "work は必須です");
        if (!(settings instanceof TargetDbWriteSettings.Usable usable) || dataSource.isEmpty() || template.isEmpty()) {
            return new TargetWriteOutcome.NotConfigured<>();
        }
        int seconds = JdbcTargetSchemaReader.toQueryTimeoutSeconds(usable.queryTimeout());
        Lease<T> lease = new Lease<>();
        try {
            T value = template.get().execute(status -> lend(status, usable.product(), seconds, work, lease));
            return new TargetWriteOutcome.Done<>(value);
        } catch (RuntimeException e) {
            return afterFailure(e, lease);
        }
    }

    @Override
    public Optional<DatabaseProduct> product() {
        return settings instanceof TargetDbWriteSettings.Usable usable
                ? Optional.of(usable.product())
                : Optional.empty();
    }

    /** トランザクションの中で接続を貸し、答えに従う（確定・巻き戻しは管理者が行う）。 */
    private <T> T lend(
            TransactionStatus status,
            DatabaseProduct product,
            int seconds,
            Function<TargetWriteSession, WriteStep<T>> work,
            Lease<T> lease) {
        lease.started = true;
        // 管理者が始めたトランザクションに結び付いた接続を返す（新しく借りない。1本を1回だけ借りる）。
        Connection connection = DataSourceUtils.getConnection(dataSource.orElseThrow());
        lease.connection = connection;
        TargetWriteSession session = new TargetWriteSession(product, connection, seconds, () -> {
            try {
                lease.savepoint = status.createSavepoint();
            } catch (RuntimeException e) {
                throw sqlCause(e);
            }
        });
        WriteStep<T> step;
        try {
            step = Objects.requireNonNull(work.apply(session), "処理の答えは必須です");
        } catch (RuntimeException e) {
            throw new TargetWriteUnexpectedException(e.getClass().getSimpleName());
        }
        lease.step = step;
        return switch (step) {
            case WriteStep.Commit<T> commit -> commit.result();
            case WriteStep.Rollback<T> rollback -> {
                status.setRollbackOnly();
                yield rollback.result();
            }
            case WriteStep.RollbackToSavepointThenInspect<T> inspect -> {
                T result = inspect(status, session, inspect, lease);
                // 戻しの後に付ける（セーブポイントへの戻しが接続の巻き戻しの印を消すため、確かめの後に全体を巻き戻す印を付ける）。
                status.setRollbackOnly();
                yield result;
            }
        };
    }

    /** セーブポイントへ戻し、同じトランザクションの中で確かめる（BR6.2、reliability-design.md の 3節）。 */
    private <T> T inspect(
            TransactionStatus status,
            TargetWriteSession session,
            WriteStep.RollbackToSavepointThenInspect<T> answer,
            Lease<T> lease) {
        T result;
        boolean failed = true;
        if (lease.savepoint == null) {
            result = answer.onFailure()
                    .apply(new TargetWriteFailure(
                            TargetWriteStage.ROLLBACK_TO_SAVEPOINT, null, null, "IllegalStateException", false));
        } else {
            try {
                status.rollbackToSavepoint(lease.savepoint);
            } catch (RuntimeException e) {
                result = answer.onFailure().apply(failure(TargetWriteStage.ROLLBACK_TO_SAVEPOINT, e));
                lease.record(result, true);
                return result;
            }
            try {
                result = answer.inspect().apply(session);
                failed = false;
            } catch (RuntimeException e) {
                result = answer.onFailure()
                        .apply(new TargetWriteFailure(
                                TargetWriteStage.INSPECT,
                                null,
                                null,
                                e.getClass().getSimpleName(),
                                false));
            }
        }
        lease.record(result, failed);
        return result;
    }

    /** 管理者の外側で受けた失敗を、段で分けて結果にする。 */
    private <T> TargetWriteOutcome<T> afterFailure(RuntimeException e, Lease<T> lease) {
        Throwable application = e instanceof TransactionSystemException tse && tse.getApplicationException() != null
                ? tse.getApplicationException()
                : e;
        if (application instanceof TargetWriteUnexpectedException unexpected) {
            if (application != e) {
                // 処理の例外の後の巻き戻しも失敗した。
                evict(lease.connection);
            }
            return new TargetWriteOutcome.Failed<>(
                    new TargetWriteFailure(TargetWriteStage.WORK, null, null, unexpected.exceptionClass(), false));
        }
        if (!lease.started || lease.step == null) {
            // トランザクションを始められない（接続を借りられない・READ COMMITTED を付けられない）。書き込みの文は送っていない。
            evict(lease.connection);
            SQLException sql = firstSql(e);
            return new TargetWriteOutcome.Unavailable<>(
                    sql == null ? null : sql.getSQLState(),
                    sql == null ? null : sql.getErrorCode(),
                    (sql == null ? e : sql).getClass().getName());
        }
        // 管理者が返した後に捨てる（W12）。
        evict(lease.connection);
        return switch (lease.step) {
            case WriteStep.Commit<T> commit -> new TargetWriteOutcome.Failed<>(failure(TargetWriteStage.COMMIT, e));
            case WriteStep.Rollback<T> rollback -> new TargetWriteOutcome.Done<>(rollback.result());
            case WriteStep.RollbackToSavepointThenInspect<T> inspect ->
                new TargetWriteOutcome.Done<>(
                        lease.inspectFailed
                                ? lease.inspectResult
                                : inspect.onFailure().apply(failure(TargetWriteStage.ROLLBACK, e)));
        };
    }

    /**
     * 失敗を、段・SQLState・業者の番号・クラスの名前（連なりに JDBC の例外があればその名前）・時間切れか断絶かだけにする（メッセージと
     * 原因を捨てる）。
     */
    static TargetWriteFailure failure(TargetWriteStage stage, Throwable e) {
        SQLException sql = firstSql(e);
        return new TargetWriteFailure(
                stage,
                sql == null ? null : sql.getSQLState(),
                sql == null ? null : sql.getErrorCode(),
                (sql == null ? e : sql).getClass().getName(),
                interrupted(e));
    }

    /** 時間切れ・通信の断絶か（{@code JdbcTargetSchemaReader#classify} とそろえる）。 */
    static boolean interrupted(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
            if (cause instanceof SQLException e && e.getSQLState() != null) {
                String state = e.getSQLState();
                if (QUERY_CANCELED.equals(state) || QUERY_INTERRUPTED.equals(state) || state.startsWith("08")) {
                    return true;
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static SQLException firstSql(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException e) {
                return e;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    /** セーブポイントを置く失敗を、SQL の失敗として処理に返す（メッセージは処理の側で読まない）。 */
    private static SQLException sqlCause(RuntimeException e) {
        SQLException sql = firstSql(e);
        return sql != null ? sql : new SQLException(null, null, 0);
    }

    /** 確定・巻き戻しが失敗した接続をプールから捨てる。捨てる処理の失敗は、例外のクラスの名前だけを WARN で出す。 */
    private void evict(Connection connection) {
        if (connection == null || dataSource.isEmpty()) {
            return;
        }
        try {
            DataSource source = dataSource.get();
            if (source.isWrapperFor(HikariDataSource.class)) {
                source.unwrap(HikariDataSource.class).evictConnection(connection);
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.atWarn()
                    .addKeyValue("exceptionClass", e.getClass().getName())
                    .log("対象DB の書き込みの接続をプールから捨てられませんでした");
        }
    }

    /** 1回の貸し出しの状態（管理者の外側で、どこまで進んだかを知るため）。 */
    private static final class Lease<T> {

        private boolean started;

        private Connection connection;

        private Object savepoint;

        private WriteStep<T> step;

        private T inspectResult;

        private boolean inspectFailed;

        /** 確かめの結果と、戻し・確かめが失敗して {@code onFailure} の結果にしたかを残す。 */
        void record(T result, boolean failed) {
            this.inspectResult = result;
            this.inspectFailed = failed;
        }
    }
}
