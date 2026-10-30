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

import cherry.mastersmith.businessdata.domain.ReferenceRows;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 参照先の検索とラベルの1件読みの応答（Intent 261029-reference-lookup-cleanup の U1、契約 C2 の {@code ReferenceRows}）。ラベルの1件読み
 * では {@code rows} がちょうど1件、{@code page} は null、{@code hasNext} は false（functional-spec.md 2節）。文字列にするときは行の数
 * だけを出す（BR10.1）。
 *
 * @param rows 行
 * @param page 検索のページの番号（ラベルの1件読みでは null）
 * @param hasNext 次のページがあるか
 */
public record ReferenceRowsResponse(
        List<ReferenceRowResponse> rows,
        @JsonInclude(JsonInclude.Include.ALWAYS) Long page,
        boolean hasNext) {

    /**
     * 結果から作る。
     *
     * @param rows 結果
     * @return 応答
     */
    static ReferenceRowsResponse from(ReferenceRows rows) {
        return new ReferenceRowsResponse(
                rows.rows().stream().map(ReferenceRowResponse::from).toList(), rows.page(), rows.hasNext());
    }

    /** 行の数・ページ・次の有無だけを出す。 */
    @Override
    public String toString() {
        return "ReferenceRowsResponse[rows=" + (rows == null ? 0 : rows.size()) + ", page=" + page + ", hasNext="
                + hasNext + "]";
    }
}
