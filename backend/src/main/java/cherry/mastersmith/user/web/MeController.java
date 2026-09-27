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
package cherry.mastersmith.user.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.user.domain.FieldError;
import cherry.mastersmith.user.domain.UserProblemTypes;
import cherry.mastersmith.user.service.PasswordChangeCommand;
import cherry.mastersmith.user.service.PasswordChangeResult;
import cherry.mastersmith.user.service.PreferencesCommand;
import cherry.mastersmith.user.service.PreferencesResult;
import cherry.mastersmith.user.service.UserPreferencesService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ログインした利用者の自分の設定の API（契約 C4）。対象は認証された本人だけで決め、URL・本文で利用者の識別を受け取らない（BR8.1、
 * NFR4.3）。既存の {@code /api/} の既定のログイン必須に乗り、新しい公開の決まり・Origin の確かめは足さない（NFR4.4）。
 *
 * <ul>
 *   <li>{@code GET /api/me/preferences}: 200 と4つ（メールアドレス・管理者かを含めない）
 *   <li>{@code PUT /api/me/preferences}: 200 と保存した4つ。入力の誤りは 400 {@code VALIDATION_FAILED}（{@code fieldErrors}）
 *   <li>{@code POST /api/me/password}: 204。入力の誤りは 400 {@code VALIDATION_FAILED}（{@code fieldErrors}）、今のパスワードの誤りは
 *       400 {@code PASSWORD_CURRENT_MISMATCH}
 *   <li>認証の後に本人の行が消えていれば 401 {@code AUTHENTICATION_REQUIRED}
 * </ul>
 *
 * <p>業務処理の結果の型を業務エラーにし、応答は共通の変換（{@code @RestControllerAdvice} の1か所）で作る。項目ごとの誤りの形は
 * {@code nfr-design/security-design.md} 3節（項目の名前と理由だけ、入れた値を載せない）。
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    /** 項目ごとの誤りを載せる追加の項目の名前。 */
    static final String FIELD_ERRORS = "fieldErrors";

    private final UserPreferencesService service;

    private final MeRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service 自分の設定の業務処理
     * @param context 要求の文脈の読み取り
     */
    public MeController(UserPreferencesService service, MeRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * 自分の氏名と表示の設定を読む。
     *
     * @return 4つ
     */
    @GetMapping(path = "/preferences", produces = MediaType.APPLICATION_JSON_VALUE)
    public PreferencesResponse getPreferences() {
        return toResponse(service.getPreferences(context.currentUserId()));
    }

    /**
     * 自分の氏名と表示の設定の4つをまとめて置き換える（監査しない）。
     *
     * @param body 4つ
     * @return 保存した4つ
     */
    @PutMapping(
            path = "/preferences",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PreferencesResponse savePreferences(@RequestBody PreferencesRequest body) {
        PreferencesCommand command =
                new PreferencesCommand(body.displayName(), body.language(), body.theme(), body.fontSize());
        return toResponse(service.savePreferences(context.currentUserId(), command));
    }

    /**
     * 今のパスワードを確かめて、自分のパスワードを変える（トークンは変えない）。
     *
     * @param body 今・新しい・確かめのパスワード
     * @param request 要求（送り手の情報を監査に載せる）
     * @return 204（内容なし）
     */
    @PostMapping(path = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changePassword(@RequestBody PasswordChangeRequest body, HttpServletRequest request) {
        long userId = context.currentUserId();
        PasswordChangeCommand command =
                PasswordChangeCommand.of(body.currentPassword(), body.newPassword(), body.newPasswordConfirmation());
        PasswordChangeResult result = service.changePassword(userId, command, context.origin(request));
        return switch (result) {
            case PasswordChangeResult.Changed _ -> ResponseEntity.noContent().build();
            case PasswordChangeResult.Invalid invalid -> throw validationFailed(invalid.errors());
            case PasswordChangeResult.CurrentMismatch _ ->
                throw new BusinessException(UserProblemTypes.PASSWORD_CURRENT_MISMATCH);
            case PasswordChangeResult.UserNotFound _ -> throw new BusinessException(context.authenticationRequired());
        };
    }

    private PreferencesResponse toResponse(PreferencesResult result) {
        return switch (result) {
            case PreferencesResult.Ok ok -> PreferencesResponse.from(ok.preferences());
            case PreferencesResult.Invalid invalid -> throw validationFailed(invalid.errors());
            case PreferencesResult.UserNotFound _ -> throw new BusinessException(context.authenticationRequired());
        };
    }

    /** 入力の誤りを、項目ごとの誤りを載せた業務エラーにする（説明は既存の一般の説明。入れた値は載せない）。 */
    private static BusinessException validationFailed(List<FieldError> errors) {
        List<Map<String, String>> fieldErrors = errors.stream()
                .map(error -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("field", error.field());
                    item.put("reason", error.reason().name());
                    return item;
                })
                .toList();
        return new BusinessException(CommonProblemTypes.VALIDATION_FAILED, null, Map.of(FIELD_ERRORS, fieldErrors));
    }
}
