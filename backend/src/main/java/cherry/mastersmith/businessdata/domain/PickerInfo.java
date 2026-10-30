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

import java.util.List;

/**
 * LOOKUP のピッカーの情報（Intent 261029-reference-lookup-cleanup の U1、契約 C2、entities.md の PickerInfo）。参照先のスキーマ名・
 * テーブル名を持たない（BR3.6）。
 *
 * @param searchFields 使える検索の条件（1件以上。DSL の並び。BR3.2・BR5.1）
 * @param listColumns 一覧の列（1件以上。{@code order} の順。主権限が NONE のカラムを含めない。BR3.5・BR5.1）
 */
public record PickerInfo(List<PickerSearchField> searchFields, List<PickerListColumn> listColumns) {

    /** 一覧を変更できないものにし、どちらも1件以上であることを確かめる。 */
    public PickerInfo {
        searchFields = List.copyOf(searchFields);
        listColumns = List.copyOf(listColumns);
        if (searchFields.isEmpty() || listColumns.isEmpty()) {
            throw new IllegalArgumentException("searchFields と listColumns は1件以上です");
        }
    }
}
