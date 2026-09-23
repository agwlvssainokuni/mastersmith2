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
package cherry.mastersmith.access.testsupport;

import cherry.mastersmith.common.security.SecurityRuleContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * U3 の既定の拒否（{@code /api/**} はログイン必須）が効く前の状態を、テストの中だけで再現する決まり（計画の C7・D1）。
 *
 * <p>{@code mastersmith.test-fixture.public-api=true} を設定したテストだけで有効になる。U1 の既存の結合テスト（エラー応答の形・
 * トレース・ヘッダー・公開する範囲の確認）が、アクセス制御ではなく本来の対象を確かめ続けられるようにするためのもの。
 *
 * <p>order は 250（U3 の {@code AdminSecurityContributor} の 210 より後）。したがって {@code /api/admin} と
 * {@code /api/admin/**} の管理者のみの決まりは、この設定を入れても緩まない。本番の設定にはこの仕組みが無く、既定の拒否を
 * 切り替えることはできない。
 */
public final class PublicApiTestRules {

    /** この決まりの順番。 */
    public static final int ORDER = 250;

    private PublicApiTestRules() {}

    /** テストの中だけで {@code /api/**} を公開にする決まり。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty("mastersmith.test-fixture.public-api")
    public static class Rules {

        /**
         * {@code /api/**} を公開にする（管理者のみの決まりより後に当たる）。
         *
         * @return 決まり
         */
        @Bean
        public SecurityRuleContributor publicApiTestContributor() {
            return new SecurityRuleContributor() {
                @Override
                public void contribute(HttpSecurity http) throws Exception {
                    http.authorizeHttpRequests(
                            authorize -> authorize.requestMatchers("/api/**").permitAll());
                }

                @Override
                public int getOrder() {
                    return ORDER;
                }
            };
        }
    }
}
