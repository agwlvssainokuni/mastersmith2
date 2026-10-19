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
import cherry.mastersmith.businessdata.domain.ForeignKeyRef;
import cherry.mastersmith.businessdata.domain.KnownIdentifiers;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TypedValue;
import cherry.mastersmith.targetdb.domain.SqlIdentifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * 書き込みの SQL の文字を組み立てる（{@link RowSqlExecutor} の書き込みのメソッドの中だけで使う。Intent 261016-business-data-views の
 * U2、BR5.1・BR6.2・BR7.1、performance-design.md の 1節）。文は作らない。
 *
 * <p>識別子は、判定の材料の種類ごとの名前の一覧（スキーマ・テーブル・カラム、外部キーの参照先はその表の DSL のカラムの一覧）を渡して
 * {@link SqlIdentifiers#quoteKnown} で引用したものだけを使い、テーブルはスキーマ名で修飾する。一覧に無い名前は
 * {@link IllegalArgumentException}（文を作らずに想定外の失敗）。値は文字に組み込まず、バインドする値の一覧で返す。ほかの部分は定数の形の
 * 文字だけ。
 */
final class RowWriteSqlBuilder {

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
    RowWriteSqlBuilder(RowDialect dialect, TableAccessView view) {
        this.dialect = dialect;
        this.view = view;
        this.names = view.knownIdentifiers();
        this.table = qualified(view.table());
    }

    /**
     * 組み立てた文とバインドする値。
     *
     * @param sql 文
     * @param values バインドする値（文の ? の順）
     * @param probes 問い合わせの結果の位置ごとの、確かめたカラム（事前の問い合わせ・確かめのとき。ほかは空）
     */
    record Statement(String sql, List<BoundValue> values, List<String> probes) {}

    private String qualified(String tableName) {
        return SqlIdentifiers.quoteKnown(dialect.product(), view.schema(), names.schemas()) + "."
                + SqlIdentifiers.quoteKnown(dialect.product(), tableName, names.tables());
    }

    private String column(String name) {
        return SqlIdentifiers.quoteKnown(dialect.product(), name, names.columns());
    }

    /**
     * 送った unique のカラムのうち値が NULL でないものを、1文でまとめて問い合わせる文を作る（BR5.1、W1）。更新では自分の行を除く。
     *
     * @param values 送った値
     * @param self 自分の行の主キー（作成では null）
     * @return 文（問い合わせるカラムが無ければ空）
     */
    Optional<Statement> uniqueProbe(List<TypedValue> values, RowKey self) {
        List<String> terms = new ArrayList<>();
        List<BoundValue> bound = new ArrayList<>();
        List<String> probes = new ArrayList<>();
        for (TypedValue value : values) {
            Optional<ColumnWriteRules> rules = view.writeRules(value.column());
            if (rules.isEmpty() || !rules.get().unique() || value.value().value() == null) {
                continue;
            }
            StringBuilder term = new StringBuilder("(SELECT 1 FROM ")
                    .append(table)
                    .append(" WHERE ")
                    .append(dialect.comparisonTarget(
                            column(value.column()), value.value().valueType()))
                    .append(" = ?");
            bound.add(value.value());
            if (self != null) {
                term.append(" AND NOT (").append(keyCondition(self, bound)).append(')');
            }
            term.append(" LIMIT 1) AS c").append(probes.size());
            terms.add(term.toString());
            probes.add(value.column());
        }
        if (terms.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Statement("SELECT " + String.join(", ", terms), bound, probes));
    }

    /**
     * 送ったカラムを含む外部キーごとに、参照先の行の有無を1文でまとめて問い合わせる文を作る（BR6.2）。外部キーのカラムをすべて送り、
     * どれも NULL でない外部キーだけを確かめる（ほかの外部キーは候補にしない）。
     *
     * @param values 送った値
     * @return 文（確かめる外部キーが無ければ空）
     */
    Optional<Statement> referenceProbe(List<TypedValue> values) {
        List<String> terms = new ArrayList<>();
        List<BoundValue> bound = new ArrayList<>();
        List<String> probes = new ArrayList<>();
        for (ForeignKeyRef key : view.foreignKeys()) {
            List<BoundValue> keyValues = new ArrayList<>();
            for (String name : key.columns()) {
                values.stream()
                        .filter(v -> v.column().equals(name) && v.value().value() != null)
                        .findFirst()
                        .ifPresent(v -> keyValues.add(v.value()));
            }
            if (keyValues.size() != key.columns().size()) {
                continue;
            }
            StringJoiner where = new StringJoiner(" AND ");
            for (int i = 0; i < key.referencedColumns().size(); i++) {
                where.add(dialect.comparisonTarget(
                                SqlIdentifiers.quoteKnown(
                                        dialect.product(),
                                        key.referencedColumns().get(i),
                                        key.referencedKnownColumns()),
                                keyValues.get(i).valueType())
                        + " = ?");
            }
            bound.addAll(keyValues);
            terms.add("(SELECT 1 FROM " + qualified(key.referencedTable()) + " WHERE " + where + " LIMIT 1) AS c"
                    + probes.size());
            probes.add(key.columns().getFirst());
        }
        if (terms.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Statement("SELECT " + String.join(", ", terms), bound, probes));
    }

    /**
     * 送ったカラムだけの作成の文を作る（BR2.2）。値が無ければ、すべてを DB の既定値に任せる形にする。
     *
     * @param values 送った値
     * @return 文
     */
    Statement insert(List<TypedValue> values) {
        if (values.isEmpty()) {
            return new Statement(dialect.insertWithoutValues(table), List.of(), List.of());
        }
        StringJoiner columns = new StringJoiner(", ", " (", ")");
        StringJoiner placeholders = new StringJoiner(", ", " VALUES (", ")");
        List<BoundValue> bound = new ArrayList<>();
        for (TypedValue value : values) {
            columns.add(column(value.column()));
            placeholders.add("?");
            bound.add(value.value());
        }
        return new Statement("INSERT INTO " + table + columns + placeholders, bound, List.of());
    }

    /**
     * 送ったカラムだけを SET し、主キーで行を指す更新の文を作る（BR2.3）。
     *
     * @param values 変えたカラム（1つ以上）
     * @param key 主キー
     * @return 文
     */
    Statement update(List<TypedValue> values, RowKey key) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("変えるカラムが無い更新の文は作りません");
        }
        StringJoiner set = new StringJoiner(", ");
        List<BoundValue> bound = new ArrayList<>();
        for (TypedValue value : values) {
            set.add(column(value.column()) + " = ?");
            bound.add(value.value());
        }
        String where = keyCondition(key, bound);
        return new Statement("UPDATE " + table + " SET " + set + " WHERE " + where, bound, List.of());
    }

    /**
     * 主キーで行を指す削除の文を作る。
     *
     * @param key 主キー
     * @return 文
     */
    Statement delete(RowKey key) {
        List<BoundValue> bound = new ArrayList<>();
        String where = keyCondition(key, bound);
        return new Statement("DELETE FROM " + table + " WHERE " + where, bound, List.of());
    }

    /**
     * 作成で送らなかった主キーのカラムの名前を返す（生成された鍵として読むもの）。
     *
     * @param values 送った値
     * @return 送らなかった主キーのカラム（主キーの順）
     */
    List<String> missingKeyColumns(List<TypedValue> values) {
        return view.primaryKey().stream()
                .filter(name -> values.stream().noneMatch(v -> v.column().equals(name)))
                .toList();
    }

    /** 主キーの条件を組み立て、値を足す（主キーの順）。 */
    private String keyCondition(RowKey key, List<BoundValue> bound) {
        if (key.values().isEmpty()) {
            throw new IllegalArgumentException("主キーの無い条件は作りません");
        }
        StringJoiner terms = new StringJoiner(" AND ");
        for (Map.Entry<String, BoundValue> entry : key.values().entrySet()) {
            terms.add(dialect.comparisonTarget(
                            column(entry.getKey()), entry.getValue().valueType()) + " = ?");
            bound.add(entry.getValue());
        }
        return terms.toString();
    }
}
