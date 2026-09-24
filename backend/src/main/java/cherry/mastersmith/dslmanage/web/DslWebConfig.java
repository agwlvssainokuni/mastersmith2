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
package cherry.mastersmith.dslmanage.web;

import cherry.mastersmith.common.web.RequestBodyLimitRoute;
import cherry.mastersmith.dslmanage.domain.DslProblemTypes;
import cherry.mastersmith.dslmanage.service.DslManageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * DSL の管理の画面入出力の設定（U4）。投入の API だけの本文の上限（決定 A、NFR2.6）を共通の本文の上限の仕組みに渡し、重い道の
 * 同時の数の制限（{@link DslHeavyOperationGate}）を DSL の API の下に置く。
 */
@Configuration(proxyBeanMethods = false)
public class DslWebConfig implements WebMvcConfigurer {

    private final DslHeavyOperationGate heavyOperationGate;

    /**
     * 作る。
     *
     * @param heavyOperationGate 重い道の同時の数の制限
     */
    public DslWebConfig(DslHeavyOperationGate heavyOperationGate) {
        this.heavyOperationGate = heavyOperationGate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(heavyOperationGate).addPathPatterns(DslAdminPaths.ROOT + "/**");
    }

    /**
     * 投入の API（{@code POST /api/admin/dsl/preview}）の本文の上限（{@code mastersmith.dsl.max-submit-size}、既定 10MB）と、
     * 超えたときの code（{@code DSL_TOO_LARGE}）。ほかの API は既定の上限（1MB）のまま。
     *
     * @param properties DSL の管理の設定
     * @return 道ごとの本文の上限
     */
    @Bean
    public static RequestBodyLimitRoute dslSubmitBodyLimitRoute(DslManageProperties properties) {
        return new RequestBodyLimitRoute(
                "POST", DslAdminPaths.PREVIEW, properties.maxSubmitSize().toBytes(), DslProblemTypes.DSL_TOO_LARGE);
    }
}
