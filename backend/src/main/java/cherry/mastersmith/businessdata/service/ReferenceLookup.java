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

import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import cherry.mastersmith.businessdata.domain.ReferenceFailureReason;
import cherry.mastersmith.businessdata.domain.ReferenceField;
import cherry.mastersmith.businessdata.domain.ReferenceOption;
import cherry.mastersmith.businessdata.domain.ReferencePolicy;
import cherry.mastersmith.businessdata.domain.ReferenceReadDeadline;
import cherry.mastersmith.businessdata.domain.ReferenceReadFailure;
import cherry.mastersmith.businessdata.domain.ReferenceReadSpec;
import cherry.mastersmith.businessdata.domain.ReferenceStatus;
import cherry.mastersmith.businessdata.domain.ReferenceTarget;
import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.businessdata.repository.RowSearchResult;
import cherry.mastersmith.businessdata.repository.RowSqlExecutor;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import cherry.mastersmith.targetdb.service.TargetReadAccess;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 他テーブル参照の読み取りの流れ（Intent 261029-reference-lookup-cleanup の U1、ADR-001、functional-spec.md 4節・5節）。この Bolt
 * （B1）では定義の取得の参照の項目（REFERENCE の選択肢・LOOKUP のピッカーの情報）を受け持つ。
 *
 * <p>参照先は参照元のカラムの DSL の options から引き（BR2.1）、参照先の判定の材料は参照元の判定で取った写しで作る（BR2.2）。REFERENCE
 * で読める項目があるときだけ、{@code REFERENCE} の目的で対象DB の読み取りの接続を1回借り（BR8.2）、その中で全体の締め切り
 * （{@link ReferenceReadDeadline}）を決めて文ごとに上限を絞る（BR8.4）。同じ参照先は1回だけ読み（BR4.7）、失敗の後は残りを読まない
 * （BR8.5）。トランザクションを開かない。
 *
 * <p>対象DB の失敗を写す所で、診断の WARN を1行だけ出す（キー {@code purpose}・{@code reason}・{@code sqlState}・
 * {@code vendorCode}・{@code exceptionClass}・{@code budgetExceeded}。名前・値・メッセージを出さない。BR8.7・observability-design.md
 * 2節）。
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
        return new DefinitionReferences.Failed(reason);
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
