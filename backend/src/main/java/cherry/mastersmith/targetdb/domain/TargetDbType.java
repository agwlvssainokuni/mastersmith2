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

/**
 * カラムの DB 上の型（契約 C1 の {@code dbType}）。値は DB が返したままで、共通の分類への対応は U3 が行う（BR2.7）。
 *
 * <p>長さは MySQL・MariaDB の {@code longtext}（4294967295）のように 32 ビットの整数に収まらないことがあるため、
 * {@code Long} で持つ。
 *
 * @param typeName DB が返した型の名前（例: {@code varchar}・{@code int4}）
 * @param length 文字列の長さ（無ければ null）
 * @param precision 数値の精度（無ければ null）
 * @param scale 数値の小数の桁数（無ければ null）
 */
public record TargetDbType(String typeName, Long length, Integer precision, Integer scale) {

    /** 型の名前が空・空白だけでなく、長さ・精度・桁数が 0 以上であることを確かめる。 */
    public TargetDbType {
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName は必須です");
        }
        DomainValues.requireNotNegative(length, "length");
        DomainValues.requireNotNegative(precision, "precision");
        DomainValues.requireNotNegative(scale, "scale");
    }
}
