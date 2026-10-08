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
import cherry.mastersmith.role.domain.RoleProblemTypes;
import cherry.mastersmith.role.service.WorkRoleService;
import cherry.mastersmith.role.service.WorkRoleSwitchResult;
import cherry.mastersmith.user.domain.FieldErrorReason;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自分の作業ロールの API（Intent 261004-role-menu の U4、契約 C8、FS の 2.10）。ログインした利用者だけ（{@link ApiAccessLevel#AUTHENTICATED}。
 * BR2.2）。主体は要求の文脈から読み、利用者を指す値を受け取らない。
 *
 * <ul>
 *   <li>{@code GET /api/me/work-role}: 200 とロール（ID の順）と今の作業ロール（無ければ null）
 *   <li>{@code PUT /api/me/work-role}: 204（保存が同じなら何もしない成功）。400 {@code VALIDATION_FAILED}、409
 *       {@code ROLE_NOT_ASSIGNED}（存在しないロールも同じ）・{@code ROLE_BUSY}
 * </ul>
 */
@RestController
@ApiAccess(ApiAccessLevel.AUTHENTICATED)
@RequestMapping(MeWorkRoleController.PATH)
public class MeWorkRoleController {

    /** 作業ロールの API の道。 */
    public static final String PATH = "/api/me/work-role";

    /** ロールの ID の項目。 */
    static final String ROLE_ID = "roleId";

    private final WorkRoleService service;

    private final RoleRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service 作業ロールの業務処理
     * @param context 要求の文脈の読み取り
     */
    public MeWorkRoleController(WorkRoleService service, RoleRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * 自分のロールと今の作業ロールを読む（BR7.1〜BR7.3）。
     *
     * @return ロールと作業ロール
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkRoleResponse read() {
        return WorkRoleResponse.from(service.read(context.currentUserId()));
    }

    /**
     * 作業ロールを切り替える（BR7.5〜BR7.8）。
     *
     * @param body 選ぶロールの ID
     * @param request 要求
     * @return 204
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> switchTo(@RequestBody WorkRoleRequest body, HttpServletRequest request) {
        if (body.roleId() == null) {
            throw RoleFieldErrors.of(ROLE_ID, FieldErrorReason.REQUIRED);
        }
        long userId = context.currentUserId();
        return switch (service.switchTo(userId, context.origin(request), body.roleId())) {
            case WorkRoleSwitchResult.Switched _, WorkRoleSwitchResult.Unchanged _ ->
                ResponseEntity.noContent().build();
            case WorkRoleSwitchResult.NotAssigned _ -> throw new BusinessException(RoleProblemTypes.ROLE_NOT_ASSIGNED);
            case WorkRoleSwitchResult.Busy _ -> throw new BusinessException(RoleProblemTypes.ROLE_BUSY);
        };
    }
}
