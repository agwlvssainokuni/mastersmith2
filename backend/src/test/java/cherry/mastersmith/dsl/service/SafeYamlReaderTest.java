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
package cherry.mastersmith.dsl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.parse.SafeYamlParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 上限つきの安全な YAML の読み込みの口の単体テスト（U2 の機能設計の BR6.1〜BR6.5、NFR1.5・NFR1.11、NFR 設計の 4.1・4.2・R-05、
 * AC3.1.7・AC3.1.13 の土台）。部品はモックにせず、本物の部品に小さな上限の値を渡して境界を作る。
 */
class SafeYamlReaderTest {

    private static final String MARKER = "LEAK_MARKER_7f3a";

    private static final SafeYamlLimits DSL_LIMITS = new SafeYamlLimits(
            DslFormat.MAX_BYTES, DslFormat.MAX_DEPTH, DslFormat.MAX_COLLECTION_ALIASES, DslFormat.MAX_EXPANDED_NODES);

    private final SafeYamlReader reader = new DefaultSafeYamlReader(new SafeYamlParser());

    private static SafeYamlLimits limits(int maxBytes, int maxDepth, int maxAliases, int maxExpandedNodes) {
        return new SafeYamlLimits(maxBytes, maxDepth, maxAliases, maxExpandedNodes);
    }

    private SafeYamlResult read(String yaml, SafeYamlLimits limits) {
        return reader.read(yaml.getBytes(StandardCharsets.UTF_8), limits);
    }

    private SafeYamlResult.Parsed parsed(String yaml, SafeYamlLimits limits) {
        SafeYamlResult result = read(yaml, limits);
        assertThat(result).isInstanceOf(SafeYamlResult.Parsed.class);
        return (SafeYamlResult.Parsed) result;
    }

    private SafeYamlResult.Rejected rejected(String yaml, SafeYamlLimits limits) {
        SafeYamlResult result = read(yaml, limits);
        assertThat(result).isInstanceOf(SafeYamlResult.Rejected.class);
        return (SafeYamlResult.Rejected) result;
    }

    /** 入れ子の深さが depth の YAML（根の対応表を 1 段目とし、各段が1つの対応表）を作る。 */
    private static String nested(int depth) {
        StringBuilder yaml = new StringBuilder();
        for (int level = 1; level < depth; level++) {
            yaml.append("  ".repeat(level - 1)).append("k").append(level).append(":\n");
        }
        yaml.append("  ".repeat(depth - 1)).append("leaf: 1\n");
        return yaml.toString();
    }

    /** コレクションを指す別名を count 回使う YAML を作る（別名は 3 行目から1行に1つ）。 */
    private static String collectionAliases(int count) {
        StringBuilder yaml = new StringBuilder("base: &b [1]\nuses:\n");
        for (int i = 0; i < count; i++) {
            yaml.append("  - *b\n");
        }
        return yaml.toString();
    }

    /** 別名の展開が爆発する YAML（9 段 × 9 個。約 3.9 億の節に展開する形、NFR 設計の試し T2）。 */
    private static String aliasExplosion() {
        StringBuilder yaml = new StringBuilder("l0: &l0 [a, a, a, a, a, a, a, a, a]\n");
        for (int level = 1; level <= 8; level++) {
            yaml.append("l")
                    .append(level)
                    .append(": &l")
                    .append(level)
                    .append(" [")
                    .append(String.join(", ", Collections.nCopies(9, "*l" + (level - 1))))
                    .append("]\n");
        }
        return yaml.toString();
    }

    @Test
    @DisplayName("a body of exactly the size limit is read and one more byte is rejected as TOO_LARGE without reading")
    void sizeLimitBoundary() {
        String exact = "a: " + "x".repeat(60) + "\n";
        assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(64);

        assertThat(parsed(exact, limits(64, 10, 10, 100)).tree().get("a").stringValue())
                .hasSize(60);

        SafeYamlResult.Rejected over = rejected("a: " + "x".repeat(61) + "\n", limits(64, 10, 10, 100));
        assertThat(over.kind()).isEqualTo(SafeYamlRejectionKind.TOO_LARGE);
        assertThat(over.line()).isNull();
        assertThat(over.column()).isNull();
        assertThat(over.path()).isNull();
    }

    @Test
    @DisplayName(
            "a body larger than the size limit is rejected even when it is not valid UTF-8, because it is not read")
    void sizeIsCheckedBeforeReading() {
        byte[] invalid = new byte[] {'a', ':', ' ', (byte) 0xC3, (byte) 0x28};

        SafeYamlResult result = reader.read(invalid, limits(4, 10, 10, 100));

        assertThat(result).isInstanceOf(SafeYamlResult.Rejected.class);
        assertThat(((SafeYamlResult.Rejected) result).kind()).isEqualTo(SafeYamlRejectionKind.TOO_LARGE);
    }

    @Test
    @DisplayName(
            "nesting depth N is read and N+1 is rejected as TOO_DEEP with the position, not as a positionless SYNTAX")
    void depthLimitBoundary() {
        SafeYamlLimits limits = limits(1024, 3, 10, 100);
        assertThat(parsed(nested(3), limits).tree().at("/k1/k2/leaf").intValue())
                .isEqualTo(1);

        SafeYamlResult.Rejected error = rejected(nested(4), limits);

        assertThat(error.kind()).isEqualTo(SafeYamlRejectionKind.TOO_DEEP);
        assertThat(error.line()).isEqualTo(4);
        assertThat(error.column()).isEqualTo(7);
    }

    @Test
    @DisplayName("N collection aliases are read and N+1 is rejected as TOO_MANY_ALIASES at the alias position")
    void aliasLimitBoundary() {
        SafeYamlLimits limits = limits(1024, 10, 1, 100);
        assertThat(parsed(collectionAliases(1), limits).tree().get("uses").size())
                .isEqualTo(1);

        SafeYamlResult.Rejected error = rejected(collectionAliases(2), limits);

        assertThat(error.kind()).isEqualTo(SafeYamlRejectionKind.TOO_MANY_ALIASES);
        assertThat(error.line()).isEqualTo(4);
        assertThat(error.column()).isEqualTo(5);
    }

    @Test
    @DisplayName("with an alias limit of 0 one alias to a mapping is rejected and an alias to a plain value is read")
    void zeroAliasLimit() {
        SafeYamlLimits limits = limits(1024, 10, 0, 100);

        SafeYamlResult.Rejected error = rejected("a: &m {x: 1}\nb: *m\n", limits);
        assertThat(error.kind()).isEqualTo(SafeYamlRejectionKind.TOO_MANY_ALIASES);
        assertThat(error.line()).isEqualTo(2);
        assertThat(error.column()).isEqualTo(4);

        assertThat(parsed("a: &s v\nb: *s\n", limits).tree().get("b").stringValue())
                .isEqualTo("v");
    }

    @Test
    @DisplayName("exactly the expanded node limit is read and one more node is rejected as TOO_MANY_ALIASES")
    void expandedNodeLimitBoundary() {
        // 根の対応表・キー a・並び・要素2つで 5 つの節。
        SafeYamlLimits limits = limits(1024, 10, 10, 5);
        assertThat(parsed("a: [1, 2]\n", limits).tree().get("a").size()).isEqualTo(2);

        SafeYamlResult.Rejected error = rejected("a: [1, 2, 3]\n", limits);

        assertThat(error.kind()).isEqualTo(SafeYamlRejectionKind.TOO_MANY_ALIASES);
        assertThat(error.line()).isEqualTo(1);
    }

    @Test
    @DisplayName("an exploding alias expansion is rejected within 5 seconds with the DSL limits")
    void aliasExplosionWithDslLimits() {
        long start = System.nanoTime();
        SafeYamlResult.Rejected error = rejected(aliasExplosion(), DSL_LIMITS);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(error.kind()).isEqualTo(SafeYamlRejectionKind.TOO_MANY_ALIASES);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("an exploding alias expansion is rejected within 5 seconds with small limits")
    void aliasExplosionWithSmallLimits() {
        long start = System.nanoTime();
        SafeYamlResult.Rejected error = rejected(aliasExplosion(), limits(1024, 10, 100, 1_000));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(error.kind()).isEqualTo(SafeYamlRejectionKind.TOO_MANY_ALIASES);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("an alias that contains itself is rejected as TOO_MANY_ALIASES")
    void recursiveAlias() {
        assertThat(rejected("a: &a\n  - *a\n", limits(1024, 10, 10, 100)).kind())
                .isEqualTo(SafeYamlRejectionKind.TOO_MANY_ALIASES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"x: !!str value\n", "x: !custom value\n", "x: !!java.net.URL [\"http://127.0.0.1/\"]\n"})
    @DisplayName("a tag is rejected as TAG_NOT_ALLOWED at its position")
    void tagIsRejected(String yaml) {
        SafeYamlResult.Rejected error = rejected(yaml, limits(1024, 10, 10, 100));

        assertThat(error.kind()).isEqualTo(SafeYamlRejectionKind.TAG_NOT_ALLOWED);
        assertThat(error.line()).isEqualTo(1);
        assertThat(error.column()).isEqualTo(4);
    }

    @Test
    @DisplayName("a duplicate key is rejected as DUPLICATE_KEY at the second key with its path")
    void duplicateKeyIsRejected() {
        SafeYamlResult.Rejected error = rejected("roles:\n  admin: 1\n  admin: 2\n", limits(1024, 10, 10, 100));

        assertThat(error.kind()).isEqualTo(SafeYamlRejectionKind.DUPLICATE_KEY);
        assertThat(error.line()).isEqualTo(3);
        assertThat(error.column()).isEqualTo(3);
        assertThat(error.path()).isEqualTo("roles.admin");
    }

    @Test
    @DisplayName("text that is not valid YAML or not UTF-8 is rejected as SYNTAX")
    void syntaxErrors() {
        SafeYamlResult.Rejected malformed = rejected("a: [1, 2\n", limits(1024, 10, 10, 100));
        assertThat(malformed.kind()).isEqualTo(SafeYamlRejectionKind.SYNTAX);
        assertThat(malformed.line()).isNotNull();

        SafeYamlResult encoding =
                reader.read(new byte[] {'a', ':', ' ', (byte) 0xC3, (byte) 0x28}, limits(1024, 10, 10, 100));
        assertThat(encoding).isInstanceOf(SafeYamlResult.Rejected.class);
        assertThat(((SafeYamlResult.Rejected) encoding).kind()).isEqualTo(SafeYamlRejectionKind.SYNTAX);
    }

    @Test
    @DisplayName("a rejection carries no text of the component exceptions")
    void rejectionHasNoComponentText() {
        for (String yaml : new String[] {"a: [1, 2\n", "a: 'open\n", "a: 1\n---\nb: 2\n", "a: *missing\n"}) {
            SafeYamlResult.Rejected error = rejected(yaml, limits(1024, 10, 10, 100));

            assertThat(error.kind()).isEqualTo(SafeYamlRejectionKind.SYNTAX);
            assertThat(error.toString()).doesNotContain("Exception", "snakeyaml", "while ", "expected");
            assertThat(error.path()).isNull();
        }
    }

    @Test
    @DisplayName("limits out of range are a programming error of the caller")
    void limitsOutOfRange() {
        assertThatThrownBy(() -> limits(0, 1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits(1, 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits(1, 1, -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits(1, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(limits(1, 1, 0, 1).maxAliases()).isZero();
    }

    @Test
    @DisplayName("the positions are looked up by JSON Pointer without exposing the parser types")
    void positionsLookup() {
        SafeYamlResult.Parsed result = parsed("roles:\n  - name: admin\n", limits(1024, 10, 10, 100));

        assertThat(result.positions().find("/roles/0/name")).contains(new YamlPositions.Location(2, 5));
        assertThat(result.positions().find("/no/such")).isEmpty();
        assertThat(result.positions().size()).isPositive();
    }

    @Test
    @DisplayName("the string form of results shows only the kind, the position and the node count, not the content")
    void toStringHasNoContent() {
        SafeYamlLimits limits = limits(1024, 10, 10, 100);
        SafeYamlResult.Parsed result = parsed(MARKER + ":\n  - " + MARKER + "\n", limits);
        SafeYamlResult.Rejected error = rejected(MARKER + ": 1\n" + MARKER + ": 2\n", limits);

        assertThat(result.toString()).isEqualTo("Parsed[nodes=3]");
        assertThat(result.positions().toString()).doesNotContain(MARKER);
        assertThat(error.path()).isEqualTo(MARKER);
        assertThat(error.toString()).doesNotContain(MARKER).contains("DUPLICATE_KEY", "line=2", "column=1");
    }
}
