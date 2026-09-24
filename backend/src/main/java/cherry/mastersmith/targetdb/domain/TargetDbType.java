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
 * <p>{@code columnType} は MySQL・MariaDB の情報スキーマの {@code COLUMN_TYPE}（例 {@code tinyint(1)}・{@code int unsigned}）。
 * {@code DATA_TYPE}（{@code typeName}）と精度だけでは {@code tinyint(1)} とほかの {@code tinyint} を見分けられないため、U3 の
 * 型の分類のために足した（U3 のコード生成の Q1: A。契約 C1 との差）。PostgreSQL では null。
 *
 * @param typeName DB が返した型の名前（例: {@code varchar}・{@code int4}）
 * @param length 文字列の長さ（無ければ null）
 * @param precision 数値の精度（無ければ null）
 * @param scale 数値の小数の桁数（無ければ null）
 * @param columnType MySQL・MariaDB の型の全体の表記（無ければ null。空の文字列・空白だけは null に揃える）
 */
public record TargetDbType(String typeName, Long length, Integer precision, Integer scale, String columnType) {

    /** 型の名前が空・空白だけでなく、長さ・精度・桁数が 0 以上であることを確かめ、型の全体の表記を揃える。 */
    public TargetDbType {
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName は必須です");
        }
        DomainValues.requireNotNegative(length, "length");
        DomainValues.requireNotNegative(precision, "precision");
        DomainValues.requireNotNegative(scale, "scale");
        columnType = columnType == null || columnType.isBlank() ? null : columnType;
    }

    /**
     * 型の全体の表記の無い型を作る（PostgreSQL と、表記の要らないテストの写し）。
     *
     * @param typeName DB が返した型の名前
     * @param length 文字列の長さ（無ければ null）
     * @param precision 数値の精度（無ければ null）
     * @param scale 数値の小数の桁数（無ければ null）
     */
    public TargetDbType(String typeName, Long length, Integer precision, Integer scale) {
        this(typeName, length, precision, scale, null);
    }
}
