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
import java.util.List;

/**
 * 読み込みの段の途中で止めるための、このパッケージの中だけの例外。{@link SafeYamlParser} が受けて結果の型にする。
 *
 * <p>部品の例外の文言を持たない（誤りは文言の鍵と埋める値だけ。NFR5.1）。スタックトレースは作らない。
 */
final class YamlRejection extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient DslError error;

    private YamlRejection(DslError error) {
        super(error.messageKey(), null, false, false);
        this.error = error;
    }

    /**
     * 止める理由を作る。
     *
     * @param kind 種類
     * @param position 位置（無ければ null）
     * @param pointer 場所（JSON Pointer の形。無ければ null）
     * @param messageKey 文言の鍵
     * @param messageArgs 埋める値
     * @return 例外
     */
    static YamlRejection of(
            DslErrorKind kind, YamlPosition position, String pointer, String messageKey, String... messageArgs) {
        return new YamlRejection(new DslError(
                kind,
                position == null ? null : position.line(),
                position == null ? null : position.column(),
                JsonPointers.toPath(pointer),
                messageKey,
                List.of(messageArgs)));
    }

    /**
     * 誤りを返す。
     *
     * @return 誤り
     */
    DslError error() {
        return error;
    }
}
