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

import java.util.List;
import java.util.Objects;

/**
 * 検索の条件1つ（entities.md の SearchCondition、BR4）。値は型として読んだ後の形で持ち、演算子は要求からではなくカラムの検索の
 * 設定（{@link ColumnAccess#search()}）から決める。条件はすべて AND で組む（BR4.1）。
 *
 * <p>文字列にするときはカラムの名前・演算子・値の数だけを出す（BR10.1）。
 *
 * @param column カラムの名前（照らした後の名前）
 * @param mode 演算子
 * @param valueType カラムの受け渡しの型
 * @param value EQUALS・CONTAINS の値（ほかは null）
 * @param from RANGE の下限（無ければ null）
 * @param to RANGE の上限（無ければ null）
 * @param values CHOICE の値（重複を除いた後。ほかは空）
 */
public record SearchCondition(
        String column,
        SearchMode mode,
        ValueType valueType,
        BoundValue value,
        BoundValue from,
        BoundValue to,
        List<BoundValue> values) {

    /** 必須の値を確かめ、演算子に合う値を持つことを確かめる。 */
    public SearchCondition {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(mode, "mode は必須です");
        Objects.requireNonNull(valueType, "valueType は必須です");
        values = List.copyOf(values == null ? List.of() : values);
        boolean ok =
                switch (mode) {
                    case EQUALS, CONTAINS -> value != null && from == null && to == null && values.isEmpty();
                    case RANGE -> value == null && (from != null || to != null) && values.isEmpty();
                    case CHOICE -> value == null && from == null && to == null && !values.isEmpty();
                };
        if (!ok) {
            throw new IllegalArgumentException("条件の値が演算子と合いません");
        }
    }

    /**
     * 完全一致の条件を作る。
     *
     * @param column カラムの名前
     * @param value 値
     * @return 条件
     */
    public static SearchCondition equalTo(String column, BoundValue value) {
        return new SearchCondition(column, SearchMode.EQUALS, value.valueType(), value, null, null, List.of());
    }

    /**
     * 部分一致の条件を作る（値はエスケープする前の文字）。
     *
     * @param column カラムの名前
     * @param value 値（STRING）
     * @return 条件
     */
    public static SearchCondition contains(String column, BoundValue value) {
        if (value.valueType() != ValueType.STRING) {
            throw new IllegalArgumentException("部分一致は STRING だけです");
        }
        return new SearchCondition(column, SearchMode.CONTAINS, ValueType.STRING, value, null, null, List.of());
    }

    /**
     * 範囲の条件を作る。
     *
     * @param column カラムの名前
     * @param valueType 受け渡しの型
     * @param from 下限（無ければ null）
     * @param to 上限（無ければ null）
     * @return 条件
     */
    public static SearchCondition range(String column, ValueType valueType, BoundValue from, BoundValue to) {
        return new SearchCondition(column, SearchMode.RANGE, valueType, null, from, to, List.of());
    }

    /**
     * 選択肢の条件を作る。
     *
     * @param column カラムの名前
     * @param valueType 受け渡しの型
     * @param values 値（1件以上）
     * @return 条件
     */
    public static SearchCondition choice(String column, ValueType valueType, List<BoundValue> values) {
        return new SearchCondition(column, SearchMode.CHOICE, valueType, null, null, null, values);
    }

    /** カラムの名前・演算子・値の数だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        int count = (value == null ? 0 : 1) + (from == null ? 0 : 1) + (to == null ? 0 : 1) + values.size();
        return "SearchCondition[column=" + column + ", mode=" + mode + ", values=" + count + "]";
    }
}
