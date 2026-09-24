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

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.parse.JsonPointers;
import cherry.mastersmith.dsl.parse.SafeYamlParser;
import cherry.mastersmith.dsl.parse.YamlDocument;
import cherry.mastersmith.dsl.parse.YamlParseResult;
import cherry.mastersmith.dsl.parse.YamlPosition;
import cherry.mastersmith.dsl.validate.DslSchemaValidator;
import cherry.mastersmith.dsl.validate.DslSemanticValidator;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * {@link DslReader} の実装（functional-spec.md の 1節・2節、BR1.1・BR2.1・BR2.3・BR5.1・BR5.2、NFR2.1）。
 *
 * <p>段の順は 大きさ（10MB。読まずに止める）→ 読み込み（{@link SafeYamlParser}）→ 書式の版 → 構文（{@link DslSchemaValidator}）→
 * 意味（{@link DslSemanticValidator}）で、ある段で誤りが見つかればその段の誤りだけを返す。通れば、本文のバイト列の SHA-256 を
 * 識別にしてモデルを作る。
 */
@Service
public class DefaultDslReader implements DslReader {

    private static final String VERSION = "version";

    private final SafeYamlParser parser;

    private final DslSchemaValidator schemaValidator;

    private final DslSemanticValidator semanticValidator;

    /**
     * 作る。
     *
     * @param parser YAML の安全な読み込み
     * @param schemaValidator 構文の検証
     * @param semanticValidator 意味の検証
     */
    public DefaultDslReader(
            SafeYamlParser parser, DslSchemaValidator schemaValidator, DslSemanticValidator semanticValidator) {
        this.parser = parser;
        this.schemaValidator = schemaValidator;
        this.semanticValidator = semanticValidator;
    }

    @Override
    public DslReadResult read(byte[] yamlBytes) {
        Objects.requireNonNull(yamlBytes, "yamlBytes は必須です");
        if (yamlBytes.length > DslFormat.MAX_BYTES) {
            return DslReadResult.invalid(List.of(new DslError(
                    DslErrorKind.SIZE_LIMIT,
                    null,
                    null,
                    null,
                    DslMessageKeys.SIZE_LIMIT,
                    List.of(String.valueOf(DslFormat.MAX_BYTES)))));
        }
        YamlDocument document;
        switch (parser.parse(yamlBytes)) {
            case YamlParseResult.Rejected rejected -> {
                return DslReadResult.invalid(List.of(rejected.error()));
            }
            case YamlParseResult.Parsed parsed -> document = parsed.document();
        }
        DslError version = checkVersion(document);
        if (version != null) {
            return DslReadResult.invalid(List.of(version));
        }
        List<DslError> syntax = schemaValidator.validate(document);
        if (!syntax.isEmpty()) {
            return DslReadResult.invalid(syntax);
        }
        List<DslError> semantic = semanticValidator.validate(document);
        if (!semantic.isEmpty()) {
            return DslReadResult.invalid(semantic);
        }
        return DslReadResult.valid(DslModelMapper.toModel(document.json(), hash(yamlBytes)));
    }

    @Override
    public String hash(byte[] yamlBytes) {
        Objects.requireNonNull(yamlBytes, "yamlBytes は必須です");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(yamlBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えません", e);
        }
    }

    /**
     * 書式の版を確かめる（BR2.1）。版が無い（本文が空・根が対応表でないときを含む）か、整数の 1 でなければ誤りを返す。
     *
     * @param document 読み込んだ文書
     * @return 誤り（版が対応していれば null）
     */
    static DslError checkVersion(YamlDocument document) {
        String pointer = JsonPointers.child(JsonPointers.ROOT, VERSION);
        YamlPosition position = document.positions().find(pointer).orElse(null);
        Integer line = position == null ? null : position.line();
        Integer column = position == null ? null : position.column();
        JsonNode version = document.json().isObject() ? document.json().get(VERSION) : null;
        String supported = String.valueOf(DslFormat.CURRENT_VERSION);
        if (version == null || version.isNull()) {
            return new DslError(
                    DslErrorKind.UNSUPPORTED_VERSION,
                    line,
                    column,
                    VERSION,
                    DslMessageKeys.VERSION_MISSING,
                    List.of(supported));
        }
        if (version.isIntegralNumber()
                && version.bigIntegerValue().equals(BigInteger.valueOf(DslFormat.CURRENT_VERSION))) {
            return null;
        }
        String written =
                version.isValueNode() ? DslError.excerpt(version.asString()) : version.isArray() ? "array" : "object";
        return new DslError(
                DslErrorKind.UNSUPPORTED_VERSION,
                line,
                column,
                VERSION,
                DslMessageKeys.VERSION_UNSUPPORTED,
                List.of(written, supported));
    }
}
