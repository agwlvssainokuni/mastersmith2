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
package cherry.mastersmith.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.user.domain.SearchText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 要求の q を伏せ字の型に包む型の変換（BR1.3）の単体テスト。 */
class SearchTextConverterTest {

    private final SearchTextConverter converter = new SearchTextConverter();

    @Test
    @DisplayName("the raw value is wrapped as it is, without stripping or validating")
    void wrapsTheRawValue() {
        SearchText text = converter.convert("  Taro 太郎 ");

        assertThat(text).isEqualTo(new SearchText("  Taro 太郎 "));
        assertThat(text.toString()).doesNotContain("Taro").doesNotContain("太郎");
    }

    @Test
    @DisplayName("an empty or blank value is wrapped without an exception and means no search")
    void emptyAndBlank() {
        assertThat(converter.convert("")).isEqualTo(new SearchText(""));
        assertThat(converter.convert(" 　 ").isBlank()).isTrue();
    }

    @Test
    @DisplayName("a value longer than the limit is wrapped without an exception and is judged later")
    void tooLongIsNotRejectedHere() {
        String value = "x".repeat(SearchText.MAX_CODE_POINTS + 1);

        SearchText text = converter.convert(value);

        assertThat(text.isTooLong()).isTrue();
        assertThat(text.toString()).doesNotContain("xxx");
    }
}
