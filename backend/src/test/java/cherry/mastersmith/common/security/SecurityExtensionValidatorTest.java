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
package cherry.mastersmith.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

class SecurityExtensionValidatorTest {

    private record Contributor(int order) implements SecurityRuleContributor {

        @Override
        public void contribute(HttpSecurity http) {
            // テストでは並びだけを確かめるため、決まりは足さない。
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    @Test
    @DisplayName("contributors are sorted by ascending order")
    void sortsByOrder() {
        List<SecurityRuleContributor> sorted = SecurityExtensionValidator.sortedContributors(
                List.of(new Contributor(200), new Contributor(100), new Contributor(150)));

        assertThat(sorted).extracting(SecurityRuleContributor::getOrder).containsExactly(100, 150, 200);
    }

    @Test
    @DisplayName("duplicate order fails and names the duplicated value")
    void duplicateOrderFails() {
        assertThatThrownBy(() -> SecurityExtensionValidator.sortedContributors(
                        List.of(new Contributor(100), new Contributor(200), new Contributor(100))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[100]");
    }

    @Test
    @DisplayName("no contributors is accepted")
    void noContributors() {
        assertThat(SecurityExtensionValidator.sortedContributors(List.of())).isEmpty();
    }

    @Test
    @DisplayName("zero or one default access is accepted")
    void zeroOrOneDefaultAccess() {
        ApiDefaultAccess access = () -> true;

        assertThat(SecurityExtensionValidator.singleDefaultAccess(List.of())).isEmpty();
        assertThat(SecurityExtensionValidator.singleDefaultAccess(List.of(access)))
                .containsSame(access);
    }

    @Test
    @DisplayName("two or more default accesses fail")
    void twoDefaultAccessesFail() {
        assertThatThrownBy(() -> SecurityExtensionValidator.singleDefaultAccess(List.of(() -> true, () -> false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2");
    }
}
