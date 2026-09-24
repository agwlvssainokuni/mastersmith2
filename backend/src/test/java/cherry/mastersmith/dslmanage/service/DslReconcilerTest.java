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

import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.validate.PatternChecker;
import cherry.mastersmith.dslmanage.domain.PreviewView.Warning;
import cherry.mastersmith.dslmanage.domain.PreviewView.WarningKind;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetColumn;
import cherry.mastersmith.targetdb.domain.TargetDbType;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.domain.TargetTable;
import cherry.mastersmith.targetdb.domain.UnavailableReason;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 対象DB との照合（BR2.4）の単体テスト。 */
class DslReconcilerTest {

    private static final PatternChecker PATTERNS = new PatternChecker();

    private static final DslReader READER = DslYaml.newReader(PATTERNS);

    /** 接続先らしい値（警告に入らないことを確かめる）。 */
    private static final String HOST = "db.internal.example";

    @AfterAll
    static void close() {
        PATTERNS.close();
    }

    private static TargetSchemaResult schema(TargetTable... tables) {
        return TargetSchemaResult.success(new TargetSchema(DatabaseProduct.POSTGRESQL, "public", List.of(tables)));
    }

    private static TargetTable table(String name, TargetColumn... columns) {
        return new TargetTable(name, false, null, List.of(columns), List.of(), List.of());
    }

    private static TargetColumn col(String name, String type, Long length, Integer precision, Integer scale) {
        return new TargetColumn(name, new TargetDbType(type, length, precision, scale), true, null, null);
    }

    @Test
    @DisplayName("missing tables, missing columns and type mismatches become warnings in DSL order")
    void threeKindsOfWarnings() {
        DslModel model = DslYaml.model(
                READER,
                DslYaml.dsl()
                        .table("absent", column("a"))
                        .table(
                                "present",
                                column("ok"),
                                column("nocol"),
                                column("wrongname").type("INTEGER", null, null, null),
                                column("wronglen").type("varchar", 20, null, null),
                                column("num").type("numeric", null, 10, 2))
                        .bytes());

        List<Warning> warnings = DslReconciler.reconcile(
                model,
                schema(table(
                        "present",
                        col("ok", "VARCHAR", 10L, null, null),
                        col("wrongname", "int4", null, 32, 0),
                        col("wronglen", "varchar", 10L, null, null),
                        col("num", "numeric", null, 10, 3))),
                DisplayLanguage.JA);

        assertThat(warnings)
                .extracting(Warning::kind, Warning::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(WarningKind.TABLE_MISSING, "tables.absent"),
                        org.assertj.core.groups.Tuple.tuple(WarningKind.COLUMN_MISSING, "tables.present.columns.nocol"),
                        org.assertj.core.groups.Tuple.tuple(
                                WarningKind.TYPE_MISMATCH, "tables.present.columns.wrongname"),
                        org.assertj.core.groups.Tuple.tuple(
                                WarningKind.TYPE_MISMATCH, "tables.present.columns.wronglen"),
                        org.assertj.core.groups.Tuple.tuple(WarningKind.TYPE_MISMATCH, "tables.present.columns.num"));
        assertThat(warnings.getFirst().message()).isEqualTo("対象DB にテーブル「absent」がありません。");
        assertThat(warnings.get(3).message()).contains("varchar(20)").contains("varchar(10)");
    }

    @Test
    @DisplayName("the English messages are used for the English display language")
    void englishMessages() {
        DslModel model =
                DslYaml.model(READER, DslYaml.dsl().table("absent", column("a")).bytes());

        List<Warning> warnings = DslReconciler.reconcile(model, schema(), DisplayLanguage.EN);

        assertThat(warnings.getFirst().message())
                .isEqualTo("The table \"absent\" does not exist in the target database.");
    }

    @Test
    @DisplayName("an unconfigured target database yields exactly one warning without a path")
    void unconfigured() {
        DslModel model =
                DslYaml.model(READER, DslYaml.dsl().table("t", column("c")).bytes());

        List<Warning> warnings = DslReconciler.reconcile(model, TargetSchemaResult.unconfigured(), DisplayLanguage.JA);

        assertThat(warnings).singleElement().satisfies(warning -> {
            assertThat(warning.kind()).isEqualTo(WarningKind.TARGET_UNCONFIGURED);
            assertThat(warning.path()).isNull();
            assertThat(warning.message()).doesNotContain(HOST);
        });
    }

    @ParameterizedTest
    @EnumSource(UnavailableReason.class)
    @DisplayName("an unavailable target database yields one warning per reason without connection details")
    void unavailable(UnavailableReason reason) {
        DslModel model =
                DslYaml.model(READER, DslYaml.dsl().table("t", column("c")).bytes());

        List<Warning> warnings =
                DslReconciler.reconcile(model, TargetSchemaResult.unavailable(reason), DisplayLanguage.EN);

        assertThat(warnings).singleElement().satisfies(warning -> {
            assertThat(warning.kind()).isEqualTo(WarningKind.TARGET_UNAVAILABLE);
            assertThat(warning.message())
                    .contains(reason == UnavailableReason.TIMEOUT ? "did not respond" : "could not be connected")
                    .doesNotContain(HOST)
                    .doesNotContain("jdbc");
        });
    }

    @Test
    @DisplayName("a longtext length beyond the DSL integers is not compared, and a matching schema gives no warning")
    void noWarningsWhenMatching() {
        DslModel model = DslYaml.model(
                READER,
                DslYaml.dsl()
                        .table("t", column("body").type("longtext", null, null, null), column("c"))
                        .bytes());

        List<Warning> warnings = DslReconciler.reconcile(
                model,
                schema(table(
                        "t",
                        col("body", "LONGTEXT", 4_294_967_295L, null, null),
                        col("c", "varchar", 10L, null, null))),
                DisplayLanguage.JA);

        assertThat(warnings).isEmpty();
        assertThat(DslReconciler.format("{0}-{1}-{2}", "a", "b")).isEqualTo("a-b-{2}");
    }

    @Test
    @DisplayName("a type with a precision but no scale is described without the scale")
    void precisionWithoutScale() {
        DslModel model = DslYaml.model(
                READER,
                DslYaml.dsl()
                        .table("t", column("n").type("numeric", null, 10, null))
                        .bytes());

        List<Warning> warnings = DslReconciler.reconcile(
                model, schema(table("t", col("n", "numeric", null, 12, null))), DisplayLanguage.EN);

        assertThat(warnings)
                .singleElement()
                .satisfies(warning -> assertThat(warning.message())
                        .contains("DSL: numeric(10)")
                        .contains("target database: numeric(12)"));
    }
}
