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
package cherry.mastersmith.auth.testsupport;

import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.common.security.SecurityRuleContributor;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ログインを求めるテスト用の窓口（テストのソースの中だけに置く）。{@code mastersmith.test-fixture.auth-protected=true} を設定した
 * テストだけで有効になる。
 */
@RestController
@ConditionalOnBooleanProperty("mastersmith.test-fixture.auth-protected")
public class ProtectedTestEndpoint {

    /** テスト用の窓口のパス。 */
    public static final String PATH = "/api/test-fixture/auth/me";

    /**
     * 検証済みの利用者を返す。
     *
     * @param user 検証済みの利用者
     * @return 利用者の項目
     */
    @GetMapping(PATH)
    public Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("userId", user.userId(), "email", user.email(), "admin", user.admin());
    }

    /** テスト用の窓口にログインを求める決まり（order 150）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty("mastersmith.test-fixture.auth-protected")
    public static class Rules {

        /**
         * テスト用の窓口にログインを求める。
         *
         * @return 決まり
         */
        @Bean
        public SecurityRuleContributor protectedTestEndpointRule() {
            return new SecurityRuleContributor() {
                @Override
                public void contribute(org.springframework.security.config.annotation.web.builders.HttpSecurity http)
                        throws Exception {
                    http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/test-fixture/auth/**")
                            .authenticated());
                }

                @Override
                public int getOrder() {
                    return 150;
                }
            };
        }
    }
}
