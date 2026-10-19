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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 部分一致のエスケープ（BR4.3）の単体テスト。 */
class ContainsPatternTest {

    @Test
    @DisplayName("percent signs are escaped with an exclamation mark")
    void percent() {
        assertThat(ContainsPattern.of("50%")).isEqualTo("%50!%%");
    }

    @Test
    @DisplayName("underscores are escaped with an exclamation mark")
    void underscore() {
        assertThat(ContainsPattern.of("a_b")).isEqualTo("%a!_b%");
    }

    @Test
    @DisplayName("the escape character itself is doubled first")
    void escapeCharacter() {
        assertThat(ContainsPattern.of("!%")).isEqualTo("%!!!%%");
    }

    @Test
    @DisplayName("backslashes, quotes and semicolons stay as they are")
    void otherCharacters() {
        assertThat(ContainsPattern.of("\\'\";--")).isEqualTo("%\\'\";--%");
    }

    @Test
    @DisplayName("an empty value matches everything and null is rejected")
    void emptyAndNull() {
        assertThat(ContainsPattern.of("")).isEqualTo("%%");
        assertThatThrownBy(() -> ContainsPattern.of(null)).isInstanceOf(NullPointerException.class);
    }
}
