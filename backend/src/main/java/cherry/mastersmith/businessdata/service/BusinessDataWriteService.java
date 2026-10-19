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
import cherry.mastersmith.businessdata.domain.AuditDetailBuilder;
import cherry.mastersmith.businessdata.domain.BoundValue;
import cherry.mastersmith.businessdata.domain.BusinessDataAuditReason;
import cherry.mastersmith.businessdata.domain.BusinessDataChangedEvent;
import cherry.mastersmith.businessdata.domain.BusinessDataFieldError;
import cherry.mastersmith.businessdata.domain.BusinessDataFieldErrorReason;
import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.DeadlinePatternMatcher;
import cherry.mastersmith.businessdata.domain.ReadRejection;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.RowWriteFailure;
import cherry.mastersmith.businessdata.domain.RowWriteFailureReason;
import cherry.mastersmith.businessdata.domain.RowWriteStage;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TypedValue;
import cherry.mastersmith.businessdata.domain.WriteOperation;
import cherry.mastersmith.businessdata.domain.WriteOrigin;
import cherry.mastersmith.businessdata.domain.WriteRejection;
import cherry.mastersmith.businessdata.domain.WriteRequest;
import cherry.mastersmith.businessdata.domain.WriteRequestValidator;
import cherry.mastersmith.businessdata.domain.WriteValidation;
import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.businessdata.repository.RowChangeResult;
import cherry.mastersmith.businessdata.repository.RowInsertResult;
import cherry.mastersmith.businessdata.repository.RowInspectResult;
import cherry.mastersmith.businessdata.repository.RowPrecheckResult;
import cherry.mastersmith.businessdata.repository.RowSqlExecutor;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.targetdb.domain.TargetWriteFailure;
import cherry.mastersmith.targetdb.domain.TargetWriteOutcome;
import cherry.mastersmith.targetdb.domain.TargetWriteSession;
import cherry.mastersmith.targetdb.domain.TargetWriteStage;
import cherry.mastersmith.targetdb.domain.WriteStep;
import cherry.mastersmith.targetdb.service.TargetWriteAccess;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 業務データの作成・更新・削除の業務処理（Intent 261016-business-data-views の U2、functional-spec.md の 2節・3節、
 * logical-components.md の L3・L10）。
 *
 * <p>判定の順（BR1.1）: ②〜④ は U1 の解決の口（権限の写しは要求1件で1回だけ取り、内部DB の短い読み取りで取り終える）→ ⑤ 定義が古い
 * → ⑥ 主キーとビューの決まり → ⑦ 補助権限・FULL のカラム → ⑧ 書き込みの設定 → ⑨ 入力の誤り → ⑩ 対象DB の結果。⑤〜⑨ で拒んだときは
 * 対象DB に触れない。書き込みの口は1回だけ呼ぶ（事前の問い合わせ・書き込みの文・違反の後の確かめを同じ接続で行う。BR9.4）。
 *
 * <p>内部DB のトランザクションを開かない（{@code @Transactional}・{@code TransactionTemplate} を使わない）。対象DB の失敗を code に変える
 * この1か所でだけ、WARN を1行出す（キーは {@code purpose}・{@code stage}・{@code reason}・{@code sqlState}・{@code vendorCode}・
 * {@code exceptionClass}。値・名前・メッセージを出さない。NFR8.4）。業務の失敗（同じ値・参照先に無い値・行が無い・2 以上）はログを
 * 出さない。想定外の失敗は、メッセージも原因も持たない {@link BusinessDataUnexpectedException} にして投げる。
 *
 * <p>監査の出来事は、書き込みの口から戻った後（対象DB の接続を返した後）か、⑥ で拒んだ直後に出す（BR11.1）。⑥ を残すのは、その操作の
 * 補助権限を持つ人の拒否だけ（NFR4.14）。
 */
@Service
public class BusinessDataWriteService {

    /** ログの目的の値。 */
    static final String PURPOSE = "WRITE";

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessDataWriteService.class);

    private final TableAccessResolver resolver;

    private final TargetWriteAccess writeAccess;

    private final RowSqlExecutor executor;

    private final ApplicationEventPublisher events;

    private final Clock clock;

    private final DeadlinePatternMatcher matcher;

    private final ColumnValueCodec codec = new ColumnValueCodec();

    /**
     * 作る。
     *
     * @param resolver 判定の材料を作る口
     * @param writeAccess 業務データの書き込みの口
     * @param executor 行の SQL の部品
     * @param events 出来事の発行
     * @param clock 出来事の日時と pattern の締め切りの時計
     */
    public BusinessDataWriteService(
            TableAccessResolver resolver,
            TargetWriteAccess writeAccess,
            RowSqlExecutor executor,
            ApplicationEventPublisher events,
            Clock clock) {
        this.resolver = resolver;
        this.writeAccess = writeAccess;
        this.executor = executor;
        this.events = events;
        this.clock = clock;
        this.matcher = new DeadlinePatternMatcher(clock);
    }

    /**
     * 作成・更新・削除を行う。
     *
     * @param userId 利用者 ID（要求の文脈から読んだもの）
     * @param request 要求（確かめる前）
     * @param origin 要求の送り手の情報
     * @return 結果
     * @throws BusinessDataUnexpectedException 想定外の失敗（メッセージと原因を持たない）
     */
    public WriteResult write(long userId, WriteRequest request, WriteOrigin origin) {
        Objects.requireNonNull(request, "request は必須です");
        Objects.requireNonNull(origin, "origin は必須です");
        WriteOperation operation = request.operation();
        TableAccessResolution resolution = resolver.resolve(userId, request.query());
        if (resolution instanceof TableAccessResolution.Rejected rejected) {
            return WriteResult.Rejected.of(fromRead(rejected.rejection()));
        }
        TableAccessView view = ((TableAccessResolution.Resolved) resolution).view();
        // ⑤ 定義の識別が無い・今と違う（書き込みは識別を必須にする。BR1.5）。
        if (request.query().definitionId() == null
                || view.definitionId().changedFrom(request.query().definitionId())) {
            return WriteResult.Rejected.of(WriteRejection.DEFINITION_CHANGED);
        }
        // ⑥ 主キーとビューの決まり（構造の理由は権限によらず同じ答え。BR1.4）。
        if (structurallyForbidden(view, operation)) {
            if (hasAuxiliaryPermission(view, operation)) {
                publish(userId, view, operation, BusinessDataAuditReason.BUSINESS_ROW_OP_NOT_ALLOWED, Map.of(), origin);
            }
            return WriteResult.Rejected.of(WriteRejection.ROW_OPERATION_NOT_ALLOWED);
        }
        // ⑦ 補助権限と FULL のカラム（BR1.6）。
        if (!permitted(view, operation)) {
            return WriteResult.Rejected.of(WriteRejection.ACCESS_DENIED);
        }
        // ⑧ 書き込みの設定（BR1.7。権限の確かめを通った要求だけに返す）。
        if (!view.writeConfigured() || !view.allows(operation.allowedOperation())) {
            return WriteResult.Rejected.of(WriteRejection.WRITE_NOT_CONFIGURED);
        }
        // ⑨ 入力の誤り（BR2〜BR4）。
        WriteValidation validation =
                WriteRequestValidator.validate(view, operation, request.key(), request.values(), codec, matcher);
        if (validation instanceof WriteValidation.Invalid invalid) {
            return new WriteResult.Rejected(WriteRejection.VALIDATION_FAILED, invalid.errors());
        }
        WriteValidation.Valid valid = (WriteValidation.Valid) validation;
        // ⑩ 書き込みの口を1回だけ呼ぶ。
        TargetWriteOutcome<Attempt> outcome = writeAccess.withWrite(session -> work(session, view, operation, valid));
        Attempt attempt = attemptOf(outcome);
        if (attempt instanceof Attempt.Unexpected unexpected) {
            warn(
                    unexpected.stage(),
                    "UNEXPECTED",
                    unexpected.sqlState(),
                    unexpected.vendorCode(),
                    unexpected.exceptionClass());
            throw new BusinessDataUnexpectedException();
        }
        return finish(userId, view, operation, valid, attempt, origin);
    }

    /** 書き込みの口に渡す処理（例外を投げずに答えを返す）。 */
    private WriteStep<Attempt> work(
            TargetWriteSession session, TableAccessView view, WriteOperation operation, WriteValidation.Valid valid) {
        List<TypedValue> values = valid.values();
        RowKey key = valid.key();
        if (operation != WriteOperation.DELETE) {
            switch (executor.precheckUnique(session, view, values, key)) {
                case RowPrecheckResult.Clear clear -> {}
                case RowPrecheckResult.Duplicate duplicate -> {
                    return new WriteStep.Rollback<>(new Attempt.Rejected(
                            WriteRejection.DUPLICATE_VALUE,
                            duplicate.columns().stream()
                                    .map(c -> new BusinessDataFieldError(c, BusinessDataFieldErrorReason.DUPLICATE))
                                    .toList(),
                            null));
                }
                case RowPrecheckResult.Failed failed -> {
                    return new WriteStep.Rollback<>(fromFailure(failed.failure(), operation, values));
                }
            }
        }
        return switch (operation) {
            case CREATE ->
                switch (executor.insert(session, view, values)) {
                    case RowInsertResult.Inserted inserted ->
                        new WriteStep.Commit<>(new Attempt.Created(inserted.key()));
                    case RowInsertResult.Failed failed -> afterFailure(failed.failure(), view, operation, values, null);
                };
            case UPDATE ->
                switch (executor.update(session, view, values, key)) {
                    case RowChangeResult.Changed changed -> byCount(changed.count());
                    case RowChangeResult.Failed failed -> afterFailure(failed.failure(), view, operation, values, key);
                };
            case DELETE ->
                switch (executor.delete(session, view, key)) {
                    case RowChangeResult.Changed changed -> byCount(changed.count());
                    case RowChangeResult.Failed failed ->
                        new WriteStep.Rollback<>(fromFailure(failed.failure(), operation, values));
                };
        };
    }

    /** 当たった行の数で答える（1 なら確定、0・2 以上なら巻き戻し。BR8.1）。 */
    private static WriteStep<Attempt> byCount(long count) {
        if (count == 1) {
            return new WriteStep.Commit<>(new Attempt.Changed());
        }
        WriteRejection rejection = count == 0 ? WriteRejection.ROW_NOT_FOUND : WriteRejection.ROW_NOT_UNIQUE;
        return new WriteStep.Rollback<>(new Attempt.Rejected(rejection, List.of(), null));
    }

    /** 書き込みの文の失敗の答え（一意・外部キーの違反だけ、セーブポイントへ戻して確かめる。BR6.2）。 */
    private WriteStep<Attempt> afterFailure(
            RowWriteFailure failure,
            TableAccessView view,
            WriteOperation operation,
            List<TypedValue> values,
            RowKey key) {
        boolean duplicate = failure.reason() == RowWriteFailureReason.DUPLICATE;
        boolean reference = failure.reason() == RowWriteFailureReason.REFERENCE_NOT_FOUND;
        if (!duplicate && !reference) {
            return new WriteStep.Rollback<>(fromFailure(failure, operation, values));
        }
        WriteRejection rejection = duplicate ? WriteRejection.DUPLICATE_VALUE : WriteRejection.REFERENCE_NOT_FOUND;
        BusinessDataFieldErrorReason reason =
                duplicate ? BusinessDataFieldErrorReason.DUPLICATE : BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND;
        return new WriteStep.RollbackToSavepointThenInspect<>(
                inspecting -> switch (executor.inspectViolation(inspecting, view, reference, values, key)) {
                    case RowInspectResult.Inspected inspected ->
                        new Attempt.Rejected(
                                rejection,
                                List.of(new BusinessDataFieldError(
                                        inspected.column().orElse(""), reason)),
                                null);
                    case RowInspectResult.Failed failed -> fromInspectFailure(failed.failure());
                },
                BusinessDataWriteService::fromInspectFailure);
    }

    /** 違反の後の確かめの問い合わせの失敗（BR6.4・D4: 時間切れ・断絶は UNAVAILABLE、ほかは SAVE_REJECTED）。 */
    private static Attempt fromInspectFailure(RowWriteFailure failure) {
        WriteRejection rejection = failure.reason() == RowWriteFailureReason.UNAVAILABLE
                ? WriteRejection.TARGET_DB_UNAVAILABLE
                : WriteRejection.SAVE_REJECTED;
        return new Attempt.Rejected(rejection, List.of(), failure);
    }

    /** 違反の後の口の段（戻し・確かめの例外・最後の巻き戻し）の失敗（D4）。 */
    private static Attempt fromInspectFailure(TargetWriteFailure failure) {
        RowWriteFailure row = new RowWriteFailure(
                failure.interrupted() ? RowWriteFailureReason.UNAVAILABLE : RowWriteFailureReason.REJECTED,
                stageOf(failure.stage()),
                failure.sqlState(),
                failure.vendorCode(),
                failure.exceptionClass());
        return fromInspectFailure(row);
    }

    /** 書き込みの SQL の失敗を拒否に写す（BR1.8・BR6.3・BR10.1、D1〜D3）。 */
    private static Attempt fromFailure(RowWriteFailure failure, WriteOperation operation, List<TypedValue> values) {
        return switch (failure.reason()) {
            case UNAVAILABLE, CONFLICT ->
                new Attempt.Rejected(WriteRejection.TARGET_DB_UNAVAILABLE, List.of(), failure);
            case TIMEOUT -> new Attempt.Rejected(WriteRejection.TARGET_DB_TIMEOUT, List.of(), failure);
            case MISMATCH -> new Attempt.Rejected(WriteRejection.TARGET_DB_MISMATCH, List.of(), failure);
            case DUPLICATE -> new Attempt.Rejected(WriteRejection.DUPLICATE_VALUE, List.of(), null);
            case REFERENCE_NOT_FOUND -> new Attempt.Rejected(WriteRejection.REFERENCE_NOT_FOUND, List.of(), null);
            case NOT_NULL ->
                new Attempt.Rejected(WriteRejection.SAVE_REJECTED, List.of(notNullError(operation, values)), failure);
            case REJECTED -> new Attempt.Rejected(WriteRejection.SAVE_REJECTED, List.of(), failure);
            case UNEXPECTED ->
                new Attempt.Unexpected(
                        failure.stage(), failure.sqlState(), failure.vendorCode(), failure.exceptionClass());
        };
    }

    /**
     * NOT NULL の違反の誤り（BR6.3）。検証は入力できるカラムの NULL を通さないため、作成の違反は入力できないカラムが原因
     * （{@code NOT_EDITABLE_NOT_NULL}。カラムは示さない）。更新は、NULL を送ったカラムがちょうど1つならそのカラムの {@code NOT_NULL}。
     */
    private static BusinessDataFieldError notNullError(WriteOperation operation, List<TypedValue> values) {
        if (operation == WriteOperation.CREATE) {
            return new BusinessDataFieldError("", BusinessDataFieldErrorReason.NOT_EDITABLE_NOT_NULL);
        }
        List<String> nulls = values.stream()
                .filter(v -> v.value().value() == null)
                .map(TypedValue::column)
                .toList();
        return new BusinessDataFieldError(
                nulls.size() == 1 ? nulls.getFirst() : "", BusinessDataFieldErrorReason.NOT_NULL);
    }

    /** 口の結果を試みの結果にする。 */
    private static Attempt attemptOf(TargetWriteOutcome<Attempt> outcome) {
        return switch (outcome) {
            case TargetWriteOutcome.Done<Attempt> done -> done.value();
            case TargetWriteOutcome.NotConfigured<Attempt> notConfigured ->
                new Attempt.Rejected(WriteRejection.WRITE_NOT_CONFIGURED, List.of(), null);
            case TargetWriteOutcome.Unavailable<Attempt> unavailable ->
                new Attempt.Rejected(
                        WriteRejection.TARGET_DB_UNAVAILABLE,
                        List.of(),
                        new RowWriteFailure(
                                RowWriteFailureReason.UNAVAILABLE,
                                RowWriteStage.BORROW,
                                unavailable.sqlState(),
                                unavailable.vendorCode(),
                                unavailable.exceptionClass()));
            case TargetWriteOutcome.Failed<Attempt> failed -> fromAccessFailure(failed.failure());
        };
    }

    /** 口が受け持つ段の失敗（確定の段は D3、処理の想定外の例外は 500）。 */
    private static Attempt fromAccessFailure(TargetWriteFailure failure) {
        RowWriteStage stage = stageOf(failure.stage());
        return switch (failure.stage()) {
            case COMMIT -> {
                boolean deferredConstraint =
                        failure.sqlState() != null && failure.sqlState().startsWith("23");
                RowWriteFailureReason reason =
                        deferredConstraint ? RowWriteFailureReason.REJECTED : RowWriteFailureReason.TIMEOUT;
                yield new Attempt.Rejected(
                        deferredConstraint ? WriteRejection.SAVE_REJECTED : WriteRejection.TARGET_DB_TIMEOUT,
                        List.of(),
                        new RowWriteFailure(
                                reason, stage, failure.sqlState(), failure.vendorCode(), failure.exceptionClass()));
            }
            case WORK, INSPECT ->
                new Attempt.Unexpected(stage, failure.sqlState(), failure.vendorCode(), failure.exceptionClass());
            case BORROW, ROLLBACK_TO_SAVEPOINT, ROLLBACK ->
                new Attempt.Rejected(
                        WriteRejection.TARGET_DB_UNAVAILABLE,
                        List.of(),
                        new RowWriteFailure(
                                RowWriteFailureReason.UNAVAILABLE,
                                stage,
                                failure.sqlState(),
                                failure.vendorCode(),
                                failure.exceptionClass()));
        };
    }

    private static RowWriteStage stageOf(TargetWriteStage stage) {
        return switch (stage) {
            case BORROW -> RowWriteStage.BORROW;
            case WORK -> RowWriteStage.WRITE;
            case COMMIT -> RowWriteStage.COMMIT;
            case ROLLBACK_TO_SAVEPOINT -> RowWriteStage.ROLLBACK_TO_SAVEPOINT;
            case INSPECT -> RowWriteStage.INSPECT;
            case ROLLBACK -> RowWriteStage.ROLLBACK;
        };
    }

    /** 口から戻った後に、ログ（1か所）と出来事を出して結果にする。 */
    private WriteResult finish(
            long userId,
            TableAccessView view,
            WriteOperation operation,
            WriteValidation.Valid valid,
            Attempt attempt,
            WriteOrigin origin) {
        return switch (attempt) {
            case Attempt.Created created -> {
                publish(userId, view, operation, null, created.key().values(), origin);
                RowValues key = view.allows(AllowedOperation.DETAIL)
                        ? created.key()
                        : new RowValues(Map.of(), java.util.Set.of());
                yield new WriteResult.Created(view.definitionId(), key);
            }
            case Attempt.Changed changed -> {
                publish(userId, view, operation, null, keyText(valid.key()), origin);
                yield new WriteResult.Written(view.definitionId());
            }
            case Attempt.Rejected rejected -> {
                if (rejected.failure() != null) {
                    RowWriteFailure f = rejected.failure();
                    warn(f.stage(), f.reason().name(), f.sqlState(), f.vendorCode(), f.exceptionClass());
                }
                Optional<BusinessDataAuditReason> reason = BusinessDataAuditReason.of(rejected.rejection());
                if (reason.isPresent()) {
                    Map<String, String> key = operation == WriteOperation.CREATE ? Map.of() : keyText(valid.key());
                    publish(userId, view, operation, reason.get(), key, origin);
                }
                yield new WriteResult.Rejected(rejected.rejection(), rejected.fieldErrors());
            }
            case Attempt.Unexpected unexpected -> throw new IllegalStateException("想定外の失敗はここに来ません");
        };
    }

    /** 主キーを監査の detail の文字にする（結果の値と同じ形）。 */
    private static Map<String, String> keyText(RowKey key) {
        Map<String, String> text = new LinkedHashMap<>();
        if (key != null) {
            for (Map.Entry<String, BoundValue> entry : key.values().entrySet()) {
                text.put(entry.getKey(), ColumnValueCodec.format(entry.getValue()));
            }
        }
        return text;
    }

    private void publish(
            long userId,
            TableAccessView view,
            WriteOperation operation,
            BusinessDataAuditReason reason,
            Map<String, String> key,
            WriteOrigin origin) {
        events.publishEvent(BusinessDataChangedEvent.of(
                userId,
                view.definitionId().workRoleId(),
                view.schema(),
                view.table(),
                operation,
                reason,
                AuditDetailBuilder.build(key),
                clock.instant(),
                origin));
    }

    /** ⑥ 主キーとビューの決まりで行えない操作か（作成はビュー、更新・削除はビュー・主キーが無い・主キーを見られない）。 */
    private static boolean structurallyForbidden(TableAccessView view, WriteOperation operation) {
        if (view.view()) {
            return true;
        }
        return operation != WriteOperation.CREATE && !view.allows(AllowedOperation.DETAIL);
    }

    /** その操作の補助権限を持つか（⑥ の監査の範囲。NFR4.14: 作成は CREATE、削除は DELETE、更新は FULL のカラム）。 */
    private static boolean hasAuxiliaryPermission(TableAccessView view, WriteOperation operation) {
        return switch (operation) {
            case CREATE -> view.canCreate();
            case DELETE -> view.canDelete();
            case UPDATE -> view.columns().stream().anyMatch(c -> c.main() == MainPermission.FULL);
        };
    }

    /** ⑦ 補助権限と FULL のカラム（BR1.6）。 */
    private static boolean permitted(TableAccessView view, WriteOperation operation) {
        return switch (operation) {
            case CREATE -> view.canCreate();
            case DELETE -> view.canDelete();
            case UPDATE -> view.columns().stream().anyMatch(ColumnAccess::editableOnUpdate);
        };
    }

    private static WriteRejection fromRead(ReadRejection rejection) {
        return switch (rejection) {
            case DSL_NOT_APPLIED -> WriteRejection.DSL_NOT_APPLIED;
            case TABLE_NOT_FOUND -> WriteRejection.TABLE_NOT_FOUND;
            case ACCESS_DENIED -> WriteRejection.ACCESS_DENIED;
            case ROW_OPERATION_NOT_ALLOWED,
                    VALIDATION_FAILED,
                    ROW_NOT_FOUND,
                    TARGET_DB_UNAVAILABLE,
                    TARGET_DB_TIMEOUT,
                    TARGET_DB_MISMATCH,
                    ROW_NOT_UNIQUE -> throw new IllegalStateException("解決の口はこの拒否を返しません");
        };
    }

    private static void warn(
            RowWriteStage stage, String reason, String sqlState, Integer vendorCode, String exceptionClass) {
        LOGGER.atWarn()
                .addKeyValue("purpose", PURPOSE)
                .addKeyValue("stage", stage)
                .addKeyValue("reason", reason)
                .addKeyValue("sqlState", sqlState)
                .addKeyValue("vendorCode", vendorCode)
                .addKeyValue("exceptionClass", exceptionClass)
                .log("対象DB の業務データを書き込めませんでした");
    }

    /** 書き込みの口の中の処理の結果（口の外へは値を伏せた形で戻す）。 */
    private sealed interface Attempt {

        /** 作った。 */
        record Created(RowValues key) implements Attempt {}

        /** 更新・削除した。 */
        record Changed() implements Attempt {}

        /** 拒否した（ログに出す失敗があれば {@code failure}）。 */
        record Rejected(WriteRejection rejection, List<BusinessDataFieldError> fieldErrors, RowWriteFailure failure)
                implements Attempt {}

        /** 想定外の失敗。 */
        record Unexpected(RowWriteStage stage, String sqlState, Integer vendorCode, String exceptionClass)
                implements Attempt {}
    }
}
