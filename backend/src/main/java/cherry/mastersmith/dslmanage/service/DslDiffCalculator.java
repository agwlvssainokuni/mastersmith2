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

import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dslmanage.domain.PreviewView.ColumnDiff;
import cherry.mastersmith.dslmanage.domain.PreviewView.Diff;
import cherry.mastersmith.dslmanage.domain.PreviewView.DiffChange;
import cherry.mastersmith.dslmanage.domain.PreviewView.TableDiff;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * プレビューと適用中の DSL の違いを求める（BR2.2）。DB にも時計にも触れない純粋な関数。
 *
 * <p>テーブルは表示名・ビューかどうか・主キー・外部キーと、カラムの違いの有無で比べ、変わらないテーブルも含めてすべて並べる（プレビューの
 * DSL の順、続けて減ったテーブルを適用中の順）。カラムは表示名・DB 上の型・フォーム部品・検索・一覧・詳細・バリデーション・選択肢を比べ、
 * 増えた・減った・変わったものだけを並べる。変わった項目の名前は DSL のキーの形（例 {@code label.ja}・{@code list.order}）。
 * バリデーションと選択肢は並びの全体を1つの項目（{@code validations}・{@code options}）として比べる。
 */
public final class DslDiffCalculator {

    /** カラムで比べる項目の名前と値の取り出し（名前の順に並べる）。 */
    private static final List<Item> COLUMN_ITEMS = List.of(
            new Item("label.ja", column -> column.label().ja()),
            new Item("label.en", column -> column.label().en()),
            new Item("dbType.name", column -> column.dbType().name()),
            new Item("dbType.length", column -> column.dbType().length()),
            new Item("dbType.precision", column -> column.dbType().precision()),
            new Item("dbType.scale", column -> column.dbType().scale()),
            new Item("dbType.nullable", column -> column.dbType().nullable()),
            new Item("formPart", DslColumn::formPart),
            new Item("search.enabled", column -> column.search().enabled()),
            new Item("search.operator", column -> column.search().operator()),
            new Item("search.default", column -> column.search().defaultValue()),
            new Item("search.collapsed", column -> column.search().collapsed()),
            new Item("list.visible", column -> column.list().visible()),
            new Item("list.order", column -> column.list().order()),
            new Item("list.width", column -> column.list().width()),
            new Item("list.sortable", column -> column.list().sortable()),
            new Item("list.defaultSort", column -> column.list().defaultSort()),
            new Item("list.format", column -> column.list().format()),
            new Item("detail.visible", column -> column.detail().visible()),
            new Item("validations", DslColumn::validations),
            new Item("options", DslColumn::options));

    private DslDiffCalculator() {}

    /**
     * 違いを求める。
     *
     * @param preview プレビューのモデル
     * @param applied 適用中のモデル（無ければ null。すべて増えたにする）
     * @return 違い
     */
    public static Diff diff(DslModel preview, DslModel applied) {
        Map<String, DslTable> appliedTables = applied == null ? Map.of() : applied.tables();
        List<TableDiff> tables = new ArrayList<>();
        for (DslTable table : preview.tables().values()) {
            DslTable before = appliedTables.get(table.name());
            tables.add(before == null ? whole(table, DiffChange.ADDED) : compare(before, table));
        }
        for (DslTable table : appliedTables.values()) {
            if (!preview.tables().containsKey(table.name())) {
                tables.add(whole(table, DiffChange.REMOVED));
            }
        }
        return new Diff(applied != null, tables);
    }

    private static TableDiff whole(DslTable table, DiffChange change) {
        List<ColumnDiff> columns = table.columns().keySet().stream()
                .map(name -> new ColumnDiff(name, change, List.of()))
                .toList();
        return new TableDiff(table.name(), change, columns);
    }

    private static TableDiff compare(DslTable before, DslTable after) {
        List<ColumnDiff> columns = new ArrayList<>();
        for (DslColumn column : after.columns().values()) {
            DslColumn old = before.columns().get(column.name());
            if (old == null) {
                columns.add(new ColumnDiff(column.name(), DiffChange.ADDED, List.of()));
            } else {
                List<String> changed = changedItems(old, column);
                if (!changed.isEmpty()) {
                    columns.add(new ColumnDiff(column.name(), DiffChange.CHANGED, changed));
                }
            }
        }
        for (String name : before.columns().keySet()) {
            if (!after.columns().containsKey(name)) {
                columns.add(new ColumnDiff(name, DiffChange.REMOVED, List.of()));
            }
        }
        boolean tableChanged = !columns.isEmpty()
                || !before.label().equals(after.label())
                || before.view() != after.view()
                || !before.primaryKey().equals(after.primaryKey())
                || !before.foreignKeys().equals(after.foreignKeys());
        return new TableDiff(after.name(), tableChanged ? DiffChange.CHANGED : DiffChange.UNCHANGED, columns);
    }

    /**
     * カラムの変わった項目の名前を求める。
     *
     * @param before 適用中のカラム
     * @param after プレビューのカラム
     * @return 変わった項目の名前（比べる項目の順）
     */
    static List<String> changedItems(DslColumn before, DslColumn after) {
        List<String> changed = new ArrayList<>();
        for (Item item : COLUMN_ITEMS) {
            if (!Objects.equals(item.value().apply(before), item.value().apply(after))) {
                changed.add(item.name());
            }
        }
        return changed;
    }

    /** 比べる項目の名前と値の取り出し。 */
    private record Item(String name, Function<DslColumn, Object> value) {}
}
