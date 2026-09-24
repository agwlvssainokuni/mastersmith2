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
package cherry.mastersmith.dsl.domain;

import java.util.List;
import java.util.Objects;

/**
 * 選択肢の出どころ（値）。種類ごとに使う項目が決まり、使わない項目は null か空。
 *
 * @param source 種類
 * @param items 固定の選択肢（{@code FIXED} のとき1件以上。ほかは空）
 * @param table 参照先のテーブルの物理名（{@code REFERENCE}・{@code LOOKUP} のとき。ほかは null）
 * @param valueColumn 値のカラム（{@code REFERENCE}・{@code LOOKUP} のとき。ほかは null）
 * @param labelColumn 表示のカラム（{@code REFERENCE} のとき必須。ほかは null を許す）
 * @param lookupSearch 参照ピッカーの専用の検索条件（{@code LOOKUP} のとき1件以上。ほかは空）
 * @param lookupList 参照ピッカーの専用の一覧表示（{@code LOOKUP} のとき1件以上。ほかは空）
 */
public record OptionSource(
        OptionSourceKind source,
        List<OptionItem> items,
        String table,
        String valueColumn,
        String labelColumn,
        List<LookupSearchItem> lookupSearch,
        List<LookupListItem> lookupList) {

    /** 種類ごとに必須の項目があることを確かめ、一覧を変更できないものにする。 */
    public OptionSource {
        Objects.requireNonNull(source, "options.source は必須です");
        items = ModelValues.copyList(items, "options.items");
        lookupSearch = ModelValues.copyList(lookupSearch, "options.lookupSearch");
        lookupList = ModelValues.copyList(lookupList, "options.lookupList");
        switch (source) {
            case FIXED -> {
                if (items.isEmpty()) {
                    throw new IllegalArgumentException("FIXED の options.items は1件以上です");
                }
            }
            case REFERENCE -> {
                requireReference(table, valueColumn);
                ModelValues.requireName(labelColumn, "options.labelColumn");
            }
            case LOOKUP -> {
                requireReference(table, valueColumn);
                if (lookupSearch.isEmpty() || lookupList.isEmpty()) {
                    throw new IllegalArgumentException("LOOKUP の lookupSearch と lookupList は1件以上です");
                }
            }
        }
    }

    private static void requireReference(String table, String valueColumn) {
        ModelValues.requireName(table, "options.table");
        ModelValues.requireName(valueColumn, "options.valueColumn");
    }
}
