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
 * 型として読んだ後の値1つ（entities.md の BoundValue、BR7.8）。検索の値と主キーの値を型つきでバインドするときに使う。
 *
 * <p>値の Java の型は受け渡しの型ごとに決まる: STRING・OTHER は {@code String}、INTEGER は {@code Long}、BIG_INTEGER・DECIMAL は
 * {@code BigDecimal}、BOOLEAN は {@code Boolean}、DATE は {@code LocalDate}、DATETIME は {@code LocalDateTime}、TIME は
 * {@code LocalTime}、DATETIME_TZ は UTC の {@code OffsetDateTime}、TIME_TZ は UTC の {@code OffsetTime}。値を読むのは
 * {@code ColumnValueCodec} の1か所だけ。
 *
 * <p>文字列にするときは値を伏せる（BR10.1）。
 *
 * @param valueType 受け渡しの型（BINARY を除く）
 * @param value 型ごとの Java の値（NULL は null）
 */
public record BoundValue(ValueType valueType, Object value) {

    /** 型が必須で、BINARY でないことを確かめる。 */
    public BoundValue {
        Objects.requireNonNull(valueType, "valueType は必須です");
        if (valueType == ValueType.BINARY) {
            throw new IllegalArgumentException("BINARY の値はバインドしません");
        }
    }

    /** 型と、値の有無だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "BoundValue[valueType=" + valueType + ", value=" + (value == null ? "null" : "***") + "]";
    }
}
