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
package cherry.mastersmith.common.error.domain;

import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import java.util.Objects;

/**
 * 日本語と英語の両方を持つ文言（BR5.14、BR6.2）。
 *
 * @param ja 日本語の文言（空は不可）
 * @param en 英語の文言（空は不可）
 */
public record LocalizedText(String ja, String en) {

    /** 両方の言語の文言がそろっていることを確かめる。 */
    public LocalizedText {
        if (ja == null || ja.isBlank()) {
            throw new IllegalArgumentException("日本語の文言がありません");
        }
        if (en == null || en.isBlank()) {
            throw new IllegalArgumentException("英語の文言がありません");
        }
    }

    /**
     * 表示言語に応じた文言を返す。
     *
     * @param language 表示言語
     * @return 文言
     */
    public String in(DisplayLanguage language) {
        return Objects.requireNonNull(language) == DisplayLanguage.EN ? en : ja;
    }
}
