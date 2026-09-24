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

/**
 * PostgreSQL の読み手。{@code information_schema} と {@code pg_catalog} を読む（BR2.7）。
 *
 * <ul>
 *   <li>テーブル・カラムは、読める権限のものだけを返す {@code information_schema} の {@code tables}・{@code columns} を
 *       使い、見分けとコメントは {@code pg_class} の {@code relkind}・{@code obj_description}・{@code col_description} で得る。
 *       テーブルは {@code r}（通常）・{@code p}（パーティションの親）、ビューは {@code v}。パーティションの子・マテリアライズド
 *       ビュー・外部テーブルは含めない（BR2.1）。
 *   <li>型の名前は {@code udt_name}（例: {@code int4}・{@code varchar}）。
 *   <li>主キー・外部キーは {@code pg_constraint} から読む。{@code information_schema.table_constraints} は、SELECT だけの
 *       権限のアカウントには制約を返さないため使わない（AC1.1.10 の読み取りだけのアカウント）。
 *   <li>外部キーは、参照先のスキーマが同じもので、パーティションの親の制約から作られた子の制約を除いたもの（BR2.6）。
 * </ul>
 */
public final class PostgresSchemaQueries implements SchemaQueries {

    /** テーブルとビュー（名前、ビューなら 1、コメント）。 */
    static final String TABLES_SQL = """
            SELECT t.table_name,
                   CASE WHEN c.relkind = 'v' THEN 1 ELSE 0 END,
                   pg_catalog.obj_description(c.oid, 'pg_class')
              FROM information_schema.tables t
              JOIN pg_catalog.pg_namespace n ON n.nspname = t.table_schema
              JOIN pg_catalog.pg_class c ON c.relnamespace = n.oid AND c.relname = t.table_name
             WHERE t.table_schema = ?
               AND c.relkind IN ('r', 'p', 'v')
               AND NOT c.relispartition
             ORDER BY t.table_name
            """;

    /**
     * カラム（テーブル名、カラム名、定義の順、型の名前、長さ、精度、桁数、NULL を許すなら 1、既定値、コメント、型の全体の表記）。
     * 型の全体の表記は MySQL・MariaDB の {@code COLUMN_TYPE} に当たるもので、PostgreSQL では持たないため null を入れる
     * （U3 のコード生成の Q1: A）。
     */
    static final String COLUMNS_SQL = """
            SELECT col.table_name, col.column_name, col.ordinal_position, col.udt_name,
                   col.character_maximum_length, col.numeric_precision, col.numeric_scale,
                   CASE WHEN col.is_nullable = 'YES' THEN 1 ELSE 0 END,
                   col.column_default,
                   pg_catalog.col_description(c.oid, CAST(col.ordinal_position AS integer)),
                   CAST(NULL AS varchar)
              FROM information_schema.columns col
              JOIN pg_catalog.pg_namespace n ON n.nspname = col.table_schema
              JOIN pg_catalog.pg_class c ON c.relnamespace = n.oid AND c.relname = col.table_name
             WHERE col.table_schema = ?
             ORDER BY col.table_name, col.ordinal_position
            """;

    /** 主キー（テーブル名、制約名、カラム名、キーの中の順）。 */
    static final String PRIMARY_KEYS_SQL = """
            SELECT c.relname, con.conname, a.attname, k.ord
              FROM pg_catalog.pg_constraint con
              JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
              JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
             CROSS JOIN LATERAL unnest(con.conkey) WITH ORDINALITY AS k(attnum, ord)
              JOIN pg_catalog.pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum
             WHERE n.nspname = ?
               AND con.contype = 'p'
             ORDER BY c.relname, k.ord
            """;

    /** 同じスキーマを参照する外部キー（テーブル名、制約名、カラム名、キーの中の順、参照先のテーブル名とカラム名）。 */
    static final String FOREIGN_KEYS_SQL = """
            SELECT c.relname, con.conname, a.attname, k.ord, rc.relname, ra.attname
              FROM pg_catalog.pg_constraint con
              JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
              JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
              JOIN pg_catalog.pg_class rc ON rc.oid = con.confrelid
             CROSS JOIN LATERAL unnest(con.conkey, con.confkey) WITH ORDINALITY AS k(attnum, refattnum, ord)
              JOIN pg_catalog.pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum
              JOIN pg_catalog.pg_attribute ra ON ra.attrelid = con.confrelid AND ra.attnum = k.refattnum
             WHERE n.nspname = ?
               AND con.contype = 'f'
               AND rc.relnamespace = n.oid
               AND con.conparentid = 0
             ORDER BY c.relname, con.conname, k.ord
            """;

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
        return SchemaRows.assemble(DatabaseProduct.POSTGRESQL, schemaName, tables, columns, primaryKeys, foreignKeys);
    }
}
