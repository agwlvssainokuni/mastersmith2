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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** メニューの深さの数え方と枝の落とし方の単体テスト（U2 dsl-v2 の BR2.1・BR2.3、NFR1.12・NFR2.9）。 */
class MenuDepthTest {

    static DslMenuItem leaf(String name) {
        return new DslMenuItem(new DisplayName(name, name), null, new TableRef("sales", name), List.of());
    }

    static DslMenuItem group(String name, DslMenuItem... children) {
        return new DslMenuItem(new DisplayName(name, name), null, null, List.of(children));
    }

    /** 1本の枝で深さ depth（最も深い項目だけがテーブルを指す）。 */
    static DslMenuItem chain(int depth) {
        DslMenuItem item = leaf("level" + depth);
        for (int level = depth - 1; level >= 1; level--) {
            item = group("level" + level, item);
        }
        return item;
    }

    @Test
    @org.junit.jupiter.api.DisplayName("each item counts as one level whether it is a group or points to a table")
    void depthCountsEveryItem() {
        assertThat(MenuDepth.depth(List.of())).isZero();
        assertThat(MenuDepth.depth(List.of(leaf("a")))).isEqualTo(1);
        assertThat(MenuDepth.depth(List.of(chain(5)))).isEqualTo(5);
        DslMenuItem tableWithChildren =
                new DslMenuItem(new DisplayName("t", "t"), null, new TableRef("sales", "t"), List.of(leaf("c")));
        assertThat(MenuDepth.depth(List.of(leaf("a"), tableWithChildren))).isEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("over-depth paths list only the first level over the limit, in DSL order")
    void overDepthListsFirstLevelOnly() {
        DslMenuItem fifth = group("level5", leaf("sixA"), group("sixB", leaf("seven")));
        DslMenuItem tree = group("level1", group("level2", group("level3", group("level4", fifth))));

        assertThat(MenuDepth.overDepth(List.of(chain(5)), 5)).isEmpty();
        assertThat(MenuDepth.overDepth(List.of(leaf("x"), tree), 5))
                .containsExactly(List.of(1, 0, 0, 0, 0, 0), List.of(1, 0, 0, 0, 0, 1));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a tree within the limit is returned as it is with nothing pruned")
    void withinLimitIsUnchanged() {
        List<DslMenuItem> menus = List.of(chain(5), leaf("x"));

        MenuDepth.Pruned pruned = MenuDepth.prune(menus, 5);

        assertThat(pruned.prunedCount()).isZero();
        assertThat(pruned.menus()).isEqualTo(menus);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a sixth level item is pruned with its children and they are all counted")
    void sixthLevelIsPrunedWithChildren() {
        DslMenuItem fifth = new DslMenuItem(
                new DisplayName("level5", "level5"),
                null,
                new TableRef("sales", "five"),
                List.of(group("six", leaf("sevenA"), leaf("sevenB"))));
        DslMenuItem tree = group("level1", group("level2", group("level3", group("level4", fifth))));

        MenuDepth.Pruned pruned = MenuDepth.prune(List.of(tree), 5);

        assertThat(pruned.prunedCount()).isEqualTo(3);
        assertThat(MenuDepth.depth(pruned.menus())).isEqualTo(5);
        DslMenuItem keptFifth = pruned.menus()
                .getFirst()
                .items()
                .getFirst()
                .items()
                .getFirst()
                .items()
                .getFirst()
                .items()
                .getFirst();
        assertThat(keptFifth.table()).as("the fifth level keeps its table").isEqualTo(new TableRef("sales", "five"));
        assertThat(keptFifth.items()).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("groups left without a table or children are pruned upwards and counted")
    void emptyGroupsArePrunedUpwards() {
        DslMenuItem tree = group("level1", leaf("keep"), group("level2", chain(5)));

        MenuDepth.Pruned pruned = MenuDepth.prune(List.of(tree, chain(7)), 5);

        // chain(7): 6・7 段目の2つと、子を失った 1〜5 段目のまとまり5つ。group level2 の下の chain(5) は 3〜7 段目で、6・7 段目の
        // 2つと、子を失った 3〜5 段目のまとまり3つと level2 自身の1つ（level1 は keep が残るため残る）。
        assertThat(pruned.prunedCount()).isEqualTo(7 + 6);
        assertThat(pruned.menus()).singleElement().satisfies(kept -> {
            assertThat(kept.label().ja()).isEqualTo("level1");
            assertThat(kept.items()).extracting(item -> item.label().ja()).containsExactly("keep");
        });
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a limit below 1 is a programming error")
    void limitBelowOne() {
        assertThatThrownBy(() -> MenuDepth.prune(List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MenuDepth.overDepth(List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MenuDepth.Pruned(List.of(), -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
