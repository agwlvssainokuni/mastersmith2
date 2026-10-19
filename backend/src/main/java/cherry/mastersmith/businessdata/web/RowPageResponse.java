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

import cherry.mastersmith.businessdata.service.SearchResult;
import java.util.List;

/**
 * 一覧と検索の API の応答（契約 C2 の {@code RowPage}）。文字列にするときは件数だけを出す（BR10.1）。
 *
 * @param definitionId 今の定義の識別
 * @param definitionChanged 要求の識別が今と違うか
 * @param items ページの行
 * @param page ページの番号
 * @param size 1ページの件数
 * @param total 条件に合う全体の件数
 */
public record RowPageResponse(
        DefinitionIdBody definitionId,
        boolean definitionChanged,
        List<RowItemResponse> items,
        long page,
        int size,
        long total) {

    /**
     * 一覧と検索の結果から作る。
     *
     * @param found 結果
     * @return 応答
     */
    static RowPageResponse from(SearchResult.Found found) {
        return new RowPageResponse(
                DefinitionIdBody.from(found.view().definitionId()),
                found.definitionChanged(),
                found.items().stream().map(RowItemResponse::from).toList(),
                found.page(),
                found.size(),
                found.total());
    }

    /** 件数だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "RowPageResponse[items=" + items.size() + ", page=" + page + ", size=" + size + ", total=" + total + "]";
    }
}
