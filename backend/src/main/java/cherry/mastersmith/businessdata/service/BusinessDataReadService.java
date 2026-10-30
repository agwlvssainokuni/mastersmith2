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
package cherry.mastersmith.businessdata.service;

import cherry.mastersmith.businessdata.domain.AllowedOperation;
import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import cherry.mastersmith.businessdata.domain.KeyInput;
import cherry.mastersmith.businessdata.domain.KeyValidation;
import cherry.mastersmith.businessdata.domain.ReadRejection;
import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.SearchInput;
import cherry.mastersmith.businessdata.domain.SearchRequestValidator;
import cherry.mastersmith.businessdata.domain.SearchValidation;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.businessdata.repository.RowDetailResult;
import cherry.mastersmith.businessdata.repository.RowSearchResult;
import cherry.mastersmith.businessdata.repository.RowSqlExecutor;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.service.TargetReadAccess;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 業務データの定義・一覧と検索・詳細の業務処理（functional-spec.md の 2節・3節、logical-components.md の L3）。
 *
 * <p>判定の順（BR1.1）: ② DSL が無い → ③ DSL に無い名前 → ④ テーブルが NONE → ⑤ 詳細が許されない → ⑥ 入力の誤り → ⑦ 行が無い →
 * ⑧ 対象DB の失敗。権限の写しは要求1件で1回だけ取り（BR3.1）、取り終えてから対象DB の読み取りの口を呼ぶ（BR8.3）。内部DB の
 * トランザクションを開かない。
 *
 * <p>想定内の失敗は結果の型で返す。対象DB の失敗を code に変える所で、WARN を1行だけ出す（キーは {@code purpose}・{@code reason}・
 * {@code sqlState}・{@code vendorCode}・{@code exceptionClass}。値・名前・メッセージを出さない。NFR8.2）。想定外の失敗は、メッセージも
 * 原因も持たない {@link BusinessDataUnexpectedException} にして投げる。読み取りは監査に残さない（BR1.10）。
 */
@Service
public class BusinessDataReadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessDataReadService.class);

    private final TableAccessResolver resolver;

    private final TargetReadAccess readAccess;

    private final RowSqlExecutor executor;

    private final ReferenceLookup references;

    private final ColumnValueCodec codec = new ColumnValueCodec();

    /**
     * 作る。
     *
     * @param resolver 判定の材料を作る口
     * @param readAccess 業務データの読み取りの口
     * @param executor 行の SQL の部品
     * @param references 他テーブル参照の読み取りの流れ（Intent 261029-reference-lookup-cleanup の U1）
     */
    public BusinessDataReadService(
            TableAccessResolver resolver,
            TargetReadAccess readAccess,
            RowSqlExecutor executor,
            ReferenceLookup references) {
        this.resolver = resolver;
        this.readAccess = readAccess;
        this.executor = executor;
        this.references = references;
    }

    /**
     * テーブルの定義を返す。用途が VIEW なら対象DB に接続しない（BR8.4）。作成・更新の用途では、参照の項目のために参照先を読む
     * （Intent 261029-reference-lookup-cleanup の U1、BR4.1。読む REFERENCE があるときだけ接続を1本借りる）。対象DB の失敗は既存の
     * code に写し、定義の取得ごと失敗にする（BR1.5）。
     *
     * @param userId 利用者 ID（要求の文脈から読んだもの）
     * @param query 要求が指すテーブル
     * @param purpose 用途
     * @return 定義、または拒否
     */
    public DefinitionResult definition(long userId, TableQuery query, DefinitionPurpose purpose) {
        TableAccessResolution resolution = resolver.resolve(userId, query);
        if (resolution instanceof TableAccessResolution.Rejected rejected) {
            return ReadRejected.of(rejected.rejection());
        }
        TableAccessResolution.Resolved resolved = (TableAccessResolution.Resolved) resolution;
        boolean changed = changed(resolved.view(), query);
        return switch (references.definitionReferences(resolved, purpose)) {
            case ReferenceLookup.DefinitionReferences.Read read ->
                new DefinitionResult.Found(resolved.view(), changed, purpose, read.fields());
            case ReferenceLookup.DefinitionReferences.Failed failed ->
                ReadRejected.of(
                        switch (failed.reason()) {
                            case TIMEOUT -> ReadRejection.TARGET_DB_TIMEOUT;
                            case UNAVAILABLE -> ReadRejection.TARGET_DB_UNAVAILABLE;
                            case MISMATCH -> ReadRejection.TARGET_DB_MISMATCH;
                        });
        };
    }

    /**
     * 一覧と検索を読む（件数とページを1回の貸し出しで読む。BR5.6・BR8.4）。
     *
     * @param userId 利用者 ID（要求の文脈から読んだもの）
     * @param query 要求が指すテーブル
     * @param input 条件・並べ替え・ページ
     * @return ページ、または拒否
     */
    public SearchResult search(long userId, TableQuery query, SearchInput input) {
        TableAccessResolution resolution = resolver.resolve(userId, query);
        if (resolution instanceof TableAccessResolution.Rejected rejected) {
            return ReadRejected.of(rejected.rejection());
        }
        TableAccessView view = ((TableAccessResolution.Resolved) resolution).view();
        boolean changed = changed(view, query);
        SearchValidation validation = SearchRequestValidator.validateSearch(view, input, codec);
        if (validation instanceof SearchValidation.Invalid invalid) {
            return new ReadRejected(ReadRejection.VALIDATION_FAILED, invalid.errors(), changed);
        }
        SearchValidation.Valid valid = (SearchValidation.Valid) validation;
        TargetReadOutcome<RowSearchResult> outcome = readAccess.withRead(
                ReadPurpose.LIST,
                session -> executor.search(session, view, valid.conditions(), valid.page(), Optional.empty()));
        return switch (outcome) {
            case TargetReadOutcome.NotConfigured<RowSearchResult> notConfigured ->
                unavailable(ReadPurpose.LIST, "NOT_CONFIGURED", null, null, null);
            case TargetReadOutcome.Unavailable<RowSearchResult> unavailable ->
                unavailable(
                        ReadPurpose.LIST,
                        "UNAVAILABLE",
                        unavailable.sqlState(),
                        unavailable.vendorCode(),
                        unavailable.exceptionClass());
            case TargetReadOutcome.Done<RowSearchResult> done ->
                switch (done.value()) {
                    case RowSearchResult.Page page ->
                        new SearchResult.Found(
                                view, valid.page().page(), valid.page().size(), page.total(), page.rows(), changed);
                    case RowSearchResult.Failed failed -> failure(ReadPurpose.LIST, failed.failure(), changed);
                    // 参照先の指定を渡していないため来ない（来たら想定外の 500）。
                    case RowSearchResult.Rows rows -> throw new BusinessDataUnexpectedException();
                };
        };
    }

    /**
     * 行の詳細を読む（最大2行。BR6.3）。
     *
     * @param userId 利用者 ID（要求の文脈から読んだもの）
     * @param query 要求が指すテーブル
     * @param key 本文の主キー
     * @return 1行、または拒否
     */
    public RowReadResult get(long userId, TableQuery query, KeyInput key) {
        TableAccessResolution resolution = resolver.resolve(userId, query);
        if (resolution instanceof TableAccessResolution.Rejected rejected) {
            return ReadRejected.of(rejected.rejection());
        }
        TableAccessView view = ((TableAccessResolution.Resolved) resolution).view();
        if (!view.allows(AllowedOperation.DETAIL)) {
            // 主キーの値を照らす前に返す（BR1.5）。
            return ReadRejected.of(ReadRejection.ROW_OPERATION_NOT_ALLOWED);
        }
        boolean changed = changed(view, query);
        KeyValidation validation = SearchRequestValidator.validateKey(view, key, codec);
        if (validation instanceof KeyValidation.Invalid invalid) {
            return new ReadRejected(ReadRejection.VALIDATION_FAILED, invalid.errors(), changed);
        }
        KeyValidation.Valid valid = (KeyValidation.Valid) validation;
        TargetReadOutcome<RowDetailResult> outcome =
                readAccess.withRead(ReadPurpose.DETAIL, session -> executor.detail(session, view, valid.key()));
        return switch (outcome) {
            case TargetReadOutcome.NotConfigured<RowDetailResult> notConfigured ->
                unavailable(ReadPurpose.DETAIL, "NOT_CONFIGURED", null, null, null);
            case TargetReadOutcome.Unavailable<RowDetailResult> unavailable ->
                unavailable(
                        ReadPurpose.DETAIL,
                        "UNAVAILABLE",
                        unavailable.sqlState(),
                        unavailable.vendorCode(),
                        unavailable.exceptionClass());
            case TargetReadOutcome.Done<RowDetailResult> done ->
                switch (done.value()) {
                    case RowDetailResult.Found found -> new RowReadResult.Found(view, found.row(), changed);
                    case RowDetailResult.NotFound notFound -> ReadRejected.of(ReadRejection.ROW_NOT_FOUND);
                    case RowDetailResult.NotUnique notUnique -> ReadRejected.of(ReadRejection.ROW_NOT_UNIQUE);
                    case RowDetailResult.Failed failed -> failure(ReadPurpose.DETAIL, failed.failure(), changed);
                };
        };
    }

    private static boolean changed(TableAccessView view, TableQuery query) {
        return view.definitionId().changedFrom(query.definitionId());
    }

    /** 接続を借りられない・設定が無い（503 UNAVAILABLE）。 */
    private static ReadRejected unavailable(
            ReadPurpose purpose, String reason, String sqlState, Integer vendorCode, String exceptionClass) {
        warn(purpose, reason, sqlState, vendorCode, exceptionClass);
        return ReadRejected.of(ReadRejection.TARGET_DB_UNAVAILABLE);
    }

    /** 行の SQL の失敗を code に変える（BR1.8・BR8.5・BR8.6、D1: A）。 */
    private static ReadRejected failure(ReadPurpose purpose, RowQueryFailure failure, boolean changed) {
        warn(purpose, failure.reason().name(), failure.sqlState(), failure.vendorCode(), failure.exceptionClass());
        ReadRejection rejection =
                switch (failure.reason()) {
                    case TIMEOUT -> ReadRejection.TARGET_DB_TIMEOUT;
                    case UNAVAILABLE -> ReadRejection.TARGET_DB_UNAVAILABLE;
                    case MISMATCH, READ_CONVERSION -> ReadRejection.TARGET_DB_MISMATCH;
                    case INVALID_VALUE -> ReadRejection.VALIDATION_FAILED;
                    case UNEXPECTED -> throw new BusinessDataUnexpectedException();
                };
        // definitionChanged は 400 にだけ付ける（BR9.2）。
        return new ReadRejected(rejection, List.of(), rejection == ReadRejection.VALIDATION_FAILED && changed);
    }

    private static void warn(
            ReadPurpose purpose, String reason, String sqlState, Integer vendorCode, String exceptionClass) {
        LOGGER.atWarn()
                .addKeyValue("purpose", purpose)
                .addKeyValue("reason", reason)
                .addKeyValue("sqlState", sqlState)
                .addKeyValue("vendorCode", vendorCode)
                .addKeyValue("exceptionClass", exceptionClass)
                .log("対象DB の業務データを読めませんでした");
    }
}
