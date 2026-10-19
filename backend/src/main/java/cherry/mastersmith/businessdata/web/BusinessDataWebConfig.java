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
 * 業務データの読み取りと書き込みの API の画面入出力の設定（NFR4.5、security-design.md の 6節。U2 の NFR4.9）。読み取りの3本と削除の道ごとに
 * 本文の上限 64 KB、作成・更新の道に 4 MiB を共通の本文の上限の仕組みに渡す（超えたら 413 {@code PAYLOAD_TOO_LARGE}。認証と認可の後、コントローラーの前に確かめるため、413 が 400 より先に出る）。
 * {@code common.web} の本体は変えない。
 */
@Configuration(proxyBeanMethods = false)
public class BusinessDataWebConfig {

    /** 本文の上限（バイト。読み取りの3本と削除）。 */
    public static final long MAX_BODY_BYTES = 65_536L;

    /** 作成・更新の本文の上限（バイト。4 MiB。U2 の NFR4.9、security-design.md の 6節）。 */
    public static final long MAX_WRITE_BODY_BYTES = 4_194_304L;

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

    /**
     * 作成の道の本文の上限（4 MiB。U2）。
     *
     * @return 道ごとの本文の上限
     */
    @Bean
    public static RequestBodyLimitRoute businessDataCreateBodyLimitRoute() {
        return new RequestBodyLimitRoute(
                "POST", BusinessDataPaths.ROWS, MAX_WRITE_BODY_BYTES, CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }

    /**
     * 更新の道の本文の上限（4 MiB。U2）。
     *
     * @return 道ごとの本文の上限
     */
    @Bean
    public static RequestBodyLimitRoute businessDataUpdateBodyLimitRoute() {
        return new RequestBodyLimitRoute(
                "PATCH", BusinessDataPaths.ROWS, MAX_WRITE_BODY_BYTES, CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }

    /**
     * 削除の道の本文の上限（64 KB。U2）。
     *
     * @return 道ごとの本文の上限
     */
    @Bean
    public static RequestBodyLimitRoute businessDataDeleteBodyLimitRoute() {
        return new RequestBodyLimitRoute(
                "POST", BusinessDataPaths.DELETE, MAX_BODY_BYTES, CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }
}
