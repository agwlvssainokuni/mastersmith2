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
package cherry.mastersmith.dsl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DSL のモデル（{@link DslModel}）の文字列化の単体テスト（Intent 261015-deps-ui-carryover の依頼者の決定 A、FR4.1・NFR4）。名前と
 * 表示名が出ず、識別の先頭と件数だけが出ること、モデルを包む結果の型の文字列にも名前が出ないことを確かめる。
 */
class DslModelToStringTest {

    private static DslModel model() {
        DslTable table = new DslTable(
                "MARKER_TABLE",
                new cherry.mastersmith.dsl.domain.DisplayName("MARKER_TABLE_JA", "MARKER_TABLE_EN"),
                false,
                List.of("MARKER_COLUMN"),
                List.of(),
                Map.of("MARKER_COLUMN", DslModelTest.column("MARKER_COLUMN")));
        DslSchema schema = new DslSchema(
                "MARKER_SCHEMA",
                new cherry.mastersmith.dsl.domain.DisplayName("MARKER_SCHEMA_JA", "MARKER_SCHEMA_EN"),
                Map.of("MARKER_TABLE", table));
        DslMenuItem menu = new DslMenuItem(
                new cherry.mastersmith.dsl.domain.DisplayName("MARKER_MENU_JA", "MARKER_MENU_EN"),
                null,
                new TableRef("MARKER_SCHEMA", "MARKER_TABLE"),
                List.of());
        return new DslModel(DslModelTest.HASH, DslFormat.CURRENT_VERSION, List.of(schema), List.of(menu, menu));
    }

    @Test
    @DisplayName("toString prints the hash prefix, the version and the counts but no name or label")
    void toStringHidesNamesAndLabels() {
        assertThat(model().toString())
                .isEqualTo("DslModel[dslHash=0123456789ab…, formatVersion=2, schemas=1, menus=2]")
                .doesNotContain("MARKER");
    }

    @Test
    @DisplayName("the present active DSL and the read results hide the names and labels of the model")
    void wrappingResultsHideNamesAndLabels() {
        DslModel model = model();

        assertThat(ActiveDsl.of(model).toString())
                .contains("DslModel[dslHash=0123456789ab…")
                .doesNotContain("MARKER");
        assertThat(DslReadResult.valid(model).toString())
                .contains("DslModel[dslHash=0123456789ab…")
                .doesNotContain("MARKER");
        assertThat(new DslStartupReadResult.Valid(model, 3).toString())
                .contains("prunedMenuItems=3")
                .doesNotContain("MARKER");
    }

    @Test
    @DisplayName("a model without menus prints zero menus")
    void modelWithoutMenus() {
        DslModel model = new DslModel(
                DslModelTest.HASH,
                DslFormat.CURRENT_VERSION,
                List.of(DslModelTest.schema("sales", DslModelTest.table("dept", "code"))),
                List.of());

        assertThat(model.toString()).endsWith("schemas=1, menus=0]");
    }
}
