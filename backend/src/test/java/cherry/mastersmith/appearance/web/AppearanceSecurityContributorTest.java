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
package cherry.mastersmith.appearance.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.security.SecurityExtensionValidator;
import cherry.mastersmith.common.security.SecurityRuleContributor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/** U8 の公開の決まりの order（NFR4.7）。割り当ては機能の名前で 100 台ずつで、appearance は 400 台。 */
class AppearanceSecurityContributorTest {

    /** 本番の値（auth 110・access 210）、テストの決まりの値（100・150・200・250）、invitation の予定の値（310）。 */
    private static final List<Integer> OTHER_ORDERS = List.of(100, 110, 150, 200, 210, 250, 310);

    private static SecurityRuleContributor stub(int order) {
        return new SecurityRuleContributor() {
            @Override
            public int getOrder() {
                return order;
            }

            @Override
            public void contribute(HttpSecurity http) {
                // 並びの確かめだけに使うため、何も足さない。
            }
        };
    }

    @Test
    @DisplayName("the order is 410 in the range 400 to 499 of appearance and is not a test rule value")
    void orderIs410() {
        AppearanceSecurityContributor contributor = new AppearanceSecurityContributor();

        assertThat(contributor.getOrder()).isEqualTo(410).isEqualTo(AppearanceSecurityContributor.ORDER);
        assertThat(contributor.getOrder()).isBetween(400, 499);
        assertThat(contributor.getOrder() % 50).as("x00・x50 はテストの決まりが使う").isNotZero();
    }

    @Test
    @DisplayName("together with the existing, test and planned orders it does not collide and comes last")
    void sortsAfterEveryOtherContributor() {
        AppearanceSecurityContributor contributor = new AppearanceSecurityContributor();
        List<SecurityRuleContributor> contributors = new ArrayList<>();
        contributors.add(contributor);
        OTHER_ORDERS.forEach(order -> contributors.add(stub(order)));

        List<SecurityRuleContributor> sorted = SecurityExtensionValidator.sortedContributors(contributors);

        assertThat(sorted)
                .extracting(SecurityRuleContributor::getOrder)
                .containsExactly(100, 110, 150, 200, 210, 250, 310, 410);
        assertThat(sorted.getLast()).isSameAs(contributor);
    }

    @Test
    @DisplayName("a second contributor with the same order stops the start")
    void duplicateOrderIsRejected() {
        List<SecurityRuleContributor> contributors =
                List.of(new AppearanceSecurityContributor(), stub(110), stub(AppearanceSecurityContributor.ORDER));

        assertThatThrownBy(() -> SecurityExtensionValidator.sortedContributors(contributors))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("410");
    }
}
