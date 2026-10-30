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

import cherry.mastersmith.dsl.domain.DisplayName;
import java.util.Objects;

/**
 * ピッカーの検索の条件1つ（Intent 261029-reference-lookup-cleanup の U1、契約 C2、entities.md の PickerSearchField）。使える条件
 * （BR3.2）だけを作る。
 *
 * @param column 参照先のカラムの名前
 * @param displayName 参照先の DSL の表示名
 * @param operator 演算子（{@code EQUALS} か {@code CONTAINS}）
 * @param valueType そのカラムの受け渡しの型（BR5.1）
 */
public record PickerSearchField(String column, DisplayName displayName, SearchMode operator, ValueType valueType) {

    /** 必須の値と、演算子が EQUALS・CONTAINS（CONTAINS は文字の型だけ）であることを確かめる（BR3.2）。 */
    public PickerSearchField {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(displayName, "displayName は必須です");
        Objects.requireNonNull(operator, "operator は必須です");
        Objects.requireNonNull(valueType, "valueType は必須です");
        if (operator != SearchMode.EQUALS && operator != SearchMode.CONTAINS) {
            throw new IllegalArgumentException("ピッカーの演算子は EQUALS か CONTAINS です");
        }
        if (operator == SearchMode.CONTAINS && valueType != ValueType.STRING) {
            throw new IllegalArgumentException("CONTAINS は文字の型だけです");
        }
    }
}
