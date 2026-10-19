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
package cherry.mastersmith.targetdb.config;

import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * 対象DB の書き込みの接続（{@code targetWriteDataSource}）と、その接続だけを扱うトランザクションの管理者
 * （{@code targetWriteTransactionManager}）の作成（Intent 261016-business-data-views の U2、BR9.1・BR9.2、NFR4.3、
 * infrastructure-specification.md の 2.1）。
 *
 * <p>どちらも既定の候補にしない（{@code defaultCandidate = false}）。型で注入される {@code DataSource}・
 * {@code PlatformTransactionManager}（{@code @Transactional} の既定）・Flyway・JPA は内部DB を使い続ける。使うときは名前
 * （{@link #TARGET_WRITE_DATA_SOURCE}・{@link #TARGET_WRITE_TRANSACTION_MANAGER}）で指定する。書き込みの設定が「使える」ときだけ作り、
 * 起動時には接続しない。
 *
 * <p>接続先は読み取りの設定をそのまま使い、資格情報だけを書き込みのものにする（URL に資格情報を入れない）。読み取り専用にしない。通信の
 * 待ちと MySQL・MariaDB の初期化の文はプールの設定で付け、貸すたびには変えない（NFR 設計 Q3: A）。
 */
@Configuration(proxyBeanMethods = false)
public class TargetWriteDataSourceConfig {

    /** 対象DB の書き込みの接続の Bean の名前。 */
    public static final String TARGET_WRITE_DATA_SOURCE = "targetWriteDataSource";

    /** 対象DB の書き込みの接続だけを扱うトランザクションの管理者の Bean の名前。 */
    public static final String TARGET_WRITE_TRANSACTION_MANAGER = "targetWriteTransactionManager";

    /** 対象DB の書き込みの接続のプールの名前（監視の系列 {@code pool="mastersmith-target-db-write"}）。 */
    static final String POOL_NAME = "mastersmith-target-db-write";

    /**
     * 対象DB の書き込みの接続を作る。書き込みの設定が「使える」ときだけ作り、起動時には接続しない。
     *
     * @param writeSettings 書き込みの設定の点検の結果
     * @return 対象DB の書き込みの接続（HikariCP）
     */
    @Bean(name = TARGET_WRITE_DATA_SOURCE, defaultCandidate = false)
    @Conditional(TargetDbWriteUsableCondition.class)
    public HikariDataSource targetWriteDataSource(TargetDbWriteSettings writeSettings) {
        if (!(writeSettings instanceof TargetDbWriteSettings.Usable usable)) {
            throw new IllegalStateException("対象DB の書き込みの設定が使える状態ではありません");
        }
        // 設定を渡す作り方はプールをすぐに始めるため、引数なしで作って設定を写す。プールは初めて接続を借りるときに始まる。
        HikariDataSource dataSource = new HikariDataSource();
        hikariConfig(usable).copyStateTo(dataSource);
        return dataSource;
    }

    /**
     * 書き込みの接続だけを扱うトランザクションの管理者を作る（BR9.2）。既定の候補にしないため、内部DB の管理者は退かない。
     *
     * @param dataSource 対象DB の書き込みの接続
     * @return トランザクションの管理者
     */
    @Bean(name = TARGET_WRITE_TRANSACTION_MANAGER, defaultCandidate = false)
    @Conditional(TargetDbWriteUsableCondition.class)
    public DataSourceTransactionManager targetWriteTransactionManager(
            @Qualifier(TARGET_WRITE_DATA_SOURCE) DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 書き込みのプールの設定を組み立てる。資格情報は URL に入れず、ユーザー名とパスワードの項目で渡す（NFR4.3）。
     *
     * @param usable 使える書き込みの設定
     * @return プールの設定
     */
    static HikariConfig hikariConfig(TargetDbWriteSettings.Usable usable) {
        TargetDbProperties properties = usable.read().properties();
        DatabaseProduct product = usable.product();
        HikariConfig config = new HikariConfig();
        config.setPoolName(POOL_NAME);
        config.setDriverClassName(TargetDataSourceConfig.driverClassName(product));
        config.setJdbcUrl(TargetDataSourceConfig.jdbcUrl(
                product, properties.host(), usable.read().port(), properties.database()));
        config.setUsername(usable.write().username());
        config.setPassword(usable.write().password());
        config.setReadOnly(false);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(usable.write().pool().maximumSize());
        // 使っていない接続を閉じるまでの時間は、読み取りの pool.idle-timeout を共用する（Q1: A）。
        config.setIdleTimeout(properties.pool().idleTimeout().toMillis());
        // 借りる待ちと確かめの待ちは、既存の接続の待ちを共用する。
        config.setConnectionTimeout(properties.connectTimeout().toMillis());
        config.setValidationTimeout(properties.connectTimeout().toMillis());
        // 起動時に接続しない（プールは初めて使うときに始まる）。
        config.setInitializationFailTimeout(-1);
        if (product != DatabaseProduct.POSTGRESQL) {
            // 接続を作るときに1回だけ、タイムゾーンと sql_mode を付ける（Q3: A・Q6: A。貸すたびには流さない）。
            config.setConnectionInitSql(TargetDataSourceConfig.WRITE_CONNECTION_INIT_SQL);
        }
        driverProperties(product, properties.connectTimeout(), usable.queryTimeout())
                .forEach(config::addDataSourceProperty);
        return config;
    }

    /**
     * 種類ごとのドライバーの設定を返す。通信の待ちは、書き込みの文の上限 + 5 秒を計算で求める（既定 10 秒。U1 の基盤の設計のレビュー
     * R-01 を先に避ける）。件数は「一致した行」に固定し（{@code useAffectedRows=false}。BR8.2）、手元のファイルを送る機能を無効にする。
     *
     * @param product DB の種類
     * @param connectTimeout 接続の待ちの上限
     * @param writeTimeout 書き込みの文1回の待ちの上限
     * @return ドライバーの設定の名前と値
     */
    static Map<String, String> driverProperties(
            DatabaseProduct product, Duration connectTimeout, Duration writeTimeout) {
        Duration connect = connectTimeout.plus(TargetDataSourceConfig.DRIVER_TIMEOUT_MARGIN);
        Duration socket = writeTimeout.plus(TargetDataSourceConfig.SOCKET_TIMEOUT_MARGIN);
        return switch (product) {
            case MYSQL ->
                Map.of(
                        "connectTimeout",
                        Long.toString(connect.toMillis()),
                        "socketTimeout",
                        Long.toString(socket.toMillis()),
                        "useAffectedRows",
                        "false",
                        "allowLoadLocalInfile",
                        "false",
                        "allowUrlInLocalInfile",
                        "false");
            case MARIADB ->
                Map.of(
                        "connectTimeout",
                        Long.toString(connect.toMillis()),
                        "socketTimeout",
                        Long.toString(socket.toMillis()),
                        "useAffectedRows",
                        "false",
                        "allowLocalInfile",
                        "false");
            case POSTGRESQL ->
                Map.of(
                        "connectTimeout", Long.toString(connect.toSeconds()),
                        "loginTimeout", Long.toString(connect.toSeconds()),
                        "socketTimeout", Long.toString(socket.toSeconds()));
        };
    }
}
