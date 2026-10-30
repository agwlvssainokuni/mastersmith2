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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 参照先の検索 API の本文の形を確かめる純粋な関数（Intent 261029-reference-lookup-cleanup の U1、BR1.4、判定の順の ②）。DSL を見ず、
 * 名前の中身にも依らない（名前の存在を推測させないため、403 より前の 400 にする）。誤りは区別せず、{@code fieldErrors} を付けない 400
 * {@code VALIDATION_FAILED} にする。
 *
 * <p>誤り: {@code schema}・{@code table}・{@code column} が無い／{@code conditions} と {@code value} の両方がある・どちらも無い／
 * 条件の要素が無い・カラムが無い・同じカラムが2回ある／空でない条件の値が1つも無い／条件の値か {@code value} が 1,000 文字
 * （Unicode のコードポイント）を超える／検索で {@code page} が無い・整数でない・1〜1,000,000 の外。{@code value} のときの
 * {@code page} は読まない。条件の値が無い（null）ときは空の値として扱う。
 */
public final class ReferenceRowsRequestValidator {

    /** 条件の値と {@code value} の長さの上限（コードポイント。既存の {@link SearchRequestValidator#MAX_VALUE_LENGTH} と同じ）。 */
    public static final int MAX_VALUE_LENGTH = SearchRequestValidator.MAX_VALUE_LENGTH;

    private ReferenceRowsRequestValidator() {}

    /**
     * 本文の形を確かめる。
     *
     * @param schema 参照元のスキーマ
     * @param table 参照元のテーブル
     * @param column 参照元のカラム
     * @param conditions 検索の条件（本文に無ければ null。要素の null を含みうる）
     * @param page ページの番号（本文に無ければ null）
     * @param value ラベルを引く値（本文に無ければ null）
     * @return 形の正しい要求（誤りなら空）
     */
    public static Optional<ReferenceRowsQuery> validate(
            String schema, String table, String column, List<RawCondition> conditions, BigDecimal page, String value) {
        if (schema == null || table == null || column == null) {
            return Optional.empty();
        }
        boolean hasConditions = conditions != null;
        boolean hasValue = value != null;
        if (hasConditions == hasValue) {
            return Optional.empty();
        }
        if (hasValue) {
            if (tooLong(value)) {
                return Optional.empty();
            }
            return Optional.of(ReferenceRowsQuery.label(schema, table, column, new ReferenceValue(value)));
        }
        List<ReferenceCondition> read = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean anyValue = false;
        for (RawCondition raw : conditions) {
            if (raw == null || raw.column() == null || !seen.add(raw.column())) {
                return Optional.empty();
            }
            String text = raw.value() == null ? "" : raw.value();
            if (tooLong(text)) {
                return Optional.empty();
            }
            anyValue |= !text.isEmpty();
            read.add(new ReferenceCondition(raw.column(), text));
        }
        Long number = page(page);
        if (!anyValue || number == null) {
            return Optional.empty();
        }
        return Optional.of(ReferenceRowsQuery.search(schema, table, column, read, number));
    }

    /** ページの番号を読む（無い・整数でない・範囲の外なら null。既存の一覧と同じ数え方。D1）。 */
    private static Long page(BigDecimal raw) {
        if (raw == null) {
            return null;
        }
        BigDecimal stripped = raw.stripTrailingZeros();
        if (stripped.scale() > 0) {
            return null;
        }
        if (stripped.compareTo(BigDecimal.ONE) < 0
                || stripped.compareTo(BigDecimal.valueOf(PageRequest.MAX_PAGE)) > 0) {
            return null;
        }
        return stripped.longValueExact();
    }

    private static boolean tooLong(String text) {
        return text.length() > MAX_VALUE_LENGTH && text.codePointCount(0, text.length()) > MAX_VALUE_LENGTH;
    }
}
