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
import cherry.mastersmith.dsl.parse.JsonPointers;
import cherry.mastersmith.dsl.parse.PositionMap;
import cherry.mastersmith.dsl.parse.YamlPosition;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** 検証の誤りの組み立ての共通の処理（このパッケージの中だけで使う）。 */
final class DslErrors {

    private DslErrors() {}

    /**
     * 場所から位置の対応表で行・列を引いて誤りを作る（BR4.1）。
     *
     * @param kind 種類
     * @param positions 位置の対応表
     * @param pointer 場所（JSON Pointer の形）
     * @param messageKey 文言の鍵
     * @param messageArgs 埋める値
     * @return 誤り
     */
    static DslError at(
            DslErrorKind kind, PositionMap positions, String pointer, String messageKey, String... messageArgs) {
        YamlPosition position = positions.find(pointer).orElse(null);
        return new DslError(
                kind,
                position == null ? null : position.line(),
                position == null ? null : position.column(),
                JsonPointers.toPath(pointer),
                messageKey,
                List.of(messageArgs));
    }

    /**
     * 利用者が書いた値を、埋める値にする。文字・数・真偽値・null はその値の先頭 100 文字、対応表と並びは中身を埋めずに
     * {@code object}・{@code array} とする（知らない項目の値を埋めないため。NFR5.2）。
     *
     * @param value 値（null 可）
     * @return 埋める値
     */
    static String valueOf(JsonNode value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (value.isObject()) {
            return "object";
        }
        if (value.isArray()) {
            return "array";
        }
        if (value.isString()) {
            return DslError.excerpt(value.stringValue());
        }
        if (value.isNumber()) {
            return DslError.excerpt(value.decimalValue().toPlainString());
        }
        return DslError.excerpt(value.asString());
    }
}
