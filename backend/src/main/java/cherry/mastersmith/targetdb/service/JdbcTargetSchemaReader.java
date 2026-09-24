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
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.domain.UnavailableReason;
import cherry.mastersmith.targetdb.repository.SchemaQueries;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * JDBC で対象DB のスキーマを読む {@link TargetSchemaReader}（functional-spec.md の 2節、BR1.6・BR1.8・BR1.9、NFR1.1・NFR1.2、
 * NFR4.4）。
 *
 * <p>内部DB のトランザクションに乗せないため、{@code @Transactional} は付けない。待ちの上限は、接続の待ち（プールの設定）と、
 * 目的ごとの問い合わせ1回の待ち（生成 20 秒・照合 5 秒）だけで、読み取りの全体では打ち切らない（照合の全体の上限は作らない。
 * NFR 設計の承認の場の決定 B。応答しない対象DB では、照合でも最悪 接続 3 秒＋5 秒×4 回ほどかかりうる）。失敗は SQLState と
 * 例外の型で TIMEOUT と CONNECTION_FAILED に分け、例外の文言は読まない。ログは WARN で、目的・理由・SQLState（原因の種類）
 * だけを出す。
 */
@Service
public class JdbcTargetSchemaReader implements TargetSchemaReader {

    /** PostgreSQL の、問い合わせの取り消し（待ちの上限による打ち切りを含む）の SQLState。 */
    static final String QUERY_CANCELED = "57014";

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcTargetSchemaReader.class);

    private final TargetDbSettings settings;

    private final Optional<DataSource> dataSource;

    /**
     * 作る。
     *
     * @param settings 起動時に点検した対象DB の設定
     * @param dataSource 対象DB の読み取り専用の接続（設定が「使える」ときだけある）
     */
    public JdbcTargetSchemaReader(
            TargetDbSettings settings,
            @Qualifier(TargetDataSourceConfig.TARGET_DATA_SOURCE) Optional<DataSource> dataSource) {
        this.settings = settings;
        this.dataSource = dataSource;
    }

    @Override
    public TargetSchemaResult readSchema(ReadPurpose purpose) {
        Objects.requireNonNull(purpose, "purpose は必須です");
        if (!(settings instanceof TargetDbSettings.Usable usable) || dataSource.isEmpty()) {
            return TargetSchemaResult.unconfigured();
        }
        int queryTimeoutSeconds = toQueryTimeoutSeconds(usable.queryTimeout(purpose));
        try (Connection connection = dataSource.get().getConnection()) {
            return TargetSchemaResult.success(
                    SchemaQueries.of(usable.product()).read(connection, usable.schema(), queryTimeoutSeconds));
        } catch (SQLException e) {
            UnavailableReason reason = classify(e);
            LOGGER.atWarn()
                    .addKeyValue("purpose", purpose)
                    .addKeyValue("reason", reason)
                    .addKeyValue("sqlState", e.getSQLState())
                    .log("対象DB のスキーマを読めませんでした");
            return TargetSchemaResult.unavailable(reason);
        }
    }

    /**
     * 問い合わせ1回の待ちの上限を、{@code Statement.setQueryTimeout} に当てる秒の数にする。設定の点検で 1 秒以上であることは
     * 確かめ済みで、秒に満たない端数は切り捨てる。
     *
     * @param queryTimeout 問い合わせ1回の待ちの上限
     * @return 秒の数（1 以上）
     */
    static int toQueryTimeoutSeconds(Duration queryTimeout) {
        return (int) Math.min(queryTimeout.getSeconds(), Integer.MAX_VALUE);
    }

    /**
     * 失敗を TIMEOUT と CONNECTION_FAILED に分ける。例外の文言は読まない。
     *
     * <ul>
     *   <li>TIMEOUT: 問い合わせの打ち切り（{@link SQLTimeoutException}、SQLState {@value #QUERY_CANCELED}）、通信の待ちの
     *       打ち切り（原因に {@link SocketTimeoutException}。ドライバーが接続の途中で相手の応答を待ちきれずに
     *       失敗したもの）、接続の待ちの打ち切り（プールが待ちの上限まで接続を作れず、その間に接続の失敗も起きていない＝相手が
     *       応答しない）。
     *   <li>CONNECTION_FAILED: 認証・接続・問い合わせのそのほかの失敗（プールの待ちの打ち切りで、原因に接続の失敗があるものを
     *       含む）。
     * </ul>
     *
     * @param e 失敗
     * @return 理由
     */
    static UnavailableReason classify(SQLException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTimeoutException || cause instanceof SocketTimeoutException) {
                return UnavailableReason.TIMEOUT;
            }
            if (cause instanceof SQLException sql && QUERY_CANCELED.equals(sql.getSQLState())) {
                return UnavailableReason.TIMEOUT;
            }
        }
        if (e instanceof SQLTransientConnectionException && e.getCause() == null) {
            return UnavailableReason.TIMEOUT;
        }
        return UnavailableReason.CONNECTION_FAILED;
    }
}
