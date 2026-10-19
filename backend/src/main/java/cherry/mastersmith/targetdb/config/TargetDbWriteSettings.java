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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 起動時に1回だけ点検した、対象DB の書き込みの接続の設定の状態（Intent 261016-business-data-views の U2、BR9.1・NFR4.11）。
 * 使わない・欠けがある・使える のどれかで、動いている間は変わらない。
 *
 * <p>読み取りの設定が使えて（{@link TargetDbSettings.Usable}）、書き込みのユーザー名とパスワードの両方があるときだけ「使える」に
 * する。片方だけ・値の範囲の外のときは、問題のある項目の名前だけを持つ（値は持たない）。どの場合も起動と読み取りは続ける。
 */
public sealed interface TargetDbWriteSettings {

    /** 書き込みのユーザー名の項目の名前（WARN に出すのは、この名前だけ）。 */
    String WRITE_USERNAME = TargetDbProperties.PREFIX + ".write.username";

    /** 書き込みのパスワードの項目の名前。 */
    String WRITE_PASSWORD = TargetDbProperties.PREFIX + ".write.password";

    /** 書き込みのプールの接続の数の上限の項目の名前。 */
    String WRITE_POOL_MAXIMUM_SIZE = TargetDbProperties.PREFIX + ".write.pool.maximum-size";

    /** 書き込みの文1回の待ちの項目の名前。 */
    String QUERY_TIMEOUT_WRITE = TargetDbProperties.PREFIX + ".query-timeout.write";

    /** 書き込みを使わない（読み取りが使えない、または書き込みの資格情報が両方とも空）。WARN は出さない。 */
    record NotConfigured() implements TargetDbWriteSettings {}

    /**
     * 書き込みの設定に欠け・不正があるため使わない（片方だけの資格情報など）。
     *
     * @param problemItems 問題のある項目の名前（値は含めない）
     */
    record Incomplete(List<String> problemItems) implements TargetDbWriteSettings {

        /** 一覧を変更できないものにする。 */
        public Incomplete {
            problemItems = List.copyOf(problemItems);
        }
    }

    /**
     * 使える。対象DB にはまだ接続していない。
     *
     * @param read 読み取りの設定（接続先はこれをそのまま使う。BR9.1）
     */
    record Usable(TargetDbSettings.Usable read) implements TargetDbWriteSettings {

        /** 読み取りの設定が必須であることを確かめる。 */
        public Usable {
            Objects.requireNonNull(read, "read は必須です");
        }

        /**
         * DB の種類を返す。
         *
         * @return DB の種類
         */
        public DatabaseProduct product() {
            return read.product();
        }

        /**
         * 書き込みの文1回の待ちの上限を返す。
         *
         * @return 待ちの上限
         */
        public Duration queryTimeout() {
            return read.properties().queryTimeout().write();
        }

        /**
         * 書き込みの設定を返す。
         *
         * @return 書き込みの設定（文字列にするときは資格情報を伏せる）
         */
        public TargetDbProperties.Write write() {
            return read.properties().write();
        }
    }

    /**
     * 書き込みの設定を点検する。ログは出さない（WARN は呼び出し側が1回だけ出す）。
     *
     * @param settings 読み取りの設定の点検の結果
     * @param properties 設定
     * @return 点検の結果
     */
    static TargetDbWriteSettings inspect(TargetDbSettings settings, TargetDbProperties properties) {
        TargetDbProperties.Write write = properties.write();
        boolean hasUsername = !isBlank(write.username());
        boolean hasPassword = !isBlank(write.password());
        if (!hasUsername && !hasPassword) {
            return new NotConfigured();
        }
        if (!(settings instanceof TargetDbSettings.Usable usable)) {
            // 読み取りが使えないときは、書き込みも使わない（読み取りの欠けの WARN は読み取りの点検が出す）。
            return new NotConfigured();
        }
        List<String> problems = new ArrayList<>();
        if (!hasUsername) {
            problems.add(WRITE_USERNAME);
        }
        if (!hasPassword) {
            problems.add(WRITE_PASSWORD);
        }
        // Statement.setQueryTimeout は秒の単位のため 1 秒以上。
        if (properties.queryTimeout().write().getSeconds() < 1) {
            problems.add(QUERY_TIMEOUT_WRITE);
        }
        if (write.pool().maximumSize() < 1) {
            problems.add(WRITE_POOL_MAXIMUM_SIZE);
        }
        if (!problems.isEmpty()) {
            return new Incomplete(problems);
        }
        return new Usable(usable);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
