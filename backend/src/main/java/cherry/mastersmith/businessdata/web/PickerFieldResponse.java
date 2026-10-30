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

import cherry.mastersmith.businessdata.domain.PickerListColumn;
import cherry.mastersmith.businessdata.domain.PickerSearchField;
import cherry.mastersmith.businessdata.domain.SearchMode;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.dsl.domain.DisplayName;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ピッカーの検索の条件・一覧の列1つの応答（Intent 261029-reference-lookup-cleanup の U1、契約 C2、BR5.1）。一覧の列は演算子を持たない。
 *
 * @param column 参照先のカラムの名前
 * @param displayName 参照先の DSL の表示名
 * @param operator 演算子（検索の条件だけ。{@code EQUALS} か {@code CONTAINS}）
 * @param valueType 受け渡しの型
 */
public record PickerFieldResponse(
        String column,
        DisplayName displayName,
        @JsonInclude(JsonInclude.Include.NON_NULL) SearchMode operator,
        ValueType valueType) {

    /**
     * 検索の条件から作る。
     *
     * @param field 検索の条件
     * @return 応答
     */
    static PickerFieldResponse from(PickerSearchField field) {
        return new PickerFieldResponse(field.column(), field.displayName(), field.operator(), field.valueType());
    }

    /**
     * 一覧の列から作る。
     *
     * @param column 一覧の列
     * @return 応答
     */
    static PickerFieldResponse from(PickerListColumn column) {
        return new PickerFieldResponse(column.column(), column.displayName(), null, column.valueType());
    }
}
