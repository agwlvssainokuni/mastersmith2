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

import cherry.mastersmith.dsl.domain.OptionItem;
import java.util.List;
import java.util.Objects;

/**
 * カラム1つの検索の決まり（読み替えと演算子と型の表を当てた後。BR4.6・BR4.9）。
 *
 * @param mode 演算子
 * @param options CHOICE の選択肢（固定の選択肢、または真偽値の {@code true}・{@code false}。値は重ならない。ほかは空）
 */
public record SearchRule(SearchMode mode, List<OptionItem> options) {

    /** 演算子が必須で、CHOICE だけが選択肢を持つことを確かめる。 */
    public SearchRule {
        Objects.requireNonNull(mode, "mode は必須です");
        options = List.copyOf(options == null ? List.of() : options);
        if ((mode == SearchMode.CHOICE) == options.isEmpty()) {
            throw new IllegalArgumentException("選択肢は CHOICE だけが持ちます");
        }
    }

    /**
     * 選択肢の値を返す。
     *
     * @return 値（選択肢の順）
     */
    public List<String> choices() {
        return options.stream().map(OptionItem::value).toList();
    }
}
