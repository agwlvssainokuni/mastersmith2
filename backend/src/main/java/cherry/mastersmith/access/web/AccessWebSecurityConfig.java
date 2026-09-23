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
package cherry.mastersmith.access.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

/**
 * 要求の検査の拒否の処理を、Spring Security の連鎖の入口に差し込む（計画の C4 の第一案）。
 *
 * <p>拒否はフィルターの連鎖そのものの入口（{@code FilterChainProxy}）で起きるため、{@code SecurityRuleContributor} では
 * なく {@link WebSecurityCustomizer} で差し込む。U1 の {@code SecurityConfig} は変えない。
 */
@Configuration(proxyBeanMethods = false)
public class AccessWebSecurityConfig {

    /**
     * 要求の検査の拒否の処理を差し込む。
     *
     * @param handler 拒否の処理
     * @return 連鎖の入口の設定
     */
    @Bean
    public WebSecurityCustomizer accessRequestRejectedCustomizer(AccessRequestRejectedHandler handler) {
        return web -> web.requestRejectedHandler(handler);
    }
}
