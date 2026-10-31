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

import cherry.mastersmith.businessdata.domain.BoundValue;
import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import cherry.mastersmith.businessdata.domain.PickerListColumn;
import cherry.mastersmith.businessdata.domain.PickerSearchField;
import cherry.mastersmith.businessdata.domain.ReferenceCheckResult;
import cherry.mastersmith.businessdata.domain.ReferenceCondition;
import cherry.mastersmith.businessdata.domain.ReferenceCountState;
import cherry.mastersmith.businessdata.domain.ReferenceFailureReason;
import cherry.mastersmith.businessdata.domain.ReferenceField;
import cherry.mastersmith.businessdata.domain.ReferenceOption;
import cherry.mastersmith.businessdata.domain.ReferencePolicy;
import cherry.mastersmith.businessdata.domain.ReferenceReadDeadline;
import cherry.mastersmith.businessdata.domain.ReferenceReadFailure;
import cherry.mastersmith.businessdata.domain.ReferenceReadSpec;
import cherry.mastersmith.businessdata.domain.ReferenceRow;
import cherry.mastersmith.businessdata.domain.ReferenceRows;
import cherry.mastersmith.businessdata.domain.ReferenceRowsQuery;
import cherry.mastersmith.businessdata.domain.ReferenceStatus;
import cherry.mastersmith.businessdata.domain.ReferenceTarget;
import cherry.mastersmith.businessdata.domain.ReferenceValue;
import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.SearchCondition;
import cherry.mastersmith.businessdata.domain.SearchMode;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.businessdata.repository.RowSearchResult;
import cherry.mastersmith.businessdata.repository.RowSqlExecutor;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import cherry.mastersmith.targetdb.service.TargetReadAccess;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 他テーブル参照の読み取りの流れ（Intent 261029-reference-lookup-cleanup の U1、ADR-001、functional-spec.md 4節・5節）。定義の取得の
 * 参照の項目（REFERENCE の選択肢・LOOKUP のピッカーの情報。B1）と、参照先の検索 API の検索・ラベルの1件読み、U2 が保存の前に呼ぶ
 * 確かめの口 {@link #check}・{@link #countState}（B2）を受け持つ。
 *
 * <p>参照先は参照元のカラムの DSL の options から引き（BR2.1）、参照先の判定の材料は参照元の判定で取った写しで作る（BR2.2）。REFERENCE
 * で読める項目があるときだけ、{@code REFERENCE} の目的で対象DB の読み取りの接続を1回借り（BR8.2）、その中で全体の締め切り
 * （{@link ReferenceReadDeadline}）を決めて文ごとに上限を絞る（BR8.4）。同じ参照先は1回だけ読み（BR4.7）、失敗の後は残りを読まない
 * （BR8.5）。トランザクションを開かない。
 *
 * <p>対象DB の失敗を写す所で、診断の WARN を1行だけ出す（キー {@code purpose}・{@code reason}・{@code sqlState}・
 * {@code vendorCode}・{@code exceptionClass}・{@code budgetExceeded}。名前・値・メッセージを出さない。BR8.7・observability-design.md
 * 2節）。検索・ラベルの1件読みも同じ1行を出す。{@link #check}・{@link #countState} は WARN を出さない（写す U2 が出す。BR9.6）。
 */
@Service
public class ReferenceLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceLookup.class);

    private final TableAccessResolver resolver;

    private final TargetReadAccess readAccess;

    private final RowSqlExecutor executor;

    private final Clock clock;

    private final ColumnValueCodec codec = new ColumnValueCodec();

    /**
     * 作る。
     *
     * @param resolver 判定の材料を作る口（参照先の判定の材料を同じ写しで作る）
     * @param readAccess 業務データの読み取りの口
     * @param executor 行の SQL の部品
     * @param clock 時計（全体の締め切りに使う）
     */
    public ReferenceLookup(
            TableAccessResolver resolver, TargetReadAccess readAccess, RowSqlExecutor executor, Clock clock) {
        this.resolver = resolver;
        this.readAccess = readAccess;
        this.executor = executor;
        this.clock = clock;
    }

    /** 定義の取得の参照の項目の結果。 */
    public sealed interface DefinitionReferences {

        /**
         * 項目を作れた。
         *
         * @param fields カラムの名前ごとの参照の項目（参照でないカラムは含めない。DSL の並び）
         */
        record Read(Map<String, ReferenceField> fields) implements DefinitionReferences {

            /** 対応表を、順を保ったまま変更できないものにする。 */
            public Read {
                fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            }

            /** 項目の数だけを出す。 */
            @Override
            public String toString() {
                return "Read[fields=" + fields.size() + "]";
            }
        }

        /**
         * 対象DB の失敗で定義の取得ごと失敗した（BR1.5。どの項目かは持たない）。
         *
         * @param reason 理由
         */
        record Failed(ReferenceFailureReason reason) implements DefinitionReferences {

            /** 理由が必須であることを確かめる。 */
            public Failed {
                Objects.requireNonNull(reason, "reason は必須です");
            }
        }
    }

    /**
     * 定義の取得の参照の項目を作る（BR4.1・BR3.3・BR3.1・BR4・BR5.1）。用途が VIEW なら何も作らず、対象DB を読まない。
     *
     * @param source 参照元の判定の材料と写し
     * @param purpose 用途
     * @return 項目、または失敗
     * @throws BusinessDataUnexpectedException 想定外の失敗（メッセージも原因も持たない）
     */
    public DefinitionReferences definitionReferences(TableAccessResolution.Resolved source, DefinitionPurpose purpose) {
        Objects.requireNonNull(source, "source は必須です");
        Objects.requireNonNull(purpose, "purpose は必須です");
        if (!purpose.readsReferences()) {
            return new DefinitionReferences.Read(Map.of());
        }
        TableAccessView view = source.view();
        DslTable table = dslTable(source).orElseThrow(BusinessDataUnexpectedException::new);
        Map<String, ReferenceField> fields = new LinkedHashMap<>();
        Map<String, Pending> pending = new LinkedHashMap<>();
        for (ColumnAccess column : view.columns()) {
            DslColumn dsl = table.columns().get(column.column());
            Optional<ReferenceTarget> target =
                    dsl == null ? Optional.empty() : ReferenceTarget.from(view.schema(), dsl.options());
            if (target.isEmpty()) {
                continue;
            }
            ReferenceTarget t = target.get();
            if (!purpose.editable(column)) {
                // 入力できない項目のために参照先を読まない（BR3.3）。
                fields.put(column.column(), ReferenceField.noPermission(t.kind()));
                continue;
            }
            Optional<TableAccessView> targetView = resolver.referenceView(source.snapshot(), t.schema(), t.table());
            if (ReferencePolicy.accessOf(t, targetView) != ReferenceStatus.AVAILABLE) {
                fields.put(column.column(), ReferenceField.noPermission(t.kind()));
                continue;
            }
            if (t.kind() == OptionSourceKind.LOOKUP) {
                fields.put(column.column(), ReferenceField.lookup(ReferencePolicy.pickerOf(t, targetView.get())));
                continue;
            }
            // 順を保つため仮の項目を置き、読み終えたら差し替える。
            fields.put(column.column(), ReferenceField.noPermission(t.kind()));
            pending.computeIfAbsent(
                            t.table() + "\u0000" + t.valueColumn() + "\u0000" + t.labelColumn(),
                            key -> new Pending(t, targetView.get()))
                    .columns()
                    .add(column.column());
        }
        if (pending.isEmpty()) {
            // 読む REFERENCE が無ければ借りない（BR8.2）。
            return new DefinitionReferences.Read(fields);
        }
        TargetReadOutcome<ReadOutcome> outcome =
                readAccess.withRead(ReadPurpose.REFERENCE, session -> readOptions(session, pending.values()));
        return switch (outcome) {
            case TargetReadOutcome.NotConfigured<ReadOutcome> notConfigured ->
                fail(ReferenceFailureReason.UNAVAILABLE, null, null, null, false);
            case TargetReadOutcome.Unavailable<ReadOutcome> unavailable ->
                fail(
                        ReferenceFailureReason.UNAVAILABLE,
                        unavailable.sqlState(),
                        unavailable.vendorCode(),
                        unavailable.exceptionClass(),
                        false);
            case TargetReadOutcome.Done<ReadOutcome> done ->
                switch (done.value()) {
                    case ReadOutcome.Fields read -> {
                        read.byTarget().forEach((p, field) -> p.columns().forEach(name -> fields.put(name, field)));
                        yield new DefinitionReferences.Read(fields);
                    }
                    case ReadOutcome.Failed failed -> {
                        ReferenceReadFailure f = failed.failure();
                        yield fail(f.reason(), f.sqlState(), f.vendorCode(), f.exceptionClass(), f.budgetExceeded());
                    }
                    case ReadOutcome.Unexpected unexpected -> throw new BusinessDataUnexpectedException();
                };
        };
    }

    /** 借りた接続の中で、REFERENCE の項目を順に読む（同じ参照先は1回。失敗の後は読まない）。 */
    private ReadOutcome readOptions(TargetReadSession session, Iterable<Pending> targets) {
        // 締め切りは最初の選択肢を読む前に1回だけ決める（借りる待ちは含めない。BR8.4）。
        ReferenceReadDeadline deadline = ReferenceReadDeadline.start(clock, readAccess.referenceReadTotal());
        Map<Pending, ReferenceField> byTarget = new LinkedHashMap<>();
        for (Pending target : targets) {
            Optional<TargetReadSession> narrowed = deadline.narrow(session);
            if (narrowed.isEmpty()) {
                return new ReadOutcome.Failed(ReferenceReadFailure.ofBudgetExceeded());
            }
            ReferenceReadSpec spec = ReferencePolicy.optionsSpec(target.target(), target.view());
            RowSearchResult result = executor.search(narrowed.get(), target.view(), List.of(), null, Optional.of(spec));
            switch (result) {
                case RowSearchResult.Rows rows -> byTarget.put(target, options(target, rows.rows()));
                case RowSearchResult.Failed failed -> {
                    try {
                        return new ReadOutcome.Failed(ReferenceReadFailure.of(failed.failure()));
                    } catch (IllegalArgumentException e) {
                        // INVALID_VALUE・UNEXPECTED は写さず想定外にする（reliability-design.md 5.2）。
                        return new ReadOutcome.Unexpected();
                    }
                }
                case RowSearchResult.Page page -> {
                    return new ReadOutcome.Unexpected();
                }
            }
        }
        return new ReadOutcome.Fields(byTarget);
    }

    /** 読んだ行から REFERENCE の項目を作る（201 行で TOO_MANY、ほかは同じ値を落とした選択肢。BR4.3・BR4.4）。 */
    private ReferenceField options(Pending target, List<RowRecord> rows) {
        if (rows.size() >= ReferenceReadSpec.OPTION_ROWS) {
            return ReferenceField.tooMany();
        }
        String valueColumn = target.target().valueColumn();
        String labelColumn = target.target().labelColumn();
        ColumnAccess value = target.view().column(valueColumn).orElseThrow();
        List<ReferenceOption> options = new ArrayList<>();
        for (RowRecord row : rows) {
            String text = row.key() == null ? null : row.key().values().get(valueColumn);
            if (text == null) {
                continue;
            }
            options.add(new ReferenceOption(text, row.values().values().get(labelColumn)));
        }
        return ReferenceField.choices(ReferencePolicy.distinctByValue(
                options, option -> codec.read(value, option.value()).orElse(null)));
    }

    /**
     * ピッカーの検索（判定の順の ③〜⑤。BR1.2・BR1.3・BR5.2〜BR5.9）。写しを1回取り、参照元のカラムが LOOKUP で FULL、参照先が
     * {@code AVAILABLE} で、条件のカラムがすべて {@code searchFields} にあるときだけ、{@code REFERENCE} の目的で接続を1回借りて文を
     * 1つ流す（件数を数えない。BR8.2）。値が空の条件は SQL に使わない（BR5.4）。
     *
     * @param userId 利用者 ID（要求の文脈から読んだもの）
     * @param query 本文の形を確かめた検索の要求
     * @return 行・同じ応答の拒否・値の型の誤り・失敗
     * @throws BusinessDataUnexpectedException 想定外の失敗（メッセージも原因も持たない）
     */
    public ReferenceRowsResult search(long userId, ReferenceRowsQuery query) {
        Objects.requireNonNull(query, "query は必須です");
        if (query.mode() != ReferenceRowsQuery.Mode.SEARCH) {
            throw new IllegalArgumentException("検索の要求ではありません");
        }
        Optional<Located> located = locateForApi(userId, query);
        if (located.isEmpty() || located.get().target().kind() != OptionSourceKind.LOOKUP) {
            // REFERENCE に検索は送れない（BR5.2）。
            return new ReferenceRowsResult.Denied();
        }
        ReferenceTarget target = located.get().target();
        TableAccessView view = located.get().view();
        Map<String, PickerSearchField> fields = new HashMap<>();
        ReferencePolicy.usableSearchFields(target, view).forEach(f -> fields.put(f.column(), f));
        // 条件のカラムの照合（403）を、値の型の読み取り（400）より先にすべて行う（BR1.2 の ③ → ④）。
        for (ReferenceCondition condition : query.conditions()) {
            if (!fields.containsKey(condition.column())) {
                return new ReferenceRowsResult.Denied();
            }
        }
        List<SearchCondition> conditions = new ArrayList<>();
        for (ReferenceCondition condition : query.conditions()) {
            if (condition.isEmpty()) {
                continue;
            }
            PickerSearchField field = fields.get(condition.column());
            ColumnAccess column = view.column(field.column()).orElseThrow(BusinessDataUnexpectedException::new);
            Optional<BoundValue> value = codec.read(column, condition.value());
            if (value.isEmpty()) {
                return new ReferenceRowsResult.InvalidValue(field.column());
            }
            conditions.add(
                    field.operator() == SearchMode.EQUALS
                            ? SearchCondition.equalTo(field.column(), value.get())
                            : SearchCondition.contains(field.column(), value.get()));
        }
        ReferenceReadSpec spec = ReferencePolicy.pickerSpec(target, view, query.offset());
        List<PickerListColumn> listColumns =
                ReferencePolicy.pickerOf(target, view).listColumns();
        return readRows(view, conditions, spec, rows -> {
            List<ReferenceRow> page = new ArrayList<>();
            for (RowRecord row : rows.subList(0, Math.min(rows.size(), ReferenceRowsQuery.PAGE_SIZE))) {
                List<String> cells = new ArrayList<>();
                listColumns.forEach(c -> cells.add(row.values().values().get(c.column())));
                page.add(new ReferenceRow(
                        valueOf(row, target), row.values().values().get(target.labelColumn()), cells));
            }
            return new ReferenceRowsResult.Found(
                    ReferenceRows.search(page, query.page(), rows.size() > ReferenceRowsQuery.PAGE_SIZE));
        });
    }

    /**
     * ラベルの1件読み（判定の順の ③・⑤・⑥。BR6.1〜BR6.4）。REFERENCE・LOOKUP の両方を検索と同じ権限の判定で受け付け、値を値の
     * カラムの型に変えられなければ問い合わせずに 403 の同じ応答にする。一致は DB の比べ方に従い、並びの最初の1行のラベルを返す。行が
     * 無ければ 403 の同じ応答。
     *
     * @param userId 利用者 ID（要求の文脈から読んだもの）
     * @param query 本文の形を確かめたラベルの1件読みの要求
     * @return 1行・同じ応答の拒否・失敗
     * @throws BusinessDataUnexpectedException 想定外の失敗（メッセージも原因も持たない）
     */
    public ReferenceRowsResult label(long userId, ReferenceRowsQuery query) {
        Objects.requireNonNull(query, "query は必須です");
        if (query.mode() != ReferenceRowsQuery.Mode.VALUE) {
            throw new IllegalArgumentException("ラベルの1件読みの要求ではありません");
        }
        Optional<Located> located = locateForApi(userId, query);
        if (located.isEmpty()) {
            return new ReferenceRowsResult.Denied();
        }
        ReferenceTarget target = located.get().target();
        TableAccessView view = located.get().view();
        Optional<BoundValue> value = typedValue(located.get(), query.value());
        if (value.isEmpty()) {
            return new ReferenceRowsResult.Denied();
        }
        return readRows(
                view,
                List.of(SearchCondition.equalTo(target.valueColumn(), value.get())),
                ReferencePolicy.labelSpec(target, view),
                rows -> rows.isEmpty()
                        ? new ReferenceRowsResult.Denied()
                        : new ReferenceRowsResult.Found(ReferenceRows.label(new ReferenceRow(
                                valueOf(rows.get(0), target),
                                rows.get(0).values().values().get(target.labelColumn()),
                                List.of()))));
    }

    /**
     * 保存前の確かめの口（契約 C1 の {@code check}、BR9.1〜BR9.6）。U2 が {@code REFERENCE} の目的で借りた接続（締め切りで絞った
     * もの）を受け、自分では借りない。参照でないカラム・読めない参照先・型として読めない値は問い合わせずに {@code NotFoundOrDenied}。
     * DB の比べ方で値の一致する行を 201 行まで読み、最初の 200 行の中に型つきの値として厳密に一致する行があるときだけ {@code Found}。
     * WARN を出さず、値をログに出さない。
     *
     * @param source 参照元の判定の材料と写し（U2 が取ったもの）
     * @param column 参照元のカラム
     * @param value 確かめる値
     * @param session U2 が借りた読み取りの接続
     * @return 結果
     * @throws BusinessDataUnexpectedException 想定外の失敗（メッセージも原因も持たない）
     */
    public ReferenceCheckResult check(
            TableAccessResolution.Resolved source, String column, ReferenceValue value, TargetReadSession session) {
        Objects.requireNonNull(source, "source は必須です");
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(value, "value は必須です");
        Objects.requireNonNull(session, "session は必須です");
        Optional<Located> located = locate(source, column);
        if (located.isEmpty()) {
            return new ReferenceCheckResult.NotFoundOrDenied();
        }
        ReferenceTarget target = located.get().target();
        TableAccessView view = located.get().view();
        Optional<BoundValue> typed = typedValue(located.get(), value);
        if (typed.isEmpty()) {
            return new ReferenceCheckResult.NotFoundOrDenied();
        }
        ColumnAccess valueColumn = view.column(target.valueColumn()).orElseThrow(BusinessDataUnexpectedException::new);
        RowSearchResult result = executor.search(
                session,
                view,
                List.of(SearchCondition.equalTo(target.valueColumn(), typed.get())),
                null,
                Optional.of(ReferencePolicy.checkSpec(target, view)));
        return switch (result) {
            case RowSearchResult.Rows rows -> {
                List<RowRecord> compared =
                        rows.rows().subList(0, Math.min(rows.rows().size(), ReferenceReadSpec.CHECK_COMPARED_ROWS));
                boolean found = compared.stream()
                        .map(row ->
                                codec.read(valueColumn, valueOf(row, target)).orElse(null))
                        .anyMatch(read -> ReferencePolicy.sameReferenceValue(typed.get(), read));
                yield found ? new ReferenceCheckResult.Found() : new ReferenceCheckResult.NotFoundOrDenied();
            }
            case RowSearchResult.Failed failed -> new ReferenceCheckResult.Failed(writeFailureOf(failed.failure()));
            case RowSearchResult.Page page -> throw new BusinessDataUnexpectedException();
        };
    }

    /**
     * U2 に向けた2つ目の口（BR9.7、機能設計の D7）。U2 が借りた接続を受け、自分では借りない。参照でない・読めない参照先は問い合わせ
     * ずに {@code NoPermission}、LOOKUP は数えず {@code Available}、REFERENCE は選択肢と同じ読み方で 201 行まで読み、201 行目が
     * あれば {@code TooMany}。WARN を出さず、値・ラベルを返さない。
     *
     * @param source 参照元の判定の材料と写し（U2 が取ったもの）
     * @param column 参照元のカラム
     * @param session U2 が借りた読み取りの接続
     * @return 結果
     * @throws BusinessDataUnexpectedException 想定外の失敗（メッセージも原因も持たない）
     */
    public ReferenceCountState countState(
            TableAccessResolution.Resolved source, String column, TargetReadSession session) {
        Objects.requireNonNull(source, "source は必須です");
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(session, "session は必須です");
        Optional<Located> located = locate(source, column);
        if (located.isEmpty()) {
            return new ReferenceCountState.NoPermission();
        }
        ReferenceTarget target = located.get().target();
        if (target.kind() == OptionSourceKind.LOOKUP) {
            return new ReferenceCountState.Available();
        }
        TableAccessView view = located.get().view();
        RowSearchResult result =
                executor.search(session, view, List.of(), null, Optional.of(ReferencePolicy.optionsSpec(target, view)));
        return switch (result) {
            case RowSearchResult.Rows rows ->
                rows.rows().size() >= ReferenceReadSpec.OPTION_ROWS
                        ? new ReferenceCountState.TooMany()
                        : new ReferenceCountState.Available();
            case RowSearchResult.Failed failed -> new ReferenceCountState.Failed(writeFailureOf(failed.failure()));
            case RowSearchResult.Page page -> throw new BusinessDataUnexpectedException();
        };
    }

    /**
     * 保存前の確かめに向けた口（Intent 261029-reference-lookup-cleanup の U2、計画の 9節 Q1）。参照元のテーブルの REFERENCE・LOOKUP
     * のカラムと参照先を、判定の材料のカラムの並び（DSL の並び）で返す。参照先は同じ写しの DSL から引き、FIXED と参照でないカラムを
     * 含めない。対象DB を読まない。
     *
     * @param source 参照元の判定の材料と写し
     * @return カラムの名前ごとの参照先（並びを保つ。変更できない）
     */
    public Map<String, ReferenceTarget> writeTargets(TableAccessResolution.Resolved source) {
        Objects.requireNonNull(source, "source は必須です");
        Optional<DslTable> table = dslTable(source);
        if (table.isEmpty()) {
            return Map.of();
        }
        Map<String, ReferenceTarget> targets = new LinkedHashMap<>();
        for (ColumnAccess column : source.view().columns()) {
            DslColumn dsl = table.get().columns().get(column.column());
            if (dsl != null) {
                ReferenceTarget.from(source.view().schema(), dsl.options())
                        .ifPresent(target -> targets.put(column.column(), target));
            }
        }
        return java.util.Collections.unmodifiableMap(targets);
    }

    /**
     * 参照先を読めるかを、参照元と同じ写しで判定する（Intent 261029-reference-lookup-cleanup の U2、計画の 9節 Q1、BR3.2）。対象DB を
     * 読まない。
     *
     * @param source 参照元の判定の材料と写し
     * @param target 参照先（{@link #writeTargets} で引いたもの）
     * @return {@link ReferenceStatus#AVAILABLE} か {@link ReferenceStatus#NO_PERMISSION}
     */
    public ReferenceStatus accessOf(TableAccessResolution.Resolved source, ReferenceTarget target) {
        Objects.requireNonNull(source, "source は必須です");
        Objects.requireNonNull(target, "target は必須です");
        return ReferencePolicy.accessOf(
                target, resolver.referenceView(source.snapshot(), target.schema(), target.table()));
    }

    /**
     * 検索 API の ③ の判定（BR1.3・BR3.3・BR6.1）。写しを1回取り、参照元のテーブル・カラムを DSL と照らし、参照元のカラムの主権限が
     * FULL で、参照先が {@code AVAILABLE} のときだけ参照先を返す。
     */
    private Optional<Located> locateForApi(long userId, ReferenceRowsQuery query) {
        TableAccessResolution resolution =
                resolver.resolve(userId, new TableQuery(query.schema(), query.table(), null));
        if (!(resolution instanceof TableAccessResolution.Resolved resolved)) {
            return Optional.empty();
        }
        boolean full = resolved.view()
                .column(query.column())
                .map(c -> c.main() == MainPermission.FULL)
                .orElse(false);
        return full ? locate(resolved, query.column()) : Optional.empty();
    }

    /** 参照元のカラムの DSL の options から参照先を引き、同じ写しで判定して {@code AVAILABLE} のときだけ返す（BR2.1〜BR2.3・BR3.1）。 */
    private Optional<Located> locate(TableAccessResolution.Resolved source, String column) {
        DslColumn dsl = dslTable(source).map(t -> t.columns().get(column)).orElse(null);
        Optional<ReferenceTarget> target = dsl == null
                ? Optional.empty()
                : ReferenceTarget.from(source.view().schema(), dsl.options());
        if (target.isEmpty()) {
            return Optional.empty();
        }
        ReferenceTarget t = target.get();
        Optional<TableAccessView> view = resolver.referenceView(source.snapshot(), t.schema(), t.table());
        if (ReferencePolicy.accessOf(t, view) != ReferenceStatus.AVAILABLE) {
            return Optional.empty();
        }
        return Optional.of(new Located(t, view.get()));
    }

    /** 値を値のカラムの DSL の型に変える（変えられなければ空。BR7.1）。 */
    private Optional<BoundValue> typedValue(Located located, ReferenceValue value) {
        return located.view()
                .column(located.target().valueColumn())
                .flatMap(column -> codec.read(column, value.value()));
    }

    /** 読んだ行の値のカラムの値（切らない側。来ないはずの欠けは想定外）。 */
    private static String valueOf(RowRecord row, ReferenceTarget target) {
        String value = row.key() == null ? null : row.key().values().get(target.valueColumn());
        if (value == null) {
            throw new BusinessDataUnexpectedException();
        }
        return value;
    }

    /** 失敗を写す（INVALID_VALUE・UNEXPECTED は写さず想定外。reliability-design.md 5.2）。 */
    private static ReferenceReadFailure failureOf(RowQueryFailure failure) {
        try {
            return ReferenceReadFailure.of(failure);
        } catch (IllegalArgumentException e) {
            throw new BusinessDataUnexpectedException();
        }
    }

    /**
     * 保存の前の確かめの口（{@link #check}・{@link #countState}）の失敗を写す。写さない理由（INVALID_VALUE・UNEXPECTED）は、値を含まない
     * 手がかり（SQLState・業者の番号・例外のクラスの名前）を持つ想定外にし、U2 が口の外で診断の WARN を出せるようにする（直しの Bolt B7、
     * U2 の NFR1.32）。読み取りの API の経路は {@link #failureOf} のまま変えない。
     */
    private static ReferenceReadFailure writeFailureOf(RowQueryFailure failure) {
        try {
            return ReferenceReadFailure.of(failure);
        } catch (IllegalArgumentException e) {
            throw BusinessDataUnexpectedException.withDiagnosis(
                    failure.sqlState(), failure.vendorCode(), failure.exceptionClass());
        }
    }

    /** 検索・ラベルの1件読みの接続を1回借りて文を1つ流し、結果を写す（失敗は診断の WARN を1行。BR1.5・BR8.7）。 */
    private ReferenceRowsResult readRows(
            TableAccessView view,
            List<SearchCondition> conditions,
            ReferenceReadSpec spec,
            java.util.function.Function<List<RowRecord>, ReferenceRowsResult> found) {
        TargetReadOutcome<RowSearchResult> outcome = readAccess.withRead(
                ReadPurpose.REFERENCE, session -> executor.search(session, view, conditions, null, Optional.of(spec)));
        return switch (outcome) {
            case TargetReadOutcome.NotConfigured<RowSearchResult> notConfigured -> {
                warn(ReferenceFailureReason.UNAVAILABLE, null, null, null, false);
                yield new ReferenceRowsResult.Failed(ReferenceFailureReason.UNAVAILABLE);
            }
            case TargetReadOutcome.Unavailable<RowSearchResult> unavailable -> {
                warn(
                        ReferenceFailureReason.UNAVAILABLE,
                        unavailable.sqlState(),
                        unavailable.vendorCode(),
                        unavailable.exceptionClass(),
                        false);
                yield new ReferenceRowsResult.Failed(ReferenceFailureReason.UNAVAILABLE);
            }
            case TargetReadOutcome.Done<RowSearchResult> done ->
                switch (done.value()) {
                    case RowSearchResult.Rows rows -> found.apply(rows.rows());
                    case RowSearchResult.Failed failed -> {
                        ReferenceReadFailure f = failureOf(failed.failure());
                        warn(f.reason(), f.sqlState(), f.vendorCode(), f.exceptionClass(), f.budgetExceeded());
                        yield new ReferenceRowsResult.Failed(f.reason());
                    }
                    case RowSearchResult.Page page -> throw new BusinessDataUnexpectedException();
                };
        };
    }

    /**
     * 照らした参照先と、その判定の材料（{@code AVAILABLE} のもの）。
     *
     * @param target 参照先
     * @param view 参照先のテーブルの判定の材料
     */
    private record Located(ReferenceTarget target, TableAccessView view) {}

    private Optional<DslTable> dslTable(TableAccessResolution.Resolved source) {
        return source.snapshot()
                .dsl()
                .flatMap(dsl -> dsl.schemas().stream()
                        .filter(s -> s.name().equals(source.view().schema()))
                        .findFirst())
                .map(DslSchema::tables)
                .map(tables -> tables.get(source.view().table()));
    }

    /** 失敗を写し、診断の WARN を1行だけ出す（BR8.7）。 */
    private static DefinitionReferences fail(
            ReferenceFailureReason reason,
            String sqlState,
            Integer vendorCode,
            String exceptionClass,
            boolean budgetExceeded) {
        warn(reason, sqlState, vendorCode, exceptionClass, budgetExceeded);
        return new DefinitionReferences.Failed(reason);
    }

    /** 診断の WARN を1行だけ出す（キーと値だけ。名前・値・メッセージを出さない。BR8.7）。 */
    private static void warn(
            ReferenceFailureReason reason,
            String sqlState,
            Integer vendorCode,
            String exceptionClass,
            boolean budgetExceeded) {
        var log = LOGGER.atWarn().addKeyValue("purpose", ReadPurpose.REFERENCE).addKeyValue("reason", reason);
        if (sqlState != null) {
            log = log.addKeyValue("sqlState", sqlState);
        }
        if (vendorCode != null) {
            log = log.addKeyValue("vendorCode", vendorCode);
        }
        if (exceptionClass != null) {
            log = log.addKeyValue("exceptionClass", exceptionClass);
        }
        log.addKeyValue("budgetExceeded", budgetExceeded).log("対象DB の参照先を読めませんでした");
    }

    /**
     * 読む参照先1つ（同じ参照先を指す参照元のカラムをまとめる）。
     *
     * @param target 参照先
     * @param view 参照先の判定の材料
     * @param columns 参照元のカラムの名前
     */
    private record Pending(ReferenceTarget target, TableAccessView view, List<String> columns) {

        Pending(ReferenceTarget target, TableAccessView view) {
            this(target, view, new ArrayList<>());
        }

        /** 同じ参照先の組は1つにまとめるため、同じものとして比べるのは自分自身だけにする。 */
        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        /** 参照先だけを出す（名前と値を出さない）。 */
        @Override
        public String toString() {
            return "Pending[target=" + target + ", columns=" + columns.size() + "]";
        }
    }

    /** 借りた接続の中の結果（例外を口の外へ投げないため）。 */
    private sealed interface ReadOutcome {

        /** 読めた。 */
        record Fields(Map<Pending, ReferenceField> byTarget) implements ReadOutcome {}

        /** 写せる失敗。 */
        record Failed(ReferenceReadFailure failure) implements ReadOutcome {}

        /** 想定外。 */
        record Unexpected() implements ReadOutcome {}
    }
}
