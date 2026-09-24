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
import cherry.mastersmith.targetdb.domain.TargetColumn;
import cherry.mastersmith.targetdb.domain.TargetDbType;
import cherry.mastersmith.targetdb.domain.TargetForeignKey;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetTable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 情報スキーマの問い合わせの結果の行と、行からスキーマの写しへの組み立て（DB の種類によらず共通）。
 *
 * <p>各読み手は、同じ並びの項目を返す固定の問い合わせを用意し、ここで値を渡して実行・変換する。
 */
final class SchemaRows {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaRows.class);

    private SchemaRows() {}

    /**
     * テーブル・ビューの行（項目の並び: 名前、ビューなら 1、コメント）。
     *
     * @param name 名前
     * @param view ビューなら true
     * @param comment コメント
     */
    record TableRow(String name, boolean view, String comment) {}

    /**
     * カラムの行（項目の並び: テーブル名、カラム名、定義の順、型の名前、長さ、精度、桁数、NULL を許すなら 1、既定値、
     * コメント、型の全体の表記（MySQL・MariaDB の {@code COLUMN_TYPE}。PostgreSQL は null））。
     *
     * @param table テーブル名
     * @param name カラム名
     * @param position 定義の順
     * @param type DB 上の型
     * @param nullable NULL を許すか
     * @param defaultValue 既定値
     * @param comment コメント
     */
    record ColumnRow(
            String table,
            String name,
            int position,
            TargetDbType type,
            boolean nullable,
            String defaultValue,
            String comment) {}

    /**
     * 主キー・外部キーの1カラムの行（項目の並び: テーブル名、制約名、カラム名、キーの中の順、外部キーなら参照先のテーブル名と
     * カラム名）。
     *
     * @param table テーブル名
     * @param constraint 制約名
     * @param column カラム名
     * @param position キーの中の順
     * @param referencedTable 参照先のテーブル名（主キーなら null）
     * @param referencedColumn 参照先のカラム名（主キーなら null）
     */
    record KeyRow(
            String table,
            String constraint,
            String column,
            int position,
            String referencedTable,
            String referencedColumn) {}

    /**
     * スキーマの名前を値で渡し、待ちの上限を当てて、テーブル・ビューの問い合わせを実行する。
     *
     * @param statement 固定の文の問い合わせ
     * @param schemaName スキーマの名前
     * @param queryTimeoutSeconds 待ちの上限（秒）
     * @return 行
     * @throws SQLException 問い合わせの失敗・打ち切り
     */
    static List<TableRow> tables(PreparedStatement statement, String schemaName, int queryTimeoutSeconds)
            throws SQLException {
        List<TableRow> rows = new ArrayList<>();
        try (ResultSet rs = execute(statement, schemaName, queryTimeoutSeconds)) {
            while (rs.next()) {
                rows.add(new TableRow(rs.getString(1), rs.getInt(2) == 1, rs.getString(3)));
            }
        }
        return rows;
    }

    /**
     * カラムの問い合わせを実行する。
     *
     * @param statement 固定の文の問い合わせ
     * @param schemaName スキーマの名前
     * @param queryTimeoutSeconds 待ちの上限（秒）
     * @return 行
     * @throws SQLException 問い合わせの失敗・打ち切り
     */
    static List<ColumnRow> columns(PreparedStatement statement, String schemaName, int queryTimeoutSeconds)
            throws SQLException {
        List<ColumnRow> rows = new ArrayList<>();
        try (ResultSet rs = execute(statement, schemaName, queryTimeoutSeconds)) {
            while (rs.next()) {
                TargetDbType type = new TargetDbType(
                        rs.getString(4), nullableLong(rs, 5), nullableInt(rs, 6), nullableInt(rs, 7), rs.getString(11));
                rows.add(new ColumnRow(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getInt(3),
                        type,
                        rs.getInt(8) == 1,
                        rs.getString(9),
                        rs.getString(10)));
            }
        }
        return rows;
    }

    /**
     * 主キーの問い合わせを実行する。
     *
     * @param statement 固定の文の問い合わせ
     * @param schemaName スキーマの名前
     * @param queryTimeoutSeconds 待ちの上限（秒）
     * @return 行
     * @throws SQLException 問い合わせの失敗・打ち切り
     */
    static List<KeyRow> primaryKeys(PreparedStatement statement, String schemaName, int queryTimeoutSeconds)
            throws SQLException {
        List<KeyRow> rows = new ArrayList<>();
        try (ResultSet rs = execute(statement, schemaName, queryTimeoutSeconds)) {
            while (rs.next()) {
                rows.add(new KeyRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), null, null));
            }
        }
        return rows;
    }

    /**
     * 外部キーの問い合わせを実行する。
     *
     * @param statement 固定の文の問い合わせ
     * @param schemaName スキーマの名前
     * @param queryTimeoutSeconds 待ちの上限（秒）
     * @return 行
     * @throws SQLException 問い合わせの失敗・打ち切り
     */
    static List<KeyRow> foreignKeys(PreparedStatement statement, String schemaName, int queryTimeoutSeconds)
            throws SQLException {
        List<KeyRow> rows = new ArrayList<>();
        try (ResultSet rs = execute(statement, schemaName, queryTimeoutSeconds)) {
            while (rs.next()) {
                rows.add(new KeyRow(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getString(5),
                        rs.getString(6)));
            }
        }
        return rows;
    }

    private static ResultSet execute(PreparedStatement statement, String schemaName, int queryTimeoutSeconds)
            throws SQLException {
        statement.setQueryTimeout(queryTimeoutSeconds);
        statement.setString(1, schemaName);
        return statement.executeQuery();
    }

    private static Long nullableLong(ResultSet rs, int index) throws SQLException {
        long value = rs.getLong(index);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, int index) throws SQLException {
        Long value = nullableLong(rs, index);
        return value == null ? null : Math.toIntExact(value);
    }

    /**
     * 行からスキーマの写しを組み立てる。テーブル・ビューの一覧に無いテーブルの行（シーケンスなど、読み取りの範囲の外）は
     * 使わない。読める権限のカラムが1つも無いテーブルは写しに入れられないため除き、除いた数を WARN で出す。主キー・外部キーは、
     * カラムの一覧に出るカラムだけで組み立てる（隠れたカラムを含む主キーは読めるカラムだけ、外部キーは入れない）。
     *
     * @param product DB の種類
     * @param schemaName スキーマの名前
     * @param tables テーブル・ビューの行
     * @param columns カラムの行
     * @param primaryKeys 主キーの行
     * @param foreignKeys 外部キーの行（同じスキーマを参照するものだけ）
     * @return スキーマの写し
     */
    static TargetSchema assemble(
            DatabaseProduct product,
            String schemaName,
            List<TableRow> tables,
            List<ColumnRow> columns,
            List<KeyRow> primaryKeys,
            List<KeyRow> foreignKeys) {
        Map<String, List<ColumnRow>> columnsByTable = group(columns, ColumnRow::table);
        Map<String, List<KeyRow>> primaryKeysByTable = group(primaryKeys, KeyRow::table);
        Map<String, List<KeyRow>> foreignKeysByTable = group(foreignKeys, KeyRow::table);
        List<TargetTable> result = new ArrayList<>();
        int withoutColumns = 0;
        for (TableRow table : tables) {
            List<ColumnRow> tableColumns = columnsByTable.getOrDefault(table.name(), List.of());
            if (tableColumns.isEmpty()) {
                withoutColumns++;
                continue;
            }
            List<TargetColumn> targetColumns = tableColumns.stream()
                    .sorted(Comparator.comparingInt(ColumnRow::position))
                    .map(row ->
                            new TargetColumn(row.name(), row.type(), row.nullable(), row.defaultValue(), row.comment()))
                    .toList();
            Set<String> readable = new HashSet<>();
            targetColumns.forEach(column -> readable.add(column.name()));
            // 主キーには、カラムの一覧に出ないものが入ることがある（MariaDB のシステムバージョニングの表は、隠れたカラム
            // row_end を主キーに含める）。写しの主キーは、読めるカラムだけにする。
            List<String> primaryKey = primaryKeysByTable.getOrDefault(table.name(), List.of()).stream()
                    .sorted(Comparator.comparingInt(KeyRow::position))
                    .map(KeyRow::column)
                    .filter(readable::contains)
                    .toList();
            result.add(new TargetTable(
                    table.name(),
                    table.view(),
                    table.comment(),
                    targetColumns,
                    primaryKey,
                    toForeignKeys(foreignKeysByTable.getOrDefault(table.name(), List.of()), readable)));
        }
        if (withoutColumns > 0) {
            LOGGER.atWarn().addKeyValue("tables", withoutColumns).log("読める権限のカラムが無いテーブルを、スキーマの写しから除きました");
        }
        return new TargetSchema(product, schemaName, result);
    }

    /** 外部キーの行を制約ごとにまとめる。読めないカラムを含む外部キーは、写しに表せないため入れない。 */
    private static List<TargetForeignKey> toForeignKeys(List<KeyRow> rows, Set<String> readable) {
        Map<String, List<KeyRow>> byConstraint = group(rows, KeyRow::constraint);
        List<TargetForeignKey> keys = new ArrayList<>();
        for (Map.Entry<String, List<KeyRow>> entry : byConstraint.entrySet()) {
            List<KeyRow> parts = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(KeyRow::position))
                    .toList();
            if (!parts.stream().map(KeyRow::column).allMatch(readable::contains)) {
                continue;
            }
            keys.add(new TargetForeignKey(
                    entry.getKey(),
                    parts.stream().map(KeyRow::column).toList(),
                    parts.getFirst().referencedTable(),
                    parts.stream().map(KeyRow::referencedColumn).toList()));
        }
        return keys;
    }

    private static <T> Map<String, List<T>> group(List<T> rows, Function<T, String> key) {
        Map<String, List<T>> groups = new LinkedHashMap<>();
        for (T row : rows) {
            groups.computeIfAbsent(key.apply(row), ignored -> new ArrayList<>()).add(row);
        }
        return groups;
    }
}
