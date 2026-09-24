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

/**
 * DB 上の型（値）。
 *
 * @param name 型の名前（DB が返したまま。例 {@code VARCHAR}）
 * @param length 長さ（無ければ null）
 * @param precision 精度（無ければ null）
 * @param scale 小数の桁（無ければ null）
 * @param nullable NULL を許すなら true
 */
public record DbType(String name, Integer length, Integer precision, Integer scale, boolean nullable) {

    /** 名前が必須で、長さ・精度・小数の桁が 0 以上であることを確かめる。 */
    public DbType {
        ModelValues.requireName(name, "dbType.name");
        requireNotNegative(length, "dbType.length");
        requireNotNegative(precision, "dbType.precision");
        requireNotNegative(scale, "dbType.scale");
    }

    private static void requireNotNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " は 0 以上です");
        }
    }
}
