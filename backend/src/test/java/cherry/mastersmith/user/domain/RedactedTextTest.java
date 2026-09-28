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
package cherry.mastersmith.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 伏せ字の文字列の単体テスト（U3 の計画の決定 3）。 */
class RedactedTextTest {

    @Test
    @DisplayName("the string form hides the value while the value stays readable")
    void hidesValue() {
        RedactedText text = new RedactedText("hanako@example.com");

        assertThat(text.value()).isEqualTo("hanako@example.com");
        assertThat(text.toString()).isEqualTo("***");
        assertThat(java.util.Optional.of(text).toString()).doesNotContain("hanako");
    }

    @Test
    @DisplayName("a null value is rejected")
    void rejectsNull() {
        assertThatThrownBy(() -> new RedactedText(null)).isInstanceOf(NullPointerException.class);
    }
}
