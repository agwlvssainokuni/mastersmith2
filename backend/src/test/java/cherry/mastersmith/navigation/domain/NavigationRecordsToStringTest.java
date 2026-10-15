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
package cherry.mastersmith.navigation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.navigation.web.DisplayLabelResponse;
import cherry.mastersmith.navigation.web.NavTableRefResponse;
import cherry.mastersmith.navigation.web.TableAccessResponse;
import cherry.mastersmith.role.domain.MainPermission;
import org.junit.jupiter.api.Test;

/**
 * navigation の6つの record の文字列化の単体テスト（Intent 261015-deps-ui-carryover の FR4.1）。DSL の表示名とスキーマ・テーブルの
 * 名前が出ず、値そのものは項目から読めることを確かめる。
 */
class NavigationRecordsToStringTest {

    private static final String SCHEMA = "MARKER_SCHEMA";

    private static final String TABLE = "MARKER_TABLE";

    private static final DisplayName LABEL = new DisplayName("MARKER_JA", "MARKER_EN");

    @Test
    @org.junit.jupiter.api.DisplayName("the display label and its response hide the labels")
    void displayLabels() {
        DisplayLabel label = DisplayLabel.of(LABEL);
        DisplayLabelResponse response = DisplayLabelResponse.from(label);

        assertThat(label.toString()).isEqualTo("DisplayLabel[ja=***, en=***]");
        assertThat(response.toString()).isEqualTo("DisplayLabelResponse[ja=***, en=***]");
        assertThat(label.ja()).isEqualTo("MARKER_JA");
        assertThat(response.en()).isEqualTo("MARKER_EN");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("the table reference and its response hide the schema and table names")
    void tableRefs() {
        NavTableRef ref = new NavTableRef(SCHEMA, TABLE);
        NavTableRefResponse response = NavTableRefResponse.from(ref);

        assertThat(ref.toString()).isEqualTo("NavTableRef[schemaName=***, tableName=***]");
        assertThat(response.toString()).isEqualTo("NavTableRefResponse[schemaName=***, tableName=***]");
        assertThat(response.schemaName()).isEqualTo(SCHEMA);
        assertThat(response.tableName()).isEqualTo(TABLE);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("the visible table access and its response print only the main permission")
    void tableAccess() {
        TableAccessResult.Visible visible = new TableAccessResult.Visible(SCHEMA, TABLE, LABEL, MainPermission.FULL);
        TableAccessResponse response = TableAccessResponse.from(visible);

        assertThat(visible.toString())
                .isEqualTo("Visible[schemaName=***, tableName=***, displayName=***, main=FULL]")
                .doesNotContain("MARKER");
        assertThat(response.toString())
                .isEqualTo("TableAccessResponse[schemaName=***, tableName=***, displayName=***, main=FULL]")
                .doesNotContain("MARKER");
        assertThat(response.displayName().ja()).isEqualTo("MARKER_JA");
        assertThat(response.main()).isEqualTo("FULL");
    }
}
