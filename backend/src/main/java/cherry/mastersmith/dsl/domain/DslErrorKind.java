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

/** DSL の誤りの種類（契約 C4 の {@code DslError.kind}）。種類ごとの行・列と場所の有無は functional-spec.md の 7.1 のとおり。 */
public enum DslErrorKind {
    /** 本文の大きさの上限を超えた（読む前に止める。行・列・場所なし）。 */
    SIZE_LIMIT,
    /** 入れ子の深さの上限を超えた。 */
    DEPTH_LIMIT,
    /** 別名（アンカー）の数、または別名を展開した後の節の数の上限を超えた。 */
    ALIAS_LIMIT,
    /** タグを使っている（型の指定・独自のタグとも拒否する）。 */
    FORBIDDEN_TAG,
    /** 1つの対応表の中で同じキーが重なっている。 */
    DUPLICATE_KEY,
    /** 書式の版が無い、または対応していない。 */
    UNSUPPORTED_VERSION,
    /** 構文の誤り（YAML として読めない、または JSON Schema に合わない）。 */
    SYNTAX,
    /** 意味の誤り。 */
    SEMANTIC
}
