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

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

/** YAML の安全な読み込みの単体テスト（BR1.2〜BR1.5、NFR2.2〜NFR2.4・NFR3.1・NFR3.2・NFR5.1、AC2.3.2〜AC2.3.5）。 */
class SafeYamlParserTest {

    /** タグで型が作られたら数える、テスト用のクラス（作られてはならない）。 */
    public static final class Recorder {

        static final AtomicInteger CREATED = new AtomicInteger();

        /** 作られたことを記録する。 */
        public Recorder() {
            CREATED.incrementAndGet();
        }
    }

    private final SafeYamlParser parser = new SafeYamlParser();

    static YamlParseResult parse(String yaml) {
        return new SafeYamlParser().parse(yaml.getBytes(StandardCharsets.UTF_8));
    }

    static YamlDocument parsed(String yaml) {
        YamlParseResult result = parse(yaml);
        assertThat(result).isInstanceOf(YamlParseResult.Parsed.class);
        return ((YamlParseResult.Parsed) result).document();
    }

    static DslError rejected(String yaml) {
        YamlParseResult result = parse(yaml);
        assertThat(result).isInstanceOf(YamlParseResult.Rejected.class);
        return ((YamlParseResult.Rejected) result).error();
    }

    /** 入れ子の深さが depth の YAML（根の対応表を 1 段目とし、各段が1つの対応表）を作る。 */
    static String nested(int depth) {
        StringBuilder yaml = new StringBuilder();
        for (int level = 1; level < depth; level++) {
            yaml.append("  ".repeat(level - 1)).append("k").append(level).append(":\n");
        }
        yaml.append("  ".repeat(depth - 1)).append("leaf: 1\n");
        return yaml.toString();
    }

    /** コレクションを指す別名を count 回使う YAML を作る。 */
    static String aliases(int count) {
        StringBuilder yaml = new StringBuilder("base: &b [1]\nuses:\n");
        for (int i = 0; i < count; i++) {
            yaml.append("  - *b\n");
        }
        return yaml.toString();
    }

    @Test
    @DisplayName("nesting depth 50 is accepted and 51 is rejected with the position of the level that starts too deep")
    void depthLimitBoundary() {
        assertThat(parsed(nested(50)).json().isObject()).isTrue();

        DslError error = rejected(nested(51));

        assertThat(error.kind()).isEqualTo(DslErrorKind.DEPTH_LIMIT);
        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.DEPTH_LIMIT);
        assertThat(error.messageArgs()).containsExactly("50");
        assertThat(error.line()).isEqualTo(51);
        assertThat(error.column()).isEqualTo(101);
        assertThat(error.path()).isNull();
    }

    @Test
    @DisplayName("100 collection aliases are accepted and the 101st is rejected at its position")
    void aliasLimitBoundary() {
        assertThat(parsed(aliases(100)).json().get("uses").size()).isEqualTo(100);

        DslError error = rejected(aliases(101));

        assertThat(error.kind()).isEqualTo(DslErrorKind.ALIAS_LIMIT);
        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.ALIAS_LIMIT);
        assertThat(error.line()).isEqualTo(103);
        assertThat(error.column()).isEqualTo(5);
    }

    @Test
    @DisplayName("an exploding alias expansion stops at the expanded node limit well within one second")
    void aliasExplosionStopsQuickly() {
        StringBuilder yaml = new StringBuilder("l0: &l0 [a, a, a, a, a, a, a, a, a, a]\n");
        for (int level = 1; level <= 8; level++) {
            String previous = "*l" + (level - 1);
            yaml.append("l")
                    .append(level)
                    .append(": &l")
                    .append(level)
                    .append(" [")
                    .append(String.join(", ", java.util.Collections.nCopies(10, previous)))
                    .append("]\n");
        }

        long start = System.nanoTime();
        DslError error = rejected(yaml.toString());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(error.kind()).isEqualTo(DslErrorKind.ALIAS_LIMIT);
        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.EXPANDED_NODES_LIMIT);
        assertThat(error.messageArgs()).containsExactly("1000000");
        assertThat(error.line()).isNotNull();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("an alias that contains itself is rejected instead of expanding forever")
    void recursiveAliasIsRejected() {
        DslError error = rejected("a: &a\n  - *a\n");

        assertThat(error.kind()).isEqualTo(DslErrorKind.ALIAS_LIMIT);
        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.RECURSIVE_ALIAS);
        assertThat(error.line()).isEqualTo(2);
        assertThat(error.column()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "x: !!cherry.mastersmith.dsl.parse.SafeYamlParserTest$Recorder {}\n",
                "x: !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL [\"http://127.0.0.1/\"]]]]\n",
                "x: !custom value\n",
                "x: !!str value\n",
                "x: ! value\n",
                "x: !<tag:example.com,2026:thing> value\n"
            })
    @DisplayName("every tag is rejected at its position and no object is created")
    void tagsAreRejected(String yaml) {
        Recorder.CREATED.set(0);

        DslError error = rejected(yaml);

        assertThat(error.kind()).isEqualTo(DslErrorKind.FORBIDDEN_TAG);
        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.FORBIDDEN_TAG);
        assertThat(error.line()).isEqualTo(1);
        assertThat(error.column()).isEqualTo(4);
        assertThat(Recorder.CREATED).hasValue(0);
    }

    @Test
    @DisplayName("a duplicate key is rejected at the second key with its path and the first value is not overwritten")
    void duplicateKeyIsRejected() {
        DslError error = rejected("version: 1\ntables:\n  dept:\n    view: false\n  dept:\n    view: true\n");

        assertThat(error.kind()).isEqualTo(DslErrorKind.DUPLICATE_KEY);
        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.DUPLICATE_KEY);
        assertThat(error.messageArgs()).containsExactly("dept");
        assertThat(error.path()).isEqualTo("tables.dept");
        assertThat(error.line()).isEqualTo(5);
        assertThat(error.column()).isEqualTo(3);
    }

    @Test
    @DisplayName("the same column defined twice in a table is a duplicate key of the columns map")
    void duplicateColumnIsRejected() {
        DslError error = rejected("tables:\n  dept:\n    columns:\n      code: {}\n      name: {}\n      code: {}\n");

        assertThat(error.kind()).isEqualTo(DslErrorKind.DUPLICATE_KEY);
        assertThat(error.path()).isEqualTo("tables.dept.columns.code");
        assertThat(error.line()).isEqualTo(6);
        assertThat(error.column()).isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a: [1, 2\n", "a:\n  b: 1\n c: 2\n", "a: 'open\n", "a: 1\n---\nb: 2\n", "a: *missing\n"})
    @DisplayName(
            "text that is not valid YAML is a syntax error at the position where reading failed, without component text")
    void malformedYamlIsSyntaxError(String yaml) {
        DslError error = rejected(yaml);

        assertThat(error.kind()).isEqualTo(DslErrorKind.SYNTAX);
        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.YAML_MALFORMED);
        assertThat(error.messageArgs()).isEmpty();
        assertThat(error.line()).isNotNull();
        assertThat(error.toString()).doesNotContain("Exception", "snakeyaml", "while ");
    }

    @Test
    @DisplayName("bytes that are not UTF-8 are a syntax error without a position")
    void invalidEncodingIsSyntaxError() {
        YamlParseResult result = parser.parse(new byte[] {'a', ':', ' ', (byte) 0xC3, (byte) 0x28});

        DslError error = ((YamlParseResult.Rejected) result).error();
        assertThat(error.kind()).isEqualTo(DslErrorKind.SYNTAX);
        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.YAML_ENCODING);
        assertThat(error.line()).isNull();
    }

    @Test
    @DisplayName("a mapping key that is a collection is a syntax error")
    void collectionKeyIsSyntaxError() {
        DslError error = rejected("? [a, b]\n: 1\n");

        assertThat(error.kind()).isEqualTo(DslErrorKind.SYNTAX);
        assertThat(error.messageKey()).isEqualTo(DslMessageKeys.YAML_KEY_NOT_SCALAR);
    }

    @Test
    @DisplayName("an empty document and a byte order mark are read")
    void emptyDocumentAndByteOrderMark() {
        assertThat(parsed("").json().isNull()).isTrue();
        assertThat(parsed("# only a comment\n").json().isNull()).isTrue();
        JsonNode json = parser.parse("\uFEFFversion: 1\n".getBytes(StandardCharsets.UTF_8))
                        instanceof YamlParseResult.Parsed parsed
                ? parsed.document().json()
                : null;
        assertThat(json).isNotNull();
        assertThat(json.get("version").intValue()).isEqualTo(1);
    }
}
