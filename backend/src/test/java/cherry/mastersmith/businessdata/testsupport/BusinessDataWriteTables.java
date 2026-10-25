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
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.tableWithForeignKeys;

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslForeignKey;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 3種類の対象DB の書き込みの結合テストの表と DSL（Intent 261016-business-data-views の U2、計画の Step 17）。テストのクラスごとに名前の
 * 重ならないスキーマに作り、終わったらスキーマごと消す（表を作る操作は巻き戻せないため、巻き戻しに頼らない。team.md の Testing Posture）。
 * 値はすべて架空。書いた後の値は、アプリとは別の接続（管理者）で読み直す（{@link #row}・{@link #count}）。
 *
 * <ul>
 *   <li>{@link #ITEM}: 自動の採番の主キー（PostgreSQL は identity）、NOT NULL、既定値、DSL と DB の一意、CHECK、境界の値の型、外部キー
 *   <li>{@link #SERIAL}: PostgreSQL の {@code serial} の主キー
 *   <li>{@link #PARENT}・{@link #CASCADE_CHILD}: 外部キーの参照先と、{@code ON DELETE CASCADE} の子
 *   <li>{@link #LINE}: 複合の主キー
 *   <li>{@link #LOCKED}: 入力できない NOT NULL のカラムと、DSL では NULL を許すが DB では NOT NULL のカラム
 *   <li>{@link #STUCK_VIEW}: 更新できないビュー（DSL ではテーブルとして書く）
 *   <li>{@link #NO_KEY}: 主キーの無い表（名前に引用符・{@code ;}・{@code --}・バッククォートを含む）
 *   <li>{@link #DUP}: DSL の主キーが DB で一意でない表（当たった行が2）
 *   <li>{@link #CONFLICT}: 更新の値で競合・形の誤りの番号を出すトリガーの表（D1・D2 の確かめ）
 *   <li>{@link #CASE_KEY}・{@link #RESERVED_KEY}: 自動の採番の主キーの名前が大文字と小文字の混じる名前・予約語の表（コードのレビューの
 *       R-02）
 * </ul>
 */
public final class BusinessDataWriteTables {

    /** いろいろな型と制約を持つ、自動の採番の主キーの表。 */
    public static final String ITEM = "w_item";

    /** PostgreSQL の serial の主キーの表。 */
    public static final String SERIAL = "w_serial";

    /** 外部キーの参照先。 */
    public static final String PARENT = "w_parent";

    /** {@code ON DELETE CASCADE} の子。 */
    public static final String CASCADE_CHILD = "w_cascade_child";

    /** 複合の主キーの表。 */
    public static final String LINE = "w_line";

    /** 入力できない NOT NULL のカラムの表。 */
    public static final String LOCKED = "w_locked";

    /** 更新できないビュー（集計）。 */
    public static final String STUCK_VIEW = "w_stuck_view";

    /** 主キーの無い表（名前に引用符・{@code ;}・{@code --}・バッククォートを含む）。 */
    public static final String NO_KEY = "w'n\"k;--`t";

    /** DSL の主キーが一意でない表。 */
    public static final String DUP = "w_dup";

    /** トリガーで競合・形の誤りを出す表。 */
    public static final String CONFLICT = "w_conflict";

    /** 自動の採番の主キーの名前が大文字と小文字の混じる表（コードのレビューの R-02）。 */
    public static final String CASE_KEY = "w_CaseKey";

    /** {@link #CASE_KEY} の主キーのカラム。 */
    public static final String CASE_KEY_COLUMN = "ItemId";

    /** 自動の採番の主キーの名前が予約語の表（R-02）。 */
    public static final String RESERVED_KEY = "w_reserved";

    /** {@link #RESERVED_KEY} の主キーのカラム。 */
    public static final String RESERVED_KEY_COLUMN = "select";

    /**
     * 整数の符号を確かめる表（Intent 261025-deps-perf-carryover の FR3.1）。MySQL・MariaDB は符号なしの列（{@code u}・{@code bu}）と
     * 符号つきの列（{@code s}）を持ち、PostgreSQL は符号なしが無いため同じ形の符号つきの列だけで作る。
     */
    public static final String SIGN = "w_sign";

    /** 競合のトリガーの値（PostgreSQL 40P01、MySQL・MariaDB 1213）。 */
    public static final String DEADLOCK = "deadlock";

    /** 競合のトリガーの値（PostgreSQL 40001、MySQL・MariaDB 1213 の 40001）。 */
    public static final String SERIALIZE = "serialize";

    /** 形の誤りのトリガーの値（PostgreSQL 22P02、MySQL・MariaDB 22007）。 */
    public static final String FORMAT = "format";

    /** 主キーの値に使う、{@code /}・{@code %}・{@code ?}・{@code #}・空白・引用符を含む値。 */
    public static final String ODD_BRANCH = "b/%?# '\"";

    /** 引用符・{@code ;}・{@code --}・改行・バッククォートを含む値。 */
    public static final String TRICKY_VALUE = "it's \"q\"; --\n`x`";

    private final TargetDbTestDatabase db;

    private final String schema;

    /**
     * 作る。
     *
     * @param db テストの DB（起動済み）
     * @param schema スキーマ（作成済み）
     */
    public BusinessDataWriteTables(TargetDbTestDatabase db, String schema) {
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

    /** 表・ビュー・トリガー・行を作る。 */
    public void create() {
        List<String> sql = new ArrayList<>();
        sql.add("CREATE TABLE " + t(PARENT) + " (id INTEGER NOT NULL PRIMARY KEY, name VARCHAR(50) NULL)");
        sql.add("INSERT INTO " + t(PARENT) + " (id, name) VALUES (1, '親1'), (2, '親2'), (3, '親3')");
        sql.add("CREATE TABLE " + t(ITEM) + " ("
                + " id " + type("INTEGER GENERATED BY DEFAULT AS IDENTITY", "INTEGER AUTO_INCREMENT") + " NOT NULL"
                + " PRIMARY KEY,"
                + " name VARCHAR(100) NOT NULL,"
                + " note VARCHAR(100) NULL DEFAULT 'def',"
                + " code VARCHAR(20) NULL UNIQUE,"
                + " soft VARCHAR(20) NULL,"
                + " hard VARCHAR(20) NULL UNIQUE,"
                + " qty INTEGER NULL CHECK (qty >= 0),"
                + " big BIGINT NULL,"
                + " amount " + type("NUMERIC(38,10)", "DECIMAL(38,10)") + " NULL,"
                + " at " + type("TIMESTAMP(6)", "DATETIME(6)") + " NULL,"
                + " at_tz " + type("TIMESTAMPTZ(6) NULL", "TIMESTAMP(6) NULL DEFAULT NULL") + ","
                + " day DATE NULL,"
                + (postgres() ? "" : " latin VARCHAR(20) CHARACTER SET latin1 NULL,")
                + " parent_id INTEGER NULL REFERENCES " + t(PARENT) + " (id))");
        sql.add("INSERT INTO " + t(ITEM) + " (id, name, code, soft, hard, parent_id) VALUES"
                + " (1, '既存1', 'C-1', 'S-1', 'H-1', 1), (2, '既存2', 'C-2', 'S-2', 'H-2', NULL)");
        if (postgres()) {
            // 値で入れた行の後も、自動の採番が重ならないように進める。
            sql.add("SELECT setval(pg_get_serial_sequence('" + db.q(schema) + "." + ITEM + "', 'id'), 100)");
            sql.add("CREATE TABLE " + t(SERIAL) + " (id SERIAL PRIMARY KEY, v VARCHAR(20) NULL)");
        } else {
            sql.add("ALTER TABLE " + t(ITEM) + " AUTO_INCREMENT = 101");
        }
        String auto = type("INTEGER GENERATED BY DEFAULT AS IDENTITY", "INTEGER AUTO_INCREMENT");
        sql.add("CREATE TABLE " + t(SIGN) + " (id " + auto + " NOT NULL PRIMARY KEY,"
                + " u " + type("INTEGER", "INT UNSIGNED") + " NULL,"
                + " s INTEGER NULL,"
                + " bu " + type("BIGINT", "BIGINT UNSIGNED") + " NULL)");
        sql.add("CREATE TABLE " + t(CASE_KEY) + " (" + db.q(CASE_KEY_COLUMN) + " " + auto + " NOT NULL PRIMARY KEY,"
                + " v VARCHAR(20) NULL)");
        sql.add("CREATE TABLE " + t(RESERVED_KEY) + " (" + db.q(RESERVED_KEY_COLUMN) + " " + auto
                + " NOT NULL PRIMARY KEY, v VARCHAR(20) NULL)");
        sql.add("CREATE TABLE " + t(CASCADE_CHILD) + " (id INTEGER NOT NULL PRIMARY KEY, parent_id INTEGER NOT NULL"
                + " REFERENCES " + t(PARENT) + " (id) ON DELETE CASCADE)");
        sql.add("INSERT INTO " + t(CASCADE_CHILD) + " (id, parent_id) VALUES (1, 3), (2, 3)");
        sql.add("CREATE TABLE " + t(LINE) + " (order_no INTEGER NOT NULL, branch VARCHAR(50) NOT NULL,"
                + " qty INTEGER NULL, PRIMARY KEY (order_no, branch))");
        sql.add("INSERT INTO " + t(LINE) + " (order_no, branch, qty) VALUES (1, 'a', 1), (1, '" + escape(ODD_BRANCH)
                + "', 2), (2, 'a', 3)");
        sql.add("CREATE TABLE " + t(LOCKED) + " (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NOT NULL,"
                + " w VARCHAR(20) NULL, locked VARCHAR(20) NOT NULL)");
        sql.add("INSERT INTO " + t(LOCKED) + " (id, v, w, locked) VALUES (1, 'a', 'b', 'x')");
        sql.add("CREATE VIEW " + t(STUCK_VIEW) + " AS SELECT MIN(id) AS id, COUNT(*) AS n FROM " + t(PARENT));
        sql.add("CREATE TABLE " + t(NO_KEY) + " (txt VARCHAR(100) NULL)");
        sql.add("CREATE TABLE " + t(DUP) + " (grp INTEGER NOT NULL, v VARCHAR(20) NULL)");
        sql.add("INSERT INTO " + t(DUP) + " (grp, v) VALUES (1, 'x'), (1, 'y'), (2, 'z')");
        sql.add("CREATE TABLE " + t(CONFLICT) + " (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL)");
        sql.add("INSERT INTO " + t(CONFLICT) + " (id, v) VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        if (postgres()) {
            sql.add("CREATE FUNCTION " + db.q(schema) + ".w_conflict_check() RETURNS trigger LANGUAGE plpgsql AS $$"
                    + " BEGIN"
                    + " IF NEW.v = '" + DEADLOCK + "' THEN RAISE EXCEPTION 'x' USING ERRCODE = '40P01'; END IF;"
                    + " IF NEW.v = '" + SERIALIZE + "' THEN RAISE EXCEPTION 'x' USING ERRCODE = '40001'; END IF;"
                    + " IF NEW.v = '" + FORMAT + "' THEN RAISE EXCEPTION 'x' USING ERRCODE = '22P02'; END IF;"
                    + " RETURN NEW; END $$");
            sql.add("CREATE TRIGGER w_conflict_check BEFORE UPDATE ON " + t(CONFLICT)
                    + " FOR EACH ROW EXECUTE FUNCTION " + db.q(schema) + ".w_conflict_check()");
        } else {
            sql.add("CREATE TRIGGER " + db.q(schema) + ".w_conflict_check BEFORE UPDATE ON " + t(CONFLICT)
                    + " FOR EACH ROW BEGIN"
                    + " IF NEW.v = '" + DEADLOCK + "' THEN SIGNAL SQLSTATE '40001' SET MYSQL_ERRNO = 1213; END IF;"
                    + " IF NEW.v = '" + SERIALIZE + "' THEN SIGNAL SQLSTATE '40001' SET MYSQL_ERRNO = 1213; END IF;"
                    + " IF NEW.v = '" + FORMAT + "' THEN SIGNAL SQLSTATE '22007'; END IF;"
                    + " END");
        }
        db.execute(sql.toArray(String[]::new));
    }

    /** SQL の文字の値の中の {@code '} を二重にする（テストの準備の値だけに使う）。 */
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
        String integer = pg ? "int4" : "int";
        Map<String, DslTable> tables = new LinkedHashMap<>();
        tables.put(
                PARENT,
                table(
                        PARENT,
                        false,
                        List.of("id"),
                        column("id").dbType(integer).nullable(false).build(),
                        column("name").length(50).build()));
        List<BusinessDataDslFixture.ColumnBuilder> item = new ArrayList<>(List.of(
                column("id").dbType(integer).nullable(false),
                column("name").length(100).nullable(false).validation(ValidationType.REQUIRED, ValidationOrigin.DB),
                column("note").length(100),
                column("code").length(20).validation(ValidationType.UNIQUE, ValidationOrigin.DB),
                column("soft").length(20).validation(ValidationType.UNIQUE, ValidationOrigin.MANUAL),
                column("hard").length(20),
                column("qty").dbType(integer),
                column("big").dbType(pg ? "int8" : "bigint"),
                column("amount").dbType(pg ? "numeric" : "decimal").decimal(38, 10),
                column("at").dbType(pg ? "timestamp" : "datetime", 6),
                column("at_tz").dbType(pg ? "timestamptz" : "timestamp", 6),
                column("day").dbType("date"),
                column("parent_id").dbType(integer)));
        if (!pg) {
            item.add(column("latin").length(20));
        }
        tables.put(
                ITEM,
                tableWithForeignKeys(
                        ITEM,
                        List.of("id"),
                        List.of(new DslForeignKey(List.of("parent_id"), PARENT, List.of("id"))),
                        item.stream()
                                .map(BusinessDataDslFixture.ColumnBuilder::build)
                                .toArray(cherry.mastersmith.dsl.domain.DslColumn[]::new)));
        if (pg) {
            tables.put(
                    SERIAL,
                    table(
                            SERIAL,
                            false,
                            List.of("id"),
                            column("id").dbType(integer).nullable(false).build(),
                            column("v").length(20).build()));
        }
        tables.put(
                SIGN,
                table(
                        SIGN,
                        false,
                        List.of("id"),
                        column("id").dbType(integer).nullable(false).build(),
                        column("u").dbType(pg ? "int4" : "int unsigned").build(),
                        column("s").dbType(integer).build(),
                        column("bu").dbType(pg ? "int8" : "bigint unsigned").build()));
        tables.put(
                CASE_KEY,
                table(
                        CASE_KEY,
                        false,
                        List.of(CASE_KEY_COLUMN),
                        column(CASE_KEY_COLUMN).dbType(integer).nullable(false).build(),
                        column("v").length(20).build()));
        tables.put(
                RESERVED_KEY,
                table(
                        RESERVED_KEY,
                        false,
                        List.of(RESERVED_KEY_COLUMN),
                        column(RESERVED_KEY_COLUMN)
                                .dbType(integer)
                                .nullable(false)
                                .build(),
                        column("v").length(20).build()));
        tables.put(
                CASCADE_CHILD,
                tableWithForeignKeys(
                        CASCADE_CHILD,
                        List.of("id"),
                        List.of(new DslForeignKey(List.of("parent_id"), PARENT, List.of("id"))),
                        column("id").dbType(integer).nullable(false).build(),
                        column("parent_id").dbType(integer).nullable(false).build()));
        tables.put(
                LINE,
                table(
                        LINE,
                        false,
                        List.of("order_no", "branch"),
                        column("order_no").dbType(integer).nullable(false).build(),
                        column("branch").length(50).nullable(false).build(),
                        column("qty").dbType(integer).build()));
        tables.put(
                LOCKED,
                table(
                        LOCKED,
                        false,
                        List.of("id"),
                        column("id").dbType(integer).nullable(false).build(),
                        // DSL では NULL を許す（DB の NOT NULL と食い違わせ、DB の違反の経路を確かめる）。
                        column("v").length(20).build(),
                        column("w").length(20).build(),
                        // 詳細に出さないため入力できない（BR2.4）。DB の既定値も無い。
                        column("locked").length(20).detail(false).build()));
        tables.put(
                STUCK_VIEW,
                table(
                        STUCK_VIEW,
                        false,
                        List.of("id"),
                        column("id").dbType(integer).build(),
                        column("n").dbType(pg ? "int8" : "bigint").build()));
        tables.put(
                NO_KEY,
                table(NO_KEY, false, List.of(), column("txt").length(100).build()));
        tables.put(
                DUP,
                table(
                        DUP,
                        false,
                        List.of("grp"),
                        column("grp").dbType(integer).nullable(false).build(),
                        column("v").length(20).build()));
        tables.put(
                CONFLICT,
                table(
                        CONFLICT,
                        false,
                        List.of("id"),
                        column("id").dbType(integer).nullable(false).build(),
                        column("v").length(20).build()));
        return new DslModel(
                BusinessDataDslFixture.HASH,
                2,
                List.of(new DslSchema(schema, new DisplayName("書き込み", "Write"), tables)),
                List.of());
    }

    /**
     * 管理者の接続で、表の行の数を数える（アプリとは別の接続）。
     *
     * @param where 条件（無ければ null。テストの架空の値だけ）
     * @param table テーブル
     * @return 行の数
     */
    public long count(String table, String where) {
        return count(db, schema, table, where);
    }

    /**
     * 管理者の接続で、表の行の数を数える（アプリとは別の接続）。
     *
     * @param db テストの DB
     * @param schema スキーマ
     * @param table テーブル
     * @param where 条件（無ければ null。テストの架空の値だけ）
     * @return 行の数
     */
    public static long count(TargetDbTestDatabase db, String schema, String table, String where) {
        List<Map<String, String>> rows = query(
                db,
                "SELECT COUNT(*) AS n FROM " + db.q(schema) + "." + db.q(table)
                        + (where == null ? "" : " WHERE " + where));
        return Long.parseLong(rows.getFirst().get("n"));
    }

    /**
     * 管理者の接続で、条件に合う行を文字にして読む（アプリとは別の接続。日時は UTC のセッションで読む）。
     *
     * @param table テーブル
     * @param where 条件（テストの架空の値だけ）
     * @return 行（カラムの名前は小文字）
     */
    public List<Map<String, String>> rows(String table, String where) {
        return query(db, "SELECT * FROM " + t(table) + (where == null ? "" : " WHERE " + where));
    }

    /**
     * 管理者の接続で、条件に合うちょうど1行を読む。
     *
     * @param table テーブル
     * @param where 条件（テストの架空の値だけ）
     * @return 行（カラムの名前は小文字。無ければ例外）
     */
    public Map<String, String> row(String table, String where) {
        List<Map<String, String>> rows = rows(table, where);
        if (rows.size() != 1) {
            throw new IllegalStateException("行の数が 1 ではありません: " + rows.size());
        }
        return rows.getFirst();
    }

    /**
     * 管理者の接続で問い合わせ、値を文字にして返す（UTC のセッション）。
     *
     * @param db テストの DB
     * @param sql 文（テストの架空の値だけ）
     * @return 行（カラムの名前は小文字）
     */
    public static List<Map<String, String>> query(TargetDbTestDatabase db, String sql) {
        try (Connection connection = db.admin();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    db.product() == DatabaseProduct.POSTGRESQL ? "SET TIME ZONE 'UTC'" : "SET time_zone = '+00:00'");
            List<Map<String, String>> rows = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData meta = resultSet.getMetaData();
                while (resultSet.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i).toLowerCase(java.util.Locale.ROOT), resultSet.getString(i));
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("読み直しの SQL に失敗しました", e);
        }
    }
}
