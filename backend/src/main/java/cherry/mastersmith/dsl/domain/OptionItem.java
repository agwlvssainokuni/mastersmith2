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
 * 固定の選択肢1つ（値）。
 *
 * @param value 値
 * @param label 表示名
 */
public record OptionItem(String value, DisplayName label) {

    /** 値と表示名が必須であることを確かめる。 */
    public OptionItem {
        Objects.requireNonNull(value, "options.items.value は必須です");
        Objects.requireNonNull(label, "options.items.label は必須です");
    }
}
