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
import java.util.Objects;

/**
 * DSL の誤り1つ（契約 C4 の {@code DslError}）。
 *
 * <p>文言は持たず、文言の鍵（{@link DslMessageKeys}）と埋める値だけを持つ。埋める値は DSL の中の名前と、利用者が書いた値の
 * 先頭の一部（{@link DslFormat#MAX_ARGUMENT_LENGTH} 文字まで）に限り、YAML・JSON Schema の部品の例外の文言を入れない
 * （BR4.3、NFR5.1・NFR5.2）。
 *
 * @param kind 種類
 * @param line YAML の行（1 から。位置が得られないときは null）
 * @param column YAML の列（1 から。位置が得られないときは null）
 * @param path DSL の中の場所（点でつないだ形。例 {@code tables.dept_mst.columns.code}。無ければ null）
 * @param messageKey 文言の鍵
 * @param messageArgs 文言に埋める値
 */
public record DslError(
        DslErrorKind kind, Integer line, Integer column, String path, String messageKey, List<String> messageArgs) {

    /** 必須の値と、行・列の組み合わせを確かめ、埋める値を変更できない一覧にする。 */
    public DslError {
        Objects.requireNonNull(kind, "kind は必須です");
        Objects.requireNonNull(messageKey, "messageKey は必須です");
        if ((line == null) != (column == null)) {
            throw new IllegalArgumentException("line と column は両方あるか両方無いかのどちらかです");
        }
        if (line != null && (line < 1 || column < 1)) {
            throw new IllegalArgumentException("line と column は 1 以上です");
        }
        if (path != null && path.isEmpty()) {
            throw new IllegalArgumentException("path は空にしません（無いときは null）");
        }
        messageArgs = List.copyOf(Objects.requireNonNull(messageArgs, "messageArgs は必須です"));
    }

    /**
     * 利用者が書いた値を、埋める値として使える長さ（先頭 {@link DslFormat#MAX_ARGUMENT_LENGTH} 文字）に切る。
     *
     * @param value 利用者が書いた値（null は空の文字列にする）
     * @return 先頭の一部
     */
    public static String excerpt(String value) {
        if (value == null) {
            return "";
        }
        if (value.codePointCount(0, value.length()) <= DslFormat.MAX_ARGUMENT_LENGTH) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, DslFormat.MAX_ARGUMENT_LENGTH));
    }
}
