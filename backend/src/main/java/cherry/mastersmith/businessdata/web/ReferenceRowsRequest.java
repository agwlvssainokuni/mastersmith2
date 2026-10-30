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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.domain.RawCondition;
import cherry.mastersmith.businessdata.domain.ReferenceRowsQuery;
import cherry.mastersmith.businessdata.domain.ReferenceRowsRequestValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 参照先の検索とラベルの1件読みの API の本文（{@code POST /api/tables/reference/rows}、Intent 261029-reference-lookup-cleanup の U1、
 * 契約 C2 の {@code ReferenceRowsRequest}）。参照先の名前は受け取らず、参照元の名前だけを受ける（BR2.1）。ページの番号は整数でない値も
 * 見分けられるよう数のまま受ける。本文の知らない項目は読み取りで捨てる（BR1.4）。文字列にするときは名前と条件の数・値の有無だけを出す
 * （BR10.1）。
 *
 * @param schema 参照元のスキーマ
 * @param table 参照元のテーブル
 * @param column 参照元のカラム
 * @param conditions 検索の条件（検索のとき）
 * @param page ページの番号（検索のとき。1 から）
 * @param value ラベルを引く値（ラベルの1件読みのとき）
 */
public record ReferenceRowsRequest(
        String schema,
        String table,
        String column,
        List<ReferenceConditionRequest> conditions,
        BigDecimal page,
        String value) {

    /**
     * 本文の形を確かめて要求にする（BR1.4。DSL を見ない）。
     *
     * @return 形の正しい要求（誤りなら空）
     */
    Optional<ReferenceRowsQuery> toQuery() {
        List<RawCondition> raw = null;
        if (conditions != null) {
            raw = new ArrayList<>();
            for (ReferenceConditionRequest condition : conditions) {
                raw.add(condition == null ? null : condition.toRaw());
            }
        }
        return ReferenceRowsRequestValidator.validate(schema, table, column, raw, page, value);
    }

    /** 名前と条件の数・値の有無だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "ReferenceRowsRequest[schema=" + schema + ", table=" + table + ", column=" + column + ", conditions="
                + (conditions == null ? "null" : conditions.size()) + ", page=" + page + ", value="
                + (value == null ? "null" : "***") + "]";
    }
}
