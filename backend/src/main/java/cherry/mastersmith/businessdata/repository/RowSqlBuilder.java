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
import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.ContainsPattern;
import cherry.mastersmith.businessdata.domain.KnownIdentifiers;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.SearchCondition;
import cherry.mastersmith.businessdata.domain.SortKey;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.targetdb.domain.SqlIdentifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 行の SQL の文字を組み立てる（{@link RowSqlExecutor} の中だけで使う。BR3.2〜BR3.5・BR5.3〜BR5.8・BR6.3、performance-design.md の
 * 1節・2節）。
 *
 * <p>識別子は、判定の材料の種類ごとの名前の一覧（{@link KnownIdentifiers}）を渡して {@link SqlIdentifiers#quoteKnown} で引用した
 * ものだけを使い、テーブルはスキーマ名で修飾する。一覧に無い名前は {@link IllegalArgumentException}（問い合わせをせずに想定外の
 * 失敗）。値は文字に組み込まず、バインドする値の一覧で返す。ほかの部分は定数の形の文字だけ。
 */
final class RowSqlBuilder {

    /** 主キーを先に読む形の外側のテーブルの別名。 */
    private static final String OUTER = "t";

    /** 主キーを先に読む形の内側の別名。 */
    private static final String INNER = "p";

    private final RowDialect dialect;

    private final TableAccessView view;

    private final KnownIdentifiers names;

    private final String table;

    /**
     * 作る。
     *
     * @param dialect 方言
     * @param view 判定の材料
     */
    RowSqlBuilder(RowDialect dialect, TableAccessView view) {
        this.dialect = dialect;
        this.view = view;
        this.names = view.knownIdentifiers();
        this.table = SqlIdentifiers.quoteKnown(dialect.product(), view.schema(), names.schemas()) + "."
                + SqlIdentifiers.quoteKnown(dialect.product(), view.table(), names.tables());
    }

    /**
     * 選ぶ列1つ。
     *
     * @param column カラム
     * @param expression 選ぶ式（{@link RowDialect} が組み立てたもの。別名は付けない）
     * @param key 主キーの値として返すなら true
     * @param limit 文字数の上限
     */
    record Selected(ColumnAccess column, String expression, boolean key, int limit) {}

    /**
     * WHERE の句とバインドする値。
     *
     * @param sql 句（条件が無ければ空の文字）
     * @param values バインドする値（句の ? の順）
     */
    record Where(String sql, List<BoundValue> values) {}

    /**
     * 組み立てた文と、選んだ列とバインドする値。
     *
     * @param sql 文
     * @param selected 選んだ列（結果の位置の順）
     * @param values バインドする値（文の ? の順。ページの上限と読み飛ばす数は含めない）
     */
    record Query(String sql, List<Selected> selected, List<BoundValue> values) {}

    String column(String name) {
        return SqlIdentifiers.quoteKnown(dialect.product(), name, names.columns());
    }

    /** 検索の条件の WHERE の句（すべて AND。BR4.1）。 */
    Where where(List<SearchCondition> conditions) {
        List<String> terms = new ArrayList<>();
        List<BoundValue> values = new ArrayList<>();
        for (SearchCondition condition : conditions) {
            String target = dialect.comparisonTarget(column(condition.column()), condition.valueType());
            switch (condition.mode()) {
                case EQUALS -> {
                    terms.add(target + " = ?");
                    values.add(condition.value());
                }
                case CONTAINS -> {
                    terms.add(target + " LIKE ? ESCAPE '" + ContainsPattern.ESCAPE + "'");
                    values.add(new BoundValue(ValueType.STRING, ContainsPattern.of((String)
                            condition.value().value())));
                }
                case RANGE -> {
                    if (condition.from() != null) {
                        terms.add(target + " >= ?");
                        values.add(condition.from());
                    }
                    if (condition.to() != null) {
                        terms.add(target + " <= ?");
                        values.add(condition.to());
                    }
                }
                case CHOICE -> {
                    StringJoiner placeholders = new StringJoiner(", ", target + " IN (", ")");
                    for (BoundValue value : condition.values()) {
                        placeholders.add("?");
                        values.add(value);
                    }
                    terms.add(placeholders.toString());
                }
            }
        }
        return new Where(terms.isEmpty() ? "" : " WHERE " + String.join(" AND ", terms), values);
    }

    /** 件数の文（BR5.6）。 */
    Query count(Where where) {
        return new Query("SELECT COUNT(*) FROM " + table + where.sql(), List.of(), where.values());
    }

    /**
     * ページの文（BR5.3〜BR5.5・BR5.8、performance-design.md の 2節）。主キーのあるテーブルは、主キーだけを先に並べて読み、その主キー
     * で行をつなぐ。主キーの無いテーブルはそのまま読む。末尾に {@code LIMIT ? OFFSET ?} を置く。
     */
    Query page(Where where, List<SortKey> keys) {
        List<String> primaryKey = view.primaryKey();
        boolean joined = !primaryKey.isEmpty();
        String prefix = joined ? OUTER + "." : "";
        List<Selected> selected = listSelection(prefix);
        String select = "SELECT "
                + String.join(", ", selected.stream().map(Selected::expression).toList());
        if (!joined) {
            return new Query(
                    select + " FROM " + table + where.sql() + orderBy(keys, "") + " LIMIT ? OFFSET ?",
                    selected,
                    where.values());
        }
        String innerColumns =
                String.join(", ", primaryKey.stream().map(this::column).toList());
        StringJoiner on = new StringJoiner(" AND ");
        for (String key : primaryKey) {
            on.add(OUTER + "." + column(key) + " = " + INNER + "." + column(key));
        }
        String sql = select + " FROM " + table + " " + OUTER + " JOIN (SELECT " + innerColumns + " FROM " + table
                + where.sql() + orderBy(keys, "") + " LIMIT ? OFFSET ?) " + INNER + " ON " + on
                + orderBy(keys, prefix);
        return new Query(sql, selected, where.values());
    }

    /** 詳細の文（主キーの条件、最大2行。BR6.3）。 */
    Query detail(RowKey key) {
        List<Selected> selected = new ArrayList<>();
        for (ColumnAccess column : view.keyColumns()) {
            selected.add(new Selected(
                    column, dialect.keyExpression(column(column.column()), column), true, Integer.MAX_VALUE));
        }
        for (ColumnAccess column : view.detailColumns()) {
            if (column.readsValue()) {
                selected.add(new Selected(
                        column,
                        dialect.selectExpression(column(column.column()), column, ColumnValueCodec.DETAIL_TEXT_LIMIT),
                        false,
                        ColumnValueCodec.DETAIL_TEXT_LIMIT));
            }
        }
        List<String> terms = new ArrayList<>();
        List<BoundValue> values = new ArrayList<>();
        for (Map.Entry<String, BoundValue> entry : key.values().entrySet()) {
            terms.add(dialect.comparisonTarget(
                            column(entry.getKey()), entry.getValue().valueType()) + " = ?");
            values.add(entry.getValue());
        }
        String sql = "SELECT "
                + String.join(", ", selected.stream().map(Selected::expression).toList()) + " FROM " + table + " WHERE "
                + String.join(" AND ", terms) + " LIMIT 2";
        return new Query(sql, selected, values);
    }

    /** 一覧で選ぶ列（DETAIL が許されるときの主キーと、一覧の列のうち値を読むもの。BR2.2・BR7.4・BR7.5）。 */
    private List<Selected> listSelection(String prefix) {
        List<Selected> selected = new ArrayList<>();
        for (ColumnAccess column : view.keyColumns()) {
            selected.add(new Selected(
                    column, dialect.keyExpression(prefix + column(column.column()), column), true, Integer.MAX_VALUE));
        }
        for (ColumnAccess column : view.listColumns()) {
            if (column.readsValue()) {
                selected.add(new Selected(
                        column,
                        dialect.selectExpression(
                                prefix + column(column.column()), column, ColumnValueCodec.LIST_TEXT_LIMIT),
                        false,
                        ColumnValueCodec.LIST_TEXT_LIMIT));
            }
        }
        if (selected.isEmpty()) {
            // 値を読む列が無いときも行の数を数えられるよう、定数を1つ選ぶ（応答の値には使わない）。
            selected.add(new Selected(null, "1", false, 0));
        }
        return selected;
    }

    private String orderBy(List<SortKey> keys, String prefix) {
        if (keys.isEmpty()) {
            return "";
        }
        return " ORDER BY "
                + String.join(
                        ", ",
                        keys.stream()
                                .map(k -> dialect.orderTerm(prefix + column(k.column()), k.direction()))
                                .toList());
    }
}
