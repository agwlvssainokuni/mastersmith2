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

import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.generate;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.valid;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.validate.PatternChecker;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetColumn;
import cherry.mastersmith.targetdb.domain.TargetDbType;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetTable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;

/**
 * 組み立てと書き出しの性質ベースのテスト（jqwik。BR5.1、NFR4.7・NFR4.8）。
 *
 * <p>失敗したときの乱数の種は jqwik の報告に出る（{@code @Property(seed = "...")} に与えて再現する。
 * {@code src/test/resources/junit-platform.properties}）。
 */
class DslGenerationPropertyTest {

    private static final PatternChecker PATTERNS = GenerateTestSupport.newPatternChecker();

    private static final DslReader READER = GenerateTestSupport.newReader(PATTERNS);

    private static final TargetDbType VARCHAR = new TargetDbType("varchar", 20L, null, null, "varchar(20)");

    @AfterContainer
    static void close() {
        PATTERNS.close();
    }

    /** YAML で意味を持つ記号・改行・引用符・制御文字・日本語・サロゲートの組を含む文字列。 */
    @Provide
    Arbitrary<String> texts() {
        Arbitrary<Character> chars = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(6, Arbitraries.chars().range(' ', '~')),
                net.jqwik.api.Tuple.of(
                        4,
                        Arbitraries.of(
                                ':', '#', '-', '\n', '\'', '"', '&', '*', '!', '|', '>', '%', '@', '`', '{', '}', '[',
                                ']', ',', '?', '~', '\t', '\\')),
                net.jqwik.api.Tuple.of(1, Arbitraries.chars().range('\u0000', '\u001F')),
                net.jqwik.api.Tuple.of(1, Arbitraries.chars().range('\u007F', ' ')),
                net.jqwik.api.Tuple.of(2, Arbitraries.chars().range('ぁ', 'ヿ')),
                net.jqwik.api.Tuple.of(1, Arbitraries.of(' ', ' ', '﻿', 'ÿ', '�')));
        Arbitrary<String> plain = chars.list().ofMinSize(1).ofMaxSize(20).map(list -> {
            StringBuilder text = new StringBuilder();
            list.forEach(text::append);
            return text.toString();
        });
        return Arbitraries.oneOf(plain, plain.map(text -> text + "😀"));
    }

    /** 名前の一覧（1〜4 件。重なりは写しを作るときに除く）。 */
    @Provide
    Arbitrary<List<String>> nameLists() {
        return texts().list().ofMinSize(1).ofMaxSize(4);
    }

    @Property(tries = 200)
    @Label("the same schema copy always gives the same bytes")
    void sameCopySameBytes(@ForAll("nameLists") List<String> names, @ForAll("texts") String comment) {
        TargetSchema schema = schemaOf(names, comment);

        assertThat(generate(schema)).isEqualTo(generate(schemaOf(names, comment)));
    }

    @Property(tries = 300)
    @Label("arbitrary names and comments pass the U2 validation and read back as the same values")
    void arbitraryTextsRoundTrip(@ForAll("nameLists") List<String> names, @ForAll("texts") String comment) {
        TargetSchema schema = schemaOf(names, comment);

        DslModel model = valid(READER, generate(schema));

        String expectedJa = DslYamlWriter.stripControlCharacters(comment);
        assertThat(model.tables().keySet())
                .containsExactlyInAnyOrderElementsOf(
                        schema.tables().stream().map(TargetTable::name).toList());
        for (TargetTable table : schema.tables()) {
            DslTable read = model.tables().get(table.name());
            assertThat(read.label().en()).isEqualTo(table.name());
            assertThat(read.label().ja()).isEqualTo(expectedJa == null ? table.name() : expectedJa);
            for (TargetColumn column : table.columns()) {
                assertThat(read.columns().get(column.name()).label().en()).isEqualTo(column.name());
                assertThat(read.columns().get(column.name()).label().ja())
                        .isEqualTo(expectedJa == null ? column.name() : expectedJa);
            }
        }
    }

    /** 名前を重なりなく並べ、テーブルごとに同じ名前のカラムを持たせた写しを作る。 */
    private static TargetSchema schemaOf(List<String> names, String comment) {
        Set<String> unique = new LinkedHashSet<>(names);
        unique.add("t");
        List<TargetTable> tables = new ArrayList<>();
        for (String name : unique) {
            List<TargetColumn> columns = new ArrayList<>();
            for (String columnName : unique) {
                columns.add(new TargetColumn(columnName, VARCHAR, true, null, comment));
            }
            tables.add(new TargetTable(name, false, comment, columns, List.of(), List.of()));
        }
        return new TargetSchema(DatabaseProduct.MARIADB, "s", tables);
    }
}
