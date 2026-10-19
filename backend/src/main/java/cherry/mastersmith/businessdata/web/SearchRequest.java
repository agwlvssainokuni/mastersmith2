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
import cherry.mastersmith.businessdata.domain.RawSort;
import cherry.mastersmith.businessdata.domain.SearchInput;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一覧と検索の API の本文（{@code POST /api/tables/rows/search}、契約 C2 の {@code SearchRequest}）。ページの番号と件数は、整数でない値も
 * 見分けられるよう数のまま受ける。文字列にするときは条件の数だけを出す（BR10.1）。
 *
 * @param schema スキーマ名
 * @param table テーブル名
 * @param definitionId 画面が持っている定義の識別（任意）
 * @param conditions 条件（無ければ空）
 * @param sort 並べ替え（無ければ null）
 * @param page ページの番号（無ければ 1）
 * @param size 1ページの件数（無ければ 20）
 */
public record SearchRequest(
        String schema,
        String table,
        DefinitionIdBody definitionId,
        List<SearchConditionRequest> conditions,
        SortKeyBody sort,
        BigDecimal page,
        BigDecimal size) {

    /**
     * 確かめる前の本文にする。
     *
     * @return 本文
     */
    SearchInput toInput() {
        List<RawCondition> raw = new ArrayList<>();
        if (conditions != null) {
            for (SearchConditionRequest condition : conditions) {
                raw.add(condition == null ? null : condition.toRaw());
            }
        }
        return new SearchInput(
                Collections.unmodifiableList(raw),
                sort == null ? null : new RawSort(sort.column(), sort.direction()),
                page,
                size);
    }

    /** テーブルの名前と条件の数だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "SearchRequest[schema=" + schema + ", table=" + table + ", conditions="
                + (conditions == null ? 0 : conditions.size()) + ", page=" + page + ", size=" + size + "]";
    }
}
