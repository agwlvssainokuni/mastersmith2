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
package cherry.mastersmith.role.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 上限ちょうどの大きさの権限の YAML を作る生成の部品（計画の 13節 Q7: A・D-22、{@code performance-design.md} 2.3）。テストからは
 * {@link #generate(Spec)} を方法として呼び、負荷の試験の準備からは1ファイルのソースの起動（{@code java LargeRoleTransferYaml.java …}）で
 * 呼ぶ。そのため、このプロジェクトのほかのクラスに依存しない（JDK だけを使う）。
 *
 * <p>作る形は、ロールごとに1つのスキーマの下へ、テーブルあたり決めた数のカラムを主権限 {@code READ} で明示したもの（スキーマと
 * テーブルにも主権限を書く）。既定の名前（スキーマ {@code large}・テーブル {@code t_001}〜・カラム {@code c_001}〜）は、
 * {@code perf/dsl-timing.sh} が対象DB に作る 100 テーブル × 100 カラムのスキーマから生成した DSL と同じ。中身を書いた後に、目標のバイト数（UTF-8）ちょうどになるよう末尾に YAML のコメントの行を足す（読み込みの
 * 部品はコメントを読み捨てるため、設定は変わらない）。名前はどれも二重引用符の要らない英数字と下線だけで、指定の長さまで {@code x} で
 * 伸ばす。
 *
 * <p>起動の引数: {@code <出力の道> <ロールの数> <ロールあたりのカラムの数> <名前の長さ> <目標のバイト数> [スキーマの名前] [テーブルの名前の
 * 頭] [テーブルあたりのカラムの数] [ロールの名前の頭] [カラムの名前の頭]}。
 */
public final class LargeRoleTransferYaml {

    /** 既定のスキーマの名前。 */
    public static final String DEFAULT_SCHEMA = "large";

    /** 既定のテーブルの名前の頭（{@code t_001} の形）。 */
    public static final String DEFAULT_TABLE_PREFIX = "t_";

    /** 既定のカラムの名前の頭（テーブルごとに {@code c_001} からの形）。 */
    public static final String DEFAULT_COLUMN_PREFIX = "c_";

    /** 既定のテーブルあたりのカラムの数。 */
    public static final int DEFAULT_COLUMNS_PER_TABLE = 100;

    /** 既定のロールの名前の頭。 */
    public static final String DEFAULT_ROLE_PREFIX = "perf_large_";

    private LargeRoleTransferYaml() {}

    /**
     * 作る形の指定。
     *
     * @param roleCount ロールの数（1 以上）
     * @param columnsPerRole ロールあたりの明示するカラムの数（0 以上）
     * @param nameLength カラムの名前の長さ（基の名前より短ければ基の名前のまま。128 以下。0 なら基の名前のまま）
     * @param targetBytes 目標のバイト数（UTF-8）
     * @param schemaName スキーマの名前
     * @param tablePrefix テーブルの名前の頭
     * @param columnsPerTable テーブルあたりのカラムの数（1 以上）
     * @param rolePrefix ロールの名前の頭
     * @param columnPrefix カラムの名前の頭
     */
    public record Spec(
            int roleCount,
            int columnsPerRole,
            int nameLength,
            long targetBytes,
            String schemaName,
            String tablePrefix,
            int columnsPerTable,
            String rolePrefix,
            String columnPrefix) {

        /** 値の範囲を確かめる。 */
        public Spec {
            if (roleCount < 1 || columnsPerRole < 0 || columnsPerTable < 1 || nameLength > 128 || targetBytes < 1) {
                throw new IllegalArgumentException("指定の値の範囲が誤りです");
            }
        }

        /**
         * 既定のスキーマ・テーブル・ロールの名前で作る。
         *
         * @param roleCount ロールの数
         * @param columnsPerRole ロールあたりのカラムの数
         * @param nameLength カラムの名前の長さ
         * @param targetBytes 目標のバイト数
         * @return 指定
         */
        public static Spec of(int roleCount, int columnsPerRole, int nameLength, long targetBytes) {
            return new Spec(
                    roleCount,
                    columnsPerRole,
                    nameLength,
                    targetBytes,
                    DEFAULT_SCHEMA,
                    DEFAULT_TABLE_PREFIX,
                    DEFAULT_COLUMNS_PER_TABLE,
                    DEFAULT_ROLE_PREFIX,
                    DEFAULT_COLUMN_PREFIX);
        }
    }

    /**
     * 目標のバイト数ちょうどの YAML を作る。
     *
     * @param spec 作る形
     * @return YAML の文字列（UTF-8 でちょうど目標のバイト数）
     * @throws IllegalArgumentException 中身だけで目標のバイト数を超えるとき
     */
    public static String generate(Spec spec) {
        StringBuilder yaml = new StringBuilder("version: 1\nroles:\n");
        for (int role = 1; role <= spec.roleCount(); role++) {
            yaml.append("  ")
                    .append(spec.rolePrefix())
                    .append(String.format(Locale.ROOT, "%05d", role))
                    .append(":\n");
            yaml.append("    schemas:\n");
            yaml.append("      ").append(spec.schemaName()).append(":\n");
            yaml.append("        main: READ\n");
            if (spec.columnsPerRole() == 0) {
                continue;
            }
            yaml.append("        tables:\n");
            for (int column = 0; column < spec.columnsPerRole(); column++) {
                if (column % spec.columnsPerTable() == 0) {
                    int table = column / spec.columnsPerTable() + 1;
                    yaml.append("          ")
                            .append(spec.tablePrefix())
                            .append(String.format(Locale.ROOT, "%03d", table))
                            .append(":\n");
                    yaml.append("            main: READ\n");
                    yaml.append("            columns:\n");
                }
                yaml.append("              ").append(columnName(spec, column)).append(": READ\n");
            }
        }
        long contentBytes = yaml.toString().getBytes(StandardCharsets.UTF_8).length;
        long remaining = spec.targetBytes() - contentBytes;
        if (remaining < 0) {
            throw new IllegalArgumentException(
                    "中身だけで目標のバイト数を超えます: content=" + contentBytes + ", target=" + spec.targetBytes());
        }
        if (remaining == 1) {
            yaml.append('\n');
        } else if (remaining >= 2) {
            yaml.append('#').append("x".repeat(Math.toIntExact(remaining - 2))).append('\n');
        }
        return yaml.toString();
    }

    /**
     * 目標のバイト数ちょうどの YAML のバイト列を作る。
     *
     * @param spec 作る形
     * @return UTF-8 のバイト列
     */
    public static byte[] generateBytes(Spec spec) {
        return generate(spec).getBytes(StandardCharsets.UTF_8);
    }

    private static String columnName(Spec spec, int column) {
        String base = spec.columnPrefix() + String.format(Locale.ROOT, "%03d", column % spec.columnsPerTable() + 1);
        return base.length() >= spec.nameLength() ? base : base + "x".repeat(spec.nameLength() - base.length());
    }

    /**
     * 1ファイルのソースの起動で YAML を作り、指定の道に書く。
     *
     * @param args 引数（クラスの説明のとおり）
     * @throws IOException 書けないとき
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 5) {
            System.err.println("usage: java LargeRoleTransferYaml.java <out> <roles> <columnsPerRole> <nameLength>"
                    + " <targetBytes> [schema] [tablePrefix] [columnsPerTable] [rolePrefix] [columnPrefix]");
            System.exit(2);
        }
        Spec spec = new Spec(
                Integer.parseInt(args[1]),
                Integer.parseInt(args[2]),
                Integer.parseInt(args[3]),
                Long.parseLong(args[4]),
                args.length > 5 ? args[5] : DEFAULT_SCHEMA,
                args.length > 6 ? args[6] : DEFAULT_TABLE_PREFIX,
                args.length > 7 ? Integer.parseInt(args[7]) : DEFAULT_COLUMNS_PER_TABLE,
                args.length > 8 ? args[8] : DEFAULT_ROLE_PREFIX,
                args.length > 9 ? args[9] : DEFAULT_COLUMN_PREFIX);
        Files.write(Path.of(args[0]), generateBytes(spec));
    }
}
