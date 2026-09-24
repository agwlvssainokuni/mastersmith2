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
package cherry.mastersmith.dsl.validate;

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import cherry.mastersmith.dsl.parse.JsonPointers;
import cherry.mastersmith.dsl.parse.PositionMap;
import cherry.mastersmith.dsl.parse.YamlDocument;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 同梱の JSON Schema による構文の検証（BR1.6・BR2.2、NFR3.3・NFR3.4・NFR5.1・NFR5.2）。
 *
 * <p>JSON Schema は 2020-12 で、同梱の正本（{@link DslFormat#SCHEMA_RESOURCE}）だけを起動時に1回読み、組み立てた検証器を
 * 使い回す。外部の参照を取りに行かない設定（{@code fetchRemoteResources(false)}）を明示する。外部を指す {@code $ref} を
 * 含むスキーマは、取りに行かずに組み立ての時点で失敗する（AC2.3.7）。
 *
 * <p>誤りは見つかったものをすべて返す。誤りは JSON Schema のキーワード・場所・埋める値から文言の鍵にし、部品の文言は使わない。
 * 埋める値は、項目の名前と、利用者が書いた値の先頭 100 文字まで。知らない項目の値は埋めない。DSL の中の {@code $ref} は
 * 知らない項目として誤りになる。
 */
@Component
public class DslSchemaValidator {

    private final Schema schema;

    /** 同梱の JSON Schema の正本を読んで作る。 */
    public DslSchemaValidator() {
        this(readBundledSchema());
    }

    /**
     * 与えた JSON Schema で作る（テストで、外部を指す {@code $ref} を含むスキーマを与えるために使う）。
     *
     * @param schemaJson JSON Schema（JSON の文字列）
     */
    DslSchemaValidator(String schemaJson) {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaLoader(loader -> loader.fetchRemoteResources(false)));
        this.schema = registry.getSchema(Objects.requireNonNull(schemaJson, "schemaJson は必須です"), InputFormat.JSON);
        // 参照の解決を起動の時点で済ませ、検証のたびに行わない（外部を指す参照があればここで失敗する）。
        this.schema.initializeValidators();
    }

    /**
     * 構文を検証する。
     *
     * @param document 検証用の JSON の形と位置の対応表
     * @return 構文の誤り（無ければ空）
     */
    public List<DslError> validate(YamlDocument document) {
        LinkedHashSet<DslError> errors = new LinkedHashSet<>();
        for (com.networknt.schema.Error error : schema.validate(document.json())) {
            errors.add(toDslError(error, document.positions()));
        }
        return List.copyOf(errors);
    }

    private static DslError toDslError(com.networknt.schema.Error error, PositionMap positions) {
        String pointer = pointerOf(error.getInstanceLocation());
        JsonNode schemaNode = error.getSchemaNode();
        String value = DslErrors.valueOf(error.getInstanceNode());
        String keyword = Objects.requireNonNullElse(error.getKeyword(), "");
        return switch (keyword) {
            case "required" ->
                DslErrors.at(
                        DslErrorKind.SYNTAX,
                        positions,
                        pointer,
                        DslMessageKeys.SYNTAX_REQUIRED,
                        DslError.excerpt(error.getProperty()));
            case "additionalProperties" -> {
                String property = Objects.requireNonNullElse(error.getProperty(), "");
                yield DslErrors.at(
                        DslErrorKind.SYNTAX,
                        positions,
                        JsonPointers.child(pointer, property),
                        DslMessageKeys.SYNTAX_UNKNOWN_PROPERTY,
                        DslError.excerpt(property));
            }
            case "type" ->
                DslErrors.at(
                        DslErrorKind.SYNTAX, positions, pointer, DslMessageKeys.SYNTAX_TYPE, joined(schemaNode), value);
            case "enum", "const" ->
                DslErrors.at(
                        DslErrorKind.SYNTAX, positions, pointer, DslMessageKeys.SYNTAX_ENUM, value, joined(schemaNode));
            case "minimum" ->
                DslErrors.at(
                        DslErrorKind.SYNTAX,
                        positions,
                        pointer,
                        DslMessageKeys.SYNTAX_MINIMUM,
                        joined(schemaNode),
                        value);
            case "maximum" ->
                DslErrors.at(
                        DslErrorKind.SYNTAX,
                        positions,
                        pointer,
                        DslMessageKeys.SYNTAX_MAXIMUM,
                        joined(schemaNode),
                        value);
            case "minLength" ->
                DslErrors.at(
                        DslErrorKind.SYNTAX, positions, pointer, DslMessageKeys.SYNTAX_MIN_LENGTH, joined(schemaNode));
            case "minItems", "minProperties" ->
                DslErrors.at(
                        DslErrorKind.SYNTAX, positions, pointer, DslMessageKeys.SYNTAX_MIN_ITEMS, joined(schemaNode));
            default -> DslErrors.at(DslErrorKind.SYNTAX, positions, pointer, DslMessageKeys.SYNTAX_INVALID, keyword);
        };
    }

    /**
     * networknt の誤りの場所を、位置の対応表と同じ JSON Pointer の形にする。
     *
     * @param path 誤りの場所
     * @return JSON Pointer の形の場所
     */
    static String pointerOf(NodePath path) {
        String pointer = JsonPointers.ROOT;
        if (path == null) {
            return pointer;
        }
        for (int i = 0; i < path.getNameCount(); i++) {
            Object element = path.getElement(i);
            pointer = element instanceof Integer index
                    ? JsonPointers.child(pointer, index)
                    : JsonPointers.child(pointer, String.valueOf(element));
        }
        return pointer;
    }

    /** JSON Schema の値（型の名前・許される値の並び・数）を、{@code ,} でつないだ埋める値にする。 */
    private static String joined(JsonNode schemaNode) {
        if (schemaNode == null) {
            return "";
        }
        if (schemaNode.isArray()) {
            return StreamSupport.stream(schemaNode.spliterator(), false)
                    .map(DslErrors::valueOf)
                    .collect(Collectors.joining(","));
        }
        return DslErrors.valueOf(schemaNode);
    }

    private static String readBundledSchema() {
        try (InputStream in =
                DslSchemaValidator.class.getClassLoader().getResourceAsStream(DslFormat.SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("同梱の JSON Schema が見つかりません: " + DslFormat.SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("同梱の JSON Schema を読めません: " + DslFormat.SCHEMA_RESOURCE, e);
        }
    }
}
