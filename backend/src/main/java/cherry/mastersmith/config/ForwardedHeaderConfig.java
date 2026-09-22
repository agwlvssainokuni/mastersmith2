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
package cherry.mastersmith.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * 転送元のヘッダー（X-Forwarded-*・Forwarded）の扱い（BR5.10、NFR3.8）。
 *
 * <p>{@code mastersmith.web.trust-forwarded-headers=true} のときだけ、Spring の {@link ForwardedHeaderFilter} を最初に通る
 * フィルターとして登録し、要求のスキーム・Host・ポート・接続元に反映する。既定では登録せず、転送元のヘッダーは使わない。
 * プロキシの内側からだけ受け付ける配備でだけ有効にする。U2・U3 の接続元IPも同じ方針に乗る。
 */
@Configuration(proxyBeanMethods = false)
public class ForwardedHeaderConfig {

    /**
     * 転送元のヘッダーを要求に反映するフィルターを登録する。
     *
     * @return フィルターの登録
     */
    @Bean
    @ConditionalOnBooleanProperty("mastersmith.web.trust-forwarded-headers")
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
