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
package cherry.mastersmith.targetdb.repository;

import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DB の種類ごとのスキーマの読み手（BR2.1〜BR2.7）。情報スキーマへ、テーブルとビュー・カラム・主キー・外部キーの4種類を
 * 1回ずつ問い合わせ（テーブルの数に比例しない。BR2.2、NFR1.3）、同じ形の写しに揃える。
 *
 * <p>問い合わせの文は固定の文字列（テキストブロックの定数。識別子を組み込まない）で、スキーマの名前は {@code PreparedStatement}
 * の値で渡す（NFR6.2）。書き込み・DDL は発行しない（BR1.6）。待ちの上限は問い合わせ1回ごとに当て、読み取りの全体では
 * 打ち切らない（照合の全体の上限は作らない。決定 B）。途中で失敗したら例外を投げ、途中までの写しは返さない。
 */
public interface SchemaQueries {

    /**
     * スキーマを読む。
     *
     * @param connection 読み取り専用の接続
     * @param schemaName 読むスキーマの名前
     * @param queryTimeoutSeconds 問い合わせ1回ごとの待ちの上限（秒、1 以上。{@code Statement.setQueryTimeout} に当てる）
     * @return スキーマの写し
     * @throws SQLException 問い合わせの失敗・打ち切り
     */
    TargetSchema read(Connection connection, String schemaName, int queryTimeoutSeconds) throws SQLException;

    /**
     * 種類に合った読み手を返す。MariaDB は MySQL と同じ読み手を使う。
     *
     * @param product DB の種類
     * @return 読み手
     */
    static SchemaQueries of(DatabaseProduct product) {
        return switch (product) {
            case MYSQL, MARIADB -> new MysqlSchemaQueries(product);
            case POSTGRESQL -> new PostgresSchemaQueries();
        };
    }
}
