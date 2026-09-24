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
package cherry.mastersmith.dsl.domain;

import java.util.List;

/**
 * DSL の誤りの文言の鍵の一覧（BR4.3）。U2 は鍵と埋める値だけを返し、ja・en の文言は U4 が鍵から用意する。U4 が文言を漏れなく
 * 用意できるよう、U2 が使う鍵はすべてここに置き、{@link #all()} で一覧を返す。
 *
 * <p>各鍵の説明の「埋める値」は {@link DslError#messageArgs()} の並びの順。
 */
public final class DslMessageKeys {

    /** 本文が大きさの上限を超えた。埋める値: 上限のバイト数。 */
    public static final String SIZE_LIMIT = "dsl.limit.size";

    /** 入れ子の深さが上限を超えた。埋める値: 上限の深さ。 */
    public static final String DEPTH_LIMIT = "dsl.limit.depth";

    /** コレクションを指す別名の数が上限を超えた。埋める値: 上限の数。 */
    public static final String ALIAS_LIMIT = "dsl.limit.alias";

    /** 別名を展開した後の節の数が上限を超えた（別名の展開の爆発を含む）。埋める値: 上限の節の数。 */
    public static final String EXPANDED_NODES_LIMIT = "dsl.limit.expandedNodes";

    /** 別名が自分自身を含む（展開が終わらない）。埋める値: なし。 */
    public static final String RECURSIVE_ALIAS = "dsl.limit.recursiveAlias";

    /** タグを使っている。埋める値: 書かれたタグ（先頭 100 文字）。 */
    public static final String FORBIDDEN_TAG = "dsl.tag.forbidden";

    /** 同じキーが重なっている。埋める値: キー（先頭 100 文字）。 */
    public static final String DUPLICATE_KEY = "dsl.key.duplicate";

    /** 書式の版が無い。埋める値: 対応する版。 */
    public static final String VERSION_MISSING = "dsl.version.missing";

    /** 書式の版が対応していない。埋める値: 書かれた版（先頭 100 文字）、対応する版。 */
    public static final String VERSION_UNSUPPORTED = "dsl.version.unsupported";

    /** 本文が UTF-8 として読めない。埋める値: なし。 */
    public static final String YAML_ENCODING = "dsl.yaml.encoding";

    /** YAML として読めない（字下げの誤り・閉じていない引用符・複数の文書など）。埋める値: なし。 */
    public static final String YAML_MALFORMED = "dsl.yaml.malformed";

    /** 対応表のキーが文字・数などの単独の値でない（並びや対応表をキーにしている）。埋める値: なし。 */
    public static final String YAML_KEY_NOT_SCALAR = "dsl.yaml.keyNotScalar";

    /** 必須の項目が無い。埋める値: 項目の名前。 */
    public static final String SYNTAX_REQUIRED = "dsl.syntax.required";

    /** 書式に無い項目がある（値は埋めない）。埋める値: 項目の名前（先頭 100 文字）。 */
    public static final String SYNTAX_UNKNOWN_PROPERTY = "dsl.syntax.unknownProperty";

    /** 値の型が違う。埋める値: 求める型（例 {@code string}・{@code integer,null}）、書かれた値（先頭 100 文字）。 */
    public static final String SYNTAX_TYPE = "dsl.syntax.type";

    /** 決まった値のどれでもない。埋める値: 書かれた値（先頭 100 文字）、許される値（{@code ,} でつないだもの）。 */
    public static final String SYNTAX_ENUM = "dsl.syntax.enum";

    /** 数が下限より小さい。埋める値: 下限、書かれた値（先頭 100 文字）。 */
    public static final String SYNTAX_MINIMUM = "dsl.syntax.minimum";

    /** 数が上限より大きい。埋める値: 上限、書かれた値（先頭 100 文字）。 */
    public static final String SYNTAX_MAXIMUM = "dsl.syntax.maximum";

    /** 文字が短すぎる（空の名前など）。埋める値: 最小の文字数。 */
    public static final String SYNTAX_MIN_LENGTH = "dsl.syntax.minLength";

    /** 並び・対応表の件数が少なすぎる。埋める値: 最小の件数。 */
    public static final String SYNTAX_MIN_ITEMS = "dsl.syntax.minItems";

    /** そのほかの構文の誤り。埋める値: JSON Schema のキーワード。 */
    public static final String SYNTAX_INVALID = "dsl.syntax.invalid";

    /** メニューが DSL に無いテーブルを指す（BR3.1）。埋める値: テーブルの名前（先頭 100 文字）。 */
    public static final String SEMANTIC_MENU_UNKNOWN_TABLE = "dsl.semantic.menu.unknownTable";

    /** メニューの項目がテーブルも子も持たない（BR3.2）。埋める値: なし。 */
    public static final String SEMANTIC_MENU_EMPTY = "dsl.semantic.menu.empty";

    /** 主キー・外部キー・参照の先のテーブルが DSL に無い（BR3.3）。埋める値: テーブルの名前（先頭 100 文字）。 */
    public static final String SEMANTIC_UNKNOWN_TABLE = "dsl.semantic.reference.unknownTable";

    /** 主キー・外部キー・参照の先のカラムがそのテーブルに無い（BR3.3）。埋める値: テーブルの名前、カラムの名前（各先頭 100 文字）。 */
    public static final String SEMANTIC_UNKNOWN_COLUMN = "dsl.semantic.reference.unknownColumn";

    /** 一覧の並び順がテーブルの中で重なる（BR3.4）。埋める値: 並び順。 */
    public static final String SEMANTIC_DUPLICATE_ORDER = "dsl.semantic.list.duplicateOrder";

    /** フォーム部品と選択肢の出どころが合わない（BR3.5）。埋める値: フォーム部品、選択肢の出どころ（無ければ {@code none}）。 */
    public static final String SEMANTIC_OPTIONS_MISMATCH = "dsl.semantic.options.mismatch";

    /** 固定の選択肢の値が重なる（entities.md の OptionSource.items の制約）。埋める値: 値（先頭 100 文字）。 */
    public static final String SEMANTIC_OPTIONS_DUPLICATE_VALUE = "dsl.semantic.options.duplicateValue";

    /** {@code min} が {@code max} より大きい（BR3.6）。埋める値: min、max。 */
    public static final String SEMANTIC_MIN_GREATER_THAN_MAX = "dsl.semantic.validation.minGreaterThanMax";

    /** {@code minLength} が {@code maxLength} より大きい（BR3.6）。埋める値: minLength、maxLength。 */
    public static final String SEMANTIC_MIN_LENGTH_GREATER_THAN_MAX_LENGTH =
            "dsl.semantic.validation.minLengthGreaterThanMaxLength";

    /** {@code pattern} が正規表現として正しくない、または確かめが時間の上限で打ち切られた（BR3.6、NFR3.6）。埋める値: なし。 */
    public static final String SEMANTIC_INVALID_PATTERN = "dsl.semantic.validation.invalidPattern";

    /** {@code pattern} が長さの上限を超えた（NFR3.6）。埋める値: 上限の文字数。 */
    public static final String SEMANTIC_PATTERN_TOO_LONG = "dsl.semantic.validation.patternTooLong";

    private static final List<String> ALL = List.of(
            SIZE_LIMIT,
            DEPTH_LIMIT,
            ALIAS_LIMIT,
            EXPANDED_NODES_LIMIT,
            RECURSIVE_ALIAS,
            FORBIDDEN_TAG,
            DUPLICATE_KEY,
            VERSION_MISSING,
            VERSION_UNSUPPORTED,
            YAML_ENCODING,
            YAML_MALFORMED,
            YAML_KEY_NOT_SCALAR,
            SYNTAX_REQUIRED,
            SYNTAX_UNKNOWN_PROPERTY,
            SYNTAX_TYPE,
            SYNTAX_ENUM,
            SYNTAX_MINIMUM,
            SYNTAX_MAXIMUM,
            SYNTAX_MIN_LENGTH,
            SYNTAX_MIN_ITEMS,
            SYNTAX_INVALID,
            SEMANTIC_MENU_UNKNOWN_TABLE,
            SEMANTIC_MENU_EMPTY,
            SEMANTIC_UNKNOWN_TABLE,
            SEMANTIC_UNKNOWN_COLUMN,
            SEMANTIC_DUPLICATE_ORDER,
            SEMANTIC_OPTIONS_MISMATCH,
            SEMANTIC_OPTIONS_DUPLICATE_VALUE,
            SEMANTIC_MIN_GREATER_THAN_MAX,
            SEMANTIC_MIN_LENGTH_GREATER_THAN_MAX_LENGTH,
            SEMANTIC_INVALID_PATTERN,
            SEMANTIC_PATTERN_TOO_LONG);

    private DslMessageKeys() {}

    /**
     * U2 が使う文言の鍵をすべて返す。
     *
     * @return 鍵の一覧（変更できない）
     */
    public static List<String> all() {
        return ALL;
    }
}
