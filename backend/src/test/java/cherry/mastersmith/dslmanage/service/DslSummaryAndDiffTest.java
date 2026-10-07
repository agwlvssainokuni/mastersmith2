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

import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.TableRef;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.testsupport.DslSamples;
import cherry.mastersmith.dsl.validate.PatternChecker;
import cherry.mastersmith.dslmanage.domain.PreviewView.ColumnDiff;
import cherry.mastersmith.dslmanage.domain.PreviewView.Diff;
import cherry.mastersmith.dslmanage.domain.PreviewView.DiffChange;
import cherry.mastersmith.dslmanage.domain.PreviewView.MissingDisplayName;
import cherry.mastersmith.dslmanage.domain.PreviewView.SchemaDiff;
import cherry.mastersmith.dslmanage.domain.PreviewView.Summary;
import cherry.mastersmith.dslmanage.domain.PreviewView.TableDiff;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 要約（BR2.3）と違い（BR2.2）の単体テスト（書式の版 2 は U2 dsl-v2 の BR5.2・BR5.3）。モデルは U2 の本物の読み込みで作る。 */
class DslSummaryAndDiffTest {

    private static final PatternChecker PATTERNS = new PatternChecker();

    private static final DslReader READER = DslYaml.newReader(PATTERNS);

    @AfterAll
    static void close() {
        PATTERNS.close();
    }

    private static DslModel model(DslYaml dsl) {
        return DslYaml.model(READER, dsl.bytes());
    }

    /** ただ1つのスキーマのテーブルの違い。 */
    private static List<TableDiff> tables(Diff diff) {
        assertThat(diff.schemas()).hasSize(1);
        return diff.schemas().getFirst().tables();
    }

    @Test
    @DisplayName("the summary counts tables, views and columns and keeps the menu tree in DSL order")
    void summaryOfTheSample() {
        DslModel sample = DslYaml.model(READER, DslSamples.validYaml().getBytes(StandardCharsets.UTF_8));

        Summary summary = DslSummaryCalculator.summarize(sample);

        assertThat(summary.schemaCount()).isEqualTo(1);
        assertThat(summary.tableCount()).isEqualTo(2);
        assertThat(summary.viewCount()).isEqualTo(1);
        assertThat(summary.columnCount()).isEqualTo(3 + 4 + 1);
        assertThat(summary.menuTree()).hasSize(2);
        assertThat(summary.menuTree().getFirst().children().getFirst().children())
                .extracting(node -> node.table())
                .containsExactly(new TableRef("sales", "dept_mst"), new TableRef("sales", "emp_mst"));
        assertThat(summary.menuTree().get(1).table()).isEqualTo(new TableRef("sales", "emp_view"));
        assertThat(summary.missingDisplayNames())
                .containsExactly(new MissingDisplayName("schemas.sales.tables.emp_mst.columns.joined_on.label", "en"));
        assertThat(summary.missingDisplayNameTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("missing display names are cut to the first 100 while the total counts all of them")
    void missingDisplayNamesAreCutAt100() {
        DslYaml dsl = DslYaml.dsl();
        DslYaml.Column[] columns = new DslYaml.Column[101];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = column("c" + i).label("カラム" + i, "");
        }
        dsl.table("t", "テーブル", "", false, columns).menu(" ", "Menu", "t");

        Summary summary = DslSummaryCalculator.summarize(model(dsl));

        assertThat(summary.missingDisplayNameTotal()).isEqualTo(1 + 1 + 101);
        assertThat(summary.missingDisplayNames()).hasSize(100);
        assertThat(summary.missingDisplayNames().getFirst()).isEqualTo(new MissingDisplayName("menus.0.label", "ja"));
        assertThat(summary.missingDisplayNames().get(1))
                .isEqualTo(new MissingDisplayName("schemas.public.tables.t.label", "en"));
        assertThat(summary.missingDisplayNames().get(2))
                .isEqualTo(new MissingDisplayName("schemas.public.tables.t.columns.c0.label", "en"));
    }

    @Test
    @DisplayName("without an applied DSL every table and column is ADDED and appliedExists is false")
    void everythingAddedWithoutApplied() {
        DslModel preview =
                model(DslYaml.dsl().table("a", column("x"), column("y")).table("b", column("z")));

        Diff diff = DslDiffCalculator.diff(preview, null);

        assertThat(diff.appliedExists()).isFalse();
        assertThat(tables(diff))
                .extracting(TableDiff::name, TableDiff::change)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a", DiffChange.ADDED),
                        org.assertj.core.groups.Tuple.tuple("b", DiffChange.ADDED));
        assertThat(tables(diff).getFirst().columns())
                .containsExactly(
                        new ColumnDiff("x", DiffChange.ADDED, List.of()),
                        new ColumnDiff("y", DiffChange.ADDED, List.of()));
    }

    @Test
    @DisplayName("an added table, a removed table and an unchanged table are all listed")
    void tableKinds() {
        DslModel applied = model(DslYaml.dsl().table("keep", column("k")).table("gone", column("g")));
        DslModel preview = model(DslYaml.dsl().table("keep", column("k")).table("new", column("n")));

        Diff diff = DslDiffCalculator.diff(preview, applied);

        assertThat(diff.appliedExists()).isTrue();
        assertThat(tables(diff))
                .extracting(TableDiff::name, TableDiff::change)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("keep", DiffChange.UNCHANGED),
                        org.assertj.core.groups.Tuple.tuple("new", DiffChange.ADDED),
                        org.assertj.core.groups.Tuple.tuple("gone", DiffChange.REMOVED));
        assertThat(tables(diff).getFirst().columns()).as("変わらないカラムは並べない").isEmpty();
        assertThat(tables(diff).get(2).columns()).containsExactly(new ColumnDiff("g", DiffChange.REMOVED, List.of()));
    }

    @Test
    @DisplayName(
            "inside a changed table, the added, removed and changed columns are listed with the changed item names")
    void columnKinds() {
        DslModel applied = model(DslYaml.dsl()
                .table(
                        "t",
                        column("same"),
                        column("gone"),
                        column("renamed").label("名前", "name").listed(1)));
        DslModel preview = model(DslYaml.dsl()
                .table(
                        "t",
                        column("same"),
                        column("renamed").label("氏名", "name").listed(2),
                        column("added")));

        TableDiff table = tables(DslDiffCalculator.diff(preview, applied)).getFirst();

        assertThat(table.change()).isEqualTo(DiffChange.CHANGED);
        assertThat(table.columns())
                .containsExactly(
                        new ColumnDiff("renamed", DiffChange.CHANGED, List.of("label.ja", "list.order")),
                        new ColumnDiff("added", DiffChange.ADDED, List.of()),
                        new ColumnDiff("gone", DiffChange.REMOVED, List.of()));
    }

    @Test
    @DisplayName("a changed table label or view flag alone marks the table CHANGED without column entries")
    void tableItemsOnly() {
        DslModel applied = model(DslYaml.dsl().table("t", "表", "t", false, column("c")));
        DslModel preview = model(DslYaml.dsl().table("t", "テーブル", "t", true, column("c")));

        TableDiff table = tables(DslDiffCalculator.diff(preview, applied)).getFirst();

        assertThat(table.change()).isEqualTo(DiffChange.CHANGED);
        assertThat(table.columns()).isEmpty();
    }

    @Test
    @DisplayName("the type items are named after the DSL keys")
    void typeItemNames() {
        DslModel applied = model(DslYaml.dsl().table("t", column("c")));
        DslModel preview = model(DslYaml.dsl().table("t", column("c").type("DECIMAL", null, 10, 2)));

        assertThat(tables(DslDiffCalculator.diff(preview, applied))
                        .getFirst()
                        .columns()
                        .getFirst()
                        .changedItems())
                .containsExactly("dbType.name", "dbType.length", "dbType.precision", "dbType.scale");
    }

    @Test
    @DisplayName("a changed primary key or foreign key alone marks the table CHANGED")
    void keysOnly() {
        String base = DslYaml.dsl().table("t", column("a"), column("b")).yaml();
        DslModel applied = DslYaml.model(READER, base.getBytes(StandardCharsets.UTF_8));
        DslModel withKey = DslYaml.model(
                READER, base.replace("primaryKey: []", "primaryKey: [a]").getBytes(StandardCharsets.UTF_8));
        DslModel withForeignKey = DslYaml.model(
                READER,
                base.replace(
                                "foreignKeys: []",
                                "foreignKeys:\n          - { columns: [b], referencedTable: t, referencedColumns: [a] }")
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(tables(DslDiffCalculator.diff(withKey, applied)).getFirst().change())
                .isEqualTo(DiffChange.CHANGED);
        assertThat(tables(DslDiffCalculator.diff(withForeignKey, applied))
                        .getFirst()
                        .change())
                .isEqualTo(DiffChange.CHANGED);
    }

    @Test
    @DisplayName("the preview cache returns the model only for the same previewId")
    void cacheMatchesPreviewId() {
        DslPreviewCache cache = new DslPreviewCache();
        java.util.UUID id = java.util.UUID.randomUUID();
        DslModel model = model(DslYaml.dsl().table("t", column("c")));

        assertThat(cache.get(id)).isEmpty();
        cache.put(id, model);
        assertThat(cache.get(id)).contains(model);
        assertThat(cache.get(java.util.UUID.randomUUID())).isEmpty();
        cache.clear();
        assertThat(cache.get(id)).isEmpty();
    }

    @Test
    @DisplayName("without an applied DSL the schema is ADDED with its label and all its tables")
    void schemaAddedWithoutApplied() {
        Diff diff = DslDiffCalculator.diff(
                model(DslYaml.dsl().schema("sales", "販売", "Sales").table("a", column("x"))), null);

        assertThat(diff.schemas())
                .extracting(SchemaDiff::name, SchemaDiff::label, SchemaDiff::change)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "sales", new cherry.mastersmith.dsl.domain.DisplayName("販売", "Sales"), DiffChange.ADDED));
        assertThat(diff.schemas().getFirst().tables())
                .extracting(TableDiff::change)
                .containsOnly(DiffChange.ADDED);
    }

    @Test
    @DisplayName("a renamed schema is ADDED and the applied one is REMOVED with its own label and tables")
    void renamedSchemaIsAddedAndRemoved() {
        DslModel applied = model(DslYaml.dsl().schema("old", "旧", "Old").table("t", column("c")));
        DslModel preview = model(DslYaml.dsl().schema("new", "新", "New").table("t", column("c")));

        Diff diff = DslDiffCalculator.diff(preview, applied);

        assertThat(diff.schemas())
                .extracting(SchemaDiff::name, schema -> schema.label().ja(), SchemaDiff::change)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("new", "新", DiffChange.ADDED),
                        org.assertj.core.groups.Tuple.tuple("old", "旧", DiffChange.REMOVED));
        assertThat(diff.schemas().get(0).tables()).extracting(TableDiff::change).containsExactly(DiffChange.ADDED);
        assertThat(diff.schemas().get(1).tables()).extracting(TableDiff::change).containsExactly(DiffChange.REMOVED);
    }

    @Test
    @DisplayName("a schema whose label alone differs is CHANGED, and one with the same label is UNCHANGED")
    void schemaChangedOnlyByLabel() {
        DslModel applied = model(DslYaml.dsl().schema("sales", "販売", "Sales").table("t", column("c")));
        DslModel relabeled = model(DslYaml.dsl().schema("sales", "営業", "Sales").table("t", column("c")));
        DslModel tableChanged =
                model(DslYaml.dsl().schema("sales", "販売", "Sales").table("t", column("c"), column("d")));

        assertThat(DslDiffCalculator.diff(relabeled, applied).schemas())
                .singleElement()
                .satisfies(schema -> {
                    assertThat(schema.change()).isEqualTo(DiffChange.CHANGED);
                    assertThat(schema.tables()).extracting(TableDiff::change).containsExactly(DiffChange.UNCHANGED);
                });
        assertThat(DslDiffCalculator.diff(tableChanged, applied).schemas())
                .singleElement()
                .satisfies(schema -> {
                    assertThat(schema.change())
                            .as("table changes are shown on the table rows")
                            .isEqualTo(DiffChange.UNCHANGED);
                    assertThat(schema.tables()).extracting(TableDiff::change).containsExactly(DiffChange.CHANGED);
                });
    }

    @Test
    @DisplayName("the summary counts the schemas and lists a missing schema label before menus and tables")
    void summaryCountsSchemasAndTheirLabels() {
        Summary summary = DslSummaryCalculator.summarize(model(DslYaml.dsl()
                .schema("sales", "", "Sales")
                .table("t", column("c"))
                .menu("", "Menu", "t")));

        assertThat(summary.schemaCount()).isEqualTo(1);
        assertThat(summary.missingDisplayNames())
                .containsExactly(
                        new MissingDisplayName("schemas.sales.label", "ja"),
                        new MissingDisplayName("menus.0.label", "ja"));
        assertThat(summary.menuTree().getFirst().table()).isEqualTo(new TableRef("sales", "t"));
    }
}
