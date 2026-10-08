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
package cherry.mastersmith.role.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 対象の名前の組と値の組（{@code entities.md} の PermissionTarget・PermissionSetting、BR4.1・BR4.2）の単体テスト。 */
class PermissionTargetTest {

    @Test
    @DisplayName("the level follows the given names and the stored form uses empty strings for missing levels")
    void levelsAndStoredForm() {
        PermissionTarget schema = PermissionTarget.schema("S");
        PermissionTarget table = PermissionTarget.table("S", "T");
        PermissionTarget column = PermissionTarget.column("S", "T", "C");

        assertThat(schema.level()).isEqualTo(PermissionLevel.SCHEMA);
        assertThat(table.level()).isEqualTo(PermissionLevel.TABLE);
        assertThat(column.level()).isEqualTo(PermissionLevel.COLUMN);
        assertThat(schema.storedTableName()).isEmpty();
        assertThat(table.storedColumnName()).isEmpty();
        assertThat(PermissionTarget.fromStored("S", "", "")).isEqualTo(schema);
        assertThat(PermissionTarget.fromStored("S", "T", "")).isEqualTo(table);
        assertThat(PermissionTarget.fromStored("S", "T", "C")).isEqualTo(column);
    }

    @Test
    @DisplayName("an empty name, or a column without a table, is not a target")
    void invalidShapes() {
        assertThatThrownBy(() -> new PermissionTarget("", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PermissionTarget(null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PermissionTarget("S", "", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PermissionTarget("S", null, "C")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a setting needs at least one value, and a column setting has no auxiliary permission")
    void settingValues() {
        java.time.Instant now = java.time.Instant.parse("2026-10-08T05:00:00Z");
        PermissionTarget column = PermissionTarget.column("S", "T", "C");

        assertThat(PermissionValues.NOT_SET.isEmpty()).isTrue();
        assertThat(new PermissionValues(null, false, null).hasAuxiliary()).isTrue();
        assertThatThrownBy(() -> new PermissionSetting(1L, column, PermissionValues.NOT_SET, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PermissionSetting(1L, column, new PermissionValues(MainPermission.READ, true, null), now))
                .isInstanceOf(IllegalArgumentException.class);
        PermissionSetting setting = new PermissionSetting(
                1L, PermissionTarget.table("S", "T"), new PermissionValues(null, true, false), now);
        setting.update(new PermissionValues(MainPermission.FULL, null, null), now.plusSeconds(1));
        assertThat(setting.values()).isEqualTo(new PermissionValues(MainPermission.FULL, null, null));
        assertThat(setting.getUpdatedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(setting.toString()).doesNotContain("\"T\"").contains("TABLE");
    }

    @Test
    @DisplayName("main permission names are read exactly and other values are refused")
    void mainPermissionNames() {
        assertThat(MainPermission.parse("READ")).contains(MainPermission.READ);
        assertThat(MainPermission.parse("read")).isEmpty();
        assertThat(MainPermission.parse("ADMIN")).isEmpty();
        assertThat(MainPermission.parse(null)).isEmpty();
    }
}
