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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * 上限つきの安全な YAML の読み込みの口（{@link SafeYamlReader}）の結果（U2 の機能設計の BR6.2〜BR6.5、entities.md の
 * SafeYamlResult）。読めた（{@link Parsed}）か、拒否した（{@link Rejected}）か。
 *
 * <p>DSL の誤りの型（{@code DslError}）に依らない。文字列にしたときは、区分・位置・節の数だけを出し、木の中身と利用者が書いた
 * キーを出さない（{@code TraceAspect} が戻り値を TRACE のログに文字列で出すため。BR6.5）。
 */
public sealed interface SafeYamlResult {

    /**
     * 読めた。DSL の JSON Schema の検証はしていない（BR6.4）。
     *
     * @param tree JSON の木（空の文書は null の節）
     * @param positions 位置を引く口
     */
    record Parsed(JsonNode tree, YamlPositions positions) implements SafeYamlResult {

        /** 両方が必須であることを確かめる。 */
        public Parsed {
            Objects.requireNonNull(tree, "tree は必須です");
            Objects.requireNonNull(positions, "positions は必須です");
        }

        /** JSON の木の節の数（対応表・並び・値。キーは数えない）だけを出す（中身は出さない）。 */
        @Override
        public String toString() {
            return "Parsed[nodes=" + countNodes(tree) + "]";
        }

        private static int countNodes(JsonNode root) {
            int count = 0;
            Deque<JsonNode> pending = new ArrayDeque<>();
            pending.push(root);
            while (!pending.isEmpty()) {
                JsonNode node = pending.pop();
                count++;
                for (JsonNode child : node) {
                    pending.push(child);
                }
            }
            return count;
        }
    }

    /**
     * 拒否した。部品の例外の文は持たない（BR6.3）。
     *
     * @param kind 区分
     * @param line 行（1 から。位置が得られないときは null）
     * @param column 列（1 から。位置が得られないときは null）
     * @param path 場所（点でつないだ形。例 {@code roles.admin}。無ければ null）
     */
    record Rejected(SafeYamlRejectionKind kind, Integer line, Integer column, String path) implements SafeYamlResult {

        /** 区分が必須で、行・列の組み合わせが正しいことを確かめる。 */
        public Rejected {
            Objects.requireNonNull(kind, "kind は必須です");
            if ((line == null) != (column == null)) {
                throw new IllegalArgumentException("line と column は両方あるか両方無いかのどちらかです");
            }
            if (line != null && (line < 1 || column < 1)) {
                throw new IllegalArgumentException("line と column は 1 以上です");
            }
            if (path != null && path.isEmpty()) {
                throw new IllegalArgumentException("path は空にしません（無いときは null）");
            }
        }

        /** 区分と位置（行・列）だけを出す（場所は利用者の書いたキーを含みうるため出さない）。 */
        @Override
        public String toString() {
            return "Rejected[kind=" + kind + ", line=" + line + ", column=" + column + "]";
        }
    }
}
