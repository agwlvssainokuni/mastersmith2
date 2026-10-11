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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.TableRef;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 絞る関数の例のテスト（BR2.1〜BR2.6・BR3.1〜BR3.5、AC5.1.2・AC5.1.14）。 */
class MenuFilterTest {

    private static final NavIconPolicy ICONS = new NavIconPolicy(Set.of("list", "home", "user"));

    private final Map<String, MainPermission> mains = new HashMap<>();

    private final List<String> asked = new ArrayList<>();

    private MenuFilterInput input() {
        return new MenuFilterInput(
                (schema, table) -> {
                    asked.add(schema + "/" + table);
                    return mains.getOrDefault(schema + "/" + table, MainPermission.NONE);
                },
                ICONS::iconOf);
    }

    private static DslMenuItem tableItem(String label, String table, DslMenuItem... children) {
        return new DslMenuItem(
                new cherry.mastersmith.dsl.domain.DisplayName(label, label + "-en"),
                null,
                new TableRef("SALES", table),
                List.of(children));
    }

    private static DslMenuItem group(String label, DslMenuItem... children) {
        return new DslMenuItem(
                new cherry.mastersmith.dsl.domain.DisplayName(label, label + "-en"), "home", null, List.of(children));
    }

    private List<NavNode> filter(DslMenuItem... menus) {
        return MenuFilter.filter(List.of(menus), input());
    }

    @Test
    @DisplayName("an item with a visible table is navigable and keeps its filtered children")
    void visibleTableWithChildren() {
        mains.put("SALES/ORDER", MainPermission.READ);
        mains.put("SALES/LINE", MainPermission.FULL);

        List<NavNode> result = filter(tableItem("受注", "ORDER", tableItem("明細", "LINE"), tableItem("隠す", "HIDDEN")));

        assertThat(result).singleElement().satisfies(node -> {
            assertThat(node.navigable()).isTrue();
            assertThat(node.table()).isEqualTo(new NavTableRef("SALES", "ORDER"));
            assertThat(node.items()).extracting(NavNode::id).containsExactly("0.0");
        });
    }

    @Test
    @DisplayName("an item with a visible table stays navigable even when all its children are dropped")
    void visibleTableWithoutRemainingChildren() {
        mains.put("SALES/ORDER", MainPermission.READ);

        List<NavNode> result = filter(tableItem("受注", "ORDER", tableItem("隠す", "HIDDEN")));

        assertThat(result).singleElement().satisfies(node -> {
            assertThat(node.navigable()).isTrue();
            assertThat(node.items()).isEmpty();
        });
    }

    @Test
    @DisplayName("an item whose table is NONE but with visible children becomes a group without a table")
    void hiddenTableWithVisibleChildren() {
        mains.put("SALES/LINE", MainPermission.READ);

        List<NavNode> result = filter(tableItem("受注", "ORDER", tableItem("明細", "LINE")));

        assertThat(result).singleElement().satisfies(node -> {
            assertThat(node.navigable()).isFalse();
            assertThat(node.table()).isNull();
            assertThat(node.items()).extracting(NavNode::id).containsExactly("0.0");
        });
    }

    @Test
    @DisplayName("an item whose table is NONE and whose children are all dropped is dropped")
    void hiddenTableWithoutChildren() {
        assertThat(filter(tableItem("受注", "ORDER", tableItem("明細", "LINE")))).isEmpty();
    }

    @Test
    @DisplayName("groups left without children are dropped up the tree")
    void emptyGroupsAreDroppedUpwards() {
        mains.put("SALES/SHOWN", MainPermission.READ);

        List<NavNode> result =
                filter(group("上", group("中", group("下", tableItem("隠す", "HIDDEN")))), tableItem("見える", "SHOWN"));

        assertThat(result).extracting(NavNode::id).containsExactly("1");
    }

    @Test
    @DisplayName("an item pointing to a table that is not in the DSL is dropped as NONE")
    void tableNotInDslIsDropped() {
        mains.put("SALES/ORDER", MainPermission.READ);

        List<NavNode> result = filter(tableItem("受注", "ORDER"), tableItem("無い", "NOT_IN_DSL"));

        assertThat(result).extracting(NavNode::id).containsExactly("0");
        assertThat(asked).contains("SALES/NOT_IN_DSL");
    }

    @Test
    @DisplayName("ids are the positions before filtering and are not compacted by dropped items")
    void idsAreNotCompacted() {
        mains.put("SALES/B", MainPermission.READ);
        mains.put("SALES/D", MainPermission.READ);

        List<NavNode> result = filter(
                tableItem("A", "A"),
                group("G", tableItem("C", "C"), tableItem("C2", "C"), tableItem("D", "D")),
                tableItem("B", "B"));

        assertThat(result).extracting(NavNode::id).containsExactly("1", "2");
        assertThat(result.getFirst().items()).extracting(NavNode::id).containsExactly("1.2");
    }

    @Test
    @DisplayName("the order and the parent of the remaining items are kept")
    void orderAndParentsAreKept() {
        mains.put("SALES/A", MainPermission.READ);
        mains.put("SALES/B", MainPermission.FULL);
        mains.put("SALES/C", MainPermission.READ);

        List<NavNode> result =
                filter(group("G1", tableItem("B", "B"), tableItem("A", "A")), group("G2", tableItem("C", "C")));

        assertThat(result).extracting(node -> node.label().ja()).containsExactly("G1", "G2");
        assertThat(result.getFirst().items())
                .extracting(node -> node.label().ja())
                .containsExactly("B", "A");
        assertThat(result.get(1).items()).extracting(NavNode::id).containsExactly("1.0");
    }

    @Test
    @DisplayName("a tree of exactly five levels is returned without losing any level")
    void fiveLevels() {
        mains.put("SALES/LEAF", MainPermission.READ);

        List<NavNode> result = filter(group("1", group("2", group("3", group("4", tableItem("5", "LEAF"))))));

        NavNode node = result.getFirst();
        for (int level = 2; level <= 5; level++) {
            node = node.items().getFirst();
        }
        assertThat(node.id()).isEqualTo("0.0.0.0.0");
        assertThat(node.navigable()).isTrue();
        assertThat(node.label().ja()).isEqualTo("5");
    }

    @Test
    @DisplayName("labels are copied as they are, including markup characters and empty strings")
    void labelsAreCopiedAsTheyAre() {
        mains.put("SALES/X", MainPermission.READ);
        DslMenuItem item = new DslMenuItem(
                new cherry.mastersmith.dsl.domain.DisplayName("<script>&\"'", ""),
                null,
                new TableRef("SALES", "X"),
                List.of());

        List<NavNode> result = MenuFilter.filter(List.of(item), input());

        assertThat(result.getFirst().label()).isEqualTo(new DisplayLabel("<script>&\"'", ""));
    }

    @Test
    @DisplayName("icons are checked against the allowed names and unknown ones become list")
    void iconsAreChecked() {
        mains.put("SALES/X", MainPermission.READ);
        DslMenuItem known = new DslMenuItem(
                new cherry.mastersmith.dsl.domain.DisplayName("a", "a"), "user", new TableRef("SALES", "X"), List.of());
        DslMenuItem unknown = new DslMenuItem(
                new cherry.mastersmith.dsl.domain.DisplayName("b", "b"), "User", new TableRef("SALES", "X"), List.of());

        List<NavNode> result = MenuFilter.filter(List.of(known, unknown), input());

        assertThat(result).extracting(NavNode::icon).containsExactly("user", "list");
    }

    @Test
    @DisplayName("an empty menu gives an empty result and the input is required")
    void emptyAndRequired() {
        assertThat(MenuFilter.filter(List.of(), input())).isEmpty();
        assertThatThrownBy(() -> MenuFilter.filter(null, input())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MenuFilter.filter(List.of(), null)).isInstanceOf(NullPointerException.class);
    }
}
