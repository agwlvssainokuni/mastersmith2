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
package cherry.mastersmith.dsl.parse;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import cherry.mastersmith.dsl.testsupport.DslSamples;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Chars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;

/**
 * YAML の読み込みの性質ベースのテスト（NFR5.1）。任意の入力でも、想定外の例外が漏れず、読めた結果か誤り1件が返る。
 * 失敗したときの乱数の種は jqwik の報告に出る（{@code @Property(seed = "...")} に与えて再現する）。
 */
class SafeYamlParserPropertyTest {

    private final SafeYamlParser parser = new SafeYamlParser();

    @Property(tries = 300)
    @Label("arbitrary bytes never leak an unexpected exception")
    void arbitraryBytes(@ForAll @Size(max = 300) byte[] bytes) {
        assertThat(parser.parse(bytes, DslSamples.DSL_YAML_LIMITS)).isNotNull();
    }

    @Property(tries = 500)
    @Label("arbitrary YAML-like text never leaks an unexpected exception and errors carry no component text")
    void arbitraryYamlLikeText(
            @ForAll
                    @StringLength(max = 200)
                    @Chars({
                        'a', 'b', ':', ' ', '\n', '-', '[', ']', '{', '}', ',', '&', '*', '!', '?', '|', '>', '"', '\'',
                        '#', '%', '@', '\t', '1', '.'
                    })
                    String text) {
        YamlParseResult result = parser.parse(text.getBytes(StandardCharsets.UTF_8), DslSamples.DSL_YAML_LIMITS);

        assertThat(result).isNotNull();
        if (result instanceof YamlParseResult.Rejected rejected) {
            assertThat(rejected.error().messageKey()).startsWith("dsl.");
            assertThat(String.join(" ", rejected.error().messageArgs())).doesNotContain("Exception");
        }
    }

    private static final int SMALL_LINE_LIMIT = 20;

    private static YamlLimits smallLineLimits() {
        return new YamlLimits(
                DslFormat.MAX_BYTES,
                DslFormat.MAX_DEPTH,
                DslFormat.MAX_COLLECTION_ALIASES,
                DslFormat.MAX_EXPANDED_NODES,
                SMALL_LINE_LIMIT);
    }

    private static String join(List<String> lines, int separator) {
        String lineBreak =
                switch (Math.floorMod(separator, 3)) {
                    case 0 -> "\n";
                    case 1 -> "\r\n";
                    default -> "\r";
                };
        return String.join(lineBreak, lines) + lineBreak;
    }

    @Property(tries = 300)
    @Label("a document whose lines are all within the limit is never rejected for the line length")
    void linesWithinTheLimitAreNotRejectedForLength(
            @ForAll @Size(max = 20)
                    List<
                                    @StringLength(max = SMALL_LINE_LIMIT)
                                    @Chars({'a', 'b', ':', ' ', '-', '#', '[', ']', '"', 'あ', '1', '\t'}) String>
                            lines,
            @ForAll @IntRange(min = 0, max = 2) int separator) {
        YamlParseResult result =
                parser.parse(join(lines, separator).getBytes(StandardCharsets.UTF_8), smallLineLimits());

        if (result instanceof YamlParseResult.Rejected rejected) {
            assertThat(rejected.error().messageKey()).isNotEqualTo(DslMessageKeys.LINE_LIMIT);
        }
    }

    @Property(tries = 200)
    @Label("a document with one line over the limit is always rejected for the line length at that line")
    void oneLineOverTheLimitIsAlwaysRejected(
            @ForAll @Size(max = 10)
                    List<@StringLength(max = SMALL_LINE_LIMIT) @Chars({'a', ' ', ':', 'あ'}) String> before,
            @ForAll @IntRange(min = 1, max = 50) int over,
            @ForAll @IntRange(min = 0, max = 2) int separator) {
        List<String> lines = new java.util.ArrayList<>(before);
        lines.add("x".repeat(SMALL_LINE_LIMIT + over));
        YamlParseResult result =
                parser.parse(join(lines, separator).getBytes(StandardCharsets.UTF_8), smallLineLimits());

        assertThat(result).isInstanceOf(YamlParseResult.Rejected.class);
        YamlParseResult.Rejected rejected = (YamlParseResult.Rejected) result;
        assertThat(rejected.error().messageKey()).isEqualTo(DslMessageKeys.LINE_LIMIT);
        assertThat(rejected.error().line()).isEqualTo(before.size() + 1);
        assertThat(rejected.error().messageArgs()).containsExactly(String.valueOf(SMALL_LINE_LIMIT));
    }
}
