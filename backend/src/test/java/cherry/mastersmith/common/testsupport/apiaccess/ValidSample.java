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
package cherry.mastersmith.common.testsupport.apiaccess;

import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 違反の無い見本（{@code ApiAccessRulesTest} が読む）。規則が広げすぎて正しい口を落とさないことを確かめる。
 *
 * <p>クラスに印が無く、方法の印だけを持つ。設定値の置き換えの道の PUBLIC、管理者の道そのもの（{@code /api/admin}）の ADMIN、
 * {@code /api/} の下の AUTHENTICATED（管理者の道に似た名前の {@code /api/administrator} を含む。NFR1.2 の境界）を持つ。
 *
 * <p>決して設定しない設定の値の条件で、どの起動でも Bean にならない。本番の検査（テストのクラスを読まない）にも入らない。
 */
@RestController
@ConditionalOnBooleanProperty("mastersmith.test-fixture.api-access-samples")
public class ValidSample {

    /**
     * 設定値の置き換えの道（既定値 {@code /error}）の PUBLIC の口。
     *
     * @return 文字
     */
    @ApiAccess(ApiAccessLevel.PUBLIC)
    @RequestMapping(path = "${server.error.path:/error}", method = RequestMethod.GET)
    public String publicOnPlaceholderPath() {
        return "sample";
    }

    /**
     * 管理者の道そのものの ADMIN の口。
     *
     * @return 文字
     */
    @ApiAccess(ApiAccessLevel.ADMIN)
    @GetMapping("/api/admin")
    public String adminOnAdminRoot() {
        return "sample";
    }

    /**
     * {@code /api/} の下の AUTHENTICATED の口。
     *
     * @return 文字
     */
    @ApiAccess(ApiAccessLevel.AUTHENTICATED)
    @GetMapping("/api/sample")
    public String authenticatedUnderApi() {
        return "sample";
    }

    /**
     * 管理者の道に似た名前（管理者の道ではない）の AUTHENTICATED の口。
     *
     * @return 文字
     */
    @ApiAccess(ApiAccessLevel.AUTHENTICATED)
    @GetMapping("/api/administrator")
    public String authenticatedOnSimilarName() {
        return "sample";
    }
}
