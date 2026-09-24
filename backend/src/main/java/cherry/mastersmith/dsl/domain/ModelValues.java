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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** モデルの値の確かめと、変更できない写しの作り方の共通の処理（このパッケージの中だけで使う）。 */
final class ModelValues {

    private ModelValues() {}

    /**
     * 名前が空でないことを確かめる。
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
     * 一覧を変更できない写しにする。
     *
     * @param <T> 要素の型
     * @param values 一覧
     * @param field 項目の名前（誤りの文に使う）
     * @return 変更できない写し
     */
    static <T> List<T> copyList(List<T> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field + " は必須です"));
    }

    /**
     * 名前の一覧を変更できない写しにし、どの要素も空でないことを確かめる。
     *
     * @param names 名前の一覧
     * @param field 項目の名前（誤りの文に使う）
     * @return 変更できない写し
     */
    static List<String> copyNames(List<String> names, String field) {
        List<String> copy = copyList(names, field);
        copy.forEach(name -> requireName(name, field + " の要素"));
        return copy;
    }

    /**
     * 対応表を、順を保つ変更できない写しにする。値の名前が対応表のキーと同じであることを確かめる。
     *
     * @param <V> 値の型
     * @param values 対応表（キーは物理名）
     * @param nameOf 値から名前を取り出す処理
     * @param field 項目の名前（誤りの文に使う）
     * @return 順を保つ変更できない写し
     */
    static <V> Map<String, V> copyNamedMap(Map<String, V> values, Function<V, String> nameOf, String field) {
        Objects.requireNonNull(values, field + " は必須です");
        Map<String, V> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            Objects.requireNonNull(value, field + " の値は必須です");
            if (!requireName(key, field + " のキー").equals(nameOf.apply(value))) {
                throw new IllegalArgumentException(field + " のキーと名前が合いません");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
