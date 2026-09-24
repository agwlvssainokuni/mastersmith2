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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 対象DB の設定の点検と、読み取り専用の接続（{@code targetDataSource}）の作成（BR1.2〜BR1.8、NFR4.5・NFR4.6、NFR7.2・
 * NFR7.4〜NFR7.7、ADR-006）。
 *
 * <p>接続は既定の候補にしない（{@code defaultCandidate = false}）。型で注入される {@code DataSource}・Flyway・JPA の
 * トランザクション・ヘルスチェックは内部DB を使い続ける。使うときは名前（{@link #TARGET_DATA_SOURCE}）で指定する。
 */
@Configuration(proxyBeanMethods = false)
public class TargetDataSourceConfig {

    /** 対象DB の接続の Bean の名前。 */
    public static final String TARGET_DATA_SOURCE = "targetDataSource";

    /** 対象DB の接続のプールの名前。 */
    static final String POOL_NAME = "mastersmith-target-db";

    /**
     * ドライバーの接続・通信の待ちを、プールの待ちより長くする分。プールの待ち（接続の待ちの上限）が先に切れるようにして、
     * 応答しない対象DB を TIMEOUT と見分ける。ドライバーの待ちは、止まった通信の後始末のためだけに置く。
     */
    static final Duration DRIVER_TIMEOUT_MARGIN = Duration.ofSeconds(2);

    /** 問い合わせの待ちの上限より、ドライバーの通信の待ちを長くする分。 */
    static final Duration SOCKET_TIMEOUT_MARGIN = Duration.ofSeconds(5);

    private static final Logger LOGGER = LoggerFactory.getLogger(TargetDataSourceConfig.class);

    /**
     * 起動時に1回だけ設定を点検する。欠け・不正があれば、項目の名前だけを WARN で1件出す（値は出さない。BR1.3、NFR4.3）。
     *
     * @param properties 対象DB の設定
     * @return 点検の結果
     */
    @Bean
    public TargetDbSettings targetDbSettings(TargetDbProperties properties) {
        TargetDbSettings settings = TargetDbSettings.inspect(properties);
        if (settings instanceof TargetDbSettings.Invalid invalid) {
            LOGGER.atWarn()
                    .addKeyValue("items", String.join(", ", invalid.problemItems()))
                    .log("対象DB の設定に欠け・不正があるため、対象DB を使いません。項目を直して再起動してください");
        }
        return settings;
    }

    /**
     * 対象DB の読み取り専用の接続を作る。設定が「使える」ときだけ作り、起動時には接続しない。
     *
     * @param settings 設定の点検の結果
     * @return 対象DB の接続（HikariCP）
     */
    @Bean(name = TARGET_DATA_SOURCE, defaultCandidate = false)
    @Conditional(TargetDbUsableCondition.class)
    public HikariDataSource targetDataSource(TargetDbSettings settings) {
        if (!(settings instanceof TargetDbSettings.Usable usable)) {
            throw new IllegalStateException("対象DB の設定が使える状態ではありません");
        }
        // 設定を渡す作り方はプールをすぐに始めるため、引数なしで作って設定を写す。プールは初めて接続を借りるときに始まる。
        HikariDataSource dataSource = new HikariDataSource();
        hikariConfig(usable).copyStateTo(dataSource);
        return dataSource;
    }

    /**
     * 接続のプールの設定を組み立てる。資格情報は URL に入れず、ユーザー名とパスワードの項目で渡す（NFR4.5）。
     *
     * @param usable 使える設定
     * @return プールの設定
     */
    static HikariConfig hikariConfig(TargetDbSettings.Usable usable) {
        TargetDbProperties properties = usable.properties();
        HikariConfig config = new HikariConfig();
        config.setPoolName(POOL_NAME);
        config.setDriverClassName(driverClassName(usable.product()));
        config.setJdbcUrl(jdbcUrl(usable.product(), properties.host(), usable.port(), properties.database()));
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setReadOnly(true);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(properties.pool().maximumSize());
        config.setIdleTimeout(properties.pool().idleTimeout().toMillis());
        config.setConnectionTimeout(properties.connectTimeout().toMillis());
        // 接続の確かめの待ちは、接続の待ちを超えない値にする（超えると HikariCP が警告を出して切り詰める）。
        config.setValidationTimeout(properties.connectTimeout().toMillis());
        // 起動時に接続しない（プールは初めて使うときに始まる。NFR7.2）。
        config.setInitializationFailTimeout(-1);
        driverProperties(usable.product(), properties).forEach(config::addDataSourceProperty);
        return config;
    }

    /**
     * 接続の URL を、種類・ホスト・番号・DB の名前だけから組み立てる（資格情報を含めない。NFR4.5）。
     *
     * @param product DB の種類
     * @param host ホスト
     * @param port 番号
     * @param database DB の名前
     * @return 接続の URL
     */
    static String jdbcUrl(DatabaseProduct product, String host, int port, String database) {
        String scheme =
                switch (product) {
                    case MYSQL -> "mysql";
                    case MARIADB -> "mariadb";
                    case POSTGRESQL -> "postgresql";
                };
        return "jdbc:" + scheme + "://" + host + ":" + port + "/" + database;
    }

    /**
     * 種類ごとのドライバーのクラスの名前を返す。
     *
     * @param product DB の種類
     * @return ドライバーのクラスの名前
     */
    static String driverClassName(DatabaseProduct product) {
        return switch (product) {
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
        };
    }

    /**
     * 種類ごとのドライバーの設定（接続・通信の待ちと、手元のファイルを送る機能の無効化）を返す。
     *
     * @param product DB の種類
     * @param properties 対象DB の設定
     * @return ドライバーの設定の名前と値
     */
    static Map<String, String> driverProperties(DatabaseProduct product, TargetDbProperties properties) {
        Duration connect = properties.connectTimeout().plus(DRIVER_TIMEOUT_MARGIN);
        Duration longestQuery = properties.queryTimeout().generate();
        if (properties.queryTimeout().compare().compareTo(longestQuery) > 0) {
            longestQuery = properties.queryTimeout().compare();
        }
        Duration socket = longestQuery.plus(SOCKET_TIMEOUT_MARGIN);
        return switch (product) {
            case MYSQL ->
                Map.of(
                        "connectTimeout",
                        Long.toString(connect.toMillis()),
                        "socketTimeout",
                        Long.toString(socket.toMillis()),
                        "allowLoadLocalInfile",
                        "false",
                        "allowUrlInLocalInfile",
                        "false");
            case MARIADB ->
                Map.of(
                        "connectTimeout", Long.toString(connect.toMillis()),
                        "socketTimeout", Long.toString(socket.toMillis()),
                        "allowLocalInfile", "false");
            case POSTGRESQL ->
                Map.of(
                        "connectTimeout", Long.toString(connect.toSeconds()),
                        "loginTimeout", Long.toString(connect.toSeconds()),
                        "socketTimeout", Long.toString(socket.toSeconds()));
        };
    }
}
