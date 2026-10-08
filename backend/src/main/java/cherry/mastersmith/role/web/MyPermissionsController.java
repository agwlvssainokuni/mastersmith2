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

import cherry.mastersmith.common.i18n.domain.AcceptLanguageResolver;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.role.service.MyPermissionService;
import cherry.mastersmith.role.service.MyPermissionTree;
import cherry.mastersmith.user.domain.FieldErrorReason;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自分の権限の木の API（Intent 261004-role-menu の U4、契約 C8、FS の 2.11。Should）。ログインした利用者だけ
 * （{@link ApiAccessLevel#AUTHENTICATED}。BR2.2）。主体は要求の文脈から読み、利用者を指す値を受け取らない。監査に残さない。
 *
 * <ul>
 *   <li>{@code GET /api/me/permissions/schemas}: 200 と1段目
 *   <li>{@code GET /api/me/permissions/tables?schema=}: 200 とスキーマの下のテーブル
 *   <li>{@code GET /api/me/permissions/columns?schema=&table=}: 200 とテーブルの下のカラム
 * </ul>
 *
 * <p>名前は問い合わせの引数で受け、欠け・空は 400、長さで拒否しない（BR4.12）。DSL が無いときは {@code items} が空、作業ロールが無いときは
 * {@code workRole} が null ですべて NONE・不可。表示名は要求の言語（Accept-Language）で選ぶ。
 */
@RestController
@ApiAccess(ApiAccessLevel.AUTHENTICATED)
@RequestMapping(MyPermissionsController.PATH)
public class MyPermissionsController {

    /** 自分の権限の木の API の道。 */
    public static final String PATH = "/api/me/permissions";

    private final MyPermissionService service;

    private final RoleRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service 自分の権限の木の業務処理
     * @param context 要求の文脈の読み取り
     */
    public MyPermissionsController(MyPermissionService service, RoleRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * 木の1段目を読む。
     *
     * @param request 要求
     * @return 1階層
     */
    @GetMapping(path = "/schemas", produces = MediaType.APPLICATION_JSON_VALUE)
    public MyPermissionNodesResponse schemas(HttpServletRequest request) {
        return respond(service.schemas(context.currentUserId()), request);
    }

    /**
     * スキーマの下のテーブルを読む。
     *
     * @param schema スキーマの名前
     * @param request 要求
     * @return 1階層
     */
    @GetMapping(path = "/tables", produces = MediaType.APPLICATION_JSON_VALUE)
    public MyPermissionNodesResponse tables(
            @RequestParam(name = RolePermissionController.SCHEMA, required = false) String schema,
            HttpServletRequest request) {
        String schemaName = required(RolePermissionController.SCHEMA, schema);
        return respond(service.tables(context.currentUserId(), schemaName), request);
    }

    /**
     * テーブルの下のカラムを読む。
     *
     * @param schema スキーマの名前
     * @param table テーブルの名前
     * @param request 要求
     * @return 1階層
     */
    @GetMapping(path = "/columns", produces = MediaType.APPLICATION_JSON_VALUE)
    public MyPermissionNodesResponse columns(
            @RequestParam(name = RolePermissionController.SCHEMA, required = false) String schema,
            @RequestParam(name = RolePermissionController.TABLE, required = false) String table,
            HttpServletRequest request) {
        String schemaName = required(RolePermissionController.SCHEMA, schema);
        String tableName = required(RolePermissionController.TABLE, table);
        return respond(service.columns(context.currentUserId(), schemaName, tableName), request);
    }

    private static MyPermissionNodesResponse respond(MyPermissionTree tree, HttpServletRequest request) {
        return MyPermissionNodesResponse.from(
                tree, AcceptLanguageResolver.resolve(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)));
    }

    /** 木の引数の欠け・空を 400 にする（長さでは拒否しない。BR4.12）。 */
    private static String required(String field, String value) {
        if (value == null || value.isEmpty()) {
            throw RoleFieldErrors.of(field, FieldErrorReason.REQUIRED);
        }
        return value;
    }
}
