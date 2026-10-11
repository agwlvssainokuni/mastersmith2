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

import cherry.mastersmith.role.domain.MainPermission;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 値の型の形の確かめのテスト（ENT-001〜ENT-008、BR2.3・BR3.3・BR3.5）。 */
class NavNodeTest {

    private static final DisplayLabel LABEL = new DisplayLabel("受注", "Orders");

    private static NavNode leaf(String id) {
        return new NavNode(id, LABEL, "list", new NavTableRef("SALES", "ORDER"), true, List.of());
    }

    @Test
    @DisplayName("a navigable item has a table and may have no children, and its children cannot be changed")
    void navigableItem() {
        List<NavNode> children = new ArrayList<>(List.of(leaf("0.0")));
        NavNode node = new NavNode("0", LABEL, "home", new NavTableRef("SALES", "ORDER"), true, children);
        children.clear();

        assertThat(node.items()).hasSize(1);
        assertThatThrownBy(() -> node.items().add(leaf("0.1"))).isInstanceOf(UnsupportedOperationException.class);
        assertThat(leaf("1").items()).isEmpty();
        assertThat(node.toString()).isEqualTo("NavNode[id=0, navigable=true, items=1]");
    }

    @Test
    @DisplayName("a group without children or a navigable item without a table cannot be made")
    void invalidShapes() {
        assertThatThrownBy(() -> new NavNode("0", LABEL, "list", null, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NavNode("0", LABEL, "list", null, true, List.of(leaf("0.0"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new NavNode("0", LABEL, "list", new NavTableRef("S", "T"), false, List.of(leaf("0.0"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NavNode("", LABEL, "list", null, false, List.of(leaf("0.0"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NavNode("0", LABEL, "", null, false, List.of(leaf("0.0"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NavNode("0", null, "list", null, false, List.of(leaf("0.0"))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("table references and labels check their values, and labels allow empty strings")
    void valueTypes() {
        assertThatThrownBy(() -> new NavTableRef("", "T")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NavTableRef("S", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DisplayLabel("", "")).isEqualTo(new DisplayLabel("", ""));
        assertThatThrownBy(() -> new DisplayLabel(null, "")).isInstanceOf(NullPointerException.class);
        assertThat(DisplayLabel.of(new cherry.mastersmith.dsl.domain.DisplayName(" 前後 ", "")))
                .isEqualTo(new DisplayLabel(" 前後 ", ""));
    }

    @Test
    @DisplayName("the empty reason is set only when the menu is empty")
    void menuEmptyReason() {
        assertThat(NavigationMenu.notConfigured().emptyReason()).isEqualTo(EmptyReason.NOT_CONFIGURED);
        assertThat(NavigationMenu.filtered(List.of()).emptyReason()).isEqualTo(EmptyReason.NOTHING_VISIBLE);
        NavigationMenu menu = NavigationMenu.filtered(List.of(leaf("0")));
        assertThat(menu.emptyReason()).isNull();
        assertThat(menu.toString()).isEqualTo("NavigationMenu[items=1, emptyReason=null]");
        assertThatThrownBy(() -> new NavigationMenu(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NavigationMenu(List.of(leaf("0")), EmptyReason.NOTHING_VISIBLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a visible table access result needs READ or FULL")
    void tableAccessResult() {
        cherry.mastersmith.dsl.domain.DisplayName name = new cherry.mastersmith.dsl.domain.DisplayName("受注", "Orders");
        assertThat(new TableAccessResult.Visible("S", "T", name, MainPermission.FULL).main())
                .isEqualTo(MainPermission.FULL);
        assertThatThrownBy(() -> new TableAccessResult.Visible("S", "T", name, MainPermission.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TableAccessResult.Visible("S", "T", null, MainPermission.READ))
                .isInstanceOf(NullPointerException.class);
        assertThat(new TableAccessResult.NotVisible()).isEqualTo(new TableAccessResult.NotVisible());
    }
}
