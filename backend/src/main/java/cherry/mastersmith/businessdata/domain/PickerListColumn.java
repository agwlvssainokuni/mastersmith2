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
 * ピッカーの一覧の列1つ（Intent 261029-reference-lookup-cleanup の U1、契約 C2、entities.md の PickerListColumn）。
 *
 * @param column 参照先のカラムの名前
 * @param displayName 参照先の DSL の表示名
 * @param valueType そのカラムの受け渡しの型（BR5.1）
 */
public record PickerListColumn(String column, DisplayName displayName, ValueType valueType) {

    /** 必須の値を確かめる。 */
    public PickerListColumn {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(displayName, "displayName は必須です");
        Objects.requireNonNull(valueType, "valueType は必須です");
    }
}
