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
package cherry.mastersmith.dslmanage.generate;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.parse.SafeYamlParser;
import cherry.mastersmith.dsl.service.DefaultDslReader;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.validate.DslSchemaValidator;
import cherry.mastersmith.dsl.validate.DslSemanticValidator;
import cherry.mastersmith.dsl.validate.PatternChecker;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetColumn;
import cherry.mastersmith.targetdb.domain.TargetDbType;
import cherry.mastersmith.targetdb.domain.TargetForeignKey;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetTable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * U3 の単体テストの補助。写しの組み立てと、U2 の本物の読み込みの口（{@link DefaultDslReader}）での検証を受け持つ。
 *
 * <p>正規表現の確かめの部品はスレッドを持つため、使うテストのクラスで {@link #newPatternChecker()} を作り、終わりに閉じる。
 */
final class GenerateTestSupport {

    /** 短い文字列の型。 */
    static final TargetDbType VARCHAR_50 = new TargetDbType("varchar", 50L, null, null, "varchar(50)");

    /** 整数の型。 */
    static final TargetDbType INT = new TargetDbType("int", null, 10, 0, "int");

    /** 長い文字列の型。 */
    static final TargetDbType TEXT = new TargetDbType("text", 65_535L, null, null, "text");

    private GenerateTestSupport() {}

    /**
     * 正規表現の確かめの部品を作る（使うクラスの終わりに閉じる）。
     *
     * @return 正規表現の確かめの部品
     */
    static PatternChecker newPatternChecker() {
        return new PatternChecker();
    }

    /**
     * U2 の本物の読み込みの口を作る。
     *
     * @param patterns 正規表現の確かめの部品
     * @return 読み込みの口
     */
    static DslReader newReader(PatternChecker patterns) {
        return new DefaultDslReader(new SafeYamlParser(), new DslSchemaValidator(), new DslSemanticValidator(patterns));
    }

    /**
     * 写しから本文を作る（組み立てと書き出し）。
     *
     * @param schema 写し
     * @return 本文
     */
    static byte[] generate(TargetSchema schema) {
        return new DslYamlWriter().write(new DslTreeBuilder().build(schema));
    }

    /**
     * 本文を U2 で検証し、通ったモデルを返す（通らなければ誤りの一覧つきで失敗させる）。
     *
     * @param reader 読み込みの口
     * @param yaml 本文
     * @return モデル
     */
    static DslModel valid(DslReader reader, byte[] yaml) {
        DslReadResult result = reader.read(yaml);
        assertThat(result).as(() -> "U2 の検証を通ること: " + result).isInstanceOf(DslReadResult.Valid.class);
        return ((DslReadResult.Valid) result).model();
    }

    /**
     * MySQL のスキーマの写しを作る。
     *
     * @param tables テーブル
     * @return 写し
     */
    static TargetSchema schema(TargetTable... tables) {
        return new TargetSchema(DatabaseProduct.MYSQL, "sales", List.of(tables));
    }

    /**
     * テーブルを作る。
     *
     * @param name 物理名
     * @param comment コメント
     * @param primaryKey 主キー
     * @param foreignKeys 外部キー
     * @param columns カラム
     * @return テーブル
     */
    static TargetTable table(
            String name,
            String comment,
            List<String> primaryKey,
            List<TargetForeignKey> foreignKeys,
            TargetColumn... columns) {
        return new TargetTable(name, false, comment, List.of(columns), primaryKey, foreignKeys);
    }

    /**
     * NULL を許すカラムを作る。
     *
     * @param name 物理名
     * @param type 型
     * @return カラム
     */
    static TargetColumn nullable(String name, TargetDbType type) {
        return new TargetColumn(name, type, true, null, null);
    }

    /**
     * NOT NULL で既定値の無いカラムを作る。
     *
     * @param name 物理名
     * @param type 型
     * @return カラム
     */
    static TargetColumn notNull(String name, TargetDbType type) {
        return new TargetColumn(name, type, false, null, null);
    }

    /**
     * テストの資源（{@code src/test/resources/cherry/mastersmith/dslmanage/generate/}）の期待する本文を読む。先頭の見出しの
     * コメントから最初の空行までを取り除く。
     *
     * @param name ファイルの名前
     * @return 期待する本文
     */
    static String expected(String name) {
        String path = "cherry/mastersmith/dslmanage/generate/" + name;
        try (InputStream in = GenerateTestSupport.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("資源が見つかりません: " + path);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.substring(text.indexOf("\n\n") + 2);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
