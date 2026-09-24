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
package cherry.mastersmith.targetdb.domain;

import java.util.List;
import java.util.Objects;

/** スキーマの写しの値の確かめと揃え方の共通の処理（このパッケージの中だけで使う）。 */
final class DomainValues {

    private DomainValues() {}

    /**
     * 名前が空でないことを確かめる。物理名は DB が返したまま扱うため、前後の空白は取らない（BR2.3）。
     *
     * @param value 名前
     * @param field 項目の名前（誤りの文に使う）
     * @return 名前
     */
    static String requireName(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " は必須です");
        }
        return value;
    }

    /**
     * コメントを揃える。空の文字列・空白だけは、コメント無し（null）として扱う（BR2.5）。
     *
     * @param comment DB が返したコメント
     * @return コメント（無ければ null）
     */
    static String normalizeComment(String comment) {
        return comment == null || comment.isBlank() ? null : comment;
    }

    /**
     * 名前の一覧を変更できない一覧にし、どの要素も空でないことを確かめる。
     *
     * @param names 名前の一覧
     * @param field 項目の名前（誤りの文に使う）
     * @return 変更できない一覧
     */
    static List<String> copyNames(List<String> names, String field) {
        Objects.requireNonNull(names, field + " は必須です");
        for (String name : names) {
            requireName(name, field + " の要素");
        }
        return List.copyOf(names);
    }

    /**
     * 0 以上であることを確かめる（null は「無し」として通す）。
     *
     * @param value 値
     * @param field 項目の名前（誤りの文に使う）
     */
    static void requireNotNegative(Number value, String field) {
        if (value != null && value.longValue() < 0) {
            throw new IllegalArgumentException(field + " は 0 以上です");
        }
    }
}
