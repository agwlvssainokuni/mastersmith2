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
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.role.domain.RoleProblemTypes;
import cherry.mastersmith.role.service.RoleAdminService;
import cherry.mastersmith.role.service.RoleChangeResult;
import cherry.mastersmith.role.service.RoleCreateResult;
import cherry.mastersmith.role.service.RoleDetailResult;
import cherry.mastersmith.role.service.RoleListResult;
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
 * ロールの管理の API（Intent 261004-role-menu の U4、契約 C7、{@code logical-components.md} の L1）。5つの口はすべて管理者だけ
 * （{@link ApiAccessLevel#ADMIN}、既存の {@code /api/admin/**} の決まり。BR2.1・NFR1.1〜NFR1.3）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/roles?page=}: 200 と1ページ（BR3.4・BR3.5）
 *   <li>{@code POST /api/admin/roles}: 201 と作ったロール（BR3.1）
 *   <li>{@code GET /api/admin/roles/{roleId}}: 200 とロール（BR3.7）
 *   <li>{@code PUT /api/admin/roles/{roleId}}: 204（名前の変更。BR1.4・BR1.5・BR3.6）
 *   <li>{@code DELETE /api/admin/roles/{roleId}}: 204（BR3.2・BR3.3）
 * </ul>
 *
 * <p>業務処理の結果の型を場合を尽くす {@code switch} で応答か業務エラーにし、エラーの応答は共通の変換（{@code @RestControllerAdvice} の
 * 1か所）で作る（BR13.1）: 400 {@code VALIDATION_FAILED}（名前の誤りは {@code fieldErrors}、page の誤りは項目なし）、404
 * {@code ROLE_NOT_FOUND}、409 {@code ROLE_NAME_DUPLICATE}・{@code ROLE_IN_USE}（{@code assignedUsers}・{@code assignedGroups} を
 * 載せる）・{@code ROLE_NO_CHANGE}・{@code ROLE_BUSY}。道の ID が整数でないときは既存の型の誤りの扱いで 400、0 以下は存在しない ID
 * として 404（計画の D-13）。要求の本文は決めた項目だけの DTO で受ける（BR2.3）。
 */
@RestController
@ApiAccess(ApiAccessLevel.ADMIN)
@RequestMapping(RoleAdminController.PATH)
public class RoleAdminController {

    /** ロールの管理の API の道。 */
    public static final String PATH = "/api/admin/roles";

    /** 割り当てが残る削除の拒否の応答に載せる利用者への直接の割り当ての数の項目（BR3.2）。 */
    static final String ASSIGNED_USERS = "assignedUsers";

    /** 割り当てが残る削除の拒否の応答に載せるグループへの割り当ての数の項目（BR3.2）。 */
    static final String ASSIGNED_GROUPS = "assignedGroups";

    private final RoleAdminService service;

    private final RoleRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service ロールの管理の業務処理
     * @param context 要求の文脈の読み取り
     */
    public RoleAdminController(RoleAdminService service, RoleRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * ロールの一覧を読む（BR3.4・BR3.5）。
     *
     * @param page ページ（無ければ 1）
     * @return 1ページ
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public RolePageResponse list(@RequestParam(name = "page", required = false) String page) {
        return switch (service.list(page)) {
            case RoleListResult.Listed listed -> RolePageResponse.from(listed.page());
            case RoleListResult.InvalidPage _ -> throw new BusinessException(CommonProblemTypes.VALIDATION_FAILED);
        };
    }

    /**
     * ロールを作る（BR1.1〜BR1.4・BR3.1）。
     *
     * @param body 名前
     * @param request 要求
     * @return 201 と作ったロール
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RoleResponse> create(@RequestBody RoleNameRequest body, HttpServletRequest request) {
        long actor = context.currentUserId();
        return switch (service.create(actor, context.origin(request), body.name())) {
            case RoleCreateResult.Created created ->
                ResponseEntity.status(HttpStatus.CREATED).body(RoleResponse.from(created.role()));
            case RoleCreateResult.InvalidName invalid -> throw RoleFieldErrors.name(invalid.reason());
            case RoleCreateResult.Rejected rejected ->
                throw new BusinessException(RoleProblemTypes.of(rejected.rejection()));
            case RoleCreateResult.Busy _ -> throw new BusinessException(RoleProblemTypes.ROLE_BUSY);
        };
    }

    /**
     * ロール1件を読む（BR3.7）。
     *
     * @param roleId ロールの ID
     * @return ロール
     */
    @GetMapping(path = "/{roleId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public RoleResponse detail(@PathVariable("roleId") long roleId) {
        return switch (service.detail(roleId)) {
            case RoleDetailResult.Found found -> RoleResponse.from(found.role());
            case RoleDetailResult.NotFound _ -> throw new BusinessException(RoleProblemTypes.ROLE_NOT_FOUND);
        };
    }

    /**
     * ロールの名前を変える（BR1.4・BR1.5・BR3.6）。
     *
     * @param roleId ロールの ID
     * @param body 名前
     * @param request 要求
     * @return 204
     */
    @PutMapping(path = "/{roleId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> rename(
            @PathVariable("roleId") long roleId, @RequestBody RoleNameRequest body, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.rename(actor, context.origin(request), roleId, body.name()));
    }

    /**
     * ロールを消す（BR3.2・BR3.3）。
     *
     * @param roleId ロールの ID
     * @param request 要求
     * @return 204
     */
    @DeleteMapping(path = "/{roleId}")
    public ResponseEntity<Void> delete(@PathVariable("roleId") long roleId, HttpServletRequest request) {
        long actor = context.currentUserId();
        return respond(service.delete(actor, context.origin(request), roleId));
    }

    /**
     * 変える操作の結果を応答か業務エラーにする（BR13.1・BR3.2・BR8.3）。権限の保存と消す操作でも使う。
     *
     * @param result 結果
     * @return 204
     */
    static ResponseEntity<Void> respond(RoleChangeResult result) {
        return switch (result) {
            case RoleChangeResult.Done _ -> ResponseEntity.noContent().build();
            case RoleChangeResult.InvalidName invalid -> throw RoleFieldErrors.name(invalid.reason());
            case RoleChangeResult.InvalidInput invalid -> throw RoleFieldErrors.of(invalid.field(), invalid.reason());
            case RoleChangeResult.Rejected rejected ->
                throw new BusinessException(RoleProblemTypes.of(rejected.rejection()));
            case RoleChangeResult.InUse inUse -> {
                Map<String, Object> remaining = new LinkedHashMap<>();
                remaining.put(ASSIGNED_USERS, inUse.assignedUsers());
                remaining.put(ASSIGNED_GROUPS, inUse.assignedGroups());
                throw new BusinessException(RoleProblemTypes.ROLE_IN_USE, null, remaining);
            }
            case RoleChangeResult.Busy _ -> throw new BusinessException(RoleProblemTypes.ROLE_BUSY);
        };
    }
}
