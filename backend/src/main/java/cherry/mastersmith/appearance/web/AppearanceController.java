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
package cherry.mastersmith.appearance.web;

import cherry.mastersmith.appearance.service.AppearanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * インスタンスの見た目の設定を返す API（{@code GET /api/appearance}、契約 C7、BR3.1）。
 *
 * <p>ログインなしで読める（{@link AppearanceSecurityContributor}）。起動時に解決した値を返すだけで、業務のエラーを返さず、要求ごとの
 * ログと監査の出来事を出さない（BR3.5）。GET 以外のメソッドは受け付けず、既存の共通の扱い（401・405）に任せる（BR3.2）。
 */
@RestController
public class AppearanceController {

    /** API のパス。 */
    public static final String PATH = "/api/appearance";

    private final AppearanceService appearanceService;

    /**
     * 作る。
     *
     * @param appearanceService 見た目の設定
     */
    public AppearanceController(AppearanceService appearanceService) {
        this.appearanceService = appearanceService;
    }

    /**
     * 見た目の設定を返す。
     *
     * @return 2項目の応答（200）
     */
    @GetMapping(PATH)
    public AppearanceResponse get() {
        return AppearanceResponse.from(appearanceService.current());
    }
}
