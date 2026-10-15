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
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.composer.Composer;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * YAML の安全な読み込み（BR1.2〜BR1.5、NFR2.2〜NFR2.4・NFR3.1・NFR3.2・NFR3.5・NFR5.1）。
 *
 * <p>SnakeYAML の節の木を作る部品（{@code Composer}）だけを使い、Java の型を作る仕組み（{@code Constructor}・{@code Yaml.load}）は
 * 使わない。深さ・別名・タグは {@link LimitingParser} で、重複キーと別名の展開後の節の数は {@link YamlTreeConverter} で確かめる。
 * {@code LoaderOptions} にも同じ上限を置き、二重に守る。
 *
 * <p>上限は呼ぶ側が {@link YamlLimits} で渡す（DSL の読み込みは {@code DslFormat} の値、上限つきの安全な読み込みの口は呼ぶ側の
 * 値。U2 の機能設計の BR6.6）。深さと別名の上限は {@code LoaderOptions} と {@link LimitingParser} に同じ値を渡す（NFR 設計の
 * 試し T1'）。
 *
 * <p>部品の例外は種類ごとに誤りの種類へ写し、部品の文言は使わない。大きさの上限（BR1.1）は呼び出し側が読む前に確かめる。
 *
 * <p>1行の長さの上限（Intent 261015-deps-ui-carryover の FR9.2）は、UTF-8 を解読した後、SnakeYAML に渡す前に確かめる。改行
 * （{@code \n}・{@code \r}。{@code \r\n} は1つの改行）で区切った1行のコードポイントの数（改行は数えない）が上限を超えた最初の行で、
 * 大きさの種類（{@link DslErrorKind#SIZE_LIMIT}）の誤りにする。位置はその行と上限＋1 の列、埋める値は上限の値だけで、本文は誤りに
 * 入れない。1行が長い文書は、読む前に止めないと部品の中で大きな文字列を何度も作るため。
 */
@Component
public class SafeYamlParser {

    private static final char BYTE_ORDER_MARK = '﻿';

    private final YamlTreeConverter converter = new YamlTreeConverter();

    /**
     * 本文を読み、検証用の JSON の形と位置の対応表を作る。
     *
     * @param yamlBytes UTF-8 の YAML の本文（大きさの上限は確かめ済みであること）
     * @param limits 読み込みの上限
     * @return 読めた、または読み込みの段で止めた
     */
    public YamlParseResult parse(byte[] yamlBytes, YamlLimits limits) {
        try {
            String text = decode(yamlBytes);
            checkLineLength(text, limits.maxLineLength());
            LoaderOptions options = loaderOptions(limits);
            LimitingParser parser = new LimitingParser(new ParserImpl(new StreamReader(text), options), limits);
            Node root = new Composer(parser, new Resolver(), options).getSingleNode();
            return new YamlParseResult.Parsed(
                    converter.convert(root, parser.aliasPositions(), limits.maxExpandedNodes()));
        } catch (YamlRejection e) {
            return new YamlParseResult.Rejected(e.error());
        } catch (MarkedYAMLException e) {
            Mark mark = e.getProblemMark() != null ? e.getProblemMark() : e.getContextMark();
            return malformed(YamlPosition.of(mark));
        } catch (YAMLException e) {
            return malformed(null);
        }
    }

    /**
     * 安全な読み込みの設定を作る。深さ・別名・大きさの上限は {@link LimitingParser} と読む前の判定に渡す値と同じにする。
     *
     * @param limits 読み込みの上限
     * @return 読み込みの設定
     */
    static LoaderOptions loaderOptions(YamlLimits limits) {
        LoaderOptions options = new LoaderOptions();
        options.setNestingDepthLimit(limits.maxDepth());
        options.setMaxAliasesForCollections(limits.maxCollectionAliases());
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMergeOnCompose(false);
        options.setProcessComments(false);
        options.setCodePointLimit(limits.maxBytes());
        options.setTagInspector(tag -> false);
        return options;
    }

    private static String decode(byte[] yamlBytes) {
        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(yamlBytes))
                    .toString();
            return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw YamlRejection.of(DslErrorKind.SYNTAX, null, null, DslMessageKeys.YAML_ENCODING);
        }
    }

    /**
     * 1行の長さ（コードポイント。改行は数えない）が上限の内であることを確かめる。
     *
     * @param text 解読した本文（先頭のバイト順の印は除いたもの）
     * @param maxLineLength 1行の長さの上限
     * @throws YamlRejection 上限を超えた最初の行で
     */
    static void checkLineLength(String text, int maxLineLength) {
        int line = 1;
        int length = 0;
        int index = 0;
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (ch == '\n' || ch == '\r') {
                index += ch == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n' ? 2 : 1;
                line++;
                length = 0;
                continue;
            }
            index += Character.isHighSurrogate(ch)
                            && index + 1 < text.length()
                            && Character.isLowSurrogate(text.charAt(index + 1))
                    ? 2
                    : 1;
            length++;
            if (length > maxLineLength) {
                throw YamlRejection.of(
                        DslErrorKind.SIZE_LIMIT,
                        new YamlPosition(line, maxLineLength + 1),
                        null,
                        DslMessageKeys.LINE_LIMIT,
                        String.valueOf(maxLineLength));
            }
        }
    }

    private static YamlParseResult malformed(YamlPosition position) {
        return new YamlParseResult.Rejected(new DslError(
                DslErrorKind.SYNTAX,
                position == null ? null : position.line(),
                position == null ? null : position.column(),
                null,
                DslMessageKeys.YAML_MALFORMED,
                List.of()));
    }
}
