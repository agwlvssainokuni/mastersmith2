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
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.domain.DslStartupReadResult;
import cherry.mastersmith.dsl.domain.MenuDepth;
import cherry.mastersmith.dsl.parse.JsonPointers;
import cherry.mastersmith.dsl.parse.SafeYamlParser;
import cherry.mastersmith.dsl.parse.YamlDocument;
import cherry.mastersmith.dsl.parse.YamlLimits;
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
 *
 * <p>起動時の読み方（{@link #readAtStartup(byte[])}）は、意味の検証からメニューの深さだけを外し、モデルを作った後に深すぎる枝を
 * 落とす（U2 dsl-v2 の BR2.3）。
 */
@Service
public class DefaultDslReader implements DslReader {

    private static final String VERSION = "version";

    /** DSL の読み込みの上限（{@link DslFormat} の値。U2 の機能設計の BR6.6）。 */
    static final YamlLimits DSL_LIMITS = new YamlLimits(
            DslFormat.MAX_BYTES, DslFormat.MAX_DEPTH, DslFormat.MAX_COLLECTION_ALIASES, DslFormat.MAX_EXPANDED_NODES);

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
        return switch (readStages(yamlBytes, true)) {
            case Stages.Failed failed -> DslReadResult.invalid(failed.errors());
            case Stages.Passed passed -> DslReadResult.valid(passed.model());
        };
    }

    @Override
    public DslStartupReadResult readAtStartup(byte[] yamlBytes) {
        return switch (readStages(yamlBytes, false)) {
            case Stages.Failed failed -> new DslStartupReadResult.Invalid(failed.errors());
            case Stages.Passed passed -> {
                DslModel model = passed.model();
                MenuDepth.Pruned pruned = MenuDepth.prune(model.menus(), DslFormat.MAX_MENU_DEPTH);
                yield new DslStartupReadResult.Valid(
                        pruned.prunedCount() == 0
                                ? model
                                : new DslModel(model.dslHash(), model.formatVersion(), model.schemas(), pruned.menus()),
                        pruned.prunedCount());
            }
        };
    }

    /**
     * 段の順に読み、検証する。
     *
     * @param yamlBytes 本文
     * @param checkMenuDepth 意味の検証でメニューの深さを確かめるなら true
     * @return 通った（モデル）か、通らなかった（誤りの一覧）か
     */
    private Stages readStages(byte[] yamlBytes, boolean checkMenuDepth) {
        Objects.requireNonNull(yamlBytes, "yamlBytes は必須です");
        if (yamlBytes.length > DslFormat.MAX_BYTES) {
            return new Stages.Failed(List.of(new DslError(
                    DslErrorKind.SIZE_LIMIT,
                    null,
                    null,
                    null,
                    DslMessageKeys.SIZE_LIMIT,
                    List.of(String.valueOf(DslFormat.MAX_BYTES)))));
        }
        YamlDocument document;
        switch (parser.parse(yamlBytes, DSL_LIMITS)) {
            case YamlParseResult.Rejected rejected -> {
                return new Stages.Failed(List.of(rejected.error()));
            }
            case YamlParseResult.Parsed parsed -> document = parsed.document();
        }
        DslError version = checkVersion(document);
        if (version != null) {
            return new Stages.Failed(List.of(version));
        }
        List<DslError> syntax = schemaValidator.validate(document);
        if (!syntax.isEmpty()) {
            return new Stages.Failed(syntax);
        }
        List<DslError> semantic = semanticValidator.validate(document, checkMenuDepth);
        if (!semantic.isEmpty()) {
            return new Stages.Failed(semantic);
        }
        return new Stages.Passed(DslModelMapper.toModel(document.json(), hash(yamlBytes)));
    }

    /** 段の順の読み込みの途中の結果（このクラスの中だけで使う）。 */
    private sealed interface Stages {

        /** 通った。 */
        record Passed(DslModel model) implements Stages {}

        /** 通らなかった（誤りは1件以上）。 */
        record Failed(List<DslError> errors) implements Stages {}
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
     * 書式の版を確かめる（BR2.1、U2 dsl-v2 の BR1.1）。版が無い（本文が空・根が対応表でないときを含む）か、整数の
     * {@link DslFormat#CURRENT_VERSION}（2）でなければ誤りを返す。版 1 は読み替えない。
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
