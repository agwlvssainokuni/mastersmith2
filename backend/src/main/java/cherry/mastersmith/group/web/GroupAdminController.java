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
package cherry.mastersmith.group.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.group.domain.GroupProblemTypes;
import cherry.mastersmith.group.service.GroupAdminService;
import cherry.mastersmith.group.service.GroupChangeResult;
import cherry.mastersmith.group.service.GroupCreateResult;
import cherry.mastersmith.group.service.GroupDetailResult;
import cherry.mastersmith.group.service.GroupListResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * グループの管理の API（Intent 261004-role-menu の U3、契約 C6、{@code logical-components.md} の L1）。7つの口はすべて管理者だけ
 * （{@link ApiAccessLevel#ADMIN}、既存の {@code /api/admin/**} の決まり。BR2.1・NFR1.1〜NFR1.3）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/groups?page=}: 200 と1ページ（BR7.1〜BR7.3）
 *   <li>{@code POST /api/admin/groups}: 201 と作ったグループ（BR1）
 *   <li>{@code GET /api/admin/groups/{groupId}}: 200 と詳細（BR7.4）
 *   <li>{@code PUT /api/admin/groups/{groupId}}: 204（名前の変更）
 *   <li>{@code DELETE /api/admin/groups/{groupId}}: 204（BR4）
 *   <li>{@code POST /api/admin/groups/{groupId}/members}: 204（BR3）
 *   <li>{@code DELETE /api/admin/groups/{groupId}/members/{userId}}: 204（BR3.4）
 * </ul>
 *
 * <p>業務処理の結果の型を場合を尽くす {@code switch} で応答か業務エラーにし、エラーの応答は共通の変換（{@code @RestControllerAdvice} の
 * 1か所）で作る（BR10.1）: 400 {@code VALIDATION_FAILED}（名前・{@code userId} の誤りは {@code fieldErrors}、page の誤りは項目なし）、
 * 404 {@code GROUP_NOT_FOUND}・{@code USER_NOT_FOUND}、409 {@code GROUP_NAME_DUPLICATE}・{@code GROUP_IN_USE}（{@code members}・
 * {@code assignedRoles} を載せる）・{@code GROUP_NO_CHANGE}・{@code GROUP_BUSY}。道の ID が整数でないときは既存の型の誤りの扱いで 400、
 * 0 以下は存在しない ID として扱う（BR2.3）。要求の本文は決めた項目だけの DTO で受ける（BR2.2）。
 */
@RestController
@ApiAccess(ApiAccessLevel.ADMIN)
@RequestMapping(GroupAdminController.PATH)
public class GroupAdminController {

    /** グループの管理の API の道。 */
    public static final String PATH = "/api/admin/groups";

    /** 使用中の削除の拒否の応答に載せる残りのメンバーの数の項目（BR4.2）。 */
    static final String MEMBERS = "members";

    /** 使用中の削除の拒否の応答に載せる残りのロールの割り当ての数の項目（BR4.2）。 */
    static final String ASSIGNED_ROLES = "assignedRoles";

    private final GroupAdminService service;

    private final GroupRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service グループの管理の業務処理
     * @param context 要求の文脈の読み取り
     */
    public GroupAdminController(GroupAdminService service, GroupRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * グループの一覧を読む（BR7.1〜BR7.3）。
     *
     * @param page ページ（無ければ 1）
     * @return 1ページ
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public GroupPageResponse list(@RequestParam(name = "page", required = false) String page) {
        return switch (service.list(page)) {
            case GroupListResult.Listed listed -> GroupPageResponse.from(listed.page());
            case GroupListResult.InvalidPage _ -> throw new BusinessException(CommonProblemTypes.VALIDATION_FAILED);
        };
    }

    /**
     * グループを作る（BR1.1〜BR1.4）。
     *
     * @param body 名前
     * @param request 要求
     * @return 201 と作ったグループ
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GroupResponse> create(@RequestBody GroupNameRequest body, HttpServletRequest request) {
        long actor = context.currentUserId();
        return switch (service.create(actor, context.origin(request), body.name())) {
            case GroupCreateResult.Created created ->
                ResponseEntity.status(HttpStatus.CREATED).body(GroupResponse.from(created.group()));
            case GroupCreateResult.Invalid invalid -> throw GroupFieldErrors.name(invalid.reason());
            case GroupCreateResult.Rejected rejected ->
                throw new BusinessException(GroupProblemTypes.of(rejected.rejection()));
            case GroupCreateResult.Busy _ -> throw new BusinessException(GroupProblemTypes.GROUP_BUSY);
        };
    }

    /**
     * グループの詳細を読む（BR7.4）。
     *
     * @param groupId グループの ID
     * @return 詳細
     */
    @GetMapping(path = "/{groupId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GroupDetailResponse detail(@PathVariable("groupId") long groupId) {
        return switch (service.detail(groupId)) {
            case GroupDetailResult.Found found -> GroupDetailResponse.from(found);
            case GroupDetailResult.NotFound _ -> throw new BusinessException(GroupProblemTypes.GROUP_NOT_FOUND);
        };
    }

    /**
     * グループの名前を変える（BR1.4・BR1.5）。
     *
     * @param groupId グループの ID
     * @param body 名前
     * @param request 要求
     * @return 204
     */
    @PutMapping(path = "/{groupId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> rename(
            @PathVariable("groupId") long groupId, @RequestBody GroupNameRequest body, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.rename(actor, context.origin(request), groupId, body.name()));
    }

    /**
     * グループを消す（BR4）。
     *
     * @param groupId グループの ID
     * @param request 要求
     * @return 204
     */
    @DeleteMapping(path = "/{groupId}")
    public ResponseEntity<Void> delete(@PathVariable("groupId") long groupId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.delete(actor, context.origin(request), groupId));
    }

    /**
     * メンバーを足す（BR3.1〜BR3.3・BR3.5）。
     *
     * @param groupId グループの ID
     * @param body 利用者 ID
     * @param request 要求
     * @return 204
     */
    @PostMapping(path = "/{groupId}/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> addMember(
            @PathVariable("groupId") long groupId, @RequestBody MemberAddRequest body, HttpServletRequest request) {
        if (body.userId() == null) {
            throw GroupFieldErrors.required(GroupFieldErrors.USER_ID);
        }
        long actor = context.currentUserId();
        return respond(service.addMember(actor, context.origin(request), groupId, body.userId()));
    }

    /**
     * メンバーを外す（BR3.4）。
     *
     * @param groupId グループの ID
     * @param userId 利用者 ID
     * @param request 要求
     * @return 204
     */
    @DeleteMapping(path = "/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable("groupId") long groupId, @PathVariable("userId") long userId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.removeMember(actor, context.origin(request), groupId, userId));
    }

    /** 変える操作の結果を応答か業務エラーにする（BR10.1・BR4.2・BR5.2）。 */
    private static ResponseEntity<Void> respond(GroupChangeResult result) {
        return switch (result) {
            case GroupChangeResult.Done _ -> ResponseEntity.noContent().build();
            case GroupChangeResult.Invalid invalid -> throw GroupFieldErrors.name(invalid.reason());
            case GroupChangeResult.Rejected rejected ->
                throw new BusinessException(GroupProblemTypes.of(rejected.rejection()));
            case GroupChangeResult.InUse inUse -> {
                Map<String, Object> remaining = new LinkedHashMap<>();
                remaining.put(MEMBERS, inUse.members());
                remaining.put(ASSIGNED_ROLES, inUse.assignedRoles());
                throw new BusinessException(GroupProblemTypes.GROUP_IN_USE, null, remaining);
            }
            case GroupChangeResult.Busy _ -> throw new BusinessException(GroupProblemTypes.GROUP_BUSY);
        };
    }
}
