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
package cherry.mastersmith.businessdata.testsupport;

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 3種類の対象DB の結合テストの表と DSL（Step 15、unit-test-instructions.md の 7節）。100 カラムまでの小さな表と数行だけを作り、
 * 100,000 行の種は入れない。値はすべて架空。DB の種類ごとの型と値の書き方の差を、この1か所にまとめる。
 */
public final class BusinessDataReadTables {

    /** いろいろな型を持つ表。 */
    public static final String ITEM = "item";

    /** 複合の主キーの表。 */
    public static final String LINE = "line";

    /** 主キーの設定が重なる行を持つビュー（DSL で {@code order_no} を主キーにする）。 */
    public static final String DUP_VIEW = "dup_view";

    /** 主キーの無い表（名前に引用符・{@code ;}・{@code --}・バッククォートを含む）。 */
    public static final String NO_KEY = "no'k\"e;y--`t";

    /** 型の分類の外の型を主キーに持つ表。 */
    public static final String OTHER_KEY = "other_key";

    /** DSL に足したカラムが対象DB に無い表。 */
    public static final String PARTIAL = "partial";

    /** DSL にあって対象DB に無い表。 */
    public static final String GHOST = "ghost";

    /** MySQL・MariaDB のゼロ日付と範囲の外の時刻を持つ表。 */
    public static final String LEGACY = "legacy";

    /** MySQL・MariaDB の {@code bit(8)} と空間の型の表。 */
    public static final String BITS = "bits";

    /** MySQL・MariaDB の {@code tinyint} を真偽値として読む表（0・1 と、0 と 1 以外の値 5 を持つ。FR3.7 の R-08）。 */
    public static final String FLAGS = "flags";

    /** PostgreSQL の {@code timetz} の表。 */
    public static final String ZONED = "zoned";

    /** 名前に引用符・{@code ;}・{@code --}・バッククォート・改行を含むカラム。 */
    public static final String WEIRD = "q'u\"o;t--`e\nx";

    /** 大文字と小文字の混ざったカラム。 */
    public static final String MIXED = "MixedCase";

    /** 部分一致の値（{@code %}・{@code _}・{@code !} を含む）。 */
    public static final String NAME_1 = "ペン50%_!";

    /** バックスラッシュを含む値。 */
    public static final String NAME_2 = "ノート\\x";

    /** 引用符・{@code ;}・{@code --}・改行・バッククォートを含む値。 */
    public static final String NAME_5 = "it's \"q\"; --\n`x`";

    /** PostgreSQL の uuid の値（行 1）。 */
    public static final String UUID_1 = "0b8e1c7a-0000-4000-8000-000000000001";

    /** 主キーの値（{@code /}・{@code %}・{@code ?}・{@code #}・空白・引用符）。 */
    public static final String ODD_BRANCH = "b/%?# '\"";

    /** 複合の主キーの表の枝の値。 */
    public static final List<String> BRANCHES = List.of("a", ODD_BRANCH, "c", "d", "e");

    private final TargetDbTestDatabase db;

    private final String schema;

    /**
     * 作る。
     *
     * @param db テストの DB（起動済み）
     * @param schema スキーマ（作成済み）
     */
    public BusinessDataReadTables(TargetDbTestDatabase db, String schema) {
        this.db = db;
        this.schema = schema;
    }

    private boolean postgres() {
        return db.product() == DatabaseProduct.POSTGRESQL;
    }

    private String t(String table) {
        return db.q(schema) + "." + db.q(table);
    }

    private String type(String postgres, String mysql) {
        return postgres() ? postgres : mysql;
    }

    /** 表・ビュー・行を作る。 */
    public void create() {
        List<String> sql = new ArrayList<>();
        if (!postgres()) {
            sql.add("SET time_zone = '+00:00'");
            sql.add("SET SESSION sql_mode = ''");
        }
        sql.add("CREATE TABLE " + t(ITEM) + " (id INTEGER NOT NULL PRIMARY KEY,"
                + " name VARCHAR(300) NULL,"
                + " memo " + type("TEXT", "LONGTEXT") + " NULL,"
                + " big BIGINT NULL,"
                + " amount " + type("NUMERIC(38,10)", "DECIMAL(38,10)") + " NULL,"
                + " flag " + type("BOOLEAN", "TINYINT(1)") + " NULL,"
                + " day DATE NULL,"
                + " at " + type("TIMESTAMP(6)", "DATETIME(6)") + " NULL,"
                + " at_tz " + type("TIMESTAMPTZ(6)", "TIMESTAMP(6) NULL DEFAULT NULL") + (postgres() ? " NULL" : "")
                + ","
                + " secret VARCHAR(50) NULL,"
                + " photo " + type("BYTEA", "BLOB") + " NULL,"
                + " kind " + type("UUID", "ENUM('A','B')") + " NULL,"
                + " doc JSON NULL,"
                + " hidden VARCHAR(50) NULL,"
                + " " + db.q(WEIRD) + " VARCHAR(50) NULL,"
                + " " + db.q(MIXED) + " VARCHAR(50) NULL)");
        String photo = postgres() ? "decode('0102', 'hex')" : "X'0102'";
        String flagTrue = postgres() ? "TRUE" : "1";
        String flagFalse = postgres() ? "FALSE" : "0";
        String kind = postgres() ? "'" + UUID_1 + "'" : "'A'";
        String atTz = postgres() ? "'2026-03-08 01:30:00+00'" : "'2026-03-08 01:30:00'";
        sql.add("INSERT INTO " + t(ITEM) + " VALUES (1, '" + escape(NAME_1) + "', " + repeat("あ", 100)
                + ", 9223372036854775807, 1234567890123456789012345678.0123456789, " + flagTrue
                + ", '2026-01-15', '2026-03-08 02:30:00.123456', " + atTz + ", 'pw-1', " + photo + ", " + kind
                + ", '{\"a\": 1}', 'H-1', 'w1', 'M1')");
        sql.add("INSERT INTO " + t(ITEM) + " VALUES (2, '" + escape(NAME_2) + "', " + repeat("い", 101)
                + ", -9223372036854775808, -0.0000000001, " + flagFalse
                + ", '2026-02-01', NULL, NULL, NULL, NULL, NULL, NULL, 'H-2', 'w2', NULL)");
        sql.add("INSERT INTO " + t(ITEM) + " (id, name, memo) VALUES (3, NULL, " + repeat("う", 10_000) + ")");
        sql.add("INSERT INTO " + t(ITEM) + " (id, name, memo) VALUES (4, '', " + repeat("え", 10_001) + ")");
        sql.add("INSERT INTO " + t(ITEM) + " (id, name, memo) VALUES (5, '" + escape(NAME_5) + "', "
                + repeat("x", 2_000_000) + ")");
        sql.add("CREATE TABLE " + t(LINE)
                + " (order_no INTEGER NOT NULL, branch VARCHAR(50) NOT NULL, qty INTEGER NULL,"
                + " PRIMARY KEY (order_no, branch))");
        StringBuilder lines = new StringBuilder("INSERT INTO " + t(LINE) + " (order_no, branch, qty) VALUES ");
        for (int order = 1; order <= 5; order++) {
            for (String branch : BRANCHES) {
                lines.append(order == 1 && branch.equals("a") ? "" : ", ")
                        .append('(')
                        .append(order)
                        .append(", '")
                        .append(escape(branch))
                        .append("', ")
                        .append(order % 2)
                        .append(')');
            }
        }
        sql.add(lines.toString());
        sql.add("CREATE VIEW " + t(DUP_VIEW) + " AS SELECT order_no, qty FROM " + t(LINE));
        sql.add("CREATE TABLE " + t(NO_KEY) + " (txt VARCHAR(50) NULL)");
        sql.add("INSERT INTO " + t(NO_KEY) + " (txt) VALUES ('one'), ('two')");
        sql.add("CREATE TABLE " + t(OTHER_KEY) + " (k " + type("UUID", "YEAR") + " NOT NULL PRIMARY KEY,"
                + " v VARCHAR(20) NULL)");
        sql.add("INSERT INTO " + t(OTHER_KEY) + " (k, v) VALUES (" + (postgres() ? "'" + UUID_1 + "'" : "2026")
                + ", 'other')");
        sql.add("CREATE TABLE " + t(PARTIAL) + " (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL)");
        sql.add("INSERT INTO " + t(PARTIAL) + " (id, v) VALUES (1, 'p')");
        if (postgres()) {
            sql.add("CREATE TABLE " + t(ZONED) + " (id INTEGER NOT NULL PRIMARY KEY, tt TIMETZ NULL)");
            sql.add("INSERT INTO " + t(ZONED) + " (id, tt) VALUES (1, '10:30:00+09'), (2, '01:30:00+00'),"
                    + " (3, '01:30:00-05')");
        } else {
            sql.add("CREATE TABLE " + t(LEGACY) + " (id INTEGER NOT NULL PRIMARY KEY, d DATE NULL, tm TIME NULL)");
            sql.add("INSERT INTO " + t(LEGACY) + " (id, d, tm) VALUES (1, '0000-00-00', '10:00:00')");
            sql.add("INSERT INTO " + t(LEGACY) + " (id, d, tm) VALUES (2, '2026-01-01', '838:59:59')");
            sql.add("CREATE TABLE " + t(FLAGS) + " (id INTEGER NOT NULL PRIMARY KEY, f TINYINT(1) NULL,"
                    + " g TINYINT NULL)");
            sql.add("INSERT INTO " + t(FLAGS) + " (id, f, g) VALUES (1, 0, 0), (2, 1, 1), (3, NULL, NULL), (4, 5, 5)");
            sql.add("CREATE TABLE " + t(BITS) + " (id INTEGER NOT NULL PRIMARY KEY, b BIT(8) NULL, g POINT NULL,"
                    + " v VARCHAR(10) NULL)");
            sql.add("INSERT INTO " + t(BITS) + " (id, b, g, v) VALUES (1, b'00000101', ST_GeomFromText('POINT(1 1)'),"
                    + " 'b1')");
        }
        db.execute(sql.toArray(String[]::new));
    }

    private String repeat(String text, int count) {
        return "REPEAT('" + text + "', " + count + ")";
    }

    /** SQL の文字の値の中の {@code '} を二重にし、MySQL・MariaDB ではバックスラッシュも二重にする。 */
    private String escape(String value) {
        String escaped = value.replace("'", "''");
        return postgres() ? escaped : escaped.replace("\\", "\\\\");
    }

    /**
     * DSL のモデルを作る（表は DB の型の名前で書く）。
     *
     * @return モデル
     */
    public DslModel model() {
        boolean pg = postgres();
        String json = db.product() == DatabaseProduct.MARIADB ? "longtext" : "json";
        Map<String, DslTable> tables = new LinkedHashMap<>();
        tables.put(
                ITEM,
                table(
                        ITEM,
                        false,
                        List.of("id"),
                        column("id")
                                .dbType(pg ? "int4" : "int")
                                .list(true, 1)
                                .search(SearchOperator.EQUALS)
                                .build(),
                        column("name")
                                .list(true, 2)
                                .search(SearchOperator.CONTAINS)
                                .build(),
                        column("memo")
                                .dbType(pg ? "text" : "longtext")
                                .list(true, 3)
                                .sortable(false)
                                .build(),
                        column("big")
                                .dbType(pg ? "int8" : "bigint")
                                .list(true, 4)
                                .search(SearchOperator.RANGE)
                                .build(),
                        column("amount")
                                .dbType(pg ? "numeric" : "decimal")
                                .list(true, 5)
                                .search(SearchOperator.RANGE)
                                .build(),
                        column("flag")
                                .dbType(pg ? "bool" : "tinyint")
                                .form(FormPart.CHECKBOX)
                                .list(true, 6)
                                .search(SearchOperator.CHOICE)
                                .build(),
                        column("day")
                                .dbType("date")
                                .list(true, 7)
                                .search(SearchOperator.RANGE)
                                .build(),
                        column("at")
                                .dbType(pg ? "timestamp" : "datetime")
                                .list(false, null)
                                .search(SearchOperator.RANGE)
                                .build(),
                        column("at_tz")
                                .dbType(pg ? "timestamptz" : "timestamp")
                                .list(true, 8)
                                .search(SearchOperator.EQUALS)
                                .build(),
                        column("secret")
                                .form(FormPart.PASSWORD)
                                .list(true, 9)
                                .search(SearchOperator.EQUALS)
                                .build(),
                        column("photo")
                                .dbType(pg ? "bytea" : "blob")
                                .list(true, 10)
                                .build(),
                        column("kind")
                                .dbType(pg ? "uuid" : "enum")
                                .list(true, 11)
                                .search(SearchOperator.EQUALS)
                                .build(),
                        column("doc")
                                .dbType(json)
                                .list(true, 12)
                                .search(SearchOperator.EQUALS)
                                .sortable(false)
                                .build(),
                        column("hidden")
                                .list(true, 13)
                                .search(SearchOperator.EQUALS)
                                .build(),
                        column(WEIRD)
                                .list(true, 14)
                                .search(SearchOperator.EQUALS)
                                .build(),
                        column(MIXED).list(true, 15).build()));
        tables.put(
                LINE,
                table(
                        LINE,
                        false,
                        List.of("order_no", "branch"),
                        column("order_no")
                                .dbType(pg ? "int4" : "int")
                                .list(true, 1)
                                .build(),
                        column("branch").list(true, 2).build(),
                        column("qty").dbType(pg ? "int4" : "int").list(true, 3).build()));
        tables.put(
                DUP_VIEW,
                table(
                        DUP_VIEW,
                        true,
                        List.of("order_no"),
                        column("order_no")
                                .dbType(pg ? "int4" : "int")
                                .list(true, 1)
                                .build(),
                        column("qty").dbType(pg ? "int4" : "int").list(true, 2).build()));
        tables.put(
                NO_KEY,
                table(NO_KEY, false, List.of(), column("txt").list(true, 1).build()));
        tables.put(
                OTHER_KEY,
                table(
                        OTHER_KEY,
                        false,
                        List.of("k"),
                        column("k")
                                .dbType(pg ? "uuid" : "year")
                                .list(true, 1)
                                .search(SearchOperator.EQUALS)
                                .build(),
                        column("v").list(true, 2).build()));
        tables.put(
                PARTIAL,
                table(
                        PARTIAL,
                        false,
                        List.of("id"),
                        column("id").dbType(pg ? "int4" : "int").list(true, 1).build(),
                        column("phantom").list(true, 2).build()));
        tables.put(
                GHOST,
                table(
                        GHOST,
                        false,
                        List.of("id"),
                        column("id").dbType(pg ? "int4" : "int").build()));
        if (pg) {
            tables.put(
                    ZONED,
                    table(
                            ZONED,
                            false,
                            List.of("id"),
                            column("id").dbType("int4").list(true, 1).build(),
                            column("tt")
                                    .dbType("timetz")
                                    .list(true, 2)
                                    .search(SearchOperator.EQUALS)
                                    .build()));
        } else {
            tables.put(
                    LEGACY,
                    table(
                            LEGACY,
                            false,
                            List.of("id"),
                            column("id").dbType("int").list(true, 1).build(),
                            column("d").dbType("date").list(true, 2).build(),
                            column("tm").dbType("time").list(true, 3).build()));
            tables.put(
                    FLAGS,
                    table(
                            FLAGS,
                            false,
                            List.of("id"),
                            column("id").dbType("int").list(true, 1).build(),
                            column("f")
                                    .dbType("tinyint")
                                    .form(FormPart.CHECKBOX)
                                    .list(true, 2)
                                    .build(),
                            column("g")
                                    .dbType("tinyint")
                                    .form(FormPart.SWITCH)
                                    .list(true, 3)
                                    .build()));
            DslColumn bit = column("b")
                    .dbType("bit", 8)
                    .list(true, 2)
                    .search(SearchOperator.EQUALS)
                    .build();
            tables.put(
                    BITS,
                    table(
                            BITS,
                            false,
                            List.of("id"),
                            column("id").dbType("int").list(true, 1).build(),
                            bit,
                            column("g")
                                    .dbType("point")
                                    .list(true, 3)
                                    .search(SearchOperator.EQUALS)
                                    .build(),
                            column("v").list(true, 4).build()));
        }
        return new DslModel(
                BusinessDataDslFixture.HASH,
                2,
                List.of(new DslSchema(schema, new DisplayName("業務", "Business"), tables)),
                List.of());
    }
}
