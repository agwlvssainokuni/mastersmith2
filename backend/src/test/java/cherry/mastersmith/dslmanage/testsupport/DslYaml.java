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
package cherry.mastersmith.dslmanage.testsupport;

import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.parse.SafeYamlParser;
import cherry.mastersmith.dsl.service.DefaultDslReader;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.validate.DslSchemaValidator;
import cherry.mastersmith.dsl.validate.DslSemanticValidator;
import cherry.mastersmith.dsl.validate.PatternChecker;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * U4 のテストで使う小さな DSL（YAML の本文）を組み立てる補助。U2 の検証を通る形だけを作る。
 *
 * <p>{@code DslYaml.dsl().menu("部署", "Departments", "dept").table("dept", DslYaml.column("code")).bytes()} の形で使う。
 * 大きなファイルをリポジトリに置かないため、101 件以上の表示名の未設定なども、ここで組み立てる。
 */
public final class DslYaml {

    private final List<String> menus = new ArrayList<>();

    private final Map<String, String> tables = new LinkedHashMap<>();

    private DslYaml() {}

    /**
     * 組み立てを始める。
     *
     * @return 組み立て
     */
    public static DslYaml dsl() {
        return new DslYaml();
    }

    /**
     * 最上位のメニューの項目（テーブルに紐付く）を足す。
     *
     * @param ja 表示名（日本語）
     * @param en 表示名（英語）
     * @param table 紐付くテーブル
     * @return この組み立て
     */
    public DslYaml menu(String ja, String en, String table) {
        menus.add("  - label: { ja: " + quote(ja) + ", en: " + quote(en) + " }\n    table: " + table + "\n");
        return this;
    }

    /**
     * 表示名が物理名のテーブルを足す。
     *
     * @param name 物理名
     * @param columns カラム（1件以上）
     * @return この組み立て
     */
    public DslYaml table(String name, Column... columns) {
        return table(name, name, name, false, columns);
    }

    /**
     * テーブルまたはビューを足す。
     *
     * @param name 物理名
     * @param ja 表示名（日本語）
     * @param en 表示名（英語）
     * @param view ビューなら true
     * @param columns カラム（1件以上）
     * @return この組み立て
     */
    public DslYaml table(String name, String ja, String en, boolean view, Column... columns) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("  ").append(name).append(":\n");
        yaml.append("    label: { ja: ")
                .append(quote(ja))
                .append(", en: ")
                .append(quote(en))
                .append(" }\n");
        yaml.append("    view: ").append(view).append("\n");
        yaml.append("    primaryKey: []\n    foreignKeys: []\n    columns:\n");
        for (Column column : columns) {
            yaml.append(column.yaml());
        }
        tables.put(name, yaml.toString());
        return this;
    }

    /**
     * YAML の本文を返す。
     *
     * @return 本文
     */
    public String yaml() {
        StringBuilder yaml = new StringBuilder("version: 1\n");
        yaml.append(menus.isEmpty() ? "menus: []\n" : "menus:\n");
        menus.forEach(yaml::append);
        yaml.append(tables.isEmpty() ? "tables: {}\n" : "tables:\n");
        tables.values().forEach(yaml::append);
        return yaml.toString();
    }

    /**
     * YAML の本文を UTF-8 のバイト列で返す。
     *
     * @return 本文のバイト列
     */
    public byte[] bytes() {
        return yaml().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 表示名が物理名で、型が VARCHAR(10) のカラムを作る。
     *
     * @param name 物理名
     * @return カラム
     */
    public static Column column(String name) {
        return new Column(name, name, name, "VARCHAR", 10, null, null, null);
    }

    /**
     * カラムの定義。
     *
     * @param name 物理名
     * @param ja 表示名（日本語）
     * @param en 表示名（英語）
     * @param typeName DB 上の型の名前
     * @param length 長さ（無ければ null）
     * @param precision 精度（無ければ null）
     * @param scale 小数の桁（無ければ null）
     * @param listOrder 一覧の並び順（一覧に出さなければ null）
     */
    public record Column(
            String name,
            String ja,
            String en,
            String typeName,
            Integer length,
            Integer precision,
            Integer scale,
            Integer listOrder) {

        /**
         * 表示名を変える。
         *
         * @param newJa 日本語
         * @param newEn 英語
         * @return 変えたカラム
         */
        public Column label(String newJa, String newEn) {
            return new Column(name, newJa, newEn, typeName, length, precision, scale, listOrder);
        }

        /**
         * 型を変える。
         *
         * @param newTypeName 型の名前
         * @param newLength 長さ
         * @param newPrecision 精度
         * @param newScale 小数の桁
         * @return 変えたカラム
         */
        public Column type(String newTypeName, Integer newLength, Integer newPrecision, Integer newScale) {
            return new Column(name, ja, en, newTypeName, newLength, newPrecision, newScale, listOrder);
        }

        /**
         * 一覧に出す。
         *
         * @param order 並び順
         * @return 変えたカラム
         */
        public Column listed(int order) {
            return new Column(name, ja, en, typeName, length, precision, scale, order);
        }

        String yaml() {
            String list = listOrder == null
                    ? "{ visible: false, sortable: false }"
                    : "{ visible: true, order: " + listOrder + ", sortable: true }";
            return "      " + name + ":\n"
                    + "        label: { ja: " + quote(ja) + ", en: " + quote(en) + " }\n"
                    + "        dbType: { name: " + typeName + ", length: " + nullable(length) + ", precision: "
                    + nullable(precision) + ", scale: " + nullable(scale) + ", nullable: true }\n"
                    + "        formPart: text\n"
                    + "        search: { enabled: false, collapsed: false }\n"
                    + "        list: " + list + "\n"
                    + "        detail: { visible: true }\n"
                    + "        validations: []\n";
        }

        private static String nullable(Integer value) {
            return value == null ? "null" : value.toString();
        }
    }

    /**
     * U2 の本物の読み込みの口を作る（正規表現の確かめの部品は使う側で閉じる）。
     *
     * @param patterns 正規表現の確かめの部品
     * @return 読み込みの口
     */
    public static DslReader newReader(PatternChecker patterns) {
        return new DefaultDslReader(new SafeYamlParser(), new DslSchemaValidator(), new DslSemanticValidator(patterns));
    }

    /**
     * 本文を U2 で読み、検証を通ったモデルを返す（通らなければ失敗させる）。
     *
     * @param reader 読み込みの口
     * @param yaml 本文
     * @return モデル
     */
    public static DslModel model(DslReader reader, byte[] yaml) {
        DslReadResult result = reader.read(yaml);
        if (result instanceof DslReadResult.Valid valid) {
            return valid.model();
        }
        throw new IllegalStateException("テストの DSL が検証を通りません: " + result);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
