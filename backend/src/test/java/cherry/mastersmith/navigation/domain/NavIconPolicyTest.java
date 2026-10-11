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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** アイコンの照らし合わせのテスト（BR4.2、NFR1.7）。 */
class NavIconPolicyTest {

    private static final NavIconPolicy POLICY = new NavIconPolicy(Set.of("list", "home", "chevron-down"));

    @ParameterizedTest
    @ValueSource(strings = {"home", "chevron-down", "list"})
    @DisplayName("an allowed name is returned as it is")
    void allowedNameIsKept(String icon) {
        assertThat(POLICY.iconOf(icon)).isEqualTo(icon);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Home", "HOME", " home", "home ", "\thome", "unknown", "<svg>", "javascript:alert(1)"})
    @DisplayName("null, empty, case differences, surrounding spaces and unknown names become list")
    void otherValuesBecomeList(String icon) {
        assertThat(POLICY.iconOf(icon)).isEqualTo("list");
    }

    @Test
    @DisplayName("the allowed names must contain list")
    void listIsRequired() {
        assertThatThrownBy(() -> new NavIconPolicy(Set.of("home"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(POLICY.allowed()).containsExactlyInAnyOrder("list", "home", "chevron-down");
    }
}
