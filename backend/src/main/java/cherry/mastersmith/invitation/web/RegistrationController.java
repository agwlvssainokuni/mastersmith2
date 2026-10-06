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
package cherry.mastersmith.invitation.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.invitation.domain.InvitationProblemTypes;
import cherry.mastersmith.invitation.service.CompleteResult;
import cherry.mastersmith.invitation.service.RegistrationCommand;
import cherry.mastersmith.invitation.service.RegistrationService;
import cherry.mastersmith.invitation.service.TokenCommand;
import cherry.mastersmith.invitation.service.VerifyResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登録の完了の API（契約 C6、ログインなし。BR9.2 の差し込み口で公開する）。
 *
 * <ul>
 *   <li>{@code POST /api/registration/verify}: 200 と招待のメールアドレスと言語
 *   <li>{@code POST /api/registration/complete}: 204。入力の誤りは 400 VALIDATION_FAILED（{@code fieldErrors}）
 * </ul>
 *
 * <p>リンクの拒否は理由によらず1つの経路（{@code REGISTRATION_LINK_INVALID}、追加の項目なし）で応答を作る（BR7.5、NFR3.1）。トークンは
 * 要求の本文の {@code token} だけで受け取る（BR3.5）。自動ではログインしない（トークンを発行しない）。
 */
@RestController
@ApiAccess(ApiAccessLevel.PUBLIC)
@RequestMapping(RegistrationController.PATH)
public class RegistrationController {

    /** 登録の完了の API の道。 */
    public static final String PATH = "/api/registration";

    /** リンクの確かめの道。 */
    public static final String VERIFY_PATH = PATH + "/verify";

    /** 登録の完了の道。 */
    public static final String COMPLETE_PATH = PATH + "/complete";

    private final RegistrationService service;

    private final InvitationRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service 登録の完了の業務処理
     * @param context 要求の文脈の読み取り（送り手の情報）
     */
    public RegistrationController(RegistrationService service, InvitationRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * リンクを確かめる（招待を消費しない。監査しない）。
     *
     * @param body トークン
     * @return 招待のメールアドレスと言語
     */
    @PostMapping(
            path = "/verify",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public InvitationViewResponse verify(@RequestBody TokenRequest body) {
        return switch (service.verify(new TokenCommand(body.token()))) {
            case VerifyResult.Valid valid -> InvitationViewResponse.from(valid.view());
            case VerifyResult.Rejected _ -> throw linkInvalid();
        };
    }

    /**
     * 登録を完了する。
     *
     * @param body 入力
     * @param request 要求（送り手の情報を監査に載せる）
     * @return 204
     */
    @PostMapping(path = "/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> complete(@RequestBody CompleteRequest body, HttpServletRequest request) {
        RegistrationCommand command = new RegistrationCommand(
                body.token(),
                body.displayName(),
                body.password(),
                body.passwordConfirmation(),
                body.language(),
                body.theme(),
                body.fontSize());
        return switch (service.complete(context.origin(request), command)) {
            case CompleteResult.Completed _ -> ResponseEntity.noContent().build();
            case CompleteResult.Invalid invalid -> throw FieldErrors.validationFailed(invalid.errors());
            case CompleteResult.Rejected _ -> throw linkInvalid();
        };
    }

    private static BusinessException linkInvalid() {
        return new BusinessException(InvitationProblemTypes.REGISTRATION_LINK_INVALID);
    }
}
