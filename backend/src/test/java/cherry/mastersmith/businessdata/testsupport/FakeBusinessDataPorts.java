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
package cherry.mastersmith.businessdata.testsupport;

import cherry.mastersmith.businessdata.domain.PageRequest;
import cherry.mastersmith.businessdata.domain.ReferenceReadSpec;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.SearchCondition;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TypedValue;
import cherry.mastersmith.businessdata.repository.RowChangeResult;
import cherry.mastersmith.businessdata.repository.RowDetailResult;
import cherry.mastersmith.businessdata.repository.RowInsertResult;
import cherry.mastersmith.businessdata.repository.RowInspectResult;
import cherry.mastersmith.businessdata.repository.RowPrecheckResult;
import cherry.mastersmith.businessdata.repository.RowSearchResult;
import cherry.mastersmith.businessdata.repository.RowSqlExecutor;
import cherry.mastersmith.role.domain.EffectivePermission;
import cherry.mastersmith.role.domain.PermissionSnapshot;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.service.EffectivePermissionResolver;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import cherry.mastersmith.targetdb.domain.TargetWriteFailure;
import cherry.mastersmith.targetdb.domain.TargetWriteOutcome;
import cherry.mastersmith.targetdb.domain.TargetWriteSession;
import cherry.mastersmith.targetdb.domain.TargetWriteStage;
import cherry.mastersmith.targetdb.domain.WriteStep;
import cherry.mastersmith.targetdb.service.TargetReadAccess;
import cherry.mastersmith.targetdb.service.TargetWriteAccess;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

/**
 * businessdata の業務処理の層のテストで使う、手で書いた小さな偽物（unit-test-instructions.md の 6節）。呼んだ順と回数を1つの記録に
 * 残す。
 */
public final class FakeBusinessDataPorts {

    /** 呼んだ順の記録。 */
    public final List<String> calls = new ArrayList<>();

    /** 解決の口の偽物。 */
    public final Resolver resolver = new Resolver();

    /** 読み取りの口の偽物。 */
    public final ReadAccess readAccess = new ReadAccess();

    /** 行の SQL の部品の偽物。 */
    public final Executor executor = new Executor();

    /** 書き込みの口の偽物（U2）。 */
    public final WriteAccess writeAccess = new WriteAccess();

    /** 出来事の発行の偽物（U2）。発行した出来事を残す。 */
    public final List<Object> events = new ArrayList<>();

    /** 出来事の発行の偽物（U2）。 */
    public final ApplicationEventPublisher publisher = event -> {
        calls.add("event");
        events.add(event);
    };

    /** 書き込みの口の答え方（U2）。 */
    public enum WriteMode {
        /** 処理を呼び、答えに従って Done を返す。 */
        DONE,
        /** 設定が無い。 */
        NOT_CONFIGURED,
        /** 借りられない。 */
        UNAVAILABLE,
        /** 処理を呼び、答えによらず決めた失敗を返す（確定の段の失敗など）。 */
        FAILED,
        /** 処理を呼び、セーブポイントへの戻しを決めた失敗にする。 */
        INSPECT_FAILS
    }

    /** 書き込みの口の偽物（U2）。答えの種類を {@link #answers} に残す。 */
    public final class WriteAccess implements TargetWriteAccess {

        /** 答え方。 */
        public WriteMode mode = WriteMode.DONE;

        /** DB の種類（空なら書き込みの設定が無い）。 */
        public Optional<DatabaseProduct> product = Optional.of(DatabaseProduct.POSTGRESQL);

        /** FAILED・INSPECT_FAILS で返す失敗。 */
        public TargetWriteFailure failure =
                new TargetWriteFailure(TargetWriteStage.COMMIT, "08006", 0, "java.sql.SQLException", true);

        /** 処理の答えの種類。 */
        public final List<String> answers = new ArrayList<>();

        @Override
        public <T> TargetWriteOutcome<T> withWrite(Function<TargetWriteSession, WriteStep<T>> work) {
            calls.add("withWrite");
            return switch (mode) {
                case NOT_CONFIGURED -> new TargetWriteOutcome.NotConfigured<>();
                case UNAVAILABLE -> new TargetWriteOutcome.Unavailable<>("08001", 0, "java.sql.SQLException");
                case FAILED -> {
                    answers.add(work.apply(session()).toString());
                    yield new TargetWriteOutcome.Failed<>(failure);
                }
                case DONE, INSPECT_FAILS -> {
                    WriteStep<T> step = work.apply(session());
                    answers.add(step.toString());
                    yield new TargetWriteOutcome.Done<>(
                            switch (step) {
                                case WriteStep.Commit<T> commit -> commit.result();
                                case WriteStep.Rollback<T> rollback -> rollback.result();
                                case WriteStep.RollbackToSavepointThenInspect<T> inspect ->
                                    mode == WriteMode.INSPECT_FAILS
                                            ? inspect.onFailure().apply(failure)
                                            : inspect.inspect().apply(session());
                            });
                }
            };
        }

        private TargetWriteSession session() {
            return new TargetWriteSession(
                    product.orElse(DatabaseProduct.POSTGRESQL), Mockito.mock(Connection.class), 5, () -> {});
        }

        @Override
        public Optional<DatabaseProduct> product() {
            return product;
        }
    }

    /** 読み取りの口の答え方。 */
    public enum Mode {
        /** 処理を呼んで Done を返す。 */
        DONE,
        /** 設定が無い。 */
        NOT_CONFIGURED,
        /** 借りられない。 */
        UNAVAILABLE
    }

    /** 解決の口の偽物（{@link #snapshotFor} だけを使う）。 */
    public final class Resolver implements EffectivePermissionResolver {

        /** 返す写し。 */
        public PermissionSnapshot snapshot;

        @Override
        public Optional<WorkRoleRef> effectiveWorkRole(long userId) {
            throw new AssertionError("使わない");
        }

        @Override
        public PermissionSnapshot snapshotFor(long userId) {
            calls.add("snapshotFor:" + userId);
            return snapshot;
        }

        @Override
        public EffectivePermission resolve(long userId, PermissionTarget target) {
            throw new AssertionError("使わない");
        }
    }

    /** 読み取りの口の偽物。 */
    public final class ReadAccess implements TargetReadAccess {

        /** 答え方。 */
        public Mode mode = Mode.DONE;

        /** DB の種類。 */
        public Optional<DatabaseProduct> product = Optional.of(DatabaseProduct.POSTGRESQL);

        @Override
        public <T> TargetReadOutcome<T> withRead(ReadPurpose purpose, Function<TargetReadSession, T> work) {
            calls.add("withRead:" + purpose);
            return switch (mode) {
                case NOT_CONFIGURED -> new TargetReadOutcome.NotConfigured<>();
                case UNAVAILABLE -> new TargetReadOutcome.Unavailable<>("08001", 0, "java.sql.SQLException");
                case DONE ->
                    new TargetReadOutcome.Done<>(work.apply(new TargetReadSession(
                            product.orElse(DatabaseProduct.POSTGRESQL), Mockito.mock(Connection.class), 5)));
            };
        }

        @Override
        public Optional<DatabaseProduct> product() {
            calls.add("product");
            return product;
        }

        /** 参照先を読む全体の待ちの上限（本物の既定 10 秒）。 */
        public Duration referenceReadTotal = Duration.ofSeconds(10);

        /** 本物と同じく、設定が使えない形（{@link Mode#NOT_CONFIGURED}）では {@link IllegalStateException} を投げる。 */
        @Override
        public Duration referenceReadTotal() {
            if (mode == Mode.NOT_CONFIGURED) {
                throw new IllegalStateException("対象DB の設定が使える状態ではありません");
            }
            return referenceReadTotal;
        }
    }

    /** 行の SQL の部品の偽物。 */
    public final class Executor extends RowSqlExecutor {

        /** 一覧の答え。 */
        public RowSearchResult searchResult = new RowSearchResult.Page(0, List.of());

        /** 詳細の答え。 */
        public RowDetailResult detailResult = new RowDetailResult.NotFound();

        /** 受け取った条件。 */
        public List<SearchCondition> conditions;

        /** 受け取ったページ。 */
        public PageRequest page;

        /** 受け取った参照先の読み取りの指定（一覧と検索では空）。 */
        public List<Optional<ReferenceReadSpec>> references = new ArrayList<>();

        /** 受け取った session（参照先の読み取りで、締め切りで絞った上限を確かめる）。 */
        public List<TargetReadSession> sessions = new ArrayList<>();

        /** 参照先の読み取りの答え（呼ばれた順。尽きたら空の行）。 */
        public java.util.Deque<RowSearchResult> referenceResults = new java.util.ArrayDeque<>();

        /** 参照先の読み取りのたびに呼ぶ処理（時計を進めるなど）。 */
        public Runnable onReference = () -> {};

        @Override
        public RowSearchResult search(
                TargetReadSession session,
                TableAccessView view,
                List<SearchCondition> conditions,
                PageRequest page,
                Optional<ReferenceReadSpec> reference) {
            calls.add("search");
            this.conditions = conditions;
            this.page = page;
            this.references.add(reference);
            this.sessions.add(session);
            if (reference.isPresent()) {
                onReference.run();
                RowSearchResult next = referenceResults.poll();
                return next != null ? next : new RowSearchResult.Rows(List.of());
            }
            return searchResult;
        }

        @Override
        public RowDetailResult detail(TargetReadSession session, TableAccessView view, RowKey key) {
            calls.add("detail");
            return detailResult;
        }

        /** 事前の問い合わせの答え（U2）。 */
        public RowPrecheckResult precheckResult = new RowPrecheckResult.Clear();

        /** 作成の答え（U2）。 */
        public RowInsertResult insertResult =
                new RowInsertResult.Inserted(new RowValues(Map.of("id", "101"), Set.of()));

        /** 更新の答え（U2）。 */
        public RowChangeResult updateResult = new RowChangeResult.Changed(1);

        /** 削除の答え（U2）。 */
        public RowChangeResult deleteResult = new RowChangeResult.Changed(1);

        /** 確かめの答え（U2）。 */
        public RowInspectResult inspectResult = new RowInspectResult.Inspected(Optional.of("name"));

        /** 受け取った型つきの値（U2）。 */
        public List<TypedValue> values;

        @Override
        public RowPrecheckResult precheckUnique(
                TargetWriteSession session, TableAccessView view, List<TypedValue> values, RowKey self) {
            calls.add("precheck");
            this.values = values;
            return precheckResult;
        }

        @Override
        public RowInsertResult insert(TargetWriteSession session, TableAccessView view, List<TypedValue> values) {
            calls.add("insert");
            this.values = values;
            return insertResult;
        }

        @Override
        public RowChangeResult update(
                TargetWriteSession session, TableAccessView view, List<TypedValue> values, RowKey key) {
            calls.add("update");
            this.values = values;
            return updateResult;
        }

        @Override
        public RowChangeResult delete(TargetWriteSession session, TableAccessView view, RowKey key) {
            calls.add("delete");
            return deleteResult;
        }

        @Override
        public RowInspectResult inspectViolation(
                TargetWriteSession session,
                TableAccessView view,
                boolean reference,
                List<TypedValue> values,
                RowKey self) {
            calls.add(reference ? "inspectReference" : "inspectUnique");
            return inspectResult;
        }
    }
}
