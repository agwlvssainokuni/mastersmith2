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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import cherry.mastersmith.dslmanage.domain.DslErrorItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * U2 の誤りの文言の鍵（{@link DslMessageKeys}）から、表示言語の文言を作る（BR1.5、NFR5.4）。
 *
 * <p>文言の {@code {0}}・{@code {1}} … には、U2 が渡す埋める値（DSL の中の名前と、利用者が書いた値の先頭の一部）を順に埋める。
 * 部品の例外の文言は U2 が渡さないため、文言にも入らない。知らない鍵は、場所だけを示す汎用の文言にする（鍵の抜けは単体テストで
 * 止める）。
 */
public final class DslErrorMessages {

    /** 知らない鍵の文言。 */
    static final LocalizedText UNKNOWN = new LocalizedText("DSL に誤りがあります。", "The DSL contains an error.");

    private static final Map<String, LocalizedText> TEXTS = texts();

    private DslErrorMessages() {}

    private static Map<String, LocalizedText> texts() {
        Map<String, LocalizedText> texts = new LinkedHashMap<>();
        put(
                texts,
                DslMessageKeys.SIZE_LIMIT,
                "本文が大きさの上限（{0} バイト）を超えています。",
                "The DSL exceeds the size limit ({0} bytes).");
        put(texts, DslMessageKeys.DEPTH_LIMIT, "入れ子の深さが上限（{0}）を超えています。", "The nesting depth exceeds the limit ({0}).");
        put(
                texts,
                DslMessageKeys.ALIAS_LIMIT,
                "別名（アンカー）の数が上限（{0}）を超えています。",
                "The number of aliases exceeds the limit ({0}).");
        put(
                texts,
                DslMessageKeys.EXPANDED_NODES_LIMIT,
                "別名を展開した後の要素の数が上限（{0}）を超えています。",
                "The number of nodes after expanding aliases exceeds the limit ({0}).");
        put(texts, DslMessageKeys.RECURSIVE_ALIAS, "別名が自分自身を含んでいます。", "An alias refers to itself.");
        put(texts, DslMessageKeys.FORBIDDEN_TAG, "タグ（{0}）は使えません。", "Tags ({0}) are not allowed.");
        put(texts, DslMessageKeys.DUPLICATE_KEY, "キー「{0}」が重なっています。", "The key \"{0}\" is duplicated.");
        put(
                texts,
                DslMessageKeys.VERSION_MISSING,
                "書式の版（version）がありません。対応する版は {0} です。",
                "The format version (version) is missing. The supported version is {0}.");
        put(
                texts,
                DslMessageKeys.VERSION_UNSUPPORTED,
                "書式の版 {0} には対応していません。対応する版は {1} です。",
                "The format version {0} is not supported. The supported version is {1}.");
        put(texts, DslMessageKeys.YAML_ENCODING, "本文を UTF-8 として読めません。", "The DSL cannot be read as UTF-8.");
        put(
                texts,
                DslMessageKeys.YAML_MALFORMED,
                "YAML として読めません（字下げ・引用符・文書の区切りを確かめてください）。",
                "The DSL cannot be read as YAML (check the indentation, quotes and document separators).");
        put(
                texts,
                DslMessageKeys.YAML_KEY_NOT_SCALAR,
                "対応表のキーには、文字や数などの単独の値を使ってください。",
                "Use a single value such as a string or a number as a mapping key.");
        put(texts, DslMessageKeys.SYNTAX_REQUIRED, "必須の項目「{0}」がありません。", "The required item \"{0}\" is missing.");
        put(
                texts,
                DslMessageKeys.SYNTAX_UNKNOWN_PROPERTY,
                "書式に無い項目「{0}」があります。",
                "The item \"{0}\" is not part of the format.");
        put(
                texts,
                DslMessageKeys.SYNTAX_TYPE,
                "値「{1}」の型が違います（求める型: {0}）。",
                "The value \"{1}\" has the wrong type (expected: {0}).");
        put(
                texts,
                DslMessageKeys.SYNTAX_ENUM,
                "値「{0}」は使えません（使える値: {1}）。",
                "The value \"{0}\" is not allowed (allowed values: {1}).");
        put(
                texts,
                DslMessageKeys.SYNTAX_MINIMUM,
                "値「{1}」が下限（{0}）より小さいです。",
                "The value \"{1}\" is less than the minimum ({0}).");
        put(
                texts,
                DslMessageKeys.SYNTAX_MAXIMUM,
                "値「{1}」が上限（{0}）より大きいです。",
                "The value \"{1}\" is greater than the maximum ({0}).");
        put(texts, DslMessageKeys.SYNTAX_MIN_LENGTH, "{0} 文字以上にしてください。", "Use at least {0} characters.");
        put(texts, DslMessageKeys.SYNTAX_MIN_ITEMS, "{0} 件以上にしてください。", "Use at least {0} items.");
        put(
                texts,
                DslMessageKeys.SYNTAX_INVALID,
                "書式の決まり（{0}）に合っていません。",
                "The DSL does not meet the format rule ({0}).");
        put(
                texts,
                DslMessageKeys.SEMANTIC_MENU_UNKNOWN_TABLE,
                "メニューが DSL に無いテーブル「{0}」を指しています。",
                "The menu refers to the table \"{0}\", which is not in the DSL.");
        put(
                texts,
                DslMessageKeys.SEMANTIC_MENU_EMPTY,
                "メニューの項目に、テーブルも子の項目もありません。",
                "The menu item has neither a table nor child items.");
        put(
                texts,
                DslMessageKeys.SEMANTIC_UNKNOWN_TABLE,
                "参照の先のテーブル「{0}」が DSL にありません。",
                "The referenced table \"{0}\" is not in the DSL.");
        put(
                texts,
                DslMessageKeys.SEMANTIC_UNKNOWN_COLUMN,
                "テーブル「{0}」にカラム「{1}」がありません。",
                "The table \"{0}\" has no column \"{1}\".");
        put(
                texts,
                DslMessageKeys.SEMANTIC_DUPLICATE_ORDER,
                "一覧の並び順 {0} がテーブルの中で重なっています。",
                "The list order {0} is used more than once in the table.");
        put(
                texts,
                DslMessageKeys.SEMANTIC_OPTIONS_MISMATCH,
                "フォーム部品「{0}」と選択肢の出どころ「{1}」が合いません。",
                "The form part \"{0}\" does not match the option source \"{1}\".");
        put(
                texts,
                DslMessageKeys.SEMANTIC_OPTIONS_DUPLICATE_VALUE,
                "選択肢の値「{0}」が重なっています。",
                "The option value \"{0}\" is duplicated.");
        put(
                texts,
                DslMessageKeys.SEMANTIC_MIN_GREATER_THAN_MAX,
                "min（{0}）が max（{1}）より大きいです。",
                "min ({0}) is greater than max ({1}).");
        put(
                texts,
                DslMessageKeys.SEMANTIC_MIN_LENGTH_GREATER_THAN_MAX_LENGTH,
                "minLength（{0}）が maxLength（{1}）より大きいです。",
                "minLength ({0}) is greater than maxLength ({1}).");
        put(
                texts,
                DslMessageKeys.SEMANTIC_INVALID_PATTERN,
                "pattern が正規表現として正しくありません。",
                "The pattern is not a valid regular expression.");
        put(
                texts,
                DslMessageKeys.SEMANTIC_PATTERN_TOO_LONG,
                "pattern が長さの上限（{0} 文字）を超えています。",
                "The pattern exceeds the length limit ({0} characters).");
        return Map.copyOf(texts);
    }

    private static void put(Map<String, LocalizedText> texts, String key, String ja, String en) {
        texts.put(key, new LocalizedText(ja, en));
    }

    /**
     * 鍵の文言を返す。
     *
     * @param messageKey 文言の鍵
     * @return 文言（知らない鍵なら空）
     */
    static Optional<LocalizedText> text(String messageKey) {
        return Optional.ofNullable(TEXTS.get(messageKey));
    }

    /**
     * 誤りの文言を作る。
     *
     * @param error U2 の誤り
     * @param language 表示言語
     * @return 文言
     */
    public static String message(DslError error, DisplayLanguage language) {
        String template = text(error.messageKey()).orElse(UNKNOWN).in(language);
        return DslReconciler.format(template, error.messageArgs().toArray(String[]::new));
    }

    /**
     * 誤りを応答の1件にする。
     *
     * @param error U2 の誤り
     * @param language 表示言語
     * @return 応答の1件
     */
    public static DslErrorItem item(DslError error, DisplayLanguage language) {
        return new DslErrorItem(error.kind(), error.line(), error.column(), error.path(), message(error, language));
    }

    /**
     * 最初の誤りの種類（受け付けなかった投入の理由の種類。BR7.4）を返す。
     *
     * @param errors 誤りの一覧（1件以上）
     * @return 最初の誤りの種類
     */
    public static DslErrorKind firstKind(List<DslError> errors) {
        return errors.getFirst().kind();
    }
}
