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
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 起動時に1回だけ点検した、対象DB の設定の状態（functional-spec.md の 1節・3節）。使わない・不正・使える のどれかで、動いている
 * 間は変わらない。
 */
public sealed interface TargetDbSettings {

    /** 項目の名前（WARN に出すのは、この名前だけ。値は出さない。BR1.3）。 */
    String TYPE = TargetDbProperties.PREFIX + ".type";

    /** ホストの項目の名前。 */
    String HOST = TargetDbProperties.PREFIX + ".host";

    /** 番号の項目の名前。 */
    String PORT = TargetDbProperties.PREFIX + ".port";

    /** DB の名前の項目の名前。 */
    String DATABASE = TargetDbProperties.PREFIX + ".database";

    /** スキーマの名前の項目の名前。 */
    String SCHEMA = TargetDbProperties.PREFIX + ".schema";

    /** ユーザー名の項目の名前。 */
    String USERNAME = TargetDbProperties.PREFIX + ".username";

    /** パスワードの項目の名前。 */
    String PASSWORD = TargetDbProperties.PREFIX + ".password";

    /** 接続の待ちの項目の名前。 */
    String CONNECT_TIMEOUT = TargetDbProperties.PREFIX + ".connect-timeout";

    /** 生成の問い合わせの待ちの項目の名前。 */
    String QUERY_TIMEOUT_GENERATE = TargetDbProperties.PREFIX + ".query-timeout.generate";

    /** 照合の問い合わせの待ちの項目の名前。 */
    String QUERY_TIMEOUT_COMPARE = TargetDbProperties.PREFIX + ".query-timeout.compare";

    /** プールの接続の数の上限の項目の名前。 */
    String POOL_MAXIMUM_SIZE = TargetDbProperties.PREFIX + ".pool.maximum-size";

    /** 使っていない接続を閉じるまでの時間の項目の名前。 */
    String POOL_IDLE_TIMEOUT = TargetDbProperties.PREFIX + ".pool.idle-timeout";

    /**
     * ホスト名として受け付ける形（名前・IPv4 の英数字と {@code .}・{@code -}・{@code _}、または {@code [...]} の IPv6）。JDBC の
     * URL に別の項目（資格情報や接続の設定）を差し込まれないよう、URL で意味を持つ記号を受け付けない（NFR4.5）。
     */
    Pattern HOST_PATTERN = Pattern.compile("[A-Za-z0-9._-]+|\\[[0-9A-Fa-f:.]+\\]");

    /** DB の名前として受け付けない記号（URL で意味を持つ記号・空白・引用符・制御文字）。 */
    Pattern DATABASE_FORBIDDEN = Pattern.compile("[/?&;#@=:\\\\'\"\\s\\p{Cntrl}]");

    /** 対象DB を使わない（7つの項目がすべて空）。WARN は出さない（BR1.2）。 */
    record NotConfigured() implements TargetDbSettings {}

    /**
     * 設定に欠け・不正があるため使わない（BR1.3）。
     *
     * @param problemItems 問題のある項目の名前（値は含めない）
     */
    record Invalid(List<String> problemItems) implements TargetDbSettings {

        /** 一覧を変更できないものにする。 */
        public Invalid {
            problemItems = List.copyOf(problemItems);
        }
    }

    /**
     * 使える。対象DB にはまだ接続していない（BR1.7）。
     *
     * @param product DB の種類
     * @param port 接続先の番号
     * @param properties 設定（文字列にするときは接続先・資格情報を伏せる）
     */
    record Usable(DatabaseProduct product, int port, TargetDbProperties properties) implements TargetDbSettings {

        /** 必須の値を確かめる。 */
        public Usable {
            Objects.requireNonNull(product, "product は必須です");
            Objects.requireNonNull(properties, "properties は必須です");
        }

        /**
         * 読み取るスキーマの名前を返す。
         *
         * @return スキーマの名前
         */
        public String schema() {
            return properties.schema();
        }

        /**
         * 目的ごとの問い合わせ1回の待ちの上限を返す。
         *
         * @param purpose 読み取りの目的
         * @return 待ちの上限
         */
        public Duration queryTimeout(ReadPurpose purpose) {
            return switch (purpose) {
                case GENERATE -> properties.queryTimeout().generate();
                case COMPARE -> properties.queryTimeout().compare();
            };
        }
    }

    /**
     * 設定を点検する（functional-spec.md の 1節）。ログは出さない（WARN は呼び出し側が1回だけ出す）。
     *
     * @param properties 設定
     * @return 点検の結果
     */
    static TargetDbSettings inspect(TargetDbProperties properties) {
        List<String> connectionItems = List.of(
                nullToEmpty(properties.type()),
                nullToEmpty(properties.host()),
                nullToEmpty(properties.port()),
                nullToEmpty(properties.database()),
                nullToEmpty(properties.schema()),
                nullToEmpty(properties.username()),
                nullToEmpty(properties.password()));
        if (connectionItems.stream().allMatch(String::isBlank)) {
            return new NotConfigured();
        }
        List<String> problems = new ArrayList<>();
        Optional<DatabaseProduct> product = DatabaseProduct.fromSetting(properties.type());
        if (product.isEmpty()) {
            problems.add(TYPE);
        }
        if (isBlank(properties.host())
                || !HOST_PATTERN.matcher(properties.host()).matches()) {
            problems.add(HOST);
        }
        int port = parsePort(properties.port());
        if (port < 0) {
            problems.add(PORT);
        }
        if (isBlank(properties.database())
                || DATABASE_FORBIDDEN.matcher(properties.database()).find()) {
            problems.add(DATABASE);
        }
        if (isBlank(properties.schema())) {
            problems.add(SCHEMA);
        }
        if (isBlank(properties.username())) {
            problems.add(USERNAME);
        }
        if (isBlank(properties.password())) {
            problems.add(PASSWORD);
        }
        addTimingProblems(properties, problems);
        if (!problems.isEmpty()) {
            return new Invalid(problems);
        }
        return new Usable(product.orElseThrow(), port, properties);
    }

    /** 待ち時間とプールの値が、接続の部品の受け付ける範囲にあるかを確かめる。 */
    private static void addTimingProblems(TargetDbProperties properties, List<String> problems) {
        // HikariCP の接続の待ちは 250 ミリ秒以上。
        if (properties.connectTimeout().toMillis() < 250) {
            problems.add(CONNECT_TIMEOUT);
        }
        // Statement.setQueryTimeout は秒の単位のため 1 秒以上。
        if (properties.queryTimeout().generate().getSeconds() < 1) {
            problems.add(QUERY_TIMEOUT_GENERATE);
        }
        if (properties.queryTimeout().compare().getSeconds() < 1) {
            problems.add(QUERY_TIMEOUT_COMPARE);
        }
        if (properties.pool().maximumSize() < 1) {
            problems.add(POOL_MAXIMUM_SIZE);
        }
        // HikariCP の使っていない接続を閉じるまでの時間は 10 秒以上。
        if (properties.pool().idleTimeout().toMillis() < 10_000) {
            problems.add(POOL_IDLE_TIMEOUT);
        }
    }

    /** ポートを 1〜65535 の数として読む。読めなければ -1。 */
    private static int parsePort(String value) {
        if (isBlank(value) || !value.matches("[0-9]{1,5}")) {
            return -1;
        }
        int port = Integer.parseInt(value);
        return port >= 1 && port <= 65535 ? port : -1;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
