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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 対象DB の接続の設定（{@code mastersmith.target-db.*}）。接続先はこの設定だけから受け取り、画面・API・DSL から受け取る口は
 * 持たない（BR1.1、NFR4.1）。
 *
 * <p>種類とポートも文字列で受ける。数や列挙で受けると、値が不正なときに設定の結び付けで起動が止まるため、点検
 * （{@link TargetDbSettings#inspect}）で判定して、起動は続ける（BR1.3）。
 *
 * @param type DB の種類（{@code mysql}・{@code mariadb}・{@code postgresql}）
 * @param host 接続先のホスト名（秘密に準じる。文字列にするときは有無だけ）
 * @param port 接続先の番号（1〜65535。文字列にするときは有無だけ）
 * @param database 接続する DB の名前（文字列にするときは有無だけ）
 * @param schema 読み取るスキーマの名前
 * @param username 接続のユーザー名（文字列にするときは有無だけ）
 * @param password 接続のパスワード（秘密。文字列にするときは有無だけ。BR1.4）
 * @param connectTimeout 接続の待ちの上限（既定 3 秒）
 * @param queryTimeout 問い合わせ1回の待ちの上限（目的ごと）
 * @param pool 接続のプールの設定
 */
@ConfigurationProperties("mastersmith.target-db")
public record TargetDbProperties(
        String type,
        String host,
        String port,
        String database,
        String schema,
        String username,
        String password,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue QueryTimeout queryTimeout,
        @DefaultValue Pool pool) {

    /** 設定の接頭辞。 */
    public static final String PREFIX = "mastersmith.target-db";

    /** 接続の待ちの既定値。 */
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** 空の値（環境変数の値が空など）で null になった項目を既定値にする。 */
    public TargetDbProperties {
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        queryTimeout = queryTimeout == null ? new QueryTimeout(null, null) : queryTimeout;
        pool = pool == null ? new Pool(null, null) : pool;
    }

    /**
     * 問い合わせ1回の待ちの上限（{@code Statement.setQueryTimeout}。秒の単位で当てる）。
     *
     * @param generate 既定の DSL の生成のとき（既定 20 秒）
     * @param compare 照合のとき（既定 5 秒）
     */
    public record QueryTimeout(
            @DefaultValue("20s") Duration generate,
            @DefaultValue("5s") Duration compare) {

        /** 空の値で null になった項目を既定値にする。 */
        public QueryTimeout {
            generate = generate == null ? Duration.ofSeconds(20) : generate;
            compare = compare == null ? Duration.ofSeconds(5) : compare;
        }
    }

    /**
     * 対象DB の接続のプール（内部DB のプールとは別）。
     *
     * @param maximumSize 接続の数の上限（既定 5）
     * @param idleTimeout 使っていない接続を閉じるまでの時間（既定 60 秒）
     */
    public record Pool(
            @DefaultValue("5") Integer maximumSize,
            @DefaultValue("60s") Duration idleTimeout) {

        /** 空の値で null になった項目を既定値にする。 */
        public Pool {
            maximumSize = maximumSize == null ? Integer.valueOf(5) : maximumSize;
            idleTimeout = idleTimeout == null ? Duration.ofSeconds(60) : idleTimeout;
        }
    }

    /**
     * 接続先・資格情報を伏せて文字列にする（BR1.4、NFR4.2）。種類・スキーマ名・待ち時間だけを値で出し、ホスト・番号・DB の
     * 名前・ユーザー名・パスワードは値の有無だけにする。
     */
    @Override
    public String toString() {
        return "TargetDbProperties[type=" + type
                + ", host=" + presence(host)
                + ", port=" + presence(port)
                + ", database=" + presence(database)
                + ", schema=" + schema
                + ", username=" + presence(username)
                + ", password=" + presence(password)
                + ", connectTimeout=" + connectTimeout
                + ", queryTimeout=" + queryTimeout
                + ", pool=" + pool + "]";
    }

    /**
     * 値の有無だけを表す文字列を返す。
     *
     * @param value 値
     * @return {@code ***}（値がある）または {@code (empty)}
     */
    static String presence(String value) {
        return value == null || value.isEmpty() ? "(empty)" : "***";
    }
}
