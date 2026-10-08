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

import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 作業ロールの設定の写し（契約 C5、BR5.1〜BR5.5、計画の D-5）の単体テスト。 */
class PermissionSnapshotTest {

    private static final DslModel DSL = RoleDslFixture.sample();

    private static final WorkRoleRef WORK_ROLE = new WorkRoleRef(7L, "営業");

    private static Map<PermissionTarget, PermissionValues> settings() {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema("SALES"), new PermissionValues(MainPermission.READ, true, null));
        settings.put(
                PermissionTarget.table("SALES", "ORDER_LINE"), new PermissionValues(MainPermission.FULL, null, true));
        settings.put(
                PermissionTarget.column("SALES", "ORDER_LINE", "UNIT_PRICE"),
                new PermissionValues(MainPermission.NONE, null, null));
        settings.put(PermissionTarget.schema("GONE"), new PermissionValues(MainPermission.FULL, true, true));
        return settings;
    }

    @Test
    @DisplayName("the values equal the inheritance function applied to the explicit settings")
    void valuesFollowTheInheritance() {
        PermissionSnapshot snapshot = PermissionSnapshot.of(WORK_ROLE, DSL, RoleDslFixture.HASH, settings());

        assertThat(snapshot.effective(PermissionTarget.schema("SALES")))
                .isEqualTo(new EffectivePermission(MainPermission.READ, true, false));
        assertThat(snapshot.effective(PermissionTarget.table("SALES", "ORDER_LINE")))
                .isEqualTo(new EffectivePermission(MainPermission.FULL, true, true));
        assertThat(snapshot.effective(PermissionTarget.table("SALES", "ORDER_HEAD")))
                .isEqualTo(new EffectivePermission(MainPermission.READ, true, false));
        assertThat(snapshot.main("SALES", "ORDER_LINE", "UNIT_PRICE")).isEqualTo(MainPermission.NONE);
        assertThat(snapshot.main("SALES", "ORDER_LINE", "QTY")).isEqualTo(MainPermission.FULL);
        assertThat(snapshot.create("SALES", "ORDER_LINE")).isTrue();
        assertThat(snapshot.delete("SALES", "ORDER_HEAD")).isFalse();
        assertThat(snapshot.effective(PermissionTarget.column("SALES", "ORDER_LINE", "UNIT_PRICE")))
                .as("カラムの補助権限はテーブルの実効の値")
                .isEqualTo(new EffectivePermission(MainPermission.NONE, true, true));
        assertThat(snapshot.main("HR", "EMPLOYEE", null)).isEqualTo(MainPermission.NONE);
    }

    @Test
    @DisplayName("a target that is not in the current DSL is NONE even if a setting with its name remains")
    void targetOutsideTheDsl() {
        PermissionSnapshot snapshot = PermissionSnapshot.of(WORK_ROLE, DSL, RoleDslFixture.HASH, settings());

        assertThat(snapshot.effective(PermissionTarget.schema("GONE"))).isEqualTo(EffectivePermission.NONE);
        assertThat(snapshot.main("SALES", "NO_SUCH_TABLE", null)).isEqualTo(MainPermission.NONE);
        assertThat(snapshot.main("sales", "ORDER_LINE", null))
                .as("大文字と小文字を区別する")
                .isEqualTo(MainPermission.NONE);
    }

    @Test
    @DisplayName("without a work role or without an applied DSL everything is NONE and not allowed")
    void noWorkRoleOrNoDsl() {
        PermissionSnapshot noRole = PermissionSnapshot.of(null, DSL, RoleDslFixture.HASH, Map.of());
        PermissionSnapshot noDsl = PermissionSnapshot.of(WORK_ROLE, null, null, settings());

        assertThat(noRole.workRole()).isEmpty();
        assertThat(noRole.dslHash()).hasValue(RoleDslFixture.HASH);
        assertThat(noRole.effective(PermissionTarget.table("SALES", "ORDER_LINE")))
                .isEqualTo(EffectivePermission.NONE);
        assertThat(noDsl.workRole()).hasValue(WORK_ROLE);
        assertThat(noDsl.dslHash()).isEmpty();
        assertThat(noDsl.effective(PermissionTarget.table("SALES", "ORDER_LINE")))
                .isEqualTo(EffectivePermission.NONE);
    }

    @Test
    @DisplayName("the string form has the work role id and the row count only")
    void stringFormHasNoNames() {
        PermissionSnapshot snapshot = PermissionSnapshot.of(WORK_ROLE, DSL, RoleDslFixture.HASH, settings());

        assertThat(snapshot.toString())
                .isEqualTo("PermissionSnapshot[workRoleId=7, dsl=true, rows=4]")
                .doesNotContain("営業")
                .doesNotContain("SALES")
                .doesNotContain("UNIT_PRICE");
        assertThat(snapshot.settingCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("the snapshot keeps its own copy and rejects inconsistent arguments")
    void copiesAndValidates() {
        Map<PermissionTarget, PermissionValues> source = settings();
        PermissionSnapshot snapshot = PermissionSnapshot.of(WORK_ROLE, DSL, RoleDslFixture.HASH, source);
        source.clear();

        assertThat(snapshot.settingCount()).isEqualTo(4);
        assertThatThrownBy(() -> PermissionSnapshot.of(WORK_ROLE, DSL, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionSnapshot.of(null, DSL, RoleDslFixture.HASH, settings()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionSnapshot.of(WORK_ROLE, DSL, RoleDslFixture.HASH, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the nodes of each level follow the DSL order with effective values, inMenu on tables and children")
    void nodes() {
        PermissionSnapshot snapshot = PermissionSnapshot.of(WORK_ROLE, DSL, RoleDslFixture.HASH, settings());

        assertThat(snapshot.schemaNodes())
                .extracting(node -> node.target().schemaName())
                .containsExactly("SALES", "HR");
        assertThat(snapshot.schemaNodes().getFirst().hasChildren()).isTrue();
        assertThat(snapshot.schemaNodes().getFirst().inMenu())
                .as("スキーマはメニューに出ない")
                .isFalse();
        assertThat(snapshot.tableNodes("SALES"))
                .extracting(node -> node.target().tableName(), EffectiveNode::inMenu)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ORDER_LINE", true),
                        org.assertj.core.groups.Tuple.tuple("ORDER_HEAD", true));
        assertThat(snapshot.tableNodes("HR"))
                .singleElement()
                .satisfies(node -> assertThat(node.inMenu()).isFalse());
        assertThat(snapshot.columnNodes("SALES", "ORDER_LINE"))
                .extracting(node -> node.effective().main())
                .containsExactly(MainPermission.FULL, MainPermission.NONE, MainPermission.FULL);
        assertThat(snapshot.columnNodes("SALES", "ORDER_LINE").getFirst().hasChildren())
                .isFalse();
        assertThat(snapshot.columnNodes("SALES", "ORDER_LINE")
                        .getFirst()
                        .label()
                        .ja())
                .isEqualTo("カラム ORDER_NO");
    }

    @Test
    @DisplayName("names outside the DSL and a snapshot without a DSL give no nodes")
    void noNodes() {
        PermissionSnapshot snapshot = PermissionSnapshot.of(WORK_ROLE, DSL, RoleDslFixture.HASH, settings());
        PermissionSnapshot noDsl = PermissionSnapshot.of(WORK_ROLE, null, null, Map.of());

        assertThat(snapshot.tableNodes("GONE")).isEmpty();
        assertThat(snapshot.columnNodes("SALES", "NO_SUCH")).isEmpty();
        assertThat(noDsl.schemaNodes()).isEmpty();
        assertThat(noDsl.tableNodes("SALES")).isEmpty();
        assertThat(noDsl.columnNodes("SALES", "ORDER_LINE")).isEmpty();
    }
}
