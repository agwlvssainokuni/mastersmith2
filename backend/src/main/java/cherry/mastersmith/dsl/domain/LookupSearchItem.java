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

import java.util.Objects;

/**
 * 参照ピッカーの専用の検索条件1つ（値）。
 *
 * @param column 参照先のカラムの物理名
 * @param operator 演算子
 */
public record LookupSearchItem(String column, SearchOperator operator) {

    /** カラムと演算子が必須であることを確かめる。 */
    public LookupSearchItem {
        ModelValues.requireName(column, "options.lookupSearch.column");
        Objects.requireNonNull(operator, "options.lookupSearch.operator は必須です");
    }
}
