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
package cherry.mastersmith.role.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.group.domain.GroupProblemTypes;
import cherry.mastersmith.role.domain.RoleProblemTypes;
import cherry.mastersmith.role.service.RoleAssignmentResult;
import cherry.mastersmith.role.service.RoleAssignmentService;
import cherry.mastersmith.role.service.RoleAssignmentsResult;
import cherry.mastersmith.user.domain.FieldErrorReason;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ロールの割り当ての API（Intent 261004-role-menu の U4、契約 C7、FS の 2.8）。4つの口はすべて管理者だけ（{@link ApiAccessLevel#ADMIN}。
 * BR2.1）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/roles/{roleId}/assignments}: 200 と割り当ての一覧（利用者は出どころつき、グループ。BR6.5・BR6.11）
 *   <li>{@code POST /api/admin/roles/{roleId}/assignments}: 204（本文は {@code userId} と {@code groupId} のちょうど一方。BR6.1〜BR6.3）
 *   <li>{@code DELETE /api/admin/roles/{roleId}/assignments/users/{userId}}: 204（BR6.4）
 *   <li>{@code DELETE /api/admin/roles/{roleId}/assignments/groups/{groupId}}: 204（BR6.4・BR6.6）
 * </ul>
 *
 * <p>誤りは 400 {@code VALIDATION_FAILED}（本文の項目は {@code fieldErrors}）、404 {@code ROLE_NOT_FOUND}・{@code USER_NOT_FOUND}・
 * {@code GROUP_NOT_FOUND}、409 {@code ROLE_NO_CHANGE}・{@code ROLE_BUSY}・{@code GROUP_BUSY}。道の ID が整数でないときは既存の型の誤りの
 * 扱いで 400、0 以下は存在しない ID として扱う（計画の D-13）。
 */
@RestController
@ApiAccess(ApiAccessLevel.ADMIN)
@RequestMapping(RoleAssignmentController.PATH)
public class RoleAssignmentController {

    /** 割り当ての API の道。 */
    public static final String PATH = "/api/admin/roles/{roleId}/assignments";

    /** 利用者 ID の項目。 */
    static final String USER_ID = "userId";

    /** グループの ID の項目。 */
    static final String GROUP_ID = "groupId";

    private final RoleAssignmentService service;

    private final RoleRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service 割り当ての業務処理
     * @param context 要求の文脈の読み取り
     */
    public RoleAssignmentController(RoleAssignmentService service, RoleRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * 割り当ての一覧を読む（BR6.5・BR6.11・BR12.1）。
     *
     * @param roleId ロールの ID
     * @return 一覧
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public RoleAssignmentsResponse list(@PathVariable("roleId") long roleId) {
        return switch (service.assignments(roleId)) {
            case RoleAssignmentsResult.Found found -> RoleAssignmentsResponse.from(found);
            case RoleAssignmentsResult.RoleNotFound _ -> throw new BusinessException(RoleProblemTypes.ROLE_NOT_FOUND);
        };
    }

    /**
     * 利用者かグループにロールを割り当てる（BR6.1〜BR6.3・BR6.6・BR6.9）。
     *
     * @param roleId ロールの ID
     * @param body 相手（ちょうど一方）
     * @param request 要求
     * @return 204
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> assign(
            @PathVariable("roleId") long roleId, @RequestBody AssignmentRequest body, HttpServletRequest request) {
        if (body.userId() == null && body.groupId() == null) {
            throw RoleFieldErrors.of(USER_ID, FieldErrorReason.REQUIRED);
        }
        if (body.userId() != null && body.groupId() != null) {
            throw RoleFieldErrors.of(GROUP_ID, FieldErrorReason.INVALID_VALUE);
        }
        long actor = context.currentUserId();
        RoleAssignmentResult result = body.userId() != null
                ? service.assignUser(actor, context.origin(request), roleId, body.userId())
                : service.assignGroup(actor, context.origin(request), roleId, body.groupId());
        return respond(result);
    }

    /**
     * 利用者への直接の割り当てを外す（BR6.4）。
     *
     * @param roleId ロールの ID
     * @param userId 利用者 ID
     * @param request 要求
     * @return 204
     */
    @DeleteMapping(path = "/users/{userId}")
    public ResponseEntity<Void> unassignUser(
            @PathVariable("roleId") long roleId, @PathVariable("userId") long userId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.unassignUser(actor, context.origin(request), roleId, userId));
    }

    /**
     * グループへの割り当てを外す（BR6.4・BR6.6）。
     *
     * @param roleId ロールの ID
     * @param groupId グループの ID
     * @param request 要求
     * @return 204
     */
    @DeleteMapping(path = "/groups/{groupId}")
    public ResponseEntity<Void> unassignGroup(
            @PathVariable("roleId") long roleId, @PathVariable("groupId") long groupId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.unassignGroup(actor, context.origin(request), roleId, groupId));
    }

    private static ResponseEntity<Void> respond(RoleAssignmentResult result) {
        return switch (result) {
            case RoleAssignmentResult.Done _ -> ResponseEntity.noContent().build();
            case RoleAssignmentResult.Rejected rejected ->
                throw new BusinessException(RoleProblemTypes.of(rejected.rejection()));
            case RoleAssignmentResult.Busy _ -> throw new BusinessException(RoleProblemTypes.ROLE_BUSY);
            case RoleAssignmentResult.GroupBusy _ -> throw new BusinessException(GroupProblemTypes.GROUP_BUSY);
        };
    }
}
