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
package cherry.mastersmith.navigation.domain;

import cherry.mastersmith.dsl.domain.DisplayName;
import java.util.Objects;

/**
 * 表示名の組（{@code entities.md} の ENT-004、BR3.2）。DSL の値をそのまま持ち、エスケープも空白の除去もしない。空の文字列（未設定）を
 * 許す。
 *
 * @param ja 日本語の表示名
 * @param en 英語の表示名
 */
public record DisplayLabel(String ja, String en) {

    /** 両方が null でないことを確かめる。 */
    public DisplayLabel {
        Objects.requireNonNull(ja, "ja は必須です");
        Objects.requireNonNull(en, "en は必須です");
    }

    /**
     * DSL の表示名から作る（値をそのまま写す）。
     *
     * @param name DSL の表示名
     * @return 表示名の組
     */
    public static DisplayLabel of(DisplayName name) {
        Objects.requireNonNull(name, "name は必須です");
        return new DisplayLabel(name.ja(), name.en());
    }
}
