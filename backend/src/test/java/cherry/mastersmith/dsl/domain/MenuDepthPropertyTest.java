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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

/**
 * メニューの深さと枝の落とし方の性質ベースのテスト（U2 dsl-v2 の BR2.1・BR2.3、NFR6.6）。任意の深さ（8 段まで）の木で確かめる。
 * 失敗したときの乱数の種は jqwik の報告に出る（{@code @Property(seed = "...")} に与えて再現する）。
 */
class MenuDepthPropertyTest {

    private static final int LIMIT = DslFormat.MAX_MENU_DEPTH;

    @Provide
    Arbitrary<List<DslMenuItem>> menuTrees() {
        return items(8).list().ofMaxSize(3).map(MenuDepthPropertyTest::numbered);
    }

    private static Arbitrary<DslMenuItem> items(int remaining) {
        Arbitrary<DslMenuItem> leaf = Arbitraries.just(
                new DslMenuItem(new DisplayName("x", "x"), null, new TableRef("sales", "t"), List.of()));
        if (remaining <= 1) {
            return leaf;
        }
        Arbitrary<DslMenuItem> branch = Combinators.combine(
                        Arbitraries.lazy(() -> items(remaining - 1))
                                .list()
                                .ofMinSize(1)
                                .ofMaxSize(2),
                        Arbitraries.of(true, false))
                .as((children, hasTable) -> new DslMenuItem(
                        new DisplayName("x", "x"), null, hasTable ? new TableRef("sales", "t") : null, children));
        return Arbitraries.frequencyOf(Tuple.of(1, leaf), Tuple.of(3, branch));
    }

    /** 項目の表示名を DSL の順（深さ優先）の通し番号にして、項目を見分けられるようにする。 */
    private static List<DslMenuItem> numbered(List<DslMenuItem> menus) {
        int[] next = {0};
        return renumber(menus, next);
    }

    private static List<DslMenuItem> renumber(List<DslMenuItem> items, int[] next) {
        List<DslMenuItem> result = new ArrayList<>();
        for (DslMenuItem item : items) {
            String name = "n" + next[0]++;
            result.add(new DslMenuItem(new DisplayName(name, name), null, item.table(), renumber(item.items(), next)));
        }
        return result;
    }

    private static int count(List<DslMenuItem> items) {
        int total = 0;
        for (DslMenuItem item : items) {
            total += 1 + count(item.items());
        }
        return total;
    }

    /** 残った木の各項目が、元の木の同じ親の下に同じ順で並んでいるか（元の木の部分の木か）。 */
    private static boolean keepsParentsAndOrder(List<DslMenuItem> kept, List<DslMenuItem> original) {
        int position = 0;
        for (DslMenuItem item : kept) {
            while (position < original.size() && !original.get(position).label().equals(item.label())) {
                position++;
            }
            if (position == original.size()) {
                return false;
            }
            DslMenuItem source = original.get(position++);
            if (!Objects.equals(source.table(), item.table()) || !keepsParentsAndOrder(item.items(), source.items())) {
                return false;
            }
        }
        return true;
    }

    private static boolean noEmptyGroup(List<DslMenuItem> items) {
        for (DslMenuItem item : items) {
            if ((item.table() == null && item.items().isEmpty()) || !noEmptyGroup(item.items())) {
                return false;
            }
        }
        return true;
    }

    @Property(tries = 300)
    @Label("the pruned tree is at most the limit deep and has no over-depth items")
    void prunedTreeIsWithinLimit(@ForAll("menuTrees") List<DslMenuItem> menus) {
        MenuDepth.Pruned pruned = MenuDepth.prune(menus, LIMIT);

        assertThat(MenuDepth.depth(pruned.menus())).isLessThanOrEqualTo(LIMIT);
        assertThat(MenuDepth.overDepth(pruned.menus(), LIMIT)).isEmpty();
    }

    @Property(tries = 300)
    @Label("remaining items keep their parents and order of the original tree")
    void remainingItemsKeepParentsAndOrder(@ForAll("menuTrees") List<DslMenuItem> menus) {
        assertThat(keepsParentsAndOrder(MenuDepth.prune(menus, LIMIT).menus(), menus))
                .isTrue();
    }

    @Property(tries = 300)
    @Label("no group is left without a table or children")
    void noGroupWithoutTableOrChildren(@ForAll("menuTrees") List<DslMenuItem> menus) {
        assertThat(noEmptyGroup(MenuDepth.prune(menus, LIMIT).menus())).isTrue();
    }

    @Property(tries = 300)
    @Label("pruned count plus remaining count equals the original count")
    void countsAddUp(@ForAll("menuTrees") List<DslMenuItem> menus) {
        MenuDepth.Pruned pruned = MenuDepth.prune(menus, LIMIT);

        assertThat(pruned.prunedCount() + count(pruned.menus())).isEqualTo(count(menus));
    }

    @Property(tries = 300)
    @Label("a tree within the limit is unchanged, and something is pruned exactly when an item is over the limit")
    void withinLimitIsUnchanged(@ForAll("menuTrees") List<DslMenuItem> menus) {
        MenuDepth.Pruned pruned = MenuDepth.prune(menus, LIMIT);
        boolean over = !MenuDepth.overDepth(menus, LIMIT).isEmpty();

        assertThat(over).isEqualTo(MenuDepth.depth(menus) > LIMIT);
        assertThat(pruned.prunedCount() > 0).isEqualTo(over);
        if (!over) {
            assertThat(pruned.menus()).isEqualTo(menus);
        }
    }
}
