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
package cherry.mastersmith.businessdata.repository;

import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 行の SQL の DB の種類ごとの差（BR3.5・BR5.5・BR7.3・BR7.7・BR7.9、ADR-007、reliability-design.md の 3節・4節、
 * performance-design.md の 1節）。MySQL・MariaDB は1つの方言（{@link MysqlFamilyRowDialect}）、PostgreSQL は別の方言
 * （{@link PostgresRowDialect}）。業務処理の層は DB の種類で分岐しない。
 *
 * <p>組み立てる式は、定数の形の文字と、引用した識別子（{@code SqlIdentifiers.quoteKnown} を通したもの）だけから作る。利用者の
 * 入力を式に組み込まない（値はバインドする）。
 */
public sealed interface RowDialect permits PostgresRowDialect, MysqlFamilyRowDialect {

    /**
     * DB の種類の方言を返す。
     *
     * @param product DB の種類
     * @return 方言
     */
    static RowDialect of(DatabaseProduct product) {
        Objects.requireNonNull(product, "product は必須です");
        return switch (product) {
            case POSTGRESQL -> PostgresRowDialect.INSTANCE;
            case MYSQL -> MysqlFamilyRowDialect.MYSQL;
            case MARIADB -> MysqlFamilyRowDialect.MARIADB;
        };
    }

    /**
     * 完全一致の検索と主キーの指し方に使える型かを返す（BR7.9）。DB の種類が分からないときは、3種類の一覧の和で外す（狭める向き）。
     *
     * @param product DB の種類（分からなければ空）
     * @param dbTypeName DSL の型の名前（大文字と小文字を区別しない）
     * @return 使えるなら true
     */
    static boolean equalsSupported(Optional<DatabaseProduct> product, String dbTypeName) {
        if (product.isPresent()) {
            return of(product.get()).equalsSupported(dbTypeName);
        }
        for (DatabaseProduct each : DatabaseProduct.values()) {
            if (!of(each).equalsSupported(dbTypeName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * DB の種類を返す。
     *
     * @return DB の種類
     */
    DatabaseProduct product();

    /**
     * 値を1つも送らない作成の文を返す（すべてのカラムを DB の既定値に任せる。U2 の BR2.2）。PostgreSQL は
     * {@code INSERT INTO 表 DEFAULT VALUES}、MySQL・MariaDB は {@code INSERT INTO 表 () VALUES ()}。
     *
     * @param table 引用・修飾したテーブル
     * @return 文
     */
    String insertWithoutValues(String table);

    /**
     * 生成された鍵を、主キーのカラムの名前で読めるかを返す（U2 の BR7.2、試しの W8）。PostgreSQL は名前で読む（渡した名前のカラムだけを
     * 返す）。MySQL・MariaDB は自動の採番の値1つだけを返すため、送らなかった主キーのカラムが1つのときだけ、その値として読む。
     *
     * @return 名前で読めるなら true
     */
    boolean readsGeneratedKeysByName();

    /**
     * 並べ替えの項を返す（NULL は昇順で最後、降順で最初。BR5.5）。
     *
     * @param columnRef 引用したカラムの参照
     * @param direction 向き
     * @return 並べ替えの項
     */
    String orderTerm(String columnRef, SortDirection direction);

    /**
     * 値を選ぶ式を返す（STRING は {@code SUBSTRING(列, 1, 上限+1)}、OTHER は文字にしてから {@code SUBSTRING}、ほかは列のまま）。
     *
     * @param columnRef 引用したカラムの参照
     * @param column カラム（password・BINARY は呼ぶ前に外している）
     * @param limit 返す文字数の上限（コードポイント）
     * @return 式
     */
    default String selectExpression(String columnRef, ColumnAccess column, int limit) {
        return switch (column.valueType()) {
            case STRING -> "SUBSTRING(" + columnRef + ", 1, " + (limit + 1) + ")";
            case OTHER -> "SUBSTRING(" + castToText(columnRef) + ", 1, " + (limit + 1) + ")";
            default -> columnRef;
        };
    }

    /**
     * 主キーの値を選ぶ式を返す（切らない。OTHER は文字にする）。
     *
     * @param columnRef 引用したカラムの参照
     * @param column 主キーのカラム
     * @return 式
     */
    default String keyExpression(String columnRef, ColumnAccess column) {
        return column.valueType() == ValueType.OTHER ? castToText(columnRef) : columnRef;
    }

    /**
     * 値と比べる左辺の式を返す（PostgreSQL の {@code timetz} は {@code (列 AT TIME ZONE 'UTC')}。T1）。
     *
     * @param columnRef 引用したカラムの参照
     * @param valueType カラムの受け渡しの型
     * @return 式
     */
    String comparisonTarget(String columnRef, ValueType valueType);

    /**
     * カラムの値を文字にする式（OTHER の読み取り）を返す。
     *
     * @param columnRef 引用したカラムの参照
     * @return 式
     */
    String castToText(String columnRef);

    /**
     * OTHER の値をバインドする（PostgreSQL は {@code Types.OTHER}、MySQL・MariaDB は {@code setString}。BR7.7）。
     *
     * @param statement 文
     * @param index 位置
     * @param value 値
     * @throws SQLException バインドの失敗
     */
    void bindOther(PreparedStatement statement, int index, String value) throws SQLException;

    /**
     * UTC にそろえた時差つきの日時をバインドする（BR7.8、reliability-design.md の 3節）。
     *
     * @param statement 文
     * @param index 位置
     * @param value 値（UTC）
     * @throws SQLException バインドの失敗
     */
    void bindDateTimeTz(PreparedStatement statement, int index, OffsetDateTime value) throws SQLException;

    /**
     * 時差つきの日時を読み、UTC にそろえる（BR7.3）。
     *
     * @param resultSet 結果
     * @param index 位置
     * @return 値（UTC。NULL は null）
     * @throws SQLException 読み取りの失敗
     */
    OffsetDateTime readDateTimeTz(ResultSet resultSet, int index) throws SQLException;

    /**
     * 日付・日時・時刻を、ドライバーの型の変換を通さずに文字で受け取って読むかを返す。MySQL・MariaDB は true（MariaDB のドライバーは
     * ゼロ日付を黙って NULL に、24 時間を超える時刻を黙って 24 で割った余りにするため。文字で受けて厳しく読み、読めない値は D1: A の
     * 409 にする。Step 15 の結合テストで分かった）。
     *
     * @return 文字で受け取るなら true
     */
    boolean readsTemporalAsText();

    /**
     * 完全一致の検索と主キーの指し方に使える型かを返す（BR7.9 の一覧に無ければ使える）。
     *
     * @param dbTypeName DSL の型の名前（大文字と小文字を区別しない）
     * @return 使えるなら true
     */
    boolean equalsSupported(String dbTypeName);

    /**
     * 型の名前の基の部分（小文字。{@code (}・空白から後を除く）を返す。
     *
     * @param dbTypeName DSL の型の名前
     * @return 基の部分
     */
    static String baseName(String dbTypeName) {
        String name = dbTypeName.strip().toLowerCase(Locale.ROOT);
        int paren = name.indexOf('(');
        if (paren >= 0) {
            name = name.substring(0, paren);
        }
        return name.strip();
    }
}
