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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.web.RequestBodyLimitRoute;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 業務データの読み取りの API の画面入出力の設定（NFR4.5、security-design.md の 6節）。3本の道ごとに本文の上限 64 KB を共通の本文の上限の
 * 仕組みに渡す（超えたら 413 {@code PAYLOAD_TOO_LARGE}。認証と認可の後、コントローラーの前に確かめるため、413 が 400 より先に出る）。
 * {@code common.web} の本体は変えない。
 */
@Configuration(proxyBeanMethods = false)
public class BusinessDataWebConfig {

    /** 本文の上限（バイト）。 */
    public static final long MAX_BODY_BYTES = 65_536L;

    /**
     * 定義の道の本文の上限。
     *
     * @return 道ごとの本文の上限
     */
    @Bean
    public static RequestBodyLimitRoute businessDataDefinitionBodyLimitRoute() {
        return new RequestBodyLimitRoute(
                "POST", BusinessDataPaths.DEFINITION, MAX_BODY_BYTES, CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }

    /**
     * 一覧と検索の道の本文の上限。
     *
     * @return 道ごとの本文の上限
     */
    @Bean
    public static RequestBodyLimitRoute businessDataSearchBodyLimitRoute() {
        return new RequestBodyLimitRoute(
                "POST", BusinessDataPaths.SEARCH, MAX_BODY_BYTES, CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }

    /**
     * 詳細の道の本文の上限。
     *
     * @return 道ごとの本文の上限
     */
    @Bean
    public static RequestBodyLimitRoute businessDataGetBodyLimitRoute() {
        return new RequestBodyLimitRoute(
                "POST", BusinessDataPaths.GET, MAX_BODY_BYTES, CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }
}
