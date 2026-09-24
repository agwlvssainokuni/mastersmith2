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

import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.INT;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.generate;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.nullable;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.schema;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.table;
import static cherry.mastersmith.dslmanage.generate.GenerateTestSupport.valid;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.validate.PatternChecker;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.DumperOptions;

/** 書き出しの単体テスト（BR5.1、NFR4.7、security-design.md の 2節の固定の書き出しの形）。 */
class DslYamlWriterTest {

    private static final PatternChecker PATTERNS = GenerateTestSupport.newPatternChecker();

    private static final DslReader READER = GenerateTestSupport.newReader(PATTERNS);

    @AfterAll
    static void close() {
        PATTERNS.close();
    }

    @Test
    @DisplayName("the dumper options are fixed: block style, indent 2, no width limit, LF, no aliases, unicode")
    void fixedOptions() {
        DumperOptions options = DslYamlWriter.dumperOptions();

        assertThat(options.getDefaultFlowStyle()).isEqualTo(DumperOptions.FlowStyle.BLOCK);
        assertThat(options.getIndent()).isEqualTo(2);
        assertThat(options.getWidth()).isEqualTo(Integer.MAX_VALUE);
        assertThat(options.getSplitLines()).isFalse();
        assertThat(options.getLineBreak()).isEqualTo(DumperOptions.LineBreak.UNIX);
        assertThat(options.isDereferenceAliases()).isTrue();
        assertThat(options.isAllowUnicode()).isTrue();
        assertThat(options.getNonPrintableStyle()).isEqualTo(DumperOptions.NonPrintableStyle.ESCAPE);
        assertThat(options.isExplicitStart()).isFalse();
    }

    @Test
    @DisplayName("the same map shared twice in the tree is written twice without anchors and aliases")
    void noAliasesForSharedValues() {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("ja", "同じ");
        shared.put("en", "same");
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("a", shared);
        tree.put("b", shared);
        tree.put("list", List.of(shared, shared));

        String yaml = new String(new DslYamlWriter().write(tree), StandardCharsets.UTF_8);

        assertThat(yaml).doesNotContain("&").doesNotContain("*").doesNotContain("!!");
        assertThat(yaml.split("ja: 同じ", -1)).hasSize(5);
    }

    @Test
    @DisplayName("names with CR, NEL, line and paragraph separators are double quoted and read back unchanged")
    void lineBreaksInNames() {
        List<String> names = List.of("a\rb", "c\u0085d", "e f", "g h", "i\nj", "k\r\nl");
        byte[] yaml = generate(schema(table(
                "t",
                null,
                List.of(),
                List.of(),
                names.stream()
                        .map(name -> nullable(name, INT))
                        .toArray(cherry.mastersmith.targetdb.domain.TargetColumn[]::new))));

        DslTable table = valid(READER, yaml).tables().get("t");
        assertThat(table.columns().keySet()).containsExactlyElementsOf(names);
        assertThat(new String(yaml, StandardCharsets.UTF_8))
                .contains("\"a\\rb\"")
                .doesNotContain("\r");
        assertThat(DslYamlWriter.containsLineBreak("plain")).isFalse();
        assertThat(DslYamlWriter.containsLineBreak("x ")).isTrue();
    }
}
