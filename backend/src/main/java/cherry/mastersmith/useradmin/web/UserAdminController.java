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
package cherry.mastersmith.useradmin.web;

import cherry.mastersmith.access.domain.AccessProblemTypes;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.service.ProfileUpdateResult;
import cherry.mastersmith.useradmin.domain.UserAdminProblemTypes;
import cherry.mastersmith.useradmin.service.OperationResult;
import cherry.mastersmith.useradmin.service.UserAdminListResult;
import cherry.mastersmith.useradmin.service.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 利用者の管理の API（Intent 260930-user-admin の U3、契約 C3、管理者だけ。BR7.1）。既存の {@code /api/admin/**} の決まり（未認証 401・
 * 管理者でない 403）にそのまま乗り、新しい公開の決まりを足さない。
 *
 * <ul>
 *   <li>{@code GET /api/admin/users?page=&q=}: 200 と1ページ（page は文字列、q は伏せ字の型 {@link SearchText} で受ける。BR1.3）
 *   <li>{@code PUT /api/admin/users/{userId}/profile}: 204（本文なし）
 *   <li>{@code POST /api/admin/users/{userId}/grant-admin}・{@code revoke-admin}・{@code suspend}・{@code resume}・
 *       {@code reset-login-failures}: 204（本文なし。B4 で足した5つの操作）
 * </ul>
 *
 * <p>業務処理の結果の型を場合を尽くす {@code switch} で応答か業務エラーにし、応答は共通の変換（{@code @RestControllerAdvice} の1か所）で
 * 作る: 400 VALIDATION_FAILED（page の誤りは項目なし、q と氏名・言語の誤りは {@code fieldErrors}）、404 USER_NOT_FOUND、5つの操作の
 * 業務の拒否は理由の code（409）、操作した人の確かめ直しで外れたときは 403 ACCESS_DENIED、行の排他の待ちの上限切れは 409
 * USER_ADMIN_BUSY（原因をつながない）。{@code userId} が整数でないときは既存の型の誤りの扱いで 400 になる（BR2.7）。
 */
@RestController
@RequestMapping(UserAdminController.PATH)
public class UserAdminController {

    /** 利用者の管理の API の道。 */
    public static final String PATH = "/api/admin/users";

    private final UserAdminService service;

    private final UserAdminRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service 利用者の管理の業務処理
     * @param context 要求の文脈の読み取り
     */
    public UserAdminController(UserAdminService service, UserAdminRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * 利用者の一覧を読む（BR1.1〜BR1.9）。
     *
     * @param page ページ（無ければ 1）
     * @param q 検索の文字（無ければ検索なし）
     * @return 1ページ
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminUserPage list(
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "q", required = false) SearchText q) {
        long actor = context.currentUserId();
        return switch (service.list(actor, page, q)) {
            case UserAdminListResult.Listed listed -> AdminUserPage.from(listed.page());
            case UserAdminListResult.InvalidPage _ -> throw new BusinessException(CommonProblemTypes.VALIDATION_FAILED);
            case UserAdminListResult.Invalid invalid -> throw UserAdminFieldErrors.validationFailed(invalid.errors());
        };
    }

    /**
     * 利用者の氏名と言語を変える（BR5.1〜BR5.4）。入力の誤りは対象の有無より先に返す（BR2.7）。
     *
     * @param userId 対象の利用者 ID
     * @param body 氏名と言語
     * @return 204
     */
    @PutMapping(path = "/{userId}/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateProfile(@PathVariable("userId") long userId, @RequestBody ProfileRequest body) {
        return switch (service.updateProfile(userId, body.toCommand())) {
            case ProfileUpdateResult.Updated _ -> ResponseEntity.noContent().build();
            case ProfileUpdateResult.Invalid invalid -> throw UserAdminFieldErrors.validationFailed(invalid.errors());
            case ProfileUpdateResult.NotFound _ -> throw new BusinessException(UserAdminProblemTypes.USER_NOT_FOUND);
        };
    }

    /**
     * 管理者の印を付ける（BR4.1）。
     *
     * @param userId 対象の利用者 ID
     * @param request 要求
     * @return 204
     */
    @PostMapping(path = "/{userId}/grant-admin")
    public ResponseEntity<Void> grantAdmin(@PathVariable("userId") long userId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.grantAdmin(actor, origin(request), userId));
    }

    /**
     * 管理者の印を外す（BR4.2）。
     *
     * @param userId 対象の利用者 ID
     * @param request 要求
     * @return 204
     */
    @PostMapping(path = "/{userId}/revoke-admin")
    public ResponseEntity<Void> revokeAdmin(@PathVariable("userId") long userId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.revokeAdmin(actor, origin(request), userId));
    }

    /**
     * 利用を止める（BR4.3）。
     *
     * @param userId 対象の利用者 ID
     * @param request 要求
     * @return 204
     */
    @PostMapping(path = "/{userId}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable("userId") long userId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.suspend(actor, origin(request), userId));
    }

    /**
     * 停止を解く（BR4.4）。
     *
     * @param userId 対象の利用者 ID
     * @param request 要求
     * @return 204
     */
    @PostMapping(path = "/{userId}/resume")
    public ResponseEntity<Void> resume(@PathVariable("userId") long userId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.resume(actor, origin(request), userId));
    }

    /**
     * ログインの失敗回数を戻す（BR4.5）。
     *
     * @param userId 対象の利用者 ID
     * @param request 要求
     * @return 204
     */
    @PostMapping(path = "/{userId}/reset-login-failures")
    public ResponseEntity<Void> resetLoginFailures(@PathVariable("userId") long userId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.resetLoginFailures(actor, origin(request), userId));
    }

    private RequestOrigin origin(HttpServletRequest request) {
        return context.origin(request);
    }

    /**
     * 5つの操作の結果を応答か業務エラーにする（BR2.3・BR2.6・BR3.5）。
     *
     * @param result 業務処理の結果
     * @return 204
     */
    private static ResponseEntity<Void> respond(OperationResult result) {
        return switch (result) {
            case OperationResult.Done _ -> ResponseEntity.noContent().build();
            case OperationResult.Rejected rejected ->
                throw new BusinessException(UserAdminProblemTypes.of(rejected.reason()));
            case OperationResult.OperatorNotAdmin _ -> throw new BusinessException(AccessProblemTypes.ACCESS_DENIED);
            case OperationResult.Busy _ -> throw new BusinessException(UserAdminProblemTypes.BUSY);
        };
    }
}
