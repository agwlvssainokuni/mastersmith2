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

/**
 * 値の受け渡しの型（契約 C2 の {@code valueType}、BR7.1）。DB の型からの対応は {@code ColumnValueCodec} の1か所で決める。
 */
public enum ValueType {
    /** 文字列。 */
    STRING,
    /** 整数（{@code long} に収まるもの）。 */
    INTEGER,
    /** 長い整数（{@code BIGINT} など）。文字で受け渡す。 */
    BIG_INTEGER,
    /** 小数。文字で受け渡し、桁を失わない。 */
    DECIMAL,
    /** 真偽値。 */
    BOOLEAN,
    /** 日付。 */
    DATE,
    /** 時差の無い日時。 */
    DATETIME,
    /** 時差つきの日時（UTC にそろえて返す）。 */
    DATETIME_TZ,
    /** 時差の無い時刻。 */
    TIME,
    /** 時差つきの時刻（UTC にそろえて返す）。 */
    TIME_TZ,
    /** バイナリ（とそれと同じ扱いの型）。値を読まず、返さない（BR7.5）。 */
    BINARY,
    /** 型の分類の外の型。読み取りだけの文字（BR7.7）。 */
    OTHER;

    /**
     * 範囲の検索（RANGE）に使える型か（BR4.9）。
     *
     * @return 数・日付・日時・時刻なら true
     */
    public boolean rangeComparable() {
        return switch (this) {
            case INTEGER, BIG_INTEGER, DECIMAL, DATE, DATETIME, DATETIME_TZ, TIME, TIME_TZ -> true;
            case STRING, BOOLEAN, BINARY, OTHER -> false;
        };
    }
}
