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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.domain.BusinessDataProblemTypes;
import cherry.mastersmith.businessdata.domain.WriteOrigin;
import cherry.mastersmith.businessdata.domain.WriteRequest;
import cherry.mastersmith.businessdata.service.BusinessDataWriteService;
import cherry.mastersmith.businessdata.service.WriteResult;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.common.observability.TraceIdProvider;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 業務データの書き込みの API（Intent 261016-business-data-views の U2、契約 C3 を機能設計の X1 で直した形、functional-spec.md の 1節、
 * BR13.1）。3本とも「ログインだけ」（{@link ApiAccessLevel#AUTHENTICATED}）で、権限は業務処理の層がテーブルごとに判定する。
 *
 * <ul>
 *   <li>{@code POST /api/tables/rows}: 作成（201）
 *   <li>{@code PATCH /api/tables/rows}: 更新（200）
 *   <li>{@code POST /api/tables/rows/delete}: 削除（200）
 * </ul>
 *
 * <p>道は固定し、名前・主キー・値・定義の識別はすべて本文で受ける（値は道にも問い合わせにも載せない。BR12.3）。利用者 ID は要求の文脈から
 * 読み、本文から受けない。業務処理の層の結果の型を {@code switch} で場合を尽くして応答か {@link BusinessException} に変える（個々の応答は
 * 共通の変換が組み立てる。誤りの応答に値を入れない）。
 */
@RestController
@ApiAccess(ApiAccessLevel.AUTHENTICATED)
public class BusinessDataWriteController {

    private final BusinessDataWriteService service;

    private final BusinessDataRequestContext context;

    private final TraceIdProvider traceIdProvider;

    private final ProblemType accessDenied;

    /**
     * 作る。
     *
     * @param service 業務データの書き込みの業務処理
     * @param context 要求の文脈
     * @param traceIdProvider 要求中のトレースIDの参照
     * @param registry 問題の種類の一覧
     * @throws IllegalStateException 403 の問題の種類が一覧に無いとき（起動を止める）
     */
    public BusinessDataWriteController(
            BusinessDataWriteService service,
            BusinessDataRequestContext context,
            TraceIdProvider traceIdProvider,
            ProblemTypeRegistry registry) {
        this.service = service;
        this.context = context;
        this.traceIdProvider = traceIdProvider;
        this.accessDenied = registry.findByCode(BusinessDataReadController.ACCESS_DENIED_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "問題の種類 " + BusinessDataReadController.ACCESS_DENIED_CODE + " が一覧にありません（認可の機能の定義が要ります）"));
    }

    /**
     * 行を作る。
     *
     * @param body 本文
     * @param request 要求（送り手の情報だけを読む）
     * @return 作った行
     */
    @PostMapping(
            path = BusinessDataPaths.ROWS,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RowCreatedResponse create(@RequestBody RowCreateRequest body, HttpServletRequest request) {
        return switch (write(body.toRequest(), request)) {
            case WriteResult.Created created -> RowCreatedResponse.from(created);
            case WriteResult.Written written -> throw new IllegalStateException("作成の結果は Created です");
            case WriteResult.Rejected rejected -> throw toException(rejected);
        };
    }

    /**
     * 行を更新する。
     *
     * @param body 本文
     * @param request 要求（送り手の情報だけを読む）
     * @return 定義の識別
     */
    @PatchMapping(
            path = BusinessDataPaths.ROWS,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RowWrittenResponse update(@RequestBody RowUpdateRequest body, HttpServletRequest request) {
        return written(write(body.toRequest(), request));
    }

    /**
     * 行を削除する。
     *
     * @param body 本文
     * @param request 要求（送り手の情報だけを読む）
     * @return 定義の識別
     */
    @PostMapping(
            path = BusinessDataPaths.DELETE,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RowWrittenResponse delete(@RequestBody RowDeleteRequest body, HttpServletRequest request) {
        return written(write(body.toRequest(), request));
    }

    private WriteResult write(WriteRequest writeRequest, HttpServletRequest request) {
        long userId = context.currentUserId();
        WriteOrigin origin = new WriteOrigin(
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getRequestURI(),
                traceIdProvider.currentTraceId().orElse(null));
        return service.write(userId, writeRequest, origin);
    }

    private RowWrittenResponse written(WriteResult result) {
        return switch (result) {
            case WriteResult.Written written -> new RowWrittenResponse(DefinitionIdBody.from(written.definitionId()));
            case WriteResult.Created created -> throw new IllegalStateException("更新・削除の結果は Written です");
            case WriteResult.Rejected rejected -> throw toException(rejected);
        };
    }

    /** 拒否を問題の種類の業務エラーに変える（判定の順の ②〜⑩。BR1.1〜BR1.9）。 */
    BusinessException toException(WriteResult.Rejected rejected) {
        return switch (rejected.rejection()) {
            case DSL_NOT_APPLIED -> new BusinessException(BusinessDataProblemTypes.BUSINESS_DSL_NOT_APPLIED);
            case TABLE_NOT_FOUND -> new BusinessException(BusinessDataProblemTypes.BUSINESS_TABLE_NOT_FOUND);
            case ACCESS_DENIED -> new BusinessException(accessDenied);
            case DEFINITION_CHANGED -> new BusinessException(BusinessDataProblemTypes.BUSINESS_DEFINITION_CHANGED);
            case ROW_OPERATION_NOT_ALLOWED ->
                new BusinessException(BusinessDataProblemTypes.BUSINESS_ROW_OPERATION_NOT_ALLOWED);
            case WRITE_NOT_CONFIGURED -> new BusinessException(BusinessDataProblemTypes.BUSINESS_WRITE_NOT_CONFIGURED);
            // 判定の順の ⑤ を先に通っているため、400 の時点で定義は今と同じ（definitionChanged は偽）。
            case VALIDATION_FAILED -> BusinessDataFieldErrors.of(rejected.fieldErrors(), false);
            case TARGET_DB_UNAVAILABLE ->
                new BusinessException(BusinessDataProblemTypes.BUSINESS_TARGET_DB_UNAVAILABLE);
            case TARGET_DB_MISMATCH -> new BusinessException(BusinessDataProblemTypes.BUSINESS_TARGET_DB_MISMATCH);
            case DUPLICATE_VALUE ->
                BusinessDataFieldErrors.withFieldErrors(
                        BusinessDataProblemTypes.BUSINESS_DUPLICATE_VALUE, rejected.fieldErrors());
            case REFERENCE_NOT_FOUND ->
                BusinessDataFieldErrors.withFieldErrors(
                        BusinessDataProblemTypes.BUSINESS_REFERENCE_NOT_FOUND, rejected.fieldErrors());
            case SAVE_REJECTED ->
                BusinessDataFieldErrors.withFieldErrors(
                        BusinessDataProblemTypes.BUSINESS_SAVE_REJECTED, rejected.fieldErrors());
            case ROW_NOT_FOUND -> new BusinessException(BusinessDataProblemTypes.BUSINESS_ROW_NOT_FOUND);
            case ROW_NOT_UNIQUE -> new BusinessException(BusinessDataProblemTypes.BUSINESS_ROW_NOT_UNIQUE);
            case TARGET_DB_TIMEOUT -> new BusinessException(BusinessDataProblemTypes.BUSINESS_TARGET_DB_TIMEOUT);
        };
    }
}
