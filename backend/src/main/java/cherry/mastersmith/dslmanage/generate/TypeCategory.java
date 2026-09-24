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
package cherry.mastersmith.dslmanage.generate;

/**
 * DB 上の型の分類（{@code entities.md} の {@code TypeCategory}）。3種類の DB の型の名前をこの分類に対応させ、分類から
 * フォーム部品・検索・一覧・詳細・書式の初期値を決める（{@link TypeCategoryMapping}、BR2.2〜BR2.5）。分類は DSL に書かない
 * （BR2.1）。
 */
public enum TypeCategory {
    /** 短い文字列（長さ 255 以下）。 */
    SHORT_TEXT,
    /** 長い文字列（長さ 256 以上、長さの無い文字列、{@code text} などの長い文字列の型）。 */
    LONG_TEXT,
    /** 整数・小数。 */
    NUMBER,
    /** 真偽値（{@code boolean}・{@code bool}、MySQL・MariaDB の {@code tinyint(1)}・{@code bit(1)}）。 */
    BOOLEAN,
    /** 日付。 */
    DATE,
    /** 日時（時差つきを含む）。 */
    DATETIME,
    /** 時刻（時差つきを含む）。 */
    TIME,
    /** 対応していない・分からない型。 */
    UNSUPPORTED
}
