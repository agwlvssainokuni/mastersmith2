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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 要求の検索の条件1つ（確かめる前。契約 C2 の {@code conditions[]}）。使う項目はカラムの演算子で決まり、ほかの項目は使わない。
 *
 * <p>文字列にするときはカラムの名前と値の数だけを出す（BR10.1）。
 *
 * @param column カラムの名前（照らす前）
 * @param value EQUALS・CONTAINS の値
 * @param from RANGE の下限
 * @param to RANGE の上限
 * @param values CHOICE の値
 */
public record RawCondition(String column, String value, String from, String to, List<String> values) {

    /** 値の一覧を、null の要素を許したまま変更できないものにする。 */
    public RawCondition {
        values = values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    /** カラムの名前と値の数だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        int count = (value == null ? 0 : 1) + (from == null ? 0 : 1) + (to == null ? 0 : 1) + values.size();
        return "RawCondition[column=" + column + ", values=" + count + "]";
    }
}
