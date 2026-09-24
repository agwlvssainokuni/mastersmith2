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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslReadResult;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dslmanage.domain.DslAppliedRef;
import cherry.mastersmith.dslmanage.domain.DslContent;
import cherry.mastersmith.dslmanage.domain.DslDownload;
import cherry.mastersmith.dslmanage.domain.DslErrorItem;
import cherry.mastersmith.dslmanage.domain.DslOperationEvent;
import cherry.mastersmith.dslmanage.domain.DslOperationType;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslProblemTypes;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.domain.DslStatus;
import cherry.mastersmith.dslmanage.domain.DslUserRef;
import cherry.mastersmith.dslmanage.domain.PreviewView;
import cherry.mastersmith.dslmanage.generate.DefaultDslGenerator;
import cherry.mastersmith.dslmanage.generate.DefaultDslResult;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

/**
 * DSL の管理の業務処理（U4）。生成・投入・履歴からの戻し・プレビューの表示・破棄・適用・今の状態・履歴・ダウンロード。
 *
 * <p>内部DB の読み書きは {@link DslRecordStore} のトランザクションで行い、その確定の後にだけ、適用中のモデルの差し替え（U2）と
 * 監査の出来事（契約 C7）を行う（BR4.6、BR7.3）。巻き戻ったときは、どちらもしない。U1・U2・U3 の結果の型の想定内の失敗は、ここで
 * 業務の例外に変える（BR8.1）。応答は共通の変換が作る。
 *
 * <p>操作ごとに指標と INFO のログを1件出す（{@link DslOperationMetrics}）。出来事・指標・ログに、DSL の本文と接続先を入れない。
 */
@Service
public class DslLifecycle {

    /** 422 の応答に載せる誤りの件数の上限（先頭の100件。BR1.5）。 */
    public static final int MAX_ERRORS = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(DslLifecycle.class);

    private final DslRecordStore store;

    private final DslReader dslReader;

    private final ActiveDslModelHolder activeDslModelHolder;

    private final DefaultDslGenerator generator;

    private final DslPreviewAnalysis analysis;

    private final DslPreviewCache cache;

    private final DslOperationMetrics metrics;

    private final UserAccountService userAccountService;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private final DslManageProperties properties;

    /**
     * 作る。
     *
     * @param store 保存
     * @param dslReader DSL の読み込み（U2）
     * @param activeDslModelHolder 適用中のモデルの差し替え（U2）
     * @param generator 既定の DSL の生成（U3）
     * @param analysis プレビューの中身
     * @param cache プレビューの読み込みの結果の保持
     * @param metrics 指標とログ
     * @param userAccountService 利用者の読み取り（メールアドレス）
     * @param eventPublisher アプリの中の出来事の通知
     * @param clock 時計
     * @param properties DSL の管理の設定
     */
    public DslLifecycle(
            DslRecordStore store,
            DslReader dslReader,
            ActiveDslModelHolder activeDslModelHolder,
            DefaultDslGenerator generator,
            DslPreviewAnalysis analysis,
            DslPreviewCache cache,
            DslOperationMetrics metrics,
            UserAccountService userAccountService,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            DslManageProperties properties) {
        this.store = store;
        this.dslReader = dslReader;
        this.activeDslModelHolder = activeDslModelHolder;
        this.generator = generator;
        this.analysis = analysis;
        this.cache = cache;
        this.metrics = metrics;
        this.userAccountService = userAccountService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * スキーマを読み込み、既定の DSL をプレビューに置く（BR1.1）。
     *
     * @param context 要求の文脈
     * @return プレビューの中身
     * @throws BusinessException 対象DB の設定が無い（503 {@code TARGET_DB_UNCONFIGURED}）・接続できない（503
     *     {@code TARGET_DB_UNAVAILABLE}）。プレビューは変わらない
     */
    public PreviewView generate(DslRequestContext context) {
        long start = metrics.start();
        try {
            DefaultDslResult.Generated generated =
                    switch (generator.generate()) {
                        case DefaultDslResult.Generated result -> result;
                        case DefaultDslResult.TargetUnconfigured unconfigured ->
                            throw new BusinessException(DslProblemTypes.TARGET_DB_UNCONFIGURED);
                        case DefaultDslResult.TargetUnavailable unavailable ->
                            throw new BusinessException(DslProblemTypes.TARGET_DB_UNAVAILABLE);
                    };
            byte[] yamlBytes = generated.yamlBytes();
            DslModel model = readValid(yamlBytes);
            PreviewView view = place(yamlBytes, model, DslSource.GENERATED, DslOperationType.DSL_GENERATED, context);
            metrics.record(DslOperation.GENERATE, DslOutcome.SUCCESS, start, model.dslHash(), DslSource.GENERATED);
            return view;
        } catch (RuntimeException e) {
            metrics.record(DslOperation.GENERATE, DslOutcome.FAILED, start, null, DslSource.GENERATED);
            throw e;
        }
    }

    /**
     * 投入された DSL を検証し、通ればプレビューに置く（BR1.2・BR1.5・BR7.4）。
     *
     * @param yamlBytes 本文
     * @param source 出どころ（{@link DslSource#UPLOAD} または {@link DslSource#PASTE}）
     * @param context 要求の文脈
     * @return プレビューの中身
     * @throws BusinessException 検証を通らない（422 {@code DSL_INVALID}。誤りの先頭100件と総数を持つ。保存しない）
     */
    public PreviewView submit(byte[] yamlBytes, DslSource source, DslRequestContext context) {
        long start = metrics.start();
        try {
            switch (dslReader.read(yamlBytes)) {
                case DslReadResult.Invalid invalid -> {
                    String dslHash = dslReader.hash(yamlBytes);
                    String kind = DslErrorMessages.firstKind(invalid.errors()).name();
                    publish(DslOperationType.DSL_SUBMISSION_REJECTED, context, dslHash, source, kind);
                    metrics.record(DslOperation.SUBMIT, DslOutcome.REJECTED, start, dslHash, source);
                    throw invalid(invalid.errors(), context);
                }
                case DslReadResult.Valid valid -> {
                    PreviewView view = place(yamlBytes, valid.model(), source, DslOperationType.DSL_SUBMITTED, context);
                    metrics.record(DslOperation.SUBMIT, DslOutcome.SUCCESS, start, valid.dslHash(), source);
                    return view;
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            metrics.record(DslOperation.SUBMIT, DslOutcome.FAILED, start, null, source);
            throw e;
        }
    }

    /**
     * 履歴の版を、今の U2 の検証にかけ直してからプレビューに置く（BR1.3）。対象DB との照合の警告つきのプレビューの中身を返す。
     *
     * <p>検証を通らないときは、投入と同じ誤りの一覧の 422 にし、プレビューは変えない。受け付けなかった投入の出来事は出さない（戻しの
     * 操作そのものの失敗で、利用者が外から投入した DSL ではないため。functional-spec.md 1節の注記）。
     *
     * @param revisionId 戻す版の識別
     * @param context 要求の文脈
     * @return プレビューの中身
     * @throws BusinessException 版が無い（件数の上限で消えたものを含む。404 {@code DSL_REVISION_NOT_FOUND}）、今の検証を通らない（422
     *     {@code DSL_INVALID}。誤りの先頭100件と総数を持つ）。どちらもプレビューは変わらない
     */
    public PreviewView restore(UUID revisionId, DslRequestContext context) {
        long start = metrics.start();
        try {
            Optional<DslContent<DslAppliedRef>> found = store.findRevisionContent(revisionId);
            if (found.isEmpty()) {
                metrics.record(DslOperation.RESTORE, DslOutcome.REJECTED, start, null, DslSource.RESTORE);
                throw new BusinessException(DslProblemTypes.DSL_REVISION_NOT_FOUND);
            }
            byte[] yamlBytes = found.get().yamlBytes();
            switch (dslReader.read(yamlBytes)) {
                case DslReadResult.Invalid invalid -> {
                    metrics.record(
                            DslOperation.RESTORE,
                            DslOutcome.REJECTED,
                            start,
                            found.get().ref().dslHash(),
                            DslSource.RESTORE);
                    throw invalid(invalid.errors(), context);
                }
                case DslReadResult.Valid valid -> {
                    PreviewView view =
                            place(yamlBytes, valid.model(), DslSource.RESTORE, DslOperationType.DSL_SUBMITTED, context);
                    metrics.record(DslOperation.RESTORE, DslOutcome.SUCCESS, start, valid.dslHash(), DslSource.RESTORE);
                    return view;
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            metrics.record(DslOperation.RESTORE, DslOutcome.FAILED, start, null, DslSource.RESTORE);
            throw e;
        }
    }

    /**
     * 要求の本文の大きさの上限で断った投入を記録する（受け付けなかった投入の出来事 {@code SIZE_LIMIT}、識別なし。BR7.4）。
     *
     * @param source 出どころ（要求から分からなければ null）
     * @param context 要求の文脈
     */
    public void recordOversizedSubmission(DslSource source, DslRequestContext context) {
        long start = metrics.start();
        publish(DslOperationType.DSL_SUBMISSION_REJECTED, context, null, source, DslOperationEvent.SIZE_LIMIT);
        metrics.record(DslOperation.SUBMIT, DslOutcome.REJECTED, start, null, source);
    }

    /**
     * 重い処理の許可が取れずに断った要求を記録する（状態を変えず、監査の出来事も出さない。NFR1.14）。
     *
     * @param operation 断った操作
     */
    public void recordBusy(DslOperation operation) {
        metrics.record(operation, DslOutcome.BUSY, metrics.start(), null, null);
    }

    /**
     * プレビューの中身を返す（BR2.1・BR2.5）。
     *
     * @param context 要求の文脈
     * @return プレビューの中身
     * @throws BusinessException プレビューが無い（404 {@code DSL_PREVIEW_NOT_FOUND}）
     */
    public PreviewView showPreview(DslRequestContext context) {
        DslPreviewRef ref = store.findPreviewRef().orElseThrow(DslLifecycle::previewNotFound);
        Optional<DslModel> cached = cache.get(ref.previewId());
        DslModel model;
        if (cached.isPresent()) {
            model = cached.get();
        } else {
            DslContent<DslPreviewRef> content = store.findPreviewContent().orElseThrow(DslLifecycle::previewNotFound);
            ref = content.ref();
            model = readValid(content.yamlBytes());
            cache.put(ref.previewId(), model);
        }
        return analysis.analyze(ref, user(ref.placedByUserId()), model, context.language(), true);
    }

    /**
     * プレビューを破棄する（BR3.1）。
     *
     * @param context 要求の文脈
     * @throws BusinessException プレビューが無い（404 {@code DSL_PREVIEW_NOT_FOUND}）
     */
    public void discard(DslRequestContext context) {
        long start = metrics.start();
        Optional<DslPreviewRef> found = store.findPreviewRef();
        if (found.isEmpty() || store.discardPreview(found.get().previewId()) == 0) {
            metrics.record(DslOperation.DISCARD, DslOutcome.REJECTED, start, null, null);
            throw previewNotFound();
        }
        DslPreviewRef ref = found.get();
        cache.clear();
        publish(DslOperationType.DSL_PREVIEW_DISCARDED, context, ref.dslHash(), ref.source(), null);
        metrics.record(DslOperation.DISCARD, DslOutcome.SUCCESS, start, ref.dslHash(), ref.source());
    }

    /**
     * 見たプレビューを適用する（BR4.1〜BR4.7）。対象DB には接続しない。
     *
     * @param previewId 見たプレビューの識別
     * @param context 要求の文脈
     * @return 適用した後の今の状態
     * @throws BusinessException プレビューが無い・違う・同時の適用に負けた（409 {@code DSL_PREVIEW_CHANGED}）。適用中は変わらない
     */
    public DslStatus apply(UUID previewId, DslRequestContext context) {
        long start = metrics.start();
        DslAppliedRef applied;
        try {
            applied = store.apply(previewId, context.actorUserId(), clock.instant(), properties.historyLimit());
        } catch (BusinessException e) {
            metrics.record(DslOperation.APPLY, DslOutcome.REJECTED, start, null, null);
            throw e;
        } catch (ConcurrencyFailureException e) {
            // 同時の適用で、プレビューの行の削除が相手の確定と競ったとき（巻き戻し済み）。
            metrics.record(DslOperation.APPLY, DslOutcome.REJECTED, start, null, null);
            throw new BusinessException(DslProblemTypes.DSL_PREVIEW_CHANGED);
        } catch (RuntimeException e) {
            metrics.record(DslOperation.APPLY, DslOutcome.FAILED, start, null, null);
            throw e;
        }
        // ここから先は確定の後（BR4.6）。
        DslModel model = cache.get(previewId)
                .orElseGet(() -> readValid(store.findRevisionContent(applied.revisionId())
                        .orElseThrow(() -> new IllegalStateException("適用した版を読めません"))
                        .yamlBytes()));
        activeDslModelHolder.replace(model);
        cache.clear();
        publish(DslOperationType.DSL_APPLIED, context, applied.dslHash(), applied.source(), null);
        metrics.record(DslOperation.APPLY, DslOutcome.SUCCESS, start, applied.dslHash(), applied.source());
        return status();
    }

    /**
     * 今の状態を返す（BR6.3）。
     *
     * @return 今の状態
     */
    public DslStatus status() {
        DslStatus.Applied applied = store.findCurrentRevision()
                .map(ref -> new DslStatus.Applied(ref, user(ref.appliedByUserId())))
                .orElse(null);
        DslStatus.Preview preview = store.findPreviewRef()
                .map(ref -> new DslStatus.Preview(ref, user(ref.placedByUserId())))
                .orElse(null);
        return new DslStatus(applied, preview);
    }

    /**
     * 適用の履歴を新しい順に、適用中の印をつけて返す（BR6.1）。
     *
     * @return 履歴
     */
    public List<DslStatus.HistoryEntry> history() {
        Optional<UUID> current = store.findCurrentRevision().map(DslAppliedRef::revisionId);
        Map<Long, DslUserRef> users = new LinkedHashMap<>();
        return store.findRevisionsNewestFirst().stream()
                .map(ref -> new DslStatus.HistoryEntry(
                        new DslStatus.Applied(ref, users.computeIfAbsent(ref.appliedByUserId(), this::user)),
                        current.filter(ref.revisionId()::equals).isPresent()))
                .toList();
    }

    /**
     * プレビュー中の DSL をダウンロードする（BR6.2）。
     *
     * @return ダウンロード
     * @throws BusinessException プレビューが無い（404 {@code DSL_PREVIEW_NOT_FOUND}）
     */
    public DslDownload downloadPreview() {
        return DslDownload.preview(store.findPreviewContent().orElseThrow(DslLifecycle::previewNotFound));
    }

    /**
     * 適用中の DSL（履歴の最新。今の状態の {@code applied} と同じ版）をダウンロードする（BR6.2）。
     *
     * @return ダウンロード
     * @throws BusinessException 適用中が無い（404 {@code DSL_APPLIED_NOT_FOUND}）
     */
    public DslDownload downloadApplied() {
        return DslDownload.applied(store.findCurrentRevisionContent()
                .orElseThrow(() -> new BusinessException(DslProblemTypes.DSL_APPLIED_NOT_FOUND)));
    }

    private PreviewView place(
            byte[] yamlBytes, DslModel model, DslSource source, DslOperationType type, DslRequestContext context) {
        DslPreviewRef ref =
                store.placePreview(yamlBytes, model.dslHash(), source, context.actorUserId(), clock.instant());
        // ここから先は確定の後。
        cache.put(ref.previewId(), model);
        publish(type, context, ref.dslHash(), source, null);
        return analysis.analyze(ref, user(context.actorUserId()), model, context.language(), false);
    }

    /** 保存してある（または U3 が作った）本文を読む。検証を通らないのは想定外の失敗。 */
    private DslModel readValid(byte[] yamlBytes) {
        return switch (dslReader.read(yamlBytes)) {
            case DslReadResult.Valid valid -> valid.model();
            case DslReadResult.Invalid invalid -> {
                LOGGER.atError()
                        .addKeyValue("dsl.hash", DslDownload.prefix(dslReader.hash(yamlBytes)))
                        .addKeyValue(
                                "dsl.errorKinds",
                                invalid.errors().stream()
                                        .map(error -> error.kind().name())
                                        .distinct()
                                        .toList())
                        .log("保存してある DSL が今の検証を通りません");
                throw new IllegalStateException("保存してある DSL が今の検証を通りません");
            }
        };
    }

    private DslUserRef user(long userId) {
        return new DslUserRef(
                userId,
                userAccountService.findById(userId).map(UserSummary::email).orElse(null));
    }

    private void publish(
            DslOperationType type, DslRequestContext context, String dslHash, DslSource source, String rejectionKind) {
        DslOperationEvent event = new DslOperationEvent(
                type,
                context.actorUserId(),
                clock.instant(),
                dslHash,
                source,
                rejectionKind,
                context.sourceIp(),
                context.userAgent(),
                context.traceId());
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException e) {
            // 監査の受け取り側は自分で失敗を受け止める決まりだが、念のため元の操作に伝えない（BR7.3）。
            LOGGER.atWarn()
                    .addKeyValue("exceptionType", e.getClass().getName())
                    .addKeyValue("dslEventType", type)
                    .log("DSL の操作の出来事の受け取りで例外が戻りました");
        }
    }

    private static BusinessException invalid(List<DslError> errors, DslRequestContext context) {
        List<DslErrorItem> items = errors.stream()
                .limit(MAX_ERRORS)
                .map(error -> DslErrorMessages.item(error, context.language()))
                .toList();
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("total", errors.size());
        extra.put("errors", items);
        return new BusinessException(DslProblemTypes.DSL_INVALID, null, extra);
    }

    private static BusinessException previewNotFound() {
        return new BusinessException(DslProblemTypes.DSL_PREVIEW_NOT_FOUND);
    }
}
