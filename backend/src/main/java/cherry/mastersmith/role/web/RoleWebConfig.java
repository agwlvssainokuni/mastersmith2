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
package cherry.mastersmith.role.web;

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.web.RequestBodyLimitRoute;
import cherry.mastersmith.role.service.RoleTransferLimits;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ロールの画面入出力の設定（{@code logical-components.md} の L13、BR9.4・NFR2.9、計画の D-21）。権限の YAML の確かめと適用の道だけの本文の
 * 上限を共通の本文の上限の仕組みに渡し（超えたら 413 {@code PAYLOAD_TOO_LARGE}）、同じ値を業務処理の上限（上限つきの読み込みと、分割
 * 送信の本文を読む上限）にする。ほかの道は既定の上限のまま。{@code common.web} の本体は変えない。
 */
@Configuration(proxyBeanMethods = false)
public class RoleWebConfig {

    /**
     * 業務処理の上限。
     *
     * @param properties 上限の設定値
     * @return 上限
     */
    @Bean
    public static RoleTransferLimits roleTransferLimits(RoleTransferLimitProperties properties) {
        return properties.toLimits();
    }

    /**
     * 確かめの道（{@code POST /api/admin/role-transfer/check}）の本文の上限。
     *
     * @param properties 上限の設定値
     * @return 道ごとの本文の上限
     */
    @Bean
    public static RequestBodyLimitRoute roleTransferCheckBodyLimitRoute(RoleTransferLimitProperties properties) {
        return new RequestBodyLimitRoute(
                "POST", RoleTransferPaths.CHECK, properties.maxBytes(), CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }

    /**
     * 適用の道（{@code POST /api/admin/role-transfer/apply}）の本文の上限。
     *
     * @param properties 上限の設定値
     * @return 道ごとの本文の上限
     */
    @Bean
    public static RequestBodyLimitRoute roleTransferApplyBodyLimitRoute(RoleTransferLimitProperties properties) {
        return new RequestBodyLimitRoute(
                "POST", RoleTransferPaths.APPLY, properties.maxBytes(), CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }
}
