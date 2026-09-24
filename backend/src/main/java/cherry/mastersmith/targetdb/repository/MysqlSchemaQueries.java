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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * MySQL・MariaDB の読み手。{@code information_schema} の {@code TABLES}・{@code COLUMNS}・{@code TABLE_CONSTRAINTS}・
 * {@code KEY_COLUMN_USAGE} を読む（BR2.7）。
 *
 * <ul>
 *   <li>テーブルは {@code BASE TABLE} と MariaDB の {@code SYSTEM VERSIONED}、ビューは {@code VIEW}。シーケンス・一時表は
 *       含めない（BR2.1）。
 *   <li>ビューの {@code TABLE_COMMENT} は、コメントではなく種類の名前（{@code VIEW}）が入るため、コメント無しにする。
 *   <li>型の名前は {@code DATA_TYPE}（例: {@code varchar}）、長さは {@code CHARACTER_MAXIMUM_LENGTH}、精度・桁数は
 *       {@code NUMERIC_PRECISION}・{@code NUMERIC_SCALE}。既定値は {@code COLUMN_DEFAULT} をそのまま入れる（MariaDB は文字列の
 *       既定値を引用符で囲んで返すなど、書き方は DB によって違う）。
 *   <li>外部キーは、参照先のスキーマが同じものだけ（BR2.6）。
 * </ul>
 */
public final class MysqlSchemaQueries implements SchemaQueries {

    /** テーブルとビュー（名前、ビューなら 1、コメント）。 */
    static final String TABLES_SQL = """
            SELECT TABLE_NAME,
                   CASE WHEN TABLE_TYPE = 'VIEW' THEN 1 ELSE 0 END,
                   CASE WHEN TABLE_TYPE = 'VIEW' THEN NULL ELSE TABLE_COMMENT END
              FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = ?
               AND TABLE_TYPE IN ('BASE TABLE', 'SYSTEM VERSIONED', 'VIEW')
             ORDER BY TABLE_NAME
            """;

    /**
     * カラム（テーブル名、カラム名、定義の順、型の名前、長さ、精度、桁数、NULL を許すなら 1、既定値、コメント、型の全体の表記）。
     * 型の全体の表記（{@code COLUMN_TYPE}）は、{@code tinyint(1)} を見分けるために読む（U3 のコード生成の Q1: A）。
     */
    static final String COLUMNS_SQL = """
            SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, DATA_TYPE,
                   CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE,
                   CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END,
                   COLUMN_DEFAULT, COLUMN_COMMENT, COLUMN_TYPE
              FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = ?
             ORDER BY TABLE_NAME, ORDINAL_POSITION
            """;

    /** 主キー（テーブル名、制約名、カラム名、キーの中の順）。 */
    static final String PRIMARY_KEYS_SQL = """
            SELECT k.TABLE_NAME, k.CONSTRAINT_NAME, k.COLUMN_NAME, k.ORDINAL_POSITION
              FROM information_schema.TABLE_CONSTRAINTS c
              JOIN information_schema.KEY_COLUMN_USAGE k
                ON k.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA
               AND k.CONSTRAINT_NAME = c.CONSTRAINT_NAME
               AND k.TABLE_SCHEMA = c.TABLE_SCHEMA
               AND k.TABLE_NAME = c.TABLE_NAME
             WHERE c.TABLE_SCHEMA = ?
               AND c.CONSTRAINT_TYPE = 'PRIMARY KEY'
             ORDER BY k.TABLE_NAME, k.ORDINAL_POSITION
            """;

    /** 同じスキーマを参照する外部キー（テーブル名、制約名、カラム名、キーの中の順、参照先のテーブル名とカラム名）。 */
    static final String FOREIGN_KEYS_SQL = """
            SELECT k.TABLE_NAME, k.CONSTRAINT_NAME, k.COLUMN_NAME, k.ORDINAL_POSITION,
                   k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME
              FROM information_schema.TABLE_CONSTRAINTS c
              JOIN information_schema.KEY_COLUMN_USAGE k
                ON k.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA
               AND k.CONSTRAINT_NAME = c.CONSTRAINT_NAME
               AND k.TABLE_SCHEMA = c.TABLE_SCHEMA
               AND k.TABLE_NAME = c.TABLE_NAME
             WHERE c.TABLE_SCHEMA = ?
               AND c.CONSTRAINT_TYPE = 'FOREIGN KEY'
               AND k.REFERENCED_TABLE_SCHEMA = c.TABLE_SCHEMA
             ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION
            """;

    private final DatabaseProduct product;

    /**
     * 作る。
     *
     * @param product {@link DatabaseProduct#MYSQL} または {@link DatabaseProduct#MARIADB}
     */
    public MysqlSchemaQueries(DatabaseProduct product) {
        if (Objects.requireNonNull(product) == DatabaseProduct.POSTGRESQL) {
            throw new IllegalArgumentException("MySQL・MariaDB の読み手です");
        }
        this.product = product;
    }

    @Override
    public TargetSchema read(Connection connection, String schemaName, int queryTimeoutSeconds) throws SQLException {
        List<SchemaRows.TableRow> tables;
        try (PreparedStatement statement = connection.prepareStatement(TABLES_SQL)) {
            tables = SchemaRows.tables(statement, schemaName, queryTimeoutSeconds);
        }
        List<SchemaRows.ColumnRow> columns;
        try (PreparedStatement statement = connection.prepareStatement(COLUMNS_SQL)) {
            columns = SchemaRows.columns(statement, schemaName, queryTimeoutSeconds);
        }
        List<SchemaRows.KeyRow> primaryKeys;
        try (PreparedStatement statement = connection.prepareStatement(PRIMARY_KEYS_SQL)) {
            primaryKeys = SchemaRows.primaryKeys(statement, schemaName, queryTimeoutSeconds);
        }
        List<SchemaRows.KeyRow> foreignKeys;
        try (PreparedStatement statement = connection.prepareStatement(FOREIGN_KEYS_SQL)) {
            foreignKeys = SchemaRows.foreignKeys(statement, schemaName, queryTimeoutSeconds);
        }
        return SchemaRows.assemble(product, schemaName, tables, columns, primaryKeys, foreignKeys);
    }
}
