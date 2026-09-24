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
 * 参照ピッカーの専用の一覧表示1つ（値）。
 *
 * @param column 参照先のカラムの物理名
 * @param order 並び順（1 から）
 */
public record LookupListItem(String column, int order) {

    /** カラムが必須で、並び順が 1 以上であることを確かめる。 */
    public LookupListItem {
        ModelValues.requireName(column, "options.lookupList.column");
        if (order < 1) {
            throw new IllegalArgumentException("options.lookupList.order は 1 以上です");
        }
    }
}
