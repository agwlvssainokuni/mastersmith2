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

import static cherry.mastersmith.dslmanage.testsupport.DslYaml.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import cherry.mastersmith.dsl.service.ActiveDslModelStore;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.validate.PatternChecker;
import cherry.mastersmith.dslmanage.domain.DslAppliedRef;
import cherry.mastersmith.dslmanage.domain.DslContent;
import cherry.mastersmith.dslmanage.domain.DslErrorItem;
import cherry.mastersmith.dslmanage.domain.DslOperationEvent;
import cherry.mastersmith.dslmanage.domain.DslOperationType;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslProblemTypes;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.domain.DslStatus;
import cherry.mastersmith.dslmanage.domain.PreviewView;
import cherry.mastersmith.dslmanage.generate.DefaultDslGenerator;
import cherry.mastersmith.dslmanage.generate.DefaultDslResult;
import cherry.mastersmith.dslmanage.repository.DslAppliedRevisionRepository;
import cherry.mastersmith.dslmanage.repository.DslPreviewRepository;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.domain.UnavailableReason;
import cherry.mastersmith.targetdb.service.TargetSchemaReader;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * DSL の管理の業務処理の単体テスト（BR1.1〜BR8.2）。U1・U3 の口と U4 のリポジトリを差し替え、U2 の読み込みと適用中のモデルの保持は
 * 本物を使う。
 */
class DslLifecycleTest {

    private static final PatternChecker PATTERNS = new PatternChecker();

    private static final DslReader READER = DslYaml.newReader(PATTERNS);

    private static final Instant NOW = Instant.parse("2026-09-24T03:00:00Z");

    /** 接続先らしい値（どこにも出ないことを確かめる）。 */
    private static final String SECRET_HOST = "db.internal.example";

    private static final long ADMIN = 11L;

    private final DslPreviewRepository previews = mock(DslPreviewRepository.class);

    private final DslAppliedRevisionRepository revisions = mock(DslAppliedRevisionRepository.class);

    private final DefaultDslGenerator generator = mock(DefaultDslGenerator.class);

    private final TargetSchemaReader target = mock(TargetSchemaReader.class);

    private final UserAccountService users = mock(UserAccountService.class);

    private final ActiveDslModelStore active = new ActiveDslModelStore();

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final List<Object> events = new ArrayList<>();

    private final DslPreviewCache cache = new DslPreviewCache();

    private DslLifecycle lifecycle;

    private DslRecordStore store;

    @AfterAll
    static void close() {
        PATTERNS.close();
    }

    @BeforeEach
    void setUp() {
        DslOperationMetrics metrics = new DslOperationMetrics(registry);
        store = new DslRecordStore(previews, revisions);
        lifecycle = new DslLifecycle(
                store,
                READER,
                active,
                generator,
                new DslPreviewAnalysis(target, active, metrics),
                cache,
                metrics,
                users,
                events::add,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new DslManageProperties(org.springframework.util.unit.DataSize.ofMegabytes(10), 20));
        when(target.readSchema(ReadPurpose.COMPARE)).thenReturn(TargetSchemaResult.unconfigured());
        when(users.findById(ADMIN)).thenReturn(Optional.of(new UserSummary(ADMIN, "admin@example.com", true)));
    }

    private static DslRequestContext context(DisplayLanguage language) {
        return new DslRequestContext(ADMIN, "192.0.2.1", "JUnit", "trace-1", language);
    }

    private static byte[] sample() {
        return DslYaml.dsl()
                .menu("部署", "Departments", "dept")
                .table("dept", column("code"))
                .bytes();
    }

    private List<DslOperationEvent> dslEvents() {
        return events.stream()
                .filter(DslOperationEvent.class::isInstance)
                .map(DslOperationEvent.class::cast)
                .toList();
    }

    private static int status(Throwable thrown) {
        return ((BusinessException) thrown).getProblemType().status();
    }

    @Test
    @DisplayName("generate places the generated DSL as preview, publishes DSL_GENERATED and keeps the applied DSL")
    void generatePlacesPreview() {
        byte[] body = sample();
        String hash = READER.hash(body);
        when(generator.generate()).thenReturn(new DefaultDslResult.Generated(body, hash));

        PreviewView view = lifecycle.generate(context(DisplayLanguage.JA));

        verify(previews).place(any(), eq(body), eq(hash), eq(DslSource.GENERATED), eq(ADMIN), eq(NOW));
        assertThat(view.preview().source()).isEqualTo(DslSource.GENERATED);
        assertThat(view.placedBy().email()).isEqualTo("admin@example.com");
        assertThat(view.summary().tableCount()).isEqualTo(1);
        assertThat(view.warnings())
                .extracting(PreviewView.Warning::kind)
                .containsExactly(PreviewView.WarningKind.TARGET_UNCONFIGURED);
        assertThat(dslEvents()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(DslOperationType.DSL_GENERATED);
            assertThat(event.dslHash()).isEqualTo(hash);
            assertThat(event.actorUserId()).isEqualTo(ADMIN);
            assertThat(event.occurredAt()).isEqualTo(NOW);
        });
        assertThat(active.current()).isInstanceOf(ActiveDsl.Absent.class);
    }

    @Test
    @DisplayName("an unconfigured target database becomes 503 TARGET_DB_UNCONFIGURED without touching the preview")
    void generateUnconfigured() {
        when(generator.generate()).thenReturn(new DefaultDslResult.TargetUnconfigured());

        assertThatThrownBy(() -> lifecycle.generate(context(DisplayLanguage.JA)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getProblemType()).isEqualTo(DslProblemTypes.TARGET_DB_UNCONFIGURED));
        verify(previews, never()).place(any(), any(), any(), any(), anyLong(), any());
        assertThat(dslEvents()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(UnavailableReason.class)
    @DisplayName("an unavailable target database becomes 503 TARGET_DB_UNAVAILABLE for every reason")
    void generateUnavailable(UnavailableReason reason) {
        when(generator.generate()).thenReturn(new DefaultDslResult.TargetUnavailable(reason));

        assertThatThrownBy(() -> lifecycle.generate(context(DisplayLanguage.JA)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getProblemType()).isEqualTo(DslProblemTypes.TARGET_DB_UNAVAILABLE));
        verify(previews, never()).place(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("an unexpected failure of the generator propagates and leaves the preview unchanged")
    void generateUnexpectedFailure() {
        when(generator.generate()).thenThrow(new IllegalStateException("生成した DSL が上限を超えました"));

        assertThatThrownBy(() -> lifecycle.generate(context(DisplayLanguage.JA)))
                .isInstanceOf(IllegalStateException.class);
        verify(previews, never()).place(any(), any(), any(), any(), anyLong(), any());
        assertThat(meterTags()).contains(Map.of("operation", "generate", "outcome", "failed"));
    }

    @Test
    @DisplayName("an invalid submission is 422 with the first 100 localized errors, the total and a rejection event")
    void submitInvalid() {
        DslYaml.Column[] columns = new DslYaml.Column[120];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = column("c" + i);
        }
        String yaml = DslYaml.dsl()
                .table("t", columns)
                .yaml()
                .replace(
                        "detail: { visible: true }",
                        "detail: { visible: true, " + SECRET_HOST.replace('.', '_') + ": 1 }");
        byte[] body = yaml.getBytes(StandardCharsets.UTF_8);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> lifecycle.submit(body, DslSource.PASTE, context(DisplayLanguage.EN)));

        assertThat(status(thrown)).isEqualTo(422);
        BusinessException invalid = (BusinessException) thrown;
        assertThat(invalid.getProblemType()).isEqualTo(DslProblemTypes.DSL_INVALID);
        assertThat(invalid.getProperties()).containsEntry("total", 120);
        @SuppressWarnings("unchecked")
        List<DslErrorItem> errors = (List<DslErrorItem>) invalid.getProperties().get("errors");
        assertThat(errors).hasSize(100).allSatisfy(error -> {
            assertThat(error.kind()).isEqualTo(DslErrorKind.SYNTAX);
            assertThat(error.line()).isPositive();
            assertThat(error.message())
                    .startsWith("The item \"")
                    .doesNotContain("$.")
                    .doesNotContain("Exception");
        });
        verify(previews, never()).place(any(), any(), any(), any(), anyLong(), any());
        assertThat(dslEvents()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(DslOperationType.DSL_SUBMISSION_REJECTED);
            assertThat(event.rejectionKind()).isEqualTo("SYNTAX");
            assertThat(event.dslHash()).isEqualTo(READER.hash(body));
            assertThat(event.source()).isEqualTo(DslSource.PASTE);
        });
    }

    @Test
    @DisplayName("a valid submission is placed and the next display reuses the parsed model without reading the body")
    void submitValidThenShow() {
        byte[] body = sample();

        PreviewView placed = lifecycle.submit(body, DslSource.UPLOAD, context(DisplayLanguage.JA));
        when(previews.findRef()).thenReturn(Optional.of(placed.preview()));
        PreviewView shown = lifecycle.showPreview(context(DisplayLanguage.JA));

        assertThat(dslEvents()).extracting(DslOperationEvent::type).containsExactly(DslOperationType.DSL_SUBMITTED);
        assertThat(shown.preview()).isEqualTo(placed.preview());
        verify(previews, never()).findContent();
        assertThat(meterTags()).contains(Map.of("operation", "compare", "outcome", "failed"));
    }

    @Test
    @DisplayName("showing a preview that is not cached reads and parses its body once")
    void showReadsBodyWhenNotCached() {
        byte[] body = sample();
        DslPreviewRef ref = new DslPreviewRef(UUID.randomUUID(), READER.hash(body), DslSource.PASTE, ADMIN, NOW);
        when(previews.findRef()).thenReturn(Optional.of(ref));
        when(previews.findContent()).thenReturn(Optional.of(new DslContent<>(ref, body)));

        PreviewView shown = lifecycle.showPreview(context(DisplayLanguage.JA));

        assertThat(shown.summary().columnCount()).isEqualTo(1);
        assertThat(cache.get(ref.previewId())).isPresent();
    }

    @Test
    @DisplayName("showing, downloading and discarding without a preview is 404 DSL_PREVIEW_NOT_FOUND")
    void noPreview() {
        assertThatThrownBy(() -> lifecycle.showPreview(context(DisplayLanguage.JA)))
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        assertThatThrownBy(() -> lifecycle.downloadPreview())
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        assertThatThrownBy(() -> lifecycle.discard(context(DisplayLanguage.JA)))
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        assertThat(dslEvents()).isEmpty();
    }

    @Test
    @DisplayName("discard removes the preview by its id and publishes DSL_PREVIEW_DISCARDED with its hash")
    void discard() {
        DslPreviewRef ref = new DslPreviewRef(UUID.randomUUID(), "c".repeat(64), DslSource.UPLOAD, ADMIN, NOW);
        when(previews.findRef()).thenReturn(Optional.of(ref));
        when(previews.deleteByPreviewId(ref.previewId())).thenReturn(1);

        lifecycle.discard(context(DisplayLanguage.JA));

        assertThat(dslEvents()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(DslOperationType.DSL_PREVIEW_DISCARDED);
            assertThat(event.dslHash()).isEqualTo(ref.dslHash());
        });
    }

    @Test
    @DisplayName("apply with another previewId is 409 and neither replaces the applied model nor publishes an event")
    void applyWithOtherPreview() {
        DslPreviewRef ref = new DslPreviewRef(UUID.randomUUID(), "c".repeat(64), DslSource.UPLOAD, ADMIN, NOW);
        when(previews.findRef()).thenReturn(Optional.of(ref));

        assertThatThrownBy(() -> lifecycle.apply(UUID.randomUUID(), context(DisplayLanguage.JA)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getProblemType()).isEqualTo(DslProblemTypes.DSL_PREVIEW_CHANGED));
        verify(revisions, never()).copyFromPreview(any(), any(), anyLong(), any());
        assertThat(active.current()).isInstanceOf(ActiveDsl.Absent.class);
        assertThat(dslEvents()).isEmpty();
    }

    @Test
    @DisplayName("apply that deletes no preview row (lost the race) is 409 and changes nothing")
    void applyLosingTheRace() {
        DslPreviewRef ref = new DslPreviewRef(UUID.randomUUID(), "c".repeat(64), DslSource.UPLOAD, ADMIN, NOW);
        when(previews.findRef()).thenReturn(Optional.of(ref));
        when(revisions.copyFromPreview(any(), eq(ref.previewId()), eq(ADMIN), eq(NOW)))
                .thenReturn(1);
        when(previews.deleteByPreviewId(ref.previewId())).thenReturn(0);

        assertThatThrownBy(() -> lifecycle.apply(ref.previewId(), context(DisplayLanguage.JA)))
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(revisions, never()).deleteOlderThanNewest(anyInt());
        assertThat(active.current()).isInstanceOf(ActiveDsl.Absent.class);
        assertThat(dslEvents()).isEmpty();
    }

    @Test
    @DisplayName(
            "a successful apply trims the history, replaces the applied model after the store and publishes DSL_APPLIED")
    void applySucceeds() {
        byte[] body = sample();
        PreviewView placed = lifecycle.submit(body, DslSource.UPLOAD, context(DisplayLanguage.JA));
        events.clear();
        DslPreviewRef ref = placed.preview();
        when(previews.findRef()).thenReturn(Optional.of(ref), Optional.empty());
        when(revisions.copyFromPreview(any(), eq(ref.previewId()), eq(ADMIN), eq(NOW)))
                .thenReturn(1);
        when(previews.deleteByPreviewId(ref.previewId())).thenReturn(1);
        when(revisions.findCurrentRef())
                .thenReturn(Optional.of(new DslAppliedRef(UUID.randomUUID(), ref.dslHash(), ref.source(), ADMIN, NOW)));

        DslStatus status = lifecycle.apply(ref.previewId(), context(DisplayLanguage.JA));

        verify(revisions).deleteOlderThanNewest(20);
        assertThat(active.current())
                .isInstanceOfSatisfying(
                        ActiveDsl.Present.class,
                        present -> assertThat(present.dslHash()).isEqualTo(ref.dslHash()));
        assertThat(dslEvents()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(DslOperationType.DSL_APPLIED);
            assertThat(event.source()).isEqualTo(DslSource.UPLOAD);
        });
        assertThat(status.applied().by().email()).isEqualTo("admin@example.com");
        assertThat(status.preview()).isNull();
        verify(target, never()).readSchema(ReadPurpose.GENERATE);
    }

    @Test
    @DisplayName(
            "an apply whose model is not cached reads the applied revision back and an unknown user shows no email")
    void applyWithoutCacheAndUnknownUser() {
        byte[] body = sample();
        UUID previewId = UUID.randomUUID();
        DslPreviewRef ref = new DslPreviewRef(previewId, READER.hash(body), DslSource.PASTE, 99L, NOW);
        when(previews.findRef()).thenReturn(Optional.of(ref));
        when(revisions.copyFromPreview(any(), eq(previewId), eq(ADMIN), eq(NOW)))
                .thenReturn(1);
        when(previews.deleteByPreviewId(previewId)).thenReturn(1);
        when(revisions.findContent(any()))
                .thenAnswer(invocation -> Optional.of(new DslContent<>(
                        new DslAppliedRef(invocation.getArgument(0), ref.dslHash(), ref.source(), ADMIN, NOW), body)));

        DslStatus status = lifecycle.apply(previewId, context(DisplayLanguage.JA));

        assertThat(active.current()).isInstanceOf(ActiveDsl.Present.class);
        assertThat(status.preview().by().email()).as("利用者が見つからなければ不明（null）").isNull();
    }

    @Test
    @DisplayName("history marks only the current revision and status reports nothing when empty")
    void historyAndStatus() {
        DslAppliedRef newer = new DslAppliedRef(UUID.randomUUID(), "d".repeat(64), DslSource.PASTE, ADMIN, NOW);
        DslAppliedRef older =
                new DslAppliedRef(UUID.randomUUID(), "e".repeat(64), DslSource.UPLOAD, ADMIN, NOW.minusSeconds(60));
        assertThat(lifecycle.status()).isEqualTo(new DslStatus(null, null));

        when(revisions.findCurrentRef()).thenReturn(Optional.of(newer));
        when(revisions.findRefsNewestFirst()).thenReturn(List.of(newer, older));

        assertThat(lifecycle.history())
                .extracting(DslStatus.HistoryEntry::current)
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("an oversized submission is recorded as SIZE_LIMIT without a hash, and busy requests only as metrics")
    void oversizedAndBusy() {
        lifecycle.recordOversizedSubmission(DslSource.UPLOAD, context(DisplayLanguage.JA));
        lifecycle.recordBusy(DslOperation.GENERATE);

        assertThat(dslEvents()).singleElement().satisfies(event -> {
            assertThat(event.rejectionKind()).isEqualTo("SIZE_LIMIT");
            assertThat(event.dslHash()).isNull();
        });
        assertThat(meterTags())
                .contains(
                        Map.of("operation", "submit", "outcome", "rejected"),
                        Map.of("operation", "generate", "outcome", "busy"));
    }

    @Test
    @DisplayName("every U2 message key has Japanese and English texts, and the arguments are filled in")
    void everyMessageKeyHasTexts() {
        assertThat(DslMessageKeys.all())
                .allSatisfy(
                        key -> assertThat(DslErrorMessages.text(key)).as(key).isPresent());
        cherry.mastersmith.dsl.domain.DslError error = new cherry.mastersmith.dsl.domain.DslError(
                DslErrorKind.SEMANTIC, 3, 5, "tables.t", DslMessageKeys.SEMANTIC_UNKNOWN_COLUMN, List.of("t", "c"));
        assertThat(DslErrorMessages.message(error, DisplayLanguage.JA)).isEqualTo("テーブル「t」にカラム「c」がありません。");
        assertThat(DslErrorMessages.message(error, DisplayLanguage.EN))
                .isEqualTo("The table \"t\" has no column \"c\".");
        cherry.mastersmith.dsl.domain.DslError unknown = new cherry.mastersmith.dsl.domain.DslError(
                DslErrorKind.SEMANTIC, null, null, null, "dsl.unknown", List.of());
        assertThat(DslErrorMessages.message(unknown, DisplayLanguage.EN)).isEqualTo("The DSL contains an error.");
    }

    @Test
    @DisplayName(
            "metric tags use fixed words only, and the operation logs carry keys but no body or connection details")
    void metricsAndLogs() {
        byte[] body = sample();
        try (LogEvents logs = LogEvents.capture(DslOperationMetrics.class)) {
            lifecycle.submit(body, DslSource.PASTE, context(DisplayLanguage.JA));

            ILoggingEvent info = logs.list().getFirst();
            assertThat(info.getLevel()).isEqualTo(Level.INFO);
            Map<String, String> keys = info.getKeyValuePairs().stream()
                    .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
            assertThat(keys)
                    .containsEntry("dsl.operation", "submit")
                    .containsEntry("dsl.outcome", "success")
                    .containsEntry("dsl.hash", READER.hash(body).substring(0, 12))
                    .containsEntry("dsl.source", "PASTE")
                    .containsKey("dsl.durationMs");
            assertThat(logs.list())
                    .allSatisfy(event -> assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                            .doesNotContain("dept")
                            .doesNotContain(SECRET_HOST));
        }
        Set<String> operations = Set.of("generate", "submit", "restore", "apply", "discard", "compare");
        Set<String> outcomes = Set.of("success", "rejected", "failed", "busy");
        assertThat(meterTags()).isNotEmpty().allSatisfy(tags -> {
            assertThat(tags.keySet()).containsExactlyInAnyOrder("operation", "outcome");
            assertThat(operations).contains(tags.get("operation"));
            assertThat(outcomes).contains(tags.get("outcome"));
        });
    }

    @Test
    @DisplayName("an unexpected failure while placing a submission propagates and is counted as failed")
    void submitUnexpectedFailure() {
        when(previews.place(any(), any(), any(), any(), anyLong(), any()))
                .thenThrow(new IllegalStateException("内部DB の失敗"));

        assertThatThrownBy(() -> lifecycle.submit(sample(), DslSource.PASTE, context(DisplayLanguage.JA)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(meterTags()).contains(Map.of("operation", "submit", "outcome", "failed"));
        assertThat(dslEvents()).isEmpty();
    }

    @Test
    @DisplayName("a lock conflict while applying becomes 409, and other failures propagate as failed")
    void applyConflictsAndFailures() {
        when(previews.findRef())
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("競合"))
                .thenThrow(new IllegalStateException("内部DB の失敗"));

        assertThatThrownBy(() -> lifecycle.apply(UUID.randomUUID(), context(DisplayLanguage.JA)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getProblemType()).isEqualTo(DslProblemTypes.DSL_PREVIEW_CHANGED));
        assertThatThrownBy(() -> lifecycle.apply(UUID.randomUUID(), context(DisplayLanguage.JA)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(meterTags()).contains(Map.of("operation", "apply", "outcome", "failed"));
    }

    @Test
    @DisplayName("apply whose preview row cannot be copied is 409 before deleting anything")
    void applyWhenCopyFindsNothing() {
        DslPreviewRef ref = new DslPreviewRef(UUID.randomUUID(), "c".repeat(64), DslSource.UPLOAD, ADMIN, NOW);
        when(previews.findRef()).thenReturn(Optional.of(ref));
        when(revisions.copyFromPreview(any(), eq(ref.previewId()), eq(ADMIN), eq(NOW)))
                .thenReturn(0);

        assertThatThrownBy(() -> lifecycle.apply(ref.previewId(), context(DisplayLanguage.JA)))
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(previews, never()).deleteByPreviewId(any());
    }

    @Test
    @DisplayName("a stored preview that no longer validates is an unexpected failure logging only hash and kinds")
    void storedPreviewNoLongerValid() {
        byte[] body = "version: 2\nbody_marker: 1\n".getBytes(StandardCharsets.UTF_8);
        DslPreviewRef ref = new DslPreviewRef(UUID.randomUUID(), READER.hash(body), DslSource.PASTE, ADMIN, NOW);
        when(previews.findRef()).thenReturn(Optional.of(ref));
        when(previews.findContent()).thenReturn(Optional.of(new DslContent<>(ref, body)));

        try (LogEvents logs = LogEvents.capture(DslLifecycle.class)) {
            assertThatThrownBy(() -> lifecycle.showPreview(context(DisplayLanguage.JA)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(String.valueOf(event.getKeyValuePairs()))
                        .contains("UNSUPPORTED_VERSION")
                        .doesNotContain("body_marker");
            });
        }
    }

    @Test
    @DisplayName("a failing event listener does not fail the operation and is logged as a warning")
    void failingListener() {
        DslLifecycle failing = new DslLifecycle(
                store,
                READER,
                active,
                generator,
                new DslPreviewAnalysis(target, active, new DslOperationMetrics(registry)),
                cache,
                new DslOperationMetrics(registry),
                users,
                event -> {
                    throw new IllegalStateException("受け取り側の失敗");
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                new DslManageProperties(org.springframework.util.unit.DataSize.ofMegabytes(10), 20));

        try (LogEvents logs = LogEvents.capture(DslLifecycle.class)) {
            PreviewView view = failing.submit(sample(), DslSource.UPLOAD, context(DisplayLanguage.JA));

            assertThat(view.preview().source()).isEqualTo(DslSource.UPLOAD);
            assertThat(logs.list())
                    .singleElement()
                    .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        }
    }

    private List<Map<String, String>> meterTags() {
        return registry.getMeters().stream()
                .map(Meter::getId)
                .filter(id -> id.getName().equals(DslOperationMetrics.METRIC))
                .map(id -> id.getTags().stream()
                        .collect(Collectors.toMap(
                                io.micrometer.core.instrument.Tag::getKey,
                                io.micrometer.core.instrument.Tag::getValue)))
                .toList();
    }
}
