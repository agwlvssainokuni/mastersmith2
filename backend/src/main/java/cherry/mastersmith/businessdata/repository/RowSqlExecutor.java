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
import cherry.mastersmith.businessdata.domain.PageRequest;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowQueryFailureReason;
import cherry.mastersmith.businessdata.domain.RowQueryStage;
import cherry.mastersmith.businessdata.domain.RowRecord;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.SearchCondition;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <p>文（{@code prepareStatement}・{@code createStatement}）を作るのは {@link #search} と {@link #detail} の本体だけで、ラムダや
 * ほかのメソッドの中では作らない（SpotBugs の除外をメソッドの名前で絞るため。計画の A2）。1回の貸し出しで、どちらかを1回だけ呼ぶ
 * （貸し出しの始めの文を1回だけ流すため。R-03）。
 */
@Component
public class RowSqlExecutor {

    /** 貸し出しの始めに MySQL・MariaDB で流す、セッションのタイムゾーンを UTC にする文（定数。利用者の入力を含まない。T1）。 */
    public static final String SET_TIME_ZONE_UTC = "SET time_zone = '+00:00'";

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
            if (dialect.needsUtcSession()) {
                stage = RowQueryStage.SESSION_INIT;
                try (Statement statement = session.connection().createStatement()) {
                    statement.setQueryTimeout(session.queryTimeoutSeconds());
                    statement.execute(SET_TIME_ZONE_UTC);
                }
            }
            stage = RowQueryStage.BIND;
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
            if (dialect.needsUtcSession()) {
                stage = RowQueryStage.SESSION_INIT;
                try (Statement statement = session.connection().createStatement()) {
                    statement.setQueryTimeout(session.queryTimeoutSeconds());
                    statement.execute(SET_TIME_ZONE_UTC);
                }
            }
            stage = RowQueryStage.BIND;
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
