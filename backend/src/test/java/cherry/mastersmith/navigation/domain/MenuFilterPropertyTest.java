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
import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.MenuDepth;
import cherry.mastersmith.dsl.domain.TableRef;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * 絞る関数の性質ベースのテスト（jqwik。BR2.7、NFR6.2 の8つの性質 (a)〜(h)、AC5.1.15）。
 *
 * <p>入力の木は深さ 1〜5 段・子の数 0〜5 で、同じテーブルを指す項目の重なりと、DSL に無いテーブル（どの権限の割り当てにも出ない名前）を
 * 指す項目を含む。名前は {@code Properties} で終わらせない（{@code *Test} に当たらず動かないため）。失敗のときの乱数の種は、既存の
 * テストの出力の設定（{@code exceptionFormat = FULL}）で残る。
 */
class MenuFilterPropertyTest {

    /** DSL にあるテーブル（権限の割り当ての対象）。 */
    private static final List<TableRef> DSL_TABLES = List.of(
            new TableRef("SALES", "T1"),
            new TableRef("SALES", "T2"),
            new TableRef("SALES", "T3"),
            new TableRef("HR", "T4"),
            new TableRef("HR", "T5"));

    /** DSL に無いテーブル（権限は常に NONE）。 */
    private static final List<TableRef> NOT_IN_DSL = List.of(new TableRef("SALES", "GONE"));

    private static final List<TableRef> ALL_TABLES;

    static {
        List<TableRef> all = new ArrayList<>(DSL_TABLES);
        all.addAll(NOT_IN_DSL);
        ALL_TABLES = List.copyOf(all);
    }

    private static final NavIconPolicy ICONS = new NavIconPolicy(Set.of("list", "home"));

    private static final int MAX_DEPTH = 5;

    /** 権限の割り当て（DSL のテーブルごとの主権限）。 */
    record Mains(Map<TableRef, MainPermission> values) {

        MainPermission of(String schema, String table) {
            return values.getOrDefault(new TableRef(schema, table), MainPermission.NONE);
        }

        MenuFilterInput input() {
            return new MenuFilterInput(this::of, ICONS::iconOf);
        }
    }

    @Provide
    Arbitrary<List<DslMenuItem>> menus() {
        return items(1).list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<DslMenuItem> items(int depth) {
        Arbitrary<TableRef> table = Arbitraries.of(ALL_TABLES);
        Arbitrary<TableRef> optionalTable = table.injectNull(0.3);
        Arbitrary<List<DslMenuItem>> children = depth >= MAX_DEPTH
                ? Arbitraries.just(List.of())
                : Arbitraries.lazy(() -> items(depth + 1)).list().ofMaxSize(5);
        Arbitrary<String> icon =
                Arbitraries.of("home", "list", "Home", "", "unknown").injectNull(0.3);
        Arbitrary<String> label = Arbitraries.strings().withCharRange('a', 'z').ofMaxLength(3);
        return Combinators.combine(optionalTable, table, children, icon, label)
                .as((maybe, fallback, kids, iconName, text) -> new DslMenuItem(
                        new DisplayName(text, text),
                        iconName,
                        maybe == null && kids.isEmpty() ? fallback : maybe,
                        kids));
    }

    @Provide
    Arbitrary<Mains> mains() {
        return Arbitraries.of(MainPermission.class)
                .list()
                .ofSize(DSL_TABLES.size())
                .map(values -> {
                    Map<TableRef, MainPermission> map = new HashMap<>();
                    for (int i = 0; i < DSL_TABLES.size(); i++) {
                        map.put(DSL_TABLES.get(i), values.get(i));
                    }
                    return new Mains(map);
                });
    }

    private static List<NavNode> all(List<NavNode> roots) {
        List<NavNode> found = new ArrayList<>();
        for (NavNode node : roots) {
            found.add(node);
            found.addAll(all(node.items()));
        }
        return found;
    }

    private static int depth(List<NavNode> roots) {
        int deepest = 0;
        for (NavNode node : roots) {
            deepest = Math.max(deepest, 1 + depth(node.items()));
        }
        return deepest;
    }

    /** 位置の道から元の項目を引く。 */
    private static DslMenuItem resolve(List<DslMenuItem> menus, String id) {
        List<DslMenuItem> level = menus;
        DslMenuItem item = null;
        for (String index : id.split("\\.", -1)) {
            item = level.get(Integer.parseInt(index));
            level = item.items();
        }
        return item;
    }

    @Property
    @Label("(a) every navigable item has READ or FULL on its table")
    void navigableItemsAreVisible(@ForAll("menus") List<DslMenuItem> menus, @ForAll("mains") Mains mains) {
        for (NavNode node : all(MenuFilter.filter(menus, mains.input()))) {
            if (node.navigable()) {
                assertThat(mains.of(node.table().schemaName(), node.table().tableName()))
                        .isNotEqualTo(MainPermission.NONE);
            }
        }
    }

    @Property
    @Label("(b) no group is left without children")
    void noEmptyGroups(@ForAll("menus") List<DslMenuItem> menus, @ForAll("mains") Mains mains) {
        assertThat(all(MenuFilter.filter(menus, mains.input())))
                .allSatisfy(node ->
                        assertThat(node.navigable() || !node.items().isEmpty()).isTrue());
    }

    @Property
    @Label("(c) the parent and the order of the remaining items are those of the original tree")
    void parentsAndOrderAreKept(@ForAll("menus") List<DslMenuItem> menus, @ForAll("mains") Mains mains) {
        List<NavNode> roots = MenuFilter.filter(menus, mains.input());
        checkLevel(menus, roots, null);
    }

    private static void checkLevel(List<DslMenuItem> menus, List<NavNode> nodes, String parentId) {
        int previous = -1;
        for (NavNode node : nodes) {
            String prefix = parentId == null ? "" : parentId + ".";
            assertThat(node.id()).startsWith(prefix);
            String rest = node.id().substring(prefix.length());
            assertThat(rest).doesNotContain(".");
            int index = Integer.parseInt(rest);
            assertThat(index).isGreaterThan(previous);
            previous = index;
            DslMenuItem original = resolve(menus, node.id());
            assertThat(node.label()).isEqualTo(DisplayLabel.of(original.label()));
            if (node.navigable()) {
                assertThat(new TableRef(node.table().schemaName(), node.table().tableName()))
                        .isEqualTo(original.table());
            }
            checkLevel(menus, node.items(), node.id());
        }
    }

    @Property
    @Label("(d) when every table is NONE the result is empty")
    void allNoneIsEmpty(@ForAll("menus") List<DslMenuItem> menus) {
        assertThat(MenuFilter.filter(menus, new Mains(Map.of()).input())).isEmpty();
    }

    @Property
    @Label("(e) raising one table from NONE to READ never removes a remaining item")
    void raisingDoesNotRemove(
            @ForAll("menus") List<DslMenuItem> menus,
            @ForAll("mains") Mains mains,
            @ForAll("dslTables") TableRef raised) {
        Map<TableRef, MainPermission> stronger = new HashMap<>(mains.values());
        if (stronger.get(raised) == MainPermission.NONE) {
            stronger.put(raised, MainPermission.READ);
        }
        Set<String> before = ids(MenuFilter.filter(menus, mains.input()));
        Set<String> after = ids(MenuFilter.filter(menus, new Mains(stronger).input()));

        assertThat(after).containsAll(before);
    }

    @Provide
    Arbitrary<TableRef> dslTables() {
        return Arbitraries.of(DSL_TABLES);
    }

    private static Set<String> ids(List<NavNode> roots) {
        Set<String> ids = new HashSet<>();
        for (NavNode node : all(roots)) {
            ids.add(node.id());
        }
        return ids;
    }

    @Property
    @Label("(f) the depth never exceeds the original depth")
    void depthIsNotExceeded(@ForAll("menus") List<DslMenuItem> menus, @ForAll("mains") Mains mains) {
        assertThat(depth(MenuFilter.filter(menus, mains.input()))).isLessThanOrEqualTo(MenuDepth.depth(menus));
    }

    @Property
    @Label("(g) ids are unique within a response")
    void idsAreUnique(@ForAll("menus") List<DslMenuItem> menus, @ForAll("mains") Mains mains) {
        List<NavNode> nodes = all(MenuFilter.filter(menus, mains.input()));

        assertThat(nodes.stream().map(NavNode::id).distinct().count()).isEqualTo(nodes.size());
    }

    @Property
    @Label("(h) an item kept under two different permissions has the same id in both")
    void idsAreStableAcrossPermissions(
            @ForAll("menus") List<DslMenuItem> menus, @ForAll("mains") Mains first, @ForAll("mains") Mains second) {
        Map<String, NavNode> firstNodes = byId(menus, MenuFilter.filter(menus, first.input()));
        Map<String, NavNode> secondNodes = byId(menus, MenuFilter.filter(menus, second.input()));

        for (Map.Entry<String, NavNode> entry : firstNodes.entrySet()) {
            NavNode other = secondNodes.get(entry.getKey());
            if (other != null) {
                assertThat(other.label()).isEqualTo(entry.getValue().label());
                assertThat(other.icon()).isEqualTo(entry.getValue().icon());
            }
        }
    }

    /**
     * 応答の項目を id で引けるようにする。id が元の木での位置を指し、その位置の項目の表示名・アイコンを持つことも確かめる（位置の道が絞った
     * 後の位置に詰まっていれば、権限の違う2つの応答で同じ id が別の項目を指してしまう）。
     */
    private static Map<String, NavNode> byId(List<DslMenuItem> menus, List<NavNode> roots) {
        Map<String, NavNode> map = new HashMap<>();
        for (NavNode node : all(roots)) {
            DslMenuItem original = resolve(menus, node.id());
            assertThat(node.label()).isEqualTo(DisplayLabel.of(original.label()));
            assertThat(node.icon()).isEqualTo(ICONS.iconOf(original.icon()));
            map.put(node.id(), node);
        }
        return map;
    }
}
