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

/**
 * 検索条件（値）。
 *
 * @param enabled 検索条件に出すなら true
 * @param operator 演算子（{@code enabled} が true のとき必須。無ければ null）
 * @param defaultValue 既定の値（DSL の {@code default}。無ければ null）
 * @param collapsed 折りたたんだ欄に置くなら true
 */
public record SearchSetting(boolean enabled, SearchOperator operator, String defaultValue, boolean collapsed) {

    /** 検索条件に出すときは演算子があることを確かめる。 */
    public SearchSetting {
        if (enabled && operator == null) {
            throw new IllegalArgumentException("search.operator は enabled が true のとき必須です");
        }
    }
}
