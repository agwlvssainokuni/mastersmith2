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
import cherry.mastersmith.role.service.RoleTransferBody;
import cherry.mastersmith.role.service.RoleTransferService;
import cherry.mastersmith.role.service.TransferApplyResult;
import cherry.mastersmith.role.service.TransferCheckResult;
import cherry.mastersmith.role.service.TransferExport;
import cherry.mastersmith.role.transfer.TransferErrors;
import cherry.mastersmith.role.transfer.TransferFingerprint;
import cherry.mastersmith.user.domain.FieldErrorReason;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 権限の YAML の受け渡しの API（Intent 261004-role-menu の U4、契約 C7 と FS の 8節の差、{@code logical-components.md} の L1、FS の
 * 2.13〜2.15）。3つの口はすべて管理者だけ（{@link ApiAccessLevel#ADMIN}。BR2.1）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/role-transfer/export}: 200 と {@code application/yaml} の添付（BR9.14）。本文が読み込みの上限を超えたら
 *       {@value #EXCEEDS_HEADER}{@code : true} を付ける（NFR2.9）
 *   <li>{@code POST /api/admin/role-transfer/check}: 本文は {@code application/yaml}（ほかは 415）。200 と変わる点の一覧・指紋
 *       （BR9.2・BR9.15）
 *   <li>{@code POST /api/admin/role-transfer/apply}: 本文は {@code application/yaml}、指紋は {@value #FINGERPRINT_HEADER}（無い・16 進数の
 *       小文字 64 文字でないときは 400）。200 と作成・置き換えの数（BR9.10〜BR9.12）
 * </ul>
 *
 * <p>本文はこの層で読まず、要求の入力の流れのまま業務処理に渡す（業務処理が入口を取った後に上限まで読む。{@code reliability-design.md}
 * 2.6）。本文の大きさの上限は {@link RoleWebConfig} が道ごとに登録する（413）。誤りの応答は共通の変換で作る: 400
 * {@code ROLE_TRANSFER_INVALID}（{@code errors} に理由・行・列・場所。入れた値と部品の例外の文は載せない）、409 {@code ROLE_BUSY}・
 * {@code DSL_NOT_APPLIED}・{@code ROLE_TRANSFER_STALE}・{@code ROLE_NO_CHANGE}・{@code ROLE_NAME_DUPLICATE}、413
 * {@code PAYLOAD_TOO_LARGE}。
 */
@RestController
@ApiAccess(ApiAccessLevel.ADMIN)
public class RoleTransferController {

    /** 本文の型。 */
    public static final String APPLICATION_YAML = "application/yaml";

    /** 適用の指紋の要求のヘッダー。 */
    public static final String FINGERPRINT_HEADER = "X-Role-Transfer-Fingerprint";

    /** 書き出しが読み込みの上限を超えたことを知らせる応答のヘッダー。 */
    public static final String EXCEEDS_HEADER = "X-Role-Transfer-Exceeds-Import-Limit";

    /** 書き出しの添付のファイル名。 */
    static final String EXPORT_FILE_NAME = "role-permissions.yaml";

    /** 誤りの一覧の応答の項目。 */
    static final String ERRORS = "errors";

    private final RoleTransferService service;

    private final RoleRequestContextResolver context;

    /**
     * 作る。
     *
     * @param service 権限の YAML の受け渡しの業務処理
     * @param context 要求の文脈の読み取り
     */
    public RoleTransferController(RoleTransferService service, RoleRequestContextResolver context) {
        this.service = service;
        this.context = context;
    }

    /**
     * 権限の YAML を書き出す（BR9.13・BR9.14、NFR2.9）。
     *
     * @return 添付の YAML
     */
    @GetMapping(path = RoleTransferPaths.EXPORT, produces = APPLICATION_YAML)
    public ResponseEntity<byte[]> export() {
        TransferExport export = service.export();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(APPLICATION_YAML))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(EXPORT_FILE_NAME)
                                .build()
                                .toString());
        if (export.exceedsImportLimit()) {
            builder.header(EXCEEDS_HEADER, "true");
        }
        return builder.body(export.yaml().bytes());
    }

    /**
     * 権限の YAML を確かめる（BR9.2〜BR9.9・BR9.13・BR9.15）。
     *
     * @param request 要求（本文の流れ）
     * @return 変わる点の一覧と指紋
     * @throws IOException 本文の流れを開けないとき
     */
    @PostMapping(
            path = RoleTransferPaths.CHECK,
            consumes = APPLICATION_YAML,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TransferCheckResponse check(HttpServletRequest request) throws IOException {
        return switch (service.check(new RoleTransferBody(request.getInputStream()))) {
            case TransferCheckResult.Checked checked -> TransferCheckResponse.from(checked.plan());
            case TransferCheckResult.Invalid invalid -> throw invalid(invalid.errors());
            case TransferCheckResult.TooLarge _ -> throw new BusinessException(CommonProblemTypes.PAYLOAD_TOO_LARGE);
            case TransferCheckResult.DslNotApplied _ -> throw new BusinessException(RoleProblemTypes.DSL_NOT_APPLIED);
            case TransferCheckResult.Busy _ -> throw new BusinessException(RoleProblemTypes.ROLE_BUSY);
        };
    }

    /**
     * 権限の YAML を適用する（BR9.2・BR9.10〜BR9.13、BR11.7）。
     *
     * @param fingerprint 確かめの指紋（要求のヘッダー）
     * @param request 要求（本文の流れ）
     * @return 作成・置き換えの数
     * @throws IOException 本文の流れを開けないとき
     */
    @PostMapping(
            path = RoleTransferPaths.APPLY,
            consumes = APPLICATION_YAML,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TransferResultResponse apply(
            @RequestHeader(name = FINGERPRINT_HEADER, required = false) String fingerprint, HttpServletRequest request)
            throws IOException {
        if (fingerprint == null || fingerprint.isEmpty()) {
            throw RoleFieldErrors.of(FINGERPRINT_HEADER, FieldErrorReason.REQUIRED);
        }
        if (!TransferFingerprint.isWellFormed(fingerprint)) {
            throw RoleFieldErrors.of(FINGERPRINT_HEADER, FieldErrorReason.INVALID_VALUE);
        }
        long actor = context.currentUserId();
        return switch (service.apply(
                actor, context.origin(request), new RoleTransferBody(request.getInputStream()), fingerprint)) {
            case TransferApplyResult.Applied applied ->
                new TransferResultResponse(applied.created(), applied.replaced());
            case TransferApplyResult.Invalid invalid -> throw invalid(invalid.errors());
            case TransferApplyResult.TooLarge _ -> throw new BusinessException(CommonProblemTypes.PAYLOAD_TOO_LARGE);
            case TransferApplyResult.Rejected rejected ->
                throw new BusinessException(RoleProblemTypes.of(rejected.rejection()));
            case TransferApplyResult.Busy _ -> throw new BusinessException(RoleProblemTypes.ROLE_BUSY);
        };
    }

    /** 誤りの一覧を載せた 400 {@code ROLE_TRANSFER_INVALID} を作る（理由・行・列・場所だけ。1件の文字列は場所を伏せる）。 */
    private static BusinessException invalid(TransferErrors errors) {
        List<TransferErrorItem> items =
                errors.errors().stream().map(TransferErrorItem::from).toList();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(ERRORS, items);
        return new BusinessException(RoleProblemTypes.ROLE_TRANSFER_INVALID, null, properties);
    }
}
