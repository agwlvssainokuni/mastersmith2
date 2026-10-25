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
package cherry.mastersmith.navigation.web;

import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.navigation.service.NavigationService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 業務のメニューの API（Intent 261004-role-menu の U5、契約 C9 を {@code functional-spec.md} の 8節の差で読んだ形、
 * {@code logical-components.md} の L1）。ログインした利用者だけ（{@link ApiAccessLevel#AUTHENTICATED}、BR6.1）。
 *
 * <ul>
 *   <li>{@code GET /api/me/navigation}: 200 と作業ロールで絞った業務のメニューの木（空のときは空の理由）
 * </ul>
 *
 * <p>主体は要求の文脈の {@link AuthenticatedUser} の利用者 ID だけを読み、利用者を指す値を要求から受け取らない（BR1.1・NFR1.4）。監査に
 * 残さない（BR5.5・BR7.3）。テーブルの置き場を問い合わせる {@code GET /api/me/table-access} は、画面が呼ばなくなった（業務データの API
 * の定義が置き場の権限を返す）ため、Intent 261025-deps-perf-carryover の FR3.3 で削除した。
 */
@RestController
@ApiAccess(ApiAccessLevel.AUTHENTICATED)
@RequestMapping(NavigationController.PATH)
public class NavigationController {

    /** 自分の業務のメニューの API の道の頭。 */
    public static final String PATH = "/api/me";

    private final NavigationService service;

    /**
     * 作る。
     *
     * @param service 業務のメニューの業務処理
     */
    public NavigationController(NavigationService service) {
        this.service = service;
    }

    /**
     * 自分の業務のメニューを読む。
     *
     * @param user 要求の文脈の主体
     * @return 絞った木と空の理由
     */
    @GetMapping(path = "/navigation", produces = MediaType.APPLICATION_JSON_VALUE)
    public NavigationResponse navigation(@AuthenticationPrincipal AuthenticatedUser user) {
        return NavigationResponse.from(service.menu(user.userId()));
    }
}
