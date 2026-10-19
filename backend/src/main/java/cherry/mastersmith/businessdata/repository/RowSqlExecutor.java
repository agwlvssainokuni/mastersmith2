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
package cherry.mastersmith.businessdata.repository;

import cherry.mastersmith.businessdata.domain.BoundValue;
import cherry.mastersmith.businessdata.domain.ColumnWriteRules;
import cherry.mastersmith.businessdata.domain.PageRequest;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowQueryFailureReason;
import cherry.mastersmith.businessdata.domain.RowQueryStage;
import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.RowWriteFailure;
import cherry.mastersmith.businessdata.domain.RowWriteStage;
import cherry.mastersmith.businessdata.domain.SearchCondition;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TypedValue;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import cherry.mastersmith.targetdb.domain.TargetWriteSession;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 業務データの行の SQL を組み立てて実行する唯一の部品（BR3.5、logical-components.md の L10）。引数は貸し出し中の
 * {@link TargetReadSession} で、{@code java.sql.Connection} を直接受けない（BR10.1）。
 *
 * <p>識別子は判定の材料の種類ごとの名前の一覧で引用し、値はすべて {@link ColumnValueCodec} の型ごとのセッターでバインドする
 * （BR3.3・BR3.4・BR7.8）。{@code SQLException} と非検査の例外は各メソッドの中で受け、メッセージと原因を捨てた
 * {@link RowQueryFailure} にして返す（BR10.2、NFR 設計のレビュー R-02）。時間切れの後は同じ接続で続けて問い合わせない。
 *
 * <p>文（{@code prepareStatement}・{@code createStatement}）を作るのは {@link #search}・{@link #detail} と、書き込みの
 * {@link #precheckUnique}・{@link #insert}・{@link #update}・{@link #delete}・{@link #inspectViolation} の本体だけで、ラムダやほかの
 * メソッドの中では作らない（SpotBugs の除外をメソッドの名前で絞るため。U1 の計画の A2、U2 の計画の A2・A3）。MySQL・MariaDB の
 * タイムゾーンは接続を作るときの初期化の文で付ける（U2 の D5: A）ため、貸し出しの始めの文は流さない。
 *
 * <p>書き込みのメソッド（Intent 261016-business-data-views の U2、BR5〜BR8・BR10）は、貸し出し中の {@link TargetWriteSession} を受け、
 * 文ごとに書き込みの上限を付け、{@code SQLException} と非検査の例外をメソッドの中で受けて、段つきの {@link RowWriteFailure} を持つ結果の
 * 型にして返す（U1 R-02 を先に避ける）。確定・巻き戻しはしない（書き込みの口が答えに従って行う）。
 */
@Component
public class RowSqlExecutor {

    private final ColumnValueCodec codec = new ColumnValueCodec();

    /**
     * 一覧と検索を読む（件数とページ。BR5.6〜BR5.8）。件数が読み飛ばす行の数以下なら、行を読まずに空のページを返す。
     *
     * @param session 貸し出し中の接続
     * @param view 判定の材料
     * @param conditions 型として読んだ条件
     * @param page ページの指定
     * @return 件数とページ、または失敗
     */
    public RowSearchResult search(
            TargetReadSession session, TableAccessView view, List<SearchCondition> conditions, PageRequest page) {
        RowDialect dialect = RowDialect.of(session.product());
        RowQueryStage stage = RowQueryStage.BIND;
        try {
            RowSqlBuilder builder = new RowSqlBuilder(dialect, view);
            RowSqlBuilder.Where where = builder.where(conditions);
            RowSqlBuilder.Query count = builder.count(where);
            RowSqlBuilder.Query rows = builder.page(where, view.orderKeys(page.sortKey()));
            long total;
            try (PreparedStatement statement = session.connection().prepareStatement(count.sql())) {
                statement.setQueryTimeout(session.queryTimeoutSeconds());
                statement.setMaxRows(1);
                bindAll(statement, 1, count.values(), dialect);
                stage = RowQueryStage.EXECUTE;
                try (ResultSet resultSet = statement.executeQuery()) {
                    stage = RowQueryStage.READ;
                    total = resultSet.next() ? resultSet.getLong(1) : 0L;
                }
            }
            if (page.offset() >= total) {
                return new RowSearchResult.Page(total, List.of());
            }
            stage = RowQueryStage.BIND;
            List<RowRecord> records = new ArrayList<>();
            try (PreparedStatement statement = session.connection().prepareStatement(rows.sql())) {
                statement.setQueryTimeout(session.queryTimeoutSeconds());
                statement.setMaxRows(page.size());
                int next = bindAll(statement, 1, rows.values(), dialect);
                statement.setLong(next, page.size());
                statement.setLong(next + 1, page.offset());
                stage = RowQueryStage.EXECUTE;
                try (ResultSet resultSet = statement.executeQuery()) {
                    stage = RowQueryStage.READ;
                    while (resultSet.next()) {
                        RowRead read = readRow(resultSet, rows.selected(), dialect);
                        if (read instanceof RowRead.Unreadable unreadable) {
                            return new RowSearchResult.Failed(unreadable.failure());
                        }
                        records.add(((RowRead.Read) read).row());
                    }
                }
            }
            return new RowSearchResult.Page(total, records);
        } catch (SQLException | RuntimeException e) {
            return new RowSearchResult.Failed(RowSqlFailureClassifier.classify(stage, e));
        }
    }

    /**
     * 詳細を読む（主キーの条件、最大2行。BR6.3）。
     *
     * @param session 貸し出し中の接続
     * @param view 判定の材料（DETAIL が許されるもの）
     * @param key 型として読んだ主キー
     * @return 1行・0行・2行以上・失敗
     */
    public RowDetailResult detail(TargetReadSession session, TableAccessView view, RowKey key) {
        RowDialect dialect = RowDialect.of(session.product());
        RowQueryStage stage = RowQueryStage.BIND;
        try {
            RowSqlBuilder.Query query = new RowSqlBuilder(dialect, view).detail(key);
            try (PreparedStatement statement = session.connection().prepareStatement(query.sql())) {
                statement.setQueryTimeout(session.queryTimeoutSeconds());
                statement.setMaxRows(2);
                bindAll(statement, 1, query.values(), dialect);
                stage = RowQueryStage.EXECUTE;
                try (ResultSet resultSet = statement.executeQuery()) {
                    stage = RowQueryStage.READ;
                    if (!resultSet.next()) {
                        return new RowDetailResult.NotFound();
                    }
                    RowRead read = readRow(resultSet, query.selected(), dialect);
                    if (read instanceof RowRead.Unreadable unreadable) {
                        return new RowDetailResult.Failed(unreadable.failure());
                    }
                    if (resultSet.next()) {
                        return new RowDetailResult.NotUnique();
                    }
                    return new RowDetailResult.Found(((RowRead.Read) read).row());
                }
            }
        } catch (SQLException | RuntimeException e) {
            return new RowDetailResult.Failed(RowSqlFailureClassifier.classify(stage, e));
        }
    }

    /**
     * 送った unique のカラムを、1文で事前に問い合わせる（BR5.1、W1）。更新では自分の行を除く。問い合わせるカラムが無ければ文を流さない。
     *
     * @param session 貸し出し中の書き込みの接続
     * @param view 判定の材料
     * @param values 送った型つきの値
     * @param self 自分の行の主キー（作成では null）
     * @return 重なりが無い・重なった・失敗
     */
    public RowPrecheckResult precheckUnique(
            TargetWriteSession session, TableAccessView view, List<TypedValue> values, RowKey self) {
        RowDialect dialect = RowDialect.of(session.product());
        try {
            Optional<RowWriteSqlBuilder.Statement> probe =
                    new RowWriteSqlBuilder(dialect, view).uniqueProbe(values, self);
            if (probe.isEmpty()) {
                return new RowPrecheckResult.Clear();
            }
            List<String> hits = new ArrayList<>();
            try (PreparedStatement statement =
                    session.connection().prepareStatement(probe.get().sql())) {
                statement.setQueryTimeout(session.queryTimeoutSeconds());
                bindAll(statement, 1, probe.get().values(), dialect);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        for (int i = 0; i < probe.get().probes().size(); i++) {
                            resultSet.getObject(i + 1);
                            if (!resultSet.wasNull()) {
                                hits.add(probe.get().probes().get(i));
                            }
                        }
                    }
                }
            }
            return hits.isEmpty() ? new RowPrecheckResult.Clear() : new RowPrecheckResult.Duplicate(hits);
        } catch (SQLException | RuntimeException e) {
            return new RowPrecheckResult.Failed(
                    RowSqlFailureClassifier.classifyWrite(session.product(), RowWriteStage.PRECHECK, false, e));
        }
    }

    /**
     * 送ったカラムだけで1行を作り、作った行の主キーの値を読む（BR2.2・BR7.1・BR7.2）。文の直前にセーブポイントを置く（違反の後の確かめの
     * ため）。送らなかった主キーは、主キーのカラムの名前を渡して生成された鍵として読む（W8。全部のカラムを受け取らない）。値で送った
     * 主キーで鍵が空なのは失敗に数えない。生成された鍵を文字として読めないときも鍵を返さず、失敗にしない（コードのレビューの R-03）。
     * 結果の読みの例外は想定外の失敗（R-02）。
     *
     * @param session 貸し出し中の書き込みの接続
     * @param view 判定の材料
     * @param values 送った型つきの値
     * @return 作った・失敗
     */
    public RowInsertResult insert(TargetWriteSession session, TableAccessView view, List<TypedValue> values) {
        RowDialect dialect = RowDialect.of(session.product());
        RowWriteStage stage = RowWriteStage.PRECHECK;
        try {
            RowWriteSqlBuilder builder = new RowWriteSqlBuilder(dialect, view);
            RowWriteSqlBuilder.Statement insert = builder.insert(values);
            List<String> missing = builder.missingKeyColumns(values);
            session.markSavepoint();
            stage = RowWriteStage.WRITE;
            boolean readKeys = !missing.isEmpty() && (dialect.readsGeneratedKeysByName() || missing.size() == 1);
            try (PreparedStatement statement = readKeys
                    ? session.connection().prepareStatement(insert.sql(), missing.toArray(String[]::new))
                    : session.connection().prepareStatement(insert.sql())) {
                statement.setQueryTimeout(session.queryTimeoutSeconds());
                bindAll(statement, 1, insert.values(), dialect);
                statement.executeUpdate();
                stage = RowWriteStage.READ_RESULT;
                Map<String, String> key = new LinkedHashMap<>();
                Map<String, String> generated = readKeys ? generatedKeys(statement, view, missing, dialect) : Map.of();
                for (String name : view.primaryKey()) {
                    Optional<TypedValue> sent =
                            values.stream().filter(v -> v.column().equals(name)).findFirst();
                    if (sent.isPresent()) {
                        key.put(name, ColumnValueCodec.format(sent.get().value()));
                    } else if (generated.containsKey(name)) {
                        key.put(name, generated.get(name));
                    } else {
                        // 生成された鍵を読めない（MySQL・MariaDB で送らなかった主キーが2つ以上など）。主キーは返さない。
                        key.clear();
                        break;
                    }
                }
                return new RowInsertResult.Inserted(new RowValues(key, Set.of()));
            }
        } catch (SQLException | RuntimeException e) {
            return new RowInsertResult.Failed(
                    RowSqlFailureClassifier.classifyWrite(session.product(), stage, false, e));
        }
    }

    /**
     * 送ったカラムだけを SET し、主キーで行を指して更新する（BR2.3・BR8.1）。文の直前にセーブポイントを置く。件数は一致した行で数える
     * （BR8.2。書き込みの接続の設定で固定）。
     *
     * @param session 貸し出し中の書き込みの接続
     * @param view 判定の材料
     * @param values 変えた型つきの値（1つ以上）
     * @param key 主キー
     * @return 件数・失敗
     */
    public RowChangeResult update(
            TargetWriteSession session, TableAccessView view, List<TypedValue> values, RowKey key) {
        RowDialect dialect = RowDialect.of(session.product());
        RowWriteStage stage = RowWriteStage.PRECHECK;
        try {
            RowWriteSqlBuilder.Statement update = new RowWriteSqlBuilder(dialect, view).update(values, key);
            session.markSavepoint();
            stage = RowWriteStage.WRITE;
            try (PreparedStatement statement = session.connection().prepareStatement(update.sql())) {
                statement.setQueryTimeout(session.queryTimeoutSeconds());
                bindAll(statement, 1, update.values(), dialect);
                return new RowChangeResult.Changed(statement.executeLargeUpdate());
            }
        } catch (SQLException | RuntimeException e) {
            return new RowChangeResult.Failed(
                    RowSqlFailureClassifier.classifyWrite(session.product(), stage, false, e));
        }
    }

    /**
     * 主キーで行を指して削除する（BR8.1）。外部キーの {@code ON DELETE CASCADE} は DB に任せる。外部キーの違反は保存の拒否（BR6.3）。
     *
     * @param session 貸し出し中の書き込みの接続
     * @param view 判定の材料
     * @param key 主キー
     * @return 件数・失敗
     */
    public RowChangeResult delete(TargetWriteSession session, TableAccessView view, RowKey key) {
        RowDialect dialect = RowDialect.of(session.product());
        try {
            RowWriteSqlBuilder.Statement delete = new RowWriteSqlBuilder(dialect, view).delete(key);
            try (PreparedStatement statement = session.connection().prepareStatement(delete.sql())) {
                statement.setQueryTimeout(session.queryTimeoutSeconds());
                bindAll(statement, 1, delete.values(), dialect);
                return new RowChangeResult.Changed(statement.executeLargeUpdate());
            }
        } catch (SQLException | RuntimeException e) {
            return new RowChangeResult.Failed(
                    RowSqlFailureClassifier.classifyWrite(session.product(), RowWriteStage.WRITE, true, e));
        }
    }

    /**
     * 違反の後に、同じ接続・同じトランザクションの中で、違反のカラムを1文で確かめる（BR6.2。口がセーブポイントへ戻した後に呼ぶ）。候補が
     * ちょうど1つならそのカラム、0 か 2 以上なら空。失敗は段 {@link RowWriteStage#INSPECT} の失敗（BR6.4）。
     *
     * @param session 貸し出し中の書き込みの接続
     * @param view 判定の材料
     * @param reference 外部キーの違反なら true、一意の違反なら false
     * @param values 送った型つきの値
     * @param self 自分の行の主キー（作成では null。一意の確かめで自分の行を除く）
     * @return 確かめた・失敗
     */
    public RowInspectResult inspectViolation(
            TargetWriteSession session, TableAccessView view, boolean reference, List<TypedValue> values, RowKey self) {
        RowDialect dialect = RowDialect.of(session.product());
        try {
            RowWriteSqlBuilder builder = new RowWriteSqlBuilder(dialect, view);
            Optional<RowWriteSqlBuilder.Statement> probe =
                    reference ? builder.referenceProbe(values) : builder.uniqueProbe(values, self);
            if (probe.isEmpty()) {
                return new RowInspectResult.Inspected(Optional.empty());
            }
            List<String> candidates = new ArrayList<>();
            try (PreparedStatement statement =
                    session.connection().prepareStatement(probe.get().sql())) {
                statement.setQueryTimeout(session.queryTimeoutSeconds());
                bindAll(statement, 1, probe.get().values(), dialect);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        for (int i = 0; i < probe.get().probes().size(); i++) {
                            resultSet.getObject(i + 1);
                            // 一意は「ほかの行がある」、外部キーは「参照先の行が無い」が候補。
                            boolean found = !resultSet.wasNull();
                            if (found != reference) {
                                candidates.add(probe.get().probes().get(i));
                            }
                        }
                    }
                }
            }
            return new RowInspectResult.Inspected(
                    candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty());
        } catch (SQLException | RuntimeException e) {
            return new RowInspectResult.Failed(
                    RowSqlFailureClassifier.classifyWrite(session.product(), RowWriteStage.INSPECT, false, e));
        }
    }

    /** 生成された鍵を、主キーの名前ごとの文字にして読む（文は作らない）。 */
    private Map<String, String> generatedKeys(
            PreparedStatement statement, TableAccessView view, List<String> missing, RowDialect dialect)
            throws SQLException {
        Map<String, String> keys = new LinkedHashMap<>();
        try (ResultSet resultSet = statement.getGeneratedKeys()) {
            if (!resultSet.next()) {
                return keys;
            }
            for (String name : missing) {
                int index = dialect.readsGeneratedKeysByName() ? resultSet.findColumn(name) : 1;
                ValueType type =
                        view.writeRules(name).map(ColumnWriteRules::valueType).orElse(ValueType.STRING);
                CellValue cell = codec.readCell(resultSet, index, type, Integer.MAX_VALUE, dialect);
                if (cell instanceof CellValue.Text text && text.text() != null) {
                    keys.put(name, text.text());
                } else {
                    // 文字として読めない鍵（NULL・読めない型）は、作成を巻き戻して 500 にせず、MySQL 系で鍵を読めないときと同じく鍵を
                    // 返さない（行は作られている。コードのレビューの R-03）。
                    return Map.of();
                }
            }
        }
        return keys;
    }

    /** 値を順にバインドし、次の位置を返す。 */
    private int bindAll(PreparedStatement statement, int start, List<BoundValue> values, RowDialect dialect)
            throws SQLException {
        int index = start;
        for (BoundValue value : values) {
            codec.bind(statement, index++, value, dialect);
        }
        return index;
    }

    /** 1行を読む（主キーは切らずに key へ、ほかは上限で切って values へ）。 */
    private RowRead readRow(ResultSet resultSet, List<RowSqlBuilder.Selected> selected, RowDialect dialect)
            throws SQLException {
        Map<String, String> key = new LinkedHashMap<>();
        Map<String, String> values = new LinkedHashMap<>();
        Set<String> truncated = new LinkedHashSet<>();
        boolean hasKey = false;
        for (int i = 0; i < selected.size(); i++) {
            RowSqlBuilder.Selected column = selected.get(i);
            if (column.column() == null) {
                continue;
            }
            CellValue cell = codec.readCell(resultSet, i + 1, column.column().valueType(), column.limit(), dialect);
            if (cell instanceof CellValue.Unreadable unreadable) {
                return new RowRead.Unreadable(new RowQueryFailure(
                        RowQueryFailureReason.READ_CONVERSION,
                        RowQueryStage.READ,
                        null,
                        null,
                        unreadable.exceptionClass()));
            }
            CellValue.Text text = (CellValue.Text) cell;
            if (column.key()) {
                hasKey = true;
                key.put(column.column().column(), text.text());
            } else {
                values.put(column.column().column(), text.text());
                if (text.truncated()) {
                    truncated.add(column.column().column());
                }
            }
        }
        return new RowRead.Read(
                new RowRecord(hasKey ? new RowValues(key, Set.of()) : null, new RowValues(values, truncated)));
    }

    /** 1行の読み取りの結果。 */
    private sealed interface RowRead {

        /** 読めた。 */
        record Read(RowRecord row) implements RowRead {}

        /** DSL の型で読めない値があった。 */
        record Unreadable(RowQueryFailure failure) implements RowRead {}
    }
}
