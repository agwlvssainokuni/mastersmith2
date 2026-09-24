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

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * SnakeYAML の節の木から、検証用の JSON の形（Jackson 3 の {@link JsonNode}）と位置の対応表を作る（ADR-008、BR1.3・BR1.5・
 * BR4.1・BR4.2、NFR2.4・NFR3.2）。
 *
 * <ul>
 *   <li>別名は展開する。展開した後の節の数が {@link DslFormat#MAX_EXPANDED_NODES} を超えたら止める（別名の展開の爆発）。
 *       自分自身を含む別名も、展開が終わらないため止める。
 *   <li>1つの対応表の中で同じキーが重なれば、2回目のキーの位置と場所で止める（後の値で上書きしない）。
 *   <li>位置の対応表は、対応表の項目ではキーの位置、並びの要素ではその要素の位置を記録する。別名で参照した値の中は、参照を
 *       書いたキーの位置（並びの要素なら別名を書いた位置）を記録する。
 *   <li>スカラーは YAML の暗黙の型（文字列・整数・小数・真偽値・null）で JSON の値にする。数にできない書き方（60 進数・
 *       無限大など）と日付は文字列のまま。
 * </ul>
 */
final class YamlTreeConverter {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private static final Set<String> TRUE_WORDS = Set.of("true", "yes", "on");

    /**
     * 節の木を変換する。
     *
     * @param root 文書の根（空の文書は null）
     * @param aliasPositions 別名を書いた位置（文書の順）
     * @return 検証用の JSON の形と位置の対応表
     * @throws YamlRejection 重複キー・展開後の節の数の上限・自分自身を含む別名・対応表のキーが単独の値でないとき
     */
    YamlDocument convert(Node root, List<YamlPosition> aliasPositions) {
        PositionMap positions = new PositionMap();
        if (root == null) {
            return new YamlDocument(FACTORY.nullNode(), positions);
        }
        Walk walk = new Walk(positions, aliasPositions);
        positions.put(JsonPointers.ROOT, YamlPosition.of(root.getStartMark()));
        return new YamlDocument(walk.node(root, JsonPointers.ROOT, null), positions);
    }

    /** 1回の変換の状態。 */
    private static final class Walk {

        private final PositionMap positions;

        private final List<YamlPosition> aliasPositions;

        private final Set<Node> definedAnchors = Collections.newSetFromMap(new IdentityHashMap<>());

        private final Set<Node> onPath = Collections.newSetFromMap(new IdentityHashMap<>());

        private int nextAlias;

        private int nodes;

        Walk(PositionMap positions, List<YamlPosition> aliasPositions) {
            this.positions = positions;
            this.aliasPositions = aliasPositions;
        }

        /**
         * 節を変換する。
         *
         * @param node 節
         * @param pointer 節の場所
         * @param aliasAt 別名で参照した値の中なら、参照を書いた場所の位置（そうでなければ null）
         * @return JSON の値
         */
        JsonNode node(Node node, String pointer, YamlPosition aliasAt) {
            count(node, aliasAt);
            if (!onPath.add(node)) {
                throw YamlRejection.of(
                        DslErrorKind.ALIAS_LIMIT, positionOf(node, aliasAt), null, DslMessageKeys.RECURSIVE_ALIAS);
            }
            try {
                return switch (node) {
                    case MappingNode mapping -> mapping(mapping, pointer, aliasAt);
                    case SequenceNode sequence -> sequence(sequence, pointer, aliasAt);
                    case ScalarNode scalar -> scalar(scalar);
                    default -> throw new IllegalStateException("知らない節の種類です: " + node.getNodeId());
                };
            } finally {
                onPath.remove(node);
            }
        }

        private ObjectNode mapping(MappingNode mapping, String pointer, YamlPosition aliasAt) {
            ObjectNode object = FACTORY.objectNode();
            Set<String> keys = new HashSet<>();
            for (NodeTuple tuple : mapping.getValue()) {
                Node keyNode = tuple.getKeyNode();
                YamlPosition keyAlias = aliasOccurrence(keyNode, aliasAt);
                YamlPosition keyPosition = aliasAt != null ? aliasAt : positionOf(keyNode, keyAlias);
                if (!(keyNode instanceof ScalarNode scalarKey)) {
                    throw YamlRejection.of(
                            DslErrorKind.SYNTAX, keyPosition, pointer, DslMessageKeys.YAML_KEY_NOT_SCALAR);
                }
                count(keyNode, aliasAt);
                String key = scalarKey.getValue();
                String childPointer = JsonPointers.child(pointer, key);
                if (!keys.add(key)) {
                    throw YamlRejection.of(
                            DslErrorKind.DUPLICATE_KEY,
                            keyPosition,
                            childPointer,
                            DslMessageKeys.DUPLICATE_KEY,
                            DslError.excerpt(key));
                }
                positions.put(childPointer, keyPosition);
                Node valueNode = tuple.getValueNode();
                YamlPosition valueAlias = aliasOccurrence(valueNode, aliasAt);
                // 別名で参照した値の中は、参照を書いたキーの位置で示す（BR4.2）。
                YamlPosition childAliasAt = aliasAt != null ? aliasAt : valueAlias == null ? null : keyPosition;
                object.set(key, node(valueNode, childPointer, childAliasAt));
            }
            return object;
        }

        private ArrayNode sequence(SequenceNode sequence, String pointer, YamlPosition aliasAt) {
            ArrayNode array = FACTORY.arrayNode();
            List<Node> items = sequence.getValue();
            for (int i = 0; i < items.size(); i++) {
                Node item = items.get(i);
                String childPointer = JsonPointers.child(pointer, i);
                YamlPosition itemAlias = aliasOccurrence(item, aliasAt);
                YamlPosition itemPosition = aliasAt != null ? aliasAt : positionOf(item, itemAlias);
                positions.put(childPointer, itemPosition);
                YamlPosition childAliasAt = aliasAt != null ? aliasAt : itemAlias == null ? null : itemPosition;
                array.add(node(item, childPointer, childAliasAt));
            }
            return array;
        }

        /**
         * 節が別名の参照として現れたかを判定する。アンカーを持つ節の2回目以降の出現が別名の参照で、文書の順に記録した
         * 別名の位置を1つ進める。別名で参照した値の中（{@code aliasAt} がある）は、文書に書かれた別名ではないため数えない。
         *
         * @return 別名の参照なら、別名を書いた位置（記録が足りなければ節の位置）。そうでなければ null
         */
        private YamlPosition aliasOccurrence(Node node, YamlPosition aliasAt) {
            if (aliasAt != null || node.getAnchor() == null || definedAnchors.add(node)) {
                return null;
            }
            YamlPosition written = nextAlias < aliasPositions.size() ? aliasPositions.get(nextAlias) : null;
            nextAlias++;
            return written != null ? written : YamlPosition.of(node.getStartMark());
        }

        private void count(Node node, YamlPosition aliasAt) {
            if (++nodes > DslFormat.MAX_EXPANDED_NODES) {
                throw YamlRejection.of(
                        DslErrorKind.ALIAS_LIMIT,
                        positionOf(node, aliasAt),
                        null,
                        DslMessageKeys.EXPANDED_NODES_LIMIT,
                        String.valueOf(DslFormat.MAX_EXPANDED_NODES));
            }
        }

        private static YamlPosition positionOf(Node node, YamlPosition alias) {
            return alias != null ? alias : YamlPosition.of(node.getStartMark());
        }
    }

    /**
     * スカラーを、YAML が暗黙に決めた型の JSON の値にする。
     *
     * @param scalar スカラー
     * @return JSON の値
     */
    static JsonNode scalar(ScalarNode scalar) {
        String value = scalar.getValue();
        Tag tag = scalar.getTag();
        if (Tag.NULL.equals(tag)) {
            return FACTORY.nullNode();
        }
        if (Tag.BOOL.equals(tag)) {
            return FACTORY.booleanNode(TRUE_WORDS.contains(value.toLowerCase(Locale.ROOT)));
        }
        if (Tag.INT.equals(tag)) {
            BigInteger integer = parseInteger(value);
            return integer == null ? FACTORY.stringNode(value) : FACTORY.numberNode(integer);
        }
        if (Tag.FLOAT.equals(tag)) {
            BigDecimal decimal = parseDecimal(value);
            return decimal == null ? FACTORY.stringNode(value) : FACTORY.numberNode(decimal);
        }
        return FACTORY.stringNode(value);
    }

    /**
     * YAML 1.1 の整数の書き方（符号、{@code _} の区切り、{@code 0b}・{@code 0x}・先頭の {@code 0} の 8 進数）を読む。
     *
     * @param value 書かれた値
     * @return 整数（60 進数など数にしない書き方は null）
     */
    static BigInteger parseInteger(String value) {
        String text = value.replace("_", "");
        if (text.contains(":")) {
            return null;
        }
        boolean negative = text.startsWith("-");
        if (negative || text.startsWith("+")) {
            text = text.substring(1);
        }
        try {
            BigInteger number;
            if (text.startsWith("0b")) {
                number = new BigInteger(text.substring(2), 2);
            } else if (text.startsWith("0x")) {
                number = new BigInteger(text.substring(2), 16);
            } else if (text.length() > 1 && text.startsWith("0")) {
                number = new BigInteger(text.substring(1), 8);
            } else {
                number = new BigInteger(text);
            }
            return negative ? number.negate() : number;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * YAML 1.1 の小数の書き方を読む。
     *
     * @param value 書かれた値
     * @return 小数（無限大・非数・60 進数など数にしない書き方は null）
     */
    static BigDecimal parseDecimal(String value) {
        String text = value.replace("_", "");
        if (text.contains(":")
                || text.toLowerCase(Locale.ROOT).contains("inf")
                || text.toLowerCase(Locale.ROOT).contains("nan")) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
