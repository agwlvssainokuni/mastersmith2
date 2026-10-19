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
package cherry.mastersmith.businessdata.service;

import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import java.util.List;
import java.util.Objects;

/** 一覧と検索の API の結果（functional-spec.md の 3.2節）。 */
public sealed interface SearchResult permits SearchResult.Found, ReadRejected {

    /**
     * ページを返す。
     *
     * @param view 判定の材料
     * @param page ページの番号
     * @param size 1ページの件数
     * @param total 条件に合う全体の件数
     * @param items ページの行（最後のページを超えたら空）
     * @param definitionChanged 要求の定義の識別が今と違うなら true（BR9.1）
     */
    record Found(
            TableAccessView view, long page, int size, long total, List<RowRecord> items, boolean definitionChanged)
            implements SearchResult {

        /** 判定の材料が必須で、一覧を変更できないものにする。 */
        public Found {
            Objects.requireNonNull(view, "view は必須です");
            items = List.copyOf(items);
        }
    }
}
