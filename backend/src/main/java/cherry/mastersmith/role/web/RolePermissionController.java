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
import cherry.mastersmith.common.i18n.domain.AcceptLanguageResolver;
import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.role.domain.RoleProblemTypes;
import cherry.mastersmith.role.service.PermissionInputs;
import cherry.mastersmith.role.service.PermissionTreeResult;
import cherry.mastersmith.role.service.RoleAdminService;
import cherry.mastersmith.user.domain.FieldErrorReason;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
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
 * ロールの権限の設定の API（Intent 261004-role-menu の U4、契約 C7、{@code logical-components.md} の L1）。5つの口はすべて管理者だけ
 * （{@link ApiAccessLevel#ADMIN}。BR2.1）。
 *
 * <ul>
 *   <li>{@code GET .../permissions/schemas}: 200 と木の1段目
 *   <li>{@code GET .../permissions/tables?schema=}: 200 とスキーマの下のテーブル
 *   <li>{@code GET .../permissions/columns?schema=&table=}: 200 とテーブルの下のカラム
 *   <li>{@code PUT .../permissions}: 204（差分の保存。BR4.4）
 *   <li>{@code POST .../permissions/clear}: 204（今の DSL に無い設定を消す。BR4.7）
 * </ul>
 *
 * <p>スキーマ名・テーブル名は道ではなく問い合わせの引数で受け、長さで拒否しない（欠け・空は 400。BR4.12、計画の D-14）。表示名は要求の
 * 言語（Accept-Language）で選ぶ。誤りは 400 {@code VALIDATION_FAILED}（{@code fieldErrors}）、404 {@code ROLE_NOT_FOUND}、409
 * {@code DSL_NOT_APPLIED}・{@code PERMISSION_TARGET_NOT_IN_DSL}・{@code ROLE_NO_CHANGE}・{@code ROLE_BUSY}。
 */
@RestController
@ApiAccess(ApiAccessLevel.ADMIN)
@RequestMapping(RolePermissionController.PATH)
public class RolePermissionController {

    /** 権限の設定の API の道。 */
    public static final String PATH = "/api/admin/roles/{roleId}/permissions";

    /** スキーマ名の問い合わせの引数。 */
    static final String SCHEMA = "schema";

    /** テーブル名の問い合わせの引数。 */
    static final String TABLE = "table";

    private final RoleAdminService service;

    private final RoleRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service ロールの管理の業務処理
     * @param context 要求の文脈の読み取り
     */
    public RolePermissionController(RoleAdminService service, RoleRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * 木の1段目（スキーマ）を読む（BR4.10・BR4.11）。
     *
     * @param roleId ロールの ID
     * @param request 要求
     * @return 節の一覧
     */
    @GetMapping(path = "/schemas", produces = MediaType.APPLICATION_JSON_VALUE)
    public PermissionNodesResponse schemas(@PathVariable("roleId") long roleId, HttpServletRequest request) {
        return nodes(service.schemas(roleId), request);
    }

    /**
     * スキーマの下のテーブルを読む（BR4.10〜BR4.12）。
     *
     * @param roleId ロールの ID
     * @param schema スキーマの名前
     * @param request 要求
     * @return 節の一覧
     */
    @GetMapping(path = "/tables", produces = MediaType.APPLICATION_JSON_VALUE)
    public PermissionNodesResponse tables(
            @PathVariable("roleId") long roleId,
            @RequestParam(name = SCHEMA, required = false) String schema,
            HttpServletRequest request) {
        return nodes(service.tables(roleId, required(SCHEMA, schema)), request);
    }

    /**
     * テーブルの下のカラムを読む（BR4.10〜BR4.12）。
     *
     * @param roleId ロールの ID
     * @param schema スキーマの名前
     * @param table テーブルの名前
     * @param request 要求
     * @return 節の一覧
     */
    @GetMapping(path = "/columns", produces = MediaType.APPLICATION_JSON_VALUE)
    public PermissionNodesResponse columns(
            @PathVariable("roleId") long roleId,
            @RequestParam(name = SCHEMA, required = false) String schema,
            @RequestParam(name = TABLE, required = false) String table,
            HttpServletRequest request) {
        String schemaName = required(SCHEMA, schema);
        return nodes(service.columns(roleId, schemaName, required(TABLE, table)), request);
    }

    /**
     * 権限を保存する（BR4.1〜BR4.9）。
     *
     * @param roleId ロールの ID
     * @param body 範囲と対象ごとの値
     * @param request 要求
     * @return 204
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> save(
            @PathVariable("roleId") long roleId, @RequestBody PermissionSaveRequest body, HttpServletRequest request) {
        PermissionInputs.TargetInput scope = body.scope() == null ? null : target(body.scope());
        List<PermissionInputs.EntryInput> entries = body.entries() == null
                ? null
                : body.entries().stream().map(RolePermissionController::entry).toList();
        return switch (PermissionInputs.save(scope, entries)) {
            case PermissionInputs.Validation.Valid<PermissionInputs.SaveCommand>(
                    PermissionInputs.SaveCommand command) -> {
                long actor = context.currentUserId();
                yield RoleAdminController.respond(
                        service.savePermissions(actor, context.origin(request), roleId, command));
            }
            case PermissionInputs.Validation.Invalid<PermissionInputs.SaveCommand>(
                    String field,
                    FieldErrorReason reason) -> throw RoleFieldErrors.of(field, reason);
        };
    }

    /**
     * 今の DSL に無い対象の設定を消す（BR4.7）。
     *
     * @param roleId ロールの ID
     * @param body 消す対象
     * @param request 要求
     * @return 204
     */
    @PostMapping(path = "/clear", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> clear(
            @PathVariable("roleId") long roleId, @RequestBody PermissionClearRequest body, HttpServletRequest request) {
        List<PermissionInputs.TargetInput> targets = body.targets() == null
                ? null
                : body.targets().stream()
                        .map(target -> target == null ? null : target(target))
                        .toList();
        return switch (PermissionInputs.clear(targets)) {
            case PermissionInputs.Validation.Valid<PermissionInputs.ClearCommand>(
                    PermissionInputs.ClearCommand command) -> {
                long actor = context.currentUserId();
                yield RoleAdminController.respond(
                        service.clearPermissions(actor, context.origin(request), roleId, command));
            }
            case PermissionInputs.Validation.Invalid<PermissionInputs.ClearCommand>(
                    String field,
                    FieldErrorReason reason) -> throw RoleFieldErrors.of(field, reason);
        };
    }

    private static PermissionNodesResponse nodes(PermissionTreeResult result, HttpServletRequest request) {
        return switch (result) {
            case PermissionTreeResult.Found found -> {
                DisplayLanguage language =
                        AcceptLanguageResolver.resolve(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
                yield new PermissionNodesResponse(found.nodes().stream()
                        .map(node -> PermissionNodeResponse.from(node, language))
                        .toList());
            }
            case PermissionTreeResult.RoleNotFound _ -> throw new BusinessException(RoleProblemTypes.ROLE_NOT_FOUND);
            case PermissionTreeResult.DslNotApplied _ -> throw new BusinessException(RoleProblemTypes.DSL_NOT_APPLIED);
        };
    }

    /** 木の引数の欠け・空を 400 にする（長さでは拒否しない。BR4.12）。 */
    private static String required(String field, String value) {
        if (value == null || value.isEmpty()) {
            throw RoleFieldErrors.of(field, FieldErrorReason.REQUIRED);
        }
        return value;
    }

    private static PermissionInputs.TargetInput target(PermissionTargetRequest request) {
        return new PermissionInputs.TargetInput(request.schemaName(), request.tableName(), request.columnName());
    }

    private static PermissionInputs.EntryInput entry(PermissionEntryRequest request) {
        return request == null
                ? null
                : new PermissionInputs.EntryInput(
                        request.schemaName(),
                        request.tableName(),
                        request.columnName(),
                        request.main(),
                        request.create(),
                        request.delete());
    }
}
