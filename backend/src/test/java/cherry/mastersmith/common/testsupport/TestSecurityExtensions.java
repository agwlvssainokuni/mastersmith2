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
package cherry.mastersmith.common.testsupport;

import cherry.mastersmith.common.security.SecurityRuleContributor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 差し込み口の結合テストのための、U2・U3 の役の決まり（テストのソースの中だけに置く）。
 *
 * <p>それぞれ {@code mastersmith.test-fixture.*} を設定したテストだけで有効になり、ほかのテストの起動には入らない。
 */
public final class TestSecurityExtensions {

    /** 追加の決まりが呼ばれた順（order の値）。 */
    public static final List<Integer> CONTRIBUTION_ORDER = new CopyOnWriteArrayList<>();

    private TestSecurityExtensions() {}

    /** 指定した order で決まりを足す役。 */
    public abstract static class RecordingContributor implements SecurityRuleContributor {

        private final int order;

        protected RecordingContributor(int order) {
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void contribute(org.springframework.security.config.annotation.web.builders.HttpSecurity http)
                throws Exception {
            CONTRIBUTION_ORDER.add(order);
            addRules(http);
        }

        /**
         * 決まりを足す。
         *
         * @param http フィルターの連鎖の設定
         * @throws Exception 設定に失敗したとき
         */
        protected abstract void addRules(org.springframework.security.config.annotation.web.builders.HttpSecurity http)
                throws Exception;
    }

    /** U2（order 100）と U3（order 200）の役の決まり。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty("mastersmith.test-fixture.security-contributors")
    public static class Contributors {

        /**
         * order 100: 数値の窓口だけを許可する（order 200 の拒否より先に当たる）。
         *
         * @return 決まり
         */
        @Bean
        public SecurityRuleContributor permitNumberContributor() {
            return new RecordingContributor(100) {
                @Override
                protected void addRules(org.springframework.security.config.annotation.web.builders.HttpSecurity http)
                        throws Exception {
                    http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/test-fixture/number")
                            .permitAll());
                }
            };
        }

        /**
         * order 200: テスト用の窓口をすべて拒否する。
         *
         * @return 決まり
         */
        @Bean
        public SecurityRuleContributor denyFixtureContributor() {
            return new RecordingContributor(200) {
                @Override
                protected void addRules(org.springframework.security.config.annotation.web.builders.HttpSecurity http)
                        throws Exception {
                    http.authorizeHttpRequests(authorize ->
                            authorize.requestMatchers("/api/test-fixture/**").denyAll());
                }
            };
        }
    }
}
