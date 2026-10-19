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

import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.SearchMode;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.dsl.domain.DisplayName;
import java.util.List;

/**
 * 定義の応答の検索の項目（契約 C2 の {@code SearchField}、BR4.6・BR4.8・BR4.10）。
 *
 * @param column カラムの名前
 * @param displayName 表示名
 * @param operator 読み替えの後の演算子
 * @param valueType 受け渡しの型
 * @param defaultValue 既定の値（DSL のまま。無ければ null）
 * @param collapsed 折りたたみの欄に置くか
 * @param options CHOICE の選択肢（ほかは空）
 */
public record SearchFieldResponse(
        String column,
        DisplayName displayName,
        SearchMode operator,
        ValueType valueType,
        String defaultValue,
        boolean collapsed,
        List<OptionResponse> options) {

    /**
     * 検索に使えるカラムから作る。
     *
     * @param column カラム（{@link ColumnAccess#search()} があるもの）
     * @return 応答の項目
     */
    static SearchFieldResponse from(ColumnAccess column) {
        return new SearchFieldResponse(
                column.column(),
                column.displayName(),
                column.search().mode(),
                column.valueType(),
                column.searchDefault(),
                column.searchCollapsed(),
                column.search().options().stream().map(OptionResponse::from).toList());
    }
}
