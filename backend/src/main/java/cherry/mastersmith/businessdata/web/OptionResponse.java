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

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.OptionItem;

/**
 * 選択肢1つ（契約 C2 の {@code Option}）。
 *
 * @param value 値
 * @param label 表示名
 */
public record OptionResponse(String value, DisplayName label) {

    /**
     * DSL の選択肢から作る。
     *
     * @param item 選択肢
     * @return 応答の選択肢
     */
    static OptionResponse from(OptionItem item) {
        return new OptionResponse(item.value(), item.label());
    }
}
