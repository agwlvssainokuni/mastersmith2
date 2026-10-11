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
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.navigation.domain.TableAccessResult;
import cherry.mastersmith.navigation.service.NavigationService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 業務のメニューの API（Intent 261004-role-menu の U5、契約 C9 を {@code functional-spec.md} の 8節の差で読んだ形、
 * {@code logical-components.md} の L1）。ログインした利用者だけ（{@link ApiAccessLevel#AUTHENTICATED}、BR6.1）。
 *
 * <ul>
 *   <li>{@code GET /api/me/navigation}: 200 と作業ロールで絞った業務のメニューの木（空のときは空の理由）
 *   <li>{@code GET /api/me/table-access?schema=&table=}: 200 と組・表示名・主権限、見られないときは 403 {@code ACCESS_DENIED}
 * </ul>
 *
 * <p>主体は要求の文脈の {@link AuthenticatedUser} の利用者 ID だけを読み、利用者を指す値を要求から受け取らない（BR1.1・NFR1.4）。置き場の
 * 引数は欠け・空のときだけ、どちらも {@code fieldErrors} の無い 400 {@code VALIDATION_FAILED} にする（BR5.1、計画の 13節 Q3: A）。空白
 * だけの名前は空と見なさず通常の判定にし（Q4: A）、長さと文字の種類では拒否しない。見られない結果は、DSL が無い・組が無い・NONE を区別
 * せず同じ 403 にする（BR5.2）。{@code ACCESS_DENIED} は access に依存せず、共通の問題の種類の一覧から code で起動時に引く（Q2: A）。
 * 監査に残さない（BR5.5・BR7.3）。
 */
@RestController
@ApiAccess(ApiAccessLevel.AUTHENTICATED)
@RequestMapping(NavigationController.PATH)
public class NavigationController {

    /** 自分の業務のメニューの API の道の頭。 */
    public static final String PATH = "/api/me";

    /** 置き場の引数のスキーマの名前。 */
    public static final String SCHEMA = "schema";

    /** 置き場の引数のテーブルの名前。 */
    public static final String TABLE = "table";

    /** 403 の問題の種類の code（{@code access} の一覧にある）。 */
    static final String ACCESS_DENIED_CODE = "ACCESS_DENIED";

    private final NavigationService service;

    private final ProblemType accessDenied;

    /**
     * 作る。
     *
     * @param service 業務のメニューの業務処理
     * @param registry 問題の種類の一覧
     * @throws IllegalStateException 403 の問題の種類が一覧に無いとき（起動を止める）
     */
    public NavigationController(NavigationService service, ProblemTypeRegistry registry) {
        this.service = service;
        this.accessDenied = registry.findByCode(ACCESS_DENIED_CODE)
                .orElseThrow(
                        () -> new IllegalStateException("問題の種類 " + ACCESS_DENIED_CODE + " が一覧にありません（認可の機能の定義が要ります）"));
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

    /**
     * テーブルの置き場の権限を問い合わせる。
     *
     * @param schema スキーマの名前
     * @param table テーブルの名前
     * @param user 要求の文脈の主体
     * @return 組・表示名・主権限
     * @throws BusinessException 引数が欠け・空のとき（400 {@code VALIDATION_FAILED}）、見られないとき（403 {@code ACCESS_DENIED}）
     */
    @GetMapping(path = "/table-access", produces = MediaType.APPLICATION_JSON_VALUE)
    public TableAccessResponse tableAccess(
            @RequestParam(name = SCHEMA, required = false) String schema,
            @RequestParam(name = TABLE, required = false) String table,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (isMissing(schema) || isMissing(table)) {
            throw new BusinessException(CommonProblemTypes.VALIDATION_FAILED);
        }
        return switch (service.tableAccess(user.userId(), schema, table)) {
            case TableAccessResult.Visible visible -> TableAccessResponse.from(visible);
            case TableAccessResult.NotVisible notVisible -> throw new BusinessException(accessDenied);
        };
    }

    /** 欠け・空だけを入力の誤りにする（空白だけの名前は空と見なさない）。 */
    private static boolean isMissing(String value) {
        return value == null || value.isEmpty();
    }
}
