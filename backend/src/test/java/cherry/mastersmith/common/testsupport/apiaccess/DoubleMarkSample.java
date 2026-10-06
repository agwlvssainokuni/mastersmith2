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
import org.springframework.web.bind.annotation.RestController;

/**
 * 違反の見本: クラスと方法の両方に印がある口（{@code ApiAccessRulesTest} が読む。NFR1.1）。
 *
 * <p>決して設定しない設定の値の条件で、どの起動でも Bean にならない。本番の検査（テストのクラスを読まない）にも入らない。
 */
@RestController
@ApiAccess(ApiAccessLevel.AUTHENTICATED)
@ConditionalOnBooleanProperty("mastersmith.test-fixture.api-access-samples")
public class DoubleMarkSample {

    /**
     * 印が二重の口。
     *
     * @return 文字
     */
    @ApiAccess(ApiAccessLevel.AUTHENTICATED)
    @GetMapping("/api/sample/double-mark")
    public String doubleMark() {
        return "sample";
    }
}
