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

import java.util.Objects;

/**
 * 検証を通ったカラム1つの、型つきの値（Intent 261016-business-data-views の U2、entities.md の TypedValue、BR3.8）。値は
 * {@code ColumnValueCodec} が読んだ {@link BoundValue}（数は桁を保つ形、日付・日時は日時の型、真偽値は真偽値、時差つきの型は UTC）。
 *
 * <p>文字列にするときはカラムの名前と型だけを出し、値を伏せる。
 *
 * @param column カラムの名前
 * @param value 型として読んだ値（NULL は {@code BoundValue#value()} が null）
 */
public record TypedValue(String column, BoundValue value) {

    /** 必須の値を確かめる。BINARY・OTHER は書き込みに使わない（BR2.1）。 */
    public TypedValue {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(value, "value は必須です");
        if (value.valueType() == ValueType.OTHER) {
            throw new IllegalArgumentException("OTHER の値は書き込みに使いません");
        }
    }

    /** カラムの名前と型だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "TypedValue[column=" + column + ", valueType=" + value.valueType() + ", value="
                + (value.value() == null ? "null" : "***") + "]";
    }
}
