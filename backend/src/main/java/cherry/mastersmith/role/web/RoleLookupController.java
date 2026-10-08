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
import cherry.mastersmith.role.service.GroupRolesResult;
import cherry.mastersmith.role.service.RoleAssignmentService;
import cherry.mastersmith.role.service.UserRolesResult;
import cherry.mastersmith.user.domain.UserProblemTypes;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * グループと利用者の側からロールを読む API（Intent 261004-role-menu の U4、契約 C7、FS の 2.8 の読み取り）。2つの口はどちらも管理者だけ
 * （{@link ApiAccessLevel#ADMIN}。BR2.1）。監査に残さない。
 *
 * <ul>
 *   <li>{@code GET /api/admin/groups/{groupId}/roles}: 200 とロールの参照（ID の順）。404 {@code GROUP_NOT_FOUND}
 *   <li>{@code GET /api/admin/users/{userId}/roles}: 200 とロールと出どころ（ID の順）。404 {@code USER_NOT_FOUND}（招待中の人を含む）
 * </ul>
 */
@RestController
@ApiAccess(ApiAccessLevel.ADMIN)
public class RoleLookupController {

    /** グループのロールの道。 */
    public static final String GROUP_ROLES = "/api/admin/groups/{groupId}/roles";

    /** 利用者のロールの道。 */
    public static final String USER_ROLES = "/api/admin/users/{userId}/roles";

    private final RoleAssignmentService service;

    /**
     * 作る。
     *
     * @param service 割り当ての業務処理
     */
    public RoleLookupController(RoleAssignmentService service) {
        this.service = service;
    }

    /**
     * グループに割り当てたロールを読む。
     *
     * @param groupId グループの ID
     * @return ロールの参照
     */
    @GetMapping(path = GROUP_ROLES, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RoleRefResponse> groupRoles(@PathVariable("groupId") long groupId) {
        return switch (service.rolesOfGroup(groupId)) {
            case GroupRolesResult.Found found ->
                found.roles().stream().map(RoleRefResponse::from).toList();
            case GroupRolesResult.GroupNotFound _ -> throw new BusinessException(GroupProblemTypes.GROUP_NOT_FOUND);
        };
    }

    /**
     * 利用者のロールと出どころを読む（BR6.5）。
     *
     * @param userId 利用者 ID
     * @return ロールと出どころ
     */
    @GetMapping(path = USER_ROLES, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UserRoleResponse> userRoles(@PathVariable("userId") long userId) {
        return switch (service.rolesOfUser(userId)) {
            case UserRolesResult.Found found ->
                found.roles().stream().map(UserRoleResponse::from).toList();
            case UserRolesResult.UserNotFound _ -> throw new BusinessException(UserProblemTypes.USER_NOT_FOUND);
        };
    }
}
