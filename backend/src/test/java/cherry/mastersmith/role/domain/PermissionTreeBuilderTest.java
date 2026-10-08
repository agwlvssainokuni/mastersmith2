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

import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.role.domain.PermissionTreeBuilder.StoredChild;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 権限の設定の木の1階層を作る関数（BR4.10・BR4.11・BR5.3、AC1.2.1〜AC1.2.4・AC1.2.7・AC1.2.16）の単体テスト。 */
class PermissionTreeBuilderTest {

    private static final DslModel DSL = RoleDslFixture.sample();

    private static StoredChild stored(MainPermission main, Boolean create, Boolean delete, long deeperRows) {
        return new StoredChild(new PermissionValues(main, create, delete), deeperRows);
    }

    private static List<String> names(List<PermissionNode> nodes) {
        return nodes.stream().map(PermissionNode::name).toList();
    }

    @Test
    @DisplayName("schemas come in the DSL order, followed by schemas only in the settings in code point order")
    void schemaOrder() {
        List<PermissionNode> nodes = PermissionTreeBuilder.schemas(
                DSL,
                Map.of(
                        "ZZZ", stored(MainPermission.READ, null, null, 0),
                        "OLD", stored(null, null, null, 3),
                        "HR", stored(MainPermission.FULL, null, null, 0)));

        assertThat(names(nodes)).containsExactly("SALES", "HR", "OLD", "ZZZ");
        assertThat(nodes).extracting(PermissionNode::inCurrentDsl).containsExactly(true, true, false, false);
        assertThat(nodes.get(0).label().ja()).isEqualTo("スキーマ SALES");
        assertThat(nodes.get(2).label()).isNull();
        assertThat(nodes.get(2).hasChildren()).as("下に設定がある今の DSL に無いスキーマ").isTrue();
        assertThat(nodes.get(3).hasChildren()).isFalse();
    }

    @Test
    @DisplayName("a schema node carries its explicit value, the effective value and the source, and no menu flag")
    void schemaValues() {
        List<PermissionNode> nodes =
                PermissionTreeBuilder.schemas(DSL, Map.of("HR", stored(MainPermission.FULL, true, null, 0)));

        PermissionNode sales = nodes.get(0);
        PermissionNode hr = nodes.get(1);
        assertThat(sales.explicit()).isEqualTo(PermissionValues.NOT_SET);
        assertThat(sales.effective()).isEqualTo(EffectivePermission.NONE);
        assertThat(sales.inheritance().mainFrom()).isEqualTo(InheritedFrom.DEFAULT);
        assertThat(sales.hasChildren()).isTrue();
        assertThat(hr.effective()).isEqualTo(new EffectivePermission(MainPermission.FULL, true, false));
        assertThat(hr.inheritance().mainFrom()).isEqualTo(InheritedFrom.EXPLICIT);
        assertThat(nodes).noneMatch(PermissionNode::inMenu);
    }

    @Test
    @DisplayName("tables inherit the schema, a table is in the menu when its effective main is not NONE")
    void tablesAndMenu() {
        List<PermissionNode> nodes = PermissionTreeBuilder.tables(
                DSL,
                "SALES",
                new PermissionValues(MainPermission.READ, null, null),
                Map.of("ORDER_HEAD", stored(MainPermission.NONE, null, null, 0)));

        assertThat(names(nodes)).containsExactly("ORDER_LINE", "ORDER_HEAD");
        PermissionNode line = nodes.get(0);
        assertThat(line.target()).isEqualTo(PermissionTarget.table("SALES", "ORDER_LINE"));
        assertThat(line.effective().main()).isEqualTo(MainPermission.READ);
        assertThat(line.inheritance().mainFrom()).isEqualTo(InheritedFrom.SCHEMA);
        assertThat(line.inMenu()).isTrue();
        assertThat(line.hasChildren()).isTrue();
        assertThat(nodes.get(1).inMenu()).as("明示の NONE").isFalse();
    }

    @Test
    @DisplayName("a node not in the current DSL is NONE and not allowed from the default even with settings")
    void notInCurrentDsl() {
        List<PermissionNode> nodes = PermissionTreeBuilder.tables(
                DSL,
                "SALES",
                new PermissionValues(MainPermission.FULL, true, true),
                Map.of("GONE", stored(MainPermission.FULL, true, true, 2)));

        PermissionNode gone = nodes.get(2);
        assertThat(gone.name()).isEqualTo("GONE");
        assertThat(gone.inCurrentDsl()).isFalse();
        assertThat(gone.explicit()).isEqualTo(new PermissionValues(MainPermission.FULL, true, true));
        assertThat(gone.effective()).isEqualTo(EffectivePermission.NONE);
        assertThat(gone.inheritance().mainFrom()).isEqualTo(InheritedFrom.DEFAULT);
        assertThat(gone.inMenu()).isFalse();
        assertThat(gone.hasChildren()).isTrue();
    }

    @Test
    @DisplayName("tables under a schema that is not in the DSL are all settings-only nodes")
    void tablesOfMissingSchema() {
        List<PermissionNode> nodes = PermissionTreeBuilder.tables(
                DSL,
                "OLD",
                PermissionValues.NOT_SET,
                Map.of("B", stored(MainPermission.READ, null, null, 0), "A", stored(null, false, null, 0)));

        assertThat(names(nodes)).containsExactly("A", "B");
        assertThat(nodes).noneMatch(PermissionNode::inCurrentDsl);
    }

    @Test
    @DisplayName("columns inherit the table then the schema, and take the auxiliary values of the table")
    void columns() {
        List<PermissionNode> nodes = PermissionTreeBuilder.columns(
                DSL,
                "SALES",
                "ORDER_LINE",
                new PermissionValues(MainPermission.FULL, true, null),
                new PermissionValues(MainPermission.READ, null, false),
                Map.of("UNIT_PRICE", stored(MainPermission.NONE, null, null, 0)));

        assertThat(names(nodes)).containsExactly("ORDER_NO", "UNIT_PRICE", "QTY");
        PermissionNode orderNo = nodes.get(0);
        assertThat(orderNo.effective()).isEqualTo(new EffectivePermission(MainPermission.READ, true, false));
        assertThat(orderNo.inheritance().mainFrom()).isEqualTo(InheritedFrom.TABLE);
        assertThat(orderNo.inheritance().createFrom()).isEqualTo(InheritedFrom.SCHEMA);
        assertThat(nodes.get(1).effective().main()).isEqualTo(MainPermission.NONE);
        assertThat(nodes.get(1).inheritance().mainFrom()).isEqualTo(InheritedFrom.EXPLICIT);
        assertThat(nodes).noneMatch(PermissionNode::hasChildren).noneMatch(PermissionNode::inMenu);
    }

    @Test
    @DisplayName("names are matched exactly with case, and the code point order puts supplementary characters last")
    void matchingAndOrder() {
        List<PermissionNode> nodes =
                PermissionTreeBuilder.schemas(DSL, Map.of("sales", stored(MainPermission.READ, null, null, 0)));

        assertThat(names(nodes)).containsExactly("SALES", "HR", "sales");
        assertThat(nodes.get(0).explicit()).isEqualTo(PermissionValues.NOT_SET);
        Comparator<String> order = PermissionTreeBuilder.CODE_POINT_ORDER;
        assertThat(List.of("😀", "～", "a", "ab").stream().sorted(order).toList())
                .containsExactly("a", "ab", "～", "😀");
        assertThat(PermissionTreeBuilder.inCurrentDsl(DSL, PermissionTarget.column("SALES", "ORDER_LINE", "QTY")))
                .isTrue();
        assertThat(PermissionTreeBuilder.inCurrentDsl(DSL, PermissionTarget.column("SALES", "ORDER_LINE", "qty")))
                .isFalse();
        assertThat(PermissionTreeBuilder.inCurrentDsl(DSL, PermissionTarget.table("HR", "ORDER_LINE")))
                .isFalse();
        assertThat(PermissionTreeBuilder.inCurrentDsl(DSL, PermissionTarget.schema("HR")))
                .isTrue();
        assertThat(PermissionTreeBuilder.inCurrentDsl(DSL, PermissionTarget.schema("GONE")))
                .isFalse();
    }
}
