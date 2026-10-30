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

import cherry.mastersmith.businessdata.domain.BusinessDataFieldError;
import cherry.mastersmith.businessdata.domain.BusinessDataFieldErrorReason;
import cherry.mastersmith.businessdata.domain.BusinessDataProblemTypes;
import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import cherry.mastersmith.businessdata.domain.ReferenceRowsQuery;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.service.BusinessDataReadService;
import cherry.mastersmith.businessdata.service.DefinitionResult;
import cherry.mastersmith.businessdata.service.ReadRejected;
import cherry.mastersmith.businessdata.service.ReferenceLookup;
import cherry.mastersmith.businessdata.service.ReferenceRowsResult;
import cherry.mastersmith.businessdata.service.RowReadResult;
import cherry.mastersmith.businessdata.service.SearchResult;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 業務データの読み取りの API（契約 C2 を機能設計の X1 で直した形、functional-spec.md の 1節、BR11.1）。4本とも「ログインだけ」
 * （{@link ApiAccessLevel#AUTHENTICATED}）で、権限は業務処理の層がテーブルごとに判定する。
 *
 * <ul>
 *   <li>{@code POST /api/tables/definition}: テーブルの定義
 *   <li>{@code POST /api/tables/rows/search}: 一覧と検索
 *   <li>{@code POST /api/tables/rows/get}: 行の詳細
 *   <li>{@code POST /api/tables/reference/rows}: 参照先の検索とラベルの1件読み（Intent 261029-reference-lookup-cleanup の U1、
 *       BR1.2・BR11.1）
 * </ul>
 *
 * <p>利用者 ID は要求の文脈から読み、本文から受けない。業務処理の層の結果の型を {@code switch} で場合を尽くして
 * {@link BusinessException} に変える（個々の応答は共通の変換が組み立てる）。403 の {@code ACCESS_DENIED} は access に依存せず、共通の
 * 問題の種類の一覧から code で起動時に引く。
 */
@RestController
@ApiAccess(ApiAccessLevel.AUTHENTICATED)
public class BusinessDataReadController {

    /** 403 の問題の種類の code（{@code access} の一覧にある）。 */
    static final String ACCESS_DENIED_CODE = "ACCESS_DENIED";

    private final BusinessDataReadService service;

    private final ReferenceLookup references;

    private final BusinessDataRequestContext context;

    private final ProblemType accessDenied;

    /**
     * 作る。
     *
     * @param service 業務データの読み取りの業務処理
     * @param references 他テーブル参照の読み取りの流れ（Intent 261029-reference-lookup-cleanup の U1）
     * @param context 要求の文脈
     * @param registry 問題の種類の一覧
     * @throws IllegalStateException 403 の問題の種類が一覧に無いとき（起動を止める）
     */
    public BusinessDataReadController(
            BusinessDataReadService service,
            ReferenceLookup references,
            BusinessDataRequestContext context,
            ProblemTypeRegistry registry) {
        this.service = service;
        this.references = references;
        this.context = context;
        this.accessDenied = registry.findByCode(ACCESS_DENIED_CODE)
                .orElseThrow(
                        () -> new IllegalStateException("問題の種類 " + ACCESS_DENIED_CODE + " が一覧にありません（認可の機能の定義が要ります）"));
    }

    /**
     * テーブルの定義を返す。
     *
     * @param body 本文
     * @return 定義
     */
    @PostMapping(
            path = BusinessDataPaths.DEFINITION,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TableDefinitionResponse definition(@RequestBody DefinitionRequest body) {
        TableQuery query =
                new TableQuery(body.schema(), body.table(), DefinitionIdBody.toRequested(body.definitionId()));
        // 本文の形（用途の列挙）を、DSL を見る前に確かめる（BR1.1 の ②）。
        DefinitionPurpose purpose =
                body.toPurpose().orElseThrow(() -> BusinessDataFieldErrors.of(java.util.List.of(), false));
        return switch (service.definition(context.currentUserId(), query, purpose)) {
            case DefinitionResult.Found found ->
                TableDefinitionResponse.from(
                        found.view(), found.definitionChanged(), found.purpose(), found.references());
            case ReadRejected rejected -> throw toException(rejected);
        };
    }

    /**
     * 一覧と検索を返す。
     *
     * @param body 本文
     * @return 1ページ
     */
    @PostMapping(
            path = BusinessDataPaths.SEARCH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RowPageResponse search(@RequestBody SearchRequest body) {
        TableQuery query =
                new TableQuery(body.schema(), body.table(), DefinitionIdBody.toRequested(body.definitionId()));
        return switch (service.search(context.currentUserId(), query, body.toInput())) {
            case SearchResult.Found found -> RowPageResponse.from(found);
            case ReadRejected rejected -> throw toException(rejected);
        };
    }

    /**
     * 行の詳細を返す。
     *
     * @param body 本文
     * @return 1行
     */
    @PostMapping(
            path = BusinessDataPaths.GET,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RowDetailResponse get(@RequestBody RowGetRequest body) {
        TableQuery query =
                new TableQuery(body.schema(), body.table(), DefinitionIdBody.toRequested(body.definitionId()));
        return switch (service.get(context.currentUserId(), query, body.toInput())) {
            case RowReadResult.Found found -> RowDetailResponse.from(found);
            case ReadRejected rejected -> throw toException(rejected);
        };
    }

    /**
     * 参照先の検索とラベルの1件読みを返す（Intent 261029-reference-lookup-cleanup の U1、判定の順 BR1.2）。本文の形の誤りは DSL を見る
     * 前に、{@code fieldErrors} を付けない 400 にする（②）。名前・権限・DSL・行の有無の拒否はすべて 403 の同じ応答（③・⑥、
     * {@code fieldErrors} なし）。検索の {@code EQUALS} の値の型の誤りは 400 の {@code INVALID_VALUE}（④）。
     *
     * @param body 本文（値は受け取る時点から伏せる型で受ける）
     * @return 行
     */
    @PostMapping(
            path = BusinessDataPaths.REFERENCE_ROWS,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ReferenceRowsResponse referenceRows(@RequestBody ReferenceRowsRequest body) {
        ReferenceRowsQuery query =
                body.toQuery().orElseThrow(() -> new BusinessException(CommonProblemTypes.VALIDATION_FAILED));
        long userId = context.currentUserId();
        ReferenceRowsResult result =
                switch (query.mode()) {
                    case SEARCH -> references.search(userId, query);
                    case VALUE -> references.label(userId, query);
                };
        return switch (result) {
            case ReferenceRowsResult.Found found -> ReferenceRowsResponse.from(found.rows());
            case ReferenceRowsResult.Denied denied -> throw new BusinessException(accessDenied);
            case ReferenceRowsResult.InvalidValue invalid ->
                throw BusinessDataFieldErrors.withFieldErrors(
                        CommonProblemTypes.VALIDATION_FAILED,
                        List.of(new BusinessDataFieldError(
                                invalid.column(), BusinessDataFieldErrorReason.INVALID_VALUE)));
            case ReferenceRowsResult.Failed failed ->
                throw new BusinessException(
                        switch (failed.reason()) {
                            case TIMEOUT -> BusinessDataProblemTypes.BUSINESS_TARGET_DB_TIMEOUT;
                            case UNAVAILABLE -> BusinessDataProblemTypes.BUSINESS_TARGET_DB_UNAVAILABLE;
                            case MISMATCH -> BusinessDataProblemTypes.BUSINESS_TARGET_DB_MISMATCH;
                        });
        };
    }

    /** 拒否を問題の種類の業務エラーに変える（判定の順の ②〜⑧。BR1.2〜BR1.9）。 */
    BusinessException toException(ReadRejected rejected) {
        return switch (rejected.rejection()) {
            case DSL_NOT_APPLIED -> new BusinessException(BusinessDataProblemTypes.BUSINESS_DSL_NOT_APPLIED);
            case TABLE_NOT_FOUND -> new BusinessException(BusinessDataProblemTypes.BUSINESS_TABLE_NOT_FOUND);
            case ACCESS_DENIED -> new BusinessException(accessDenied);
            case ROW_OPERATION_NOT_ALLOWED ->
                new BusinessException(BusinessDataProblemTypes.BUSINESS_ROW_OPERATION_NOT_ALLOWED);
            case VALIDATION_FAILED -> BusinessDataFieldErrors.of(rejected.fieldErrors(), rejected.definitionChanged());
            case ROW_NOT_FOUND -> new BusinessException(BusinessDataProblemTypes.BUSINESS_ROW_NOT_FOUND);
            case ROW_NOT_UNIQUE -> new BusinessException(BusinessDataProblemTypes.BUSINESS_ROW_NOT_UNIQUE);
            case TARGET_DB_UNAVAILABLE ->
                new BusinessException(BusinessDataProblemTypes.BUSINESS_TARGET_DB_UNAVAILABLE);
            case TARGET_DB_TIMEOUT -> new BusinessException(BusinessDataProblemTypes.BUSINESS_TARGET_DB_TIMEOUT);
            case TARGET_DB_MISMATCH -> new BusinessException(BusinessDataProblemTypes.BUSINESS_TARGET_DB_MISMATCH);
        };
    }
}
