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

import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 他テーブル参照の結合テストの参照先・参照元の表と DSL（Intent 261029-reference-lookup-cleanup の U1、unit-test-instructions.md）。表は
 * {@link BusinessDataSampleSchema} がクラスごとに作る一意の名前のスキーマ（MySQL・MariaDB はデータベース）に作り、スキーマごと消える。
 * 値はすべて架空。DB の種類ごとの型と値の書き方の差を、この1か所にまとめる。
 *
 * <p>参照先の値のカラムは {@code v}、ラベルのカラムは {@code l}、主キーは {@code id}（整数）。参照元の表は、確かめる組ごとに分ける
 * （1要求で読む参照先の数を、全体の上限の既定 10 秒に収めるため）。
 */
public final class ReferenceTables {

    /** 199・200・201・10,000 行の参照先と、それを参照する参照元。 */
    public static final String SRC_COUNTS = "src_counts";

    /** 長い値の行が混ざる参照先と、値・ラベルの長さの境界の参照先を参照する参照元。 */
    public static final String SRC_LENGTHS = "src_lengths";

    /** 型・同じ値・引用符を含む名前・参照先の形（複合の主キー・主キーの無い表・ビュー・自己参照）を参照する参照元。 */
    public static final String SRC_SHAPES = "src_shapes";

    /** 先頭 100 字が同じ並び・長いラベルの 2,000 行・信頼できない値・読めない参照先・LOOKUP を参照する参照元。 */
    public static final String SRC_MISC = "src_misc";

    /** LOOKUP のピッカー（行の数の境界・エスケープ・値の型・長いラベル）と、厳密な一致の確かめの参照元（B2）。 */
    public static final String SRC_PICK = "src_pick";

    /** 検索 API の認可・同じ応答・上限・漏えい・トレースの結合テストの小さな参照元（B2。{@link #createApi()}）。 */
    public static final String SRC_API = "src_api";

    /** 小さな参照先（B2）。 */
    public static final String TGT = "tgt";

    /** 小さな参照先の値の目印（B2。漏えいの確かめ）。 */
    public static final String VALUE_MARK = "zz-ref-value-5e1a";

    /** 小さな参照先のラベルの目印（B2）。 */
    public static final String LABEL_MARK = "zz-ref-label-93bd";

    /** 小さな参照先の一覧の列の目印（B2）。 */
    public static final String CELL_MARK = "zz-ref-cell-71f0";

    /** 小さな参照先の検索の列の目印（B2）。 */
    public static final String SEARCH_MARK = "zz-ref-search-2c48";

    /** 引用符・{@code ;}・{@code --} を含む参照先のテーブルの名前。 */
    public static final String QUOTED = "q'r\"e;f--";

    /** 引用符を含む値のカラムの名前。 */
    public static final String QUOTED_VALUE = "va'l";

    /** 引用符を含むラベルのカラムの名前。 */
    public static final String QUOTED_LABEL = "la\"b";

    /** 信頼できない値（HTML のタグ）。 */
    public static final String XSS_VALUE = "<script>alert(1)</script>";

    /** 信頼できない値（属性・URL）。 */
    public static final String XSS_LABEL = "<img src=x onerror=alert(1)> \"q\" 'q' javascript:alert(1)";

    /** BIGINT の最大。 */
    public static final String BIG_MAX = "9223372036854775807";

    /** 桁の多い小数。 */
    public static final String BIG_DECIMAL = "1234567890123456789012345678.0123456789";

    /** 補助面の文字（CHAR_LENGTH で1文字）。 */
    public static final String SUPPLEMENTARY = "𠮷";

    private final BusinessDataSampleSchema sample;

    /**
     * 作る。
     *
     * @param sample 見本のスキーマ（起動済み）
     */
    public ReferenceTables(BusinessDataSampleSchema sample) {
        this.sample = sample;
    }

    private boolean postgres() {
        return sample.database().product() == DatabaseProduct.POSTGRESQL;
    }

    private String integer() {
        return postgres() ? "int4" : "int";
    }

    private String text() {
        return postgres() ? "text" : "longtext";
    }

    private String textSql() {
        return postgres() ? "TEXT" : "LONGTEXT";
    }

    private String q(String name) {
        return sample.database().q(name);
    }

    private static String repeat(String text, int count) {
        return "REPEAT('" + text + "', " + count + ")";
    }

    /** 表・ビュー・行を作る（管理者で流し、読み取りだけのアカウントに読む権限を与える）。 */
    public void create() {
        List<String> sql = new ArrayList<>();
        for (int count : new int[] {199, 200, 201, 10_000}) {
            String name = "r" + count;
            sql.add("CREATE TABLE %s" + name
                    + " (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL, l VARCHAR(50) NULL)");
            sql.addAll(rows(name, count, 0, i -> "'V" + pad(i) + "', 'L" + pad(i) + "'"));
        }
        for (int count : new int[] {200, 201}) {
            String name = "mix" + count;
            sql.add("CREATE TABLE %s" + name + " (id INTEGER NOT NULL PRIMARY KEY, v " + textSql() + " NULL,"
                    + " l VARCHAR(50) NULL)");
            sql.addAll(rows(name, count, 0, i -> "'V" + pad(i) + "', 'L" + pad(i) + "'"));
            // 値が 10,000 字を超える行（SQL の条件で除く。NFR2.8、T-B2・T-B3）。
            for (int i = 0; i < 3; i++) {
                sql.add("INSERT INTO %s" + name + " (id, v, l) VALUES (" + (100_000 + i) + ", " + repeat("x", 10_001)
                        + ", 'A長い" + i + "')");
            }
        }
        sql.add("CREATE TABLE %sedge (id INTEGER NOT NULL PRIMARY KEY, v " + textSql() + " NULL, l " + textSql()
                + " NULL)");
        sql.add("INSERT INTO %sedge (id, v, l) VALUES"
                + " (1, " + repeat("a", 10_000) + ", 'A'),"
                + " (2, " + repeat("あ", 10_000) + ", 'B'),"
                + " (3, " + repeat(SUPPLEMENTARY, 10_000) + ", 'C'),"
                + " (4, " + repeat("a", 10_001) + ", 'D'),"
                + " (5, " + repeat("あ", 10_001) + ", 'E'),"
                + " (6, " + repeat(SUPPLEMENTARY, 10_001) + ", 'F'),"
                + " (7, " + repeat("b", 100) + ", " + repeat("ら", 100) + "),"
                + " (8, " + repeat("c", 101) + ", " + repeat("り", 101) + "),"
                + " (9, 'nl', NULL),"
                + " (10, NULL, 'G')");
        sql.add("CREATE TABLE %snums (id INTEGER NOT NULL PRIMARY KEY, v BIGINT NULL, l VARCHAR(20) NULL)");
        sql.add("INSERT INTO %snums (id, v, l) VALUES (1, " + BIG_MAX + ", 'max'), (2, -9223372036854775808, 'min'),"
                + " (3, 1, 'one')");
        sql.add("CREATE TABLE %sdecs (id INTEGER NOT NULL PRIMARY KEY, v " + (postgres() ? "NUMERIC" : "DECIMAL")
                + "(38,10) NULL, l VARCHAR(20) NULL)");
        sql.add("INSERT INTO %sdecs (id, v, l) VALUES (1, " + BIG_DECIMAL + ", 'big'), (2, 1.5, 'y'), (3, 1.50, 'x')");
        sql.add("CREATE TABLE %sdupval (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL, l VARCHAR(20) NULL)");
        sql.add("INSERT INTO %sdupval (id, v, l) VALUES (1, 'K', 'b'), (2, 'K', 'a'), (3, 'k', 'c')");
        sql.add("CREATE TABLE %s" + q(QUOTED) + " (id INTEGER NOT NULL PRIMARY KEY, " + q(QUOTED_VALUE)
                + " VARCHAR(20) NULL, " + q(QUOTED_LABEL) + " VARCHAR(20) NULL)");
        sql.add("INSERT INTO %s" + q(QUOTED) + " (id, " + q(QUOTED_VALUE) + ", " + q(QUOTED_LABEL)
                + ") VALUES (1, 'it''s', 'say \"hi\"')");
        sql.add("CREATE TABLE %scomp (a INTEGER NOT NULL, b VARCHAR(20) NOT NULL, l VARCHAR(20) NULL,"
                + " PRIMARY KEY (a, b))");
        sql.add("INSERT INTO %scomp (a, b, l) VALUES (1, 'B1', 'one'), (2, 'B1', 'two'), (1, 'B2', 'three')");
        sql.add("CREATE TABLE %snokey (v VARCHAR(20) NULL, l VARCHAR(20) NULL)");
        sql.add("INSERT INTO %snokey (v, l) VALUES ('N1', 'n'), ('N2', 'm')");
        sql.add("CREATE VIEW %svw AS SELECT id, v, l FROM %sr199");
        sql.add("CREATE TABLE %sprefix (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL, l " + textSql()
                + " NULL)");
        sql.add("INSERT INTO %sprefix (id, v, l) VALUES (1, 'p2', CONCAT(" + repeat("同", 100) + ", '1')),"
                + " (2, 'p1', CONCAT(" + repeat("同", 100) + ", '2')), (3, 'p3', 'A先')");
        sql.add("CREATE TABLE %swide (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL, l " + textSql() + " NULL)");
        sql.addAll(rows("wide", 2_000, 0, i -> "'W" + pad(i) + "', " + (i % 10 == 0 ? repeat("長", 10_000) : "'w'")));
        sql.add("CREATE TABLE %sxss (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(100) NULL, l VARCHAR(200) NULL)");
        sql.add("INSERT INTO %sxss (id, v, l) VALUES (1, '" + XSS_VALUE + "', '" + XSS_LABEL.replace("'", "''") + "')");
        sql.add("CREATE TABLE %ssecret (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL, l VARCHAR(20) NULL)");
        sql.add("INSERT INTO %ssecret (id, v, l) VALUES (1, 'S1', '社外秘')");
        // ピッカーの行の数の境界（B2、T-B5）。目印の語ごとに 20・21・40・41 行と、10,000 字を超える値の行を2つずつ。
        sql.add("CREATE TABLE %spick (id INTEGER NOT NULL PRIMARY KEY, v " + textSql() + " NULL, l VARCHAR(50) NULL)");
        String[] groups = {"A20", "B21", "C40", "D41"};
        int[] sizes = {20, 21, 40, 41};
        for (int g = 0; g < groups.length; g++) {
            String group = groups[g];
            sql.addAll(
                    rows("pick", sizes[g], g * 100, i -> "'P" + group + pad(i) + "', '" + group + "-" + pad(i) + "'"));
            for (int k = 0; k < 2; k++) {
                sql.add("INSERT INTO %spick (id, v, l) VALUES (" + (900 + g * 10 + k) + ", " + repeat("z", 10_001)
                        + ", '" + group + "-L" + k + "')");
            }
        }
        // 部分一致のエスケープ（B2、(e)）。MySQL・MariaDB の文字の書き方ではバックスラッシュを重ねる。
        String backslash = postgres() ? "\\" : "\\\\";
        sql.add("CREATE TABLE %sesc (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL, l VARCHAR(20) NULL)");
        sql.add("INSERT INTO %sesc (id, v, l) VALUES (1, 'e1', '50%off'), (2, 'e2', '50Xoff'), (3, 'e3', 'a_b'),"
                + " (4, 'e4', 'aXb'), (5, 'e5', 'c" + backslash + "d'), (6, 'e6', 'cXd')");
        // 末尾の空白だけが違う値（MariaDB の PAD SPACE。T-B7）と、大文字と小文字だけが違う値（B2）。
        sql.add("CREATE TABLE %spad (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(10001) NULL, l VARCHAR(20) NULL)");
        sql.add("INSERT INTO %spad (id, v, l) VALUES (1, 'x', 'px'), (2, CONCAT('x', " + repeat(" ", 9_999)
                + "), 'p9999'), (3, CONCAT('x', " + repeat(" ", 10_000) + "), 'p10000')");
        sql.add("CREATE TABLE %scs (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL, l VARCHAR(20) NULL)");
        sql.add("INSERT INTO %scs (id, v, l) VALUES (1, 'ABC', 'big')");
        sql.add("CREATE TABLE %s" + SRC_PICK + " (id INTEGER NOT NULL PRIMARY KEY, name VARCHAR(50) NULL)");
        for (String source : List.of(SRC_COUNTS, SRC_LENGTHS, SRC_SHAPES, SRC_MISC)) {
            sql.add("CREATE TABLE %s" + source + " (id INTEGER NOT NULL PRIMARY KEY, name VARCHAR(50) NULL)");
        }
        // 自己参照の参照先（参照元と同じ表）の行。
        sql.add("INSERT INTO %s" + SRC_SHAPES + " (id, name) VALUES (1, '親'), (2, '子')");
        sample.addObjects(sql.toArray(String[]::new));
    }

    private static String pad(int i) {
        return String.format("%05d", i);
    }

    /** {@code count} 行を 500 行ずつの文で入れる（id は 1 から）。 */
    private static List<String> rows(
            String table, int count, int offset, java.util.function.IntFunction<String> values) {
        List<String> sql = new ArrayList<>();
        StringBuilder current = null;
        for (int i = 1; i <= count; i++) {
            if (current == null) {
                current = new StringBuilder("INSERT INTO %s" + table + " (id, v, l) VALUES ");
            } else {
                current.append(", ");
            }
            current.append('(')
                    .append(offset + i)
                    .append(", ")
                    .append(values.apply(i))
                    .append(')');
            if (i % 500 == 0 || i == count) {
                sql.add(current.toString());
                current = null;
            }
        }
        return sql;
    }

    private static OptionSource reference(String table, String value, String label) {
        return new OptionSource(OptionSourceKind.REFERENCE, List.of(), table, value, label, List.of(), List.of());
    }

    private DslTable target(String name, List<String> primaryKey, String valueType, String labelType) {
        return table(
                name,
                false,
                primaryKey,
                column("id").dbType(integer()).build(),
                column("v").dbType(valueType).build(),
                column("l").dbType(labelType).build());
    }

    private static DslColumn ref(String name, OptionSource options) {
        return column(name).form(FormPart.SELECT).options(options).build();
    }

    /**
     * 参照先・参照元の DSL のテーブルを返す（見本のスキーマの {@code model(...)} に足す）。
     *
     * @return テーブル
     */
    public DslTable[] tables() {
        String bigint = postgres() ? "int8" : "bigint";
        String decimal = postgres() ? "numeric" : "decimal";
        List<DslTable> tables = new ArrayList<>();
        for (String name : List.of("r199", "r200", "r201", "r10000", "dupval", "secret", "xss")) {
            tables.add(target(name, List.of("id"), "varchar", "varchar"));
        }
        tables.add(target("mix200", List.of("id"), text(), "varchar"));
        tables.add(target("mix201", List.of("id"), text(), "varchar"));
        tables.add(target("edge", List.of("id"), text(), text()));
        tables.add(target("nums", List.of("id"), bigint, "varchar"));
        tables.add(target("decs", List.of("id"), decimal, "varchar"));
        tables.add(target("prefix", List.of("id"), "varchar", text()));
        tables.add(target("wide", List.of("id"), "varchar", text()));
        tables.add(table(
                QUOTED,
                false,
                List.of("id"),
                column("id").dbType(integer()).build(),
                column(QUOTED_VALUE).build(),
                column(QUOTED_LABEL).build()));
        tables.add(table(
                "comp",
                false,
                List.of("a", "b"),
                column("a").dbType(integer()).build(),
                column("b").build(),
                column("l").build()));
        tables.add(table(
                "nokey", false, List.of(), column("v").build(), column("l").build()));
        tables.add(table(
                "vw",
                true,
                List.of("id"),
                column("id").dbType(integer()).build(),
                column("v").build(),
                column("l").build()));
        tables.add(target("pick", List.of("id"), text(), "varchar"));
        for (String name : List.of("esc", "pad", "cs")) {
            tables.add(target(name, List.of("id"), "varchar", "varchar"));
        }
        tables.add(source(
                SRC_PICK,
                lookup("pick", "pick", List.of(new LookupSearchItem("l", SearchOperator.CONTAINS)), "l", "v"),
                lookup("esc", "esc", List.of(new LookupSearchItem("l", SearchOperator.CONTAINS)), "l"),
                lookup("numpick", "nums", List.of(new LookupSearchItem("v", SearchOperator.EQUALS)), "l"),
                lookup("intpick", "r199", List.of(new LookupSearchItem("id", SearchOperator.EQUALS)), "l", "id"),
                lookup("widepick", "wide", List.of(new LookupSearchItem("l", SearchOperator.CONTAINS)), "l"),
                ref("padref", reference("pad", "v", "l")),
                ref("csref", reference("cs", "v", "l"))));
        tables.add(source(
                SRC_COUNTS,
                ref("c199", reference("r199", "v", "l")),
                ref("c200", reference("r200", "v", "l")),
                ref("c201", reference("r201", "v", "l")),
                ref("c10000", reference("r10000", "v", "l"))));
        tables.add(source(
                SRC_LENGTHS,
                ref("m200", reference("mix200", "v", "l")),
                ref("m201", reference("mix201", "v", "l")),
                ref("edge", reference("edge", "v", "l"))));
        tables.add(source(
                SRC_SHAPES,
                ref("num", reference("nums", "v", "l")),
                ref("dec", reference("decs", "v", "l")),
                ref("dup", reference("dupval", "v", "l")),
                ref("quoted", reference(QUOTED, QUOTED_VALUE, QUOTED_LABEL)),
                ref("comp", reference("comp", "b", "l")),
                ref("nokey", reference("nokey", "v", "l")),
                ref("view", reference("vw", "v", "l")),
                ref("parent", reference(SRC_SHAPES, "id", "name"))));
        tables.add(source(
                SRC_MISC,
                ref("prefix", reference("prefix", "v", "l")),
                ref("wide", reference("wide", "v", "l")),
                ref("xss", reference("xss", "v", "l")),
                ref("secret", reference("secret", "v", "l")),
                column("pick")
                        .form(FormPart.LOOKUP)
                        .options(new OptionSource(
                                OptionSourceKind.LOOKUP,
                                List.of(),
                                "r10000",
                                "v",
                                null,
                                List.of(new LookupSearchItem("l", SearchOperator.CONTAINS)),
                                List.of(new LookupListItem("l", 1), new LookupListItem("v", 2))))
                        .build()));
        return tables.toArray(DslTable[]::new);
    }

    /** LOOKUP の参照のカラム（値のカラムは {@code v}、ラベルは一覧の最初の列）。 */
    private static DslColumn lookup(String name, String table, List<LookupSearchItem> search, String... list) {
        List<LookupListItem> items = new ArrayList<>();
        for (int i = 0; i < list.length; i++) {
            items.add(new LookupListItem(list[i], i + 1));
        }
        return column(name)
                .form(FormPart.LOOKUP)
                .options(new OptionSource(OptionSourceKind.LOOKUP, List.of(), table, "v", null, search, items))
                .build();
    }

    private DslTable source(String name, DslColumn... references) {
        List<DslColumn> columns = new ArrayList<>();
        columns.add(column("id").dbType(integer()).build());
        columns.add(column("name").build());
        columns.addAll(List.of(references));
        return table(name, false, List.of("id"), columns.toArray(DslColumn[]::new));
    }

    /**
     * スキーマを FULL（補助権限 CREATE・DELETE あり）にし、{@code secret} だけを NONE にした設定を返す。
     *
     * @return 設定
     */
    public Map<PermissionTarget, PermissionValues> settings() {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(sample.schema()), new PermissionValues(MainPermission.FULL, true, true));
        settings.put(
                PermissionTarget.table(sample.schema(), "secret"),
                new PermissionValues(MainPermission.NONE, null, null));
        return settings;
    }

    // ── 小さな参照元と参照先（B2。検索 API の認可・同じ応答・上限・漏えい・トレース） ──

    /** 小さな参照先・参照元の表と行を作る（{@link #create()} とは別に、軽い結合テストで使う）。 */
    public void createApi() {
        String binary = postgres() ? "BYTEA" : "LONGBLOB";
        sample.addObjects(
                "CREATE TABLE %s" + TGT + " (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(40) NULL, l VARCHAR(40) NULL,"
                        + " x VARCHAR(40) NULL, s VARCHAR(40) NULL, n INTEGER NULL)",
                "INSERT INTO %s" + TGT + " (id, v, l, x, s, n) VALUES (1, 'V1', 'L1', 'X1', 'S1', 1),"
                        + " (2, 'V2', 'L2', 'X2', 'S2', 2), (3, '" + VALUE_MARK + "', '" + LABEL_MARK + "', '"
                        + CELL_MARK + "', '" + SEARCH_MARK + "', 3)",
                "CREATE TABLE %stgtbin (id INTEGER NOT NULL PRIMARY KEY, v " + binary + " NULL, l VARCHAR(40) NULL)",
                "CREATE TABLE %stgtpw (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(40) NULL, l VARCHAR(40) NULL)",
                "INSERT INTO %stgtpw (id, v, l) VALUES (1, 'W1', 'L1')",
                "CREATE TABLE %s" + SRC_API + " (id INTEGER NOT NULL PRIMARY KEY, memo VARCHAR(40) NULL)");
    }

    /**
     * 小さな参照先・参照元の DSL のテーブルを返す。参照元 {@link #SRC_API} のカラム: {@code pick}（LOOKUP。検索は {@code s}・{@code n}
     * の完全一致、一覧は {@code l}・{@code x}）、{@code ref}（REFERENCE）、{@code pickbin}（値がバイナリ）、{@code pickpw}（値が
     * password）、{@code refnum}（値が整数の REFERENCE）、{@code memo}（参照でない）。
     *
     * @return テーブル
     */
    public DslTable[] apiTables() {
        String binary = postgres() ? "bytea" : "longblob";
        return new DslTable[] {
            table(
                    TGT,
                    false,
                    List.of("id"),
                    column("id").dbType(integer()).build(),
                    column("v").build(),
                    column("l").build(),
                    column("x").build(),
                    column("s").build(),
                    column("n").dbType(integer()).build()),
            table(
                    "tgtbin",
                    false,
                    List.of("id"),
                    column("id").dbType(integer()).build(),
                    column("v").dbType(binary).build(),
                    column("l").build()),
            table(
                    "tgtpw",
                    false,
                    List.of("id"),
                    column("id").dbType(integer()).build(),
                    column("v").form(FormPart.PASSWORD).build(),
                    column("l").build()),
            table(
                    SRC_API,
                    false,
                    List.of("id"),
                    column("id").dbType(integer()).build(),
                    column("memo").build(),
                    lookup(
                            "pick",
                            TGT,
                            List.of(
                                    new LookupSearchItem("s", SearchOperator.EQUALS),
                                    new LookupSearchItem("n", SearchOperator.EQUALS)),
                            "l",
                            "x"),
                    ref("ref", reference(TGT, "v", "l")),
                    ref("refnum", reference(TGT, "n", "l")),
                    lookup("pickbin", "tgtbin", List.of(new LookupSearchItem("l", SearchOperator.CONTAINS)), "l"),
                    lookup("pickpw", "tgtpw", List.of(new LookupSearchItem("l", SearchOperator.CONTAINS)), "l"))
        };
    }

    /**
     * スキーマを FULL（補助権限 CREATE・DELETE あり）にし、与えた上書きを足した設定を返す。
     *
     * @param overrides 上書き（無ければ空）
     * @return 設定
     */
    public Map<PermissionTarget, PermissionValues> apiSettings(Map<PermissionTarget, PermissionValues> overrides) {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(sample.schema()), new PermissionValues(MainPermission.FULL, true, true));
        settings.putAll(overrides);
        return settings;
    }
}
