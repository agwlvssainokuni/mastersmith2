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
package cherry.mastersmith.businessdata.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 行を指す主キーのカラムと、型として読んだ値の組（entities.md の RowKey、BR6.2）。カラムは DSL の主キーの順。
 *
 * <p>文字列にするときはカラムの数だけを出す（BR10.1）。
 *
 * @param values カラムの名前と値（主キーの順。変更できない）
 */
public record RowKey(Map<String, BoundValue> values) {

    /** 対応表を、順を保ったまま変更できないものにする。 */
    public RowKey {
        Objects.requireNonNull(values, "values は必須です");
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** カラムの数だけを出す（名前と値を出さない）。 */
    @Override
    public String toString() {
        return "RowKey[columns=" + values.size() + "]";
    }
}
