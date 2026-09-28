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
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.invitation.domain.InvitationProblemTypes;
import cherry.mastersmith.invitation.domain.UnavailableReason;
import cherry.mastersmith.invitation.service.CancelResult;
import cherry.mastersmith.invitation.service.InvitationService;
import cherry.mastersmith.invitation.service.InviteCommand;
import cherry.mastersmith.invitation.service.InviteResult;
import cherry.mastersmith.invitation.service.ListResult;
import cherry.mastersmith.invitation.service.ResendResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 招待の管理の API（契約 C5、管理者だけ。BR9.1）。既存の {@code /api/admin/**} の決まり（未認証 401・管理者でない 403）にそのまま乗る。
 *
 * <ul>
 *   <li>{@code POST /api/admin/invitations}: 201 と招待（送信に失敗しても 201 で sendResult FAILED）
 *   <li>{@code GET /api/admin/invitations?page=}: 200 と1ページ（page は文字列で受ける）
 *   <li>{@code POST /api/admin/invitations/{invitationId}/resend}: 200 と招待
 *   <li>{@code POST /api/admin/invitations/{invitationId}/cancel}: 204
 * </ul>
 *
 * <p>業務処理の結果の型を業務エラーにし、応答は共通の変換（{@code @RestControllerAdvice} の1か所）で作る: 400 VALIDATION_FAILED
 * （{@code fieldErrors}。page の誤りは項目なし）、409 INVITATION_EMAIL_REGISTERED、409 INVITATION_ALREADY_PENDING（{@code invitationId}・
 * {@code page}）、503 INVITATION_NOT_CONFIGURED（{@code unavailableReasons}）、404 INVITATION_NOT_FOUND。
 */
@RestController
@RequestMapping(InvitationAdminController.PATH)
public class InvitationAdminController {

    /** 招待の管理の API の道。 */
    public static final String PATH = "/api/admin/invitations";

    private final InvitationService service;

    private final InvitationRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service 招待の管理の業務処理
     * @param context 要求の文脈の読み取り
     */
    public InvitationAdminController(InvitationService service, InvitationRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * 招待する。
     *
     * @param body 招待先と言語
     * @param request 要求（送り手の情報を監査に載せる）
     * @return 201 と招待
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InvitationResponse> invite(@RequestBody InvitationRequest body, HttpServletRequest request) {
        long actor = context.currentUserId();
        InviteResult result =
                service.invite(actor, context.origin(request), new InviteCommand(body.email(), body.language()));
        return switch (result) {
            case InviteResult.Invited invited ->
                ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.from(invited.summary()));
            case InviteResult.Invalid invalid -> throw FieldErrors.validationFailed(invalid.errors());
            case InviteResult.NotConfigured notConfigured -> throw notConfigured(notConfigured.reasons());
            case InviteResult.EmailRegistered _ ->
                throw new BusinessException(InvitationProblemTypes.INVITATION_EMAIL_REGISTERED);
            case InviteResult.AlreadyPending pending -> throw alreadyPending(pending);
        };
    }

    /**
     * 招待中の招待の一覧を読む。
     *
     * @param page ページ（無ければ 1）
     * @return 1ページ
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public InvitationPageResponse list(@RequestParam(name = "page", required = false) String page) {
        return switch (service.list(page)) {
            case ListResult.Listed listed -> InvitationPageResponse.from(listed.page());
            case ListResult.InvalidPage _ -> throw new BusinessException(CommonProblemTypes.VALIDATION_FAILED);
        };
    }

    /**
     * 招待を送り直す。
     *
     * @param invitationId 招待の ID
     * @param request 要求
     * @return 200 と招待
     */
    @PostMapping(path = "/{invitationId}/resend", produces = MediaType.APPLICATION_JSON_VALUE)
    public InvitationResponse resend(@PathVariable("invitationId") long invitationId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return switch (service.resend(actor, context.origin(request), invitationId)) {
            case ResendResult.Resent resent -> InvitationResponse.from(resent.summary());
            case ResendResult.NotConfigured notConfigured -> throw notConfigured(notConfigured.reasons());
            case ResendResult.NotFound _ -> throw new BusinessException(InvitationProblemTypes.INVITATION_NOT_FOUND);
        };
    }

    /**
     * 招待を取り消す。
     *
     * @param invitationId 招待の ID
     * @param request 要求
     * @return 204
     */
    @PostMapping(path = "/{invitationId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable("invitationId") long invitationId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return switch (service.cancel(actor, context.origin(request), invitationId)) {
            case CancelResult.Cancelled _ -> ResponseEntity.noContent().build();
            case CancelResult.NotFound _ -> throw new BusinessException(InvitationProblemTypes.INVITATION_NOT_FOUND);
        };
    }

    private static BusinessException notConfigured(List<UnavailableReason> reasons) {
        return new BusinessException(
                InvitationProblemTypes.INVITATION_NOT_CONFIGURED,
                null,
                Map.of("unavailableReasons", reasons.stream().map(Enum::name).toList()));
    }

    private static BusinessException alreadyPending(InviteResult.AlreadyPending pending) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("invitationId", pending.invitationId());
        properties.put("page", pending.page());
        return new BusinessException(InvitationProblemTypes.INVITATION_ALREADY_PENDING, null, properties);
    }
}
