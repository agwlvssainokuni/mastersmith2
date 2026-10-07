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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * メニューの深さの数え方と、深すぎる枝の落とし方（U2 dsl-v2 の BR2.1・BR2.3、NFR 設計の 4.5）。木を1回たどるだけの純粋な関数。
 *
 * <p>使い方:
 *
 * <ul>
 *   <li>深さは {@code menus} の直下の項目を 1 段目とし、まとまりかテーブルを指す項目かによらず項目1つを1段と数える。
 *   <li>{@link #overDepth} は、上限を超えた最初の段（上限＋1 段目）の項目の道を返す。その下の子孫は返さない（誤りを重ねない）。
 *       意味の検証（JSON の木）と、モデル（{@link DslMenuItem} の木）の両方に使える。
 *   <li>{@link #prune} は、上限を超えた項目をその子ごと落とし、落とした結果テーブルも子も持たなくなったまとまりを上へさかのぼって
 *       落とす（{@link DslMenuItem} の「テーブルか子の少なくとも一方を持つ」決まりを保つ）。起動時の読み直しだけが使う。
 * </ul>
 *
 * <p>入れ子は YAML の深さの上限（{@link DslFormat#MAX_DEPTH}）の内に収まるため、再帰でたどってもスタックを使い尽くさない。
 */
public final class MenuDepth {

    private MenuDepth() {}

    /**
     * 上限を超えた最初の段の項目の道を返す（DSL の順）。
     *
     * @param <T> 項目の型
     * @param roots 最上位の項目
     * @param children 項目から子の一覧を取り出す処理
     * @param limit 深さの上限（1 以上）
     * @return 道の一覧。道は最上位からの位置（0 から）の並び（例 {@code [0, 2]} は {@code menus[0].items[2]}）
     */
    public static <T> List<List<Integer>> overDepth(List<T> roots, Function<T, List<T>> children, int limit) {
        requireLimit(limit);
        List<List<Integer>> found = new ArrayList<>();
        collect(roots, children, 1, limit, new ArrayList<>(), found);
        return found;
    }

    /**
     * モデルのメニューで、上限を超えた最初の段の項目の道を返す。
     *
     * @param menus 最上位のメニュー
     * @param limit 深さの上限（1 以上）
     * @return 道の一覧（{@link #overDepth(List, Function, int)} と同じ形）
     */
    public static List<List<Integer>> overDepth(List<DslMenuItem> menus, int limit) {
        return overDepth(menus, DslMenuItem::items, limit);
    }

    /**
     * メニューの木の深さ（最も深い項目の段の数。項目が無ければ 0）を返す。
     *
     * @param menus 最上位のメニュー
     * @return 深さ
     */
    public static int depth(List<DslMenuItem> menus) {
        int deepest = 0;
        for (DslMenuItem item : menus) {
            deepest = Math.max(deepest, 1 + depth(item.items()));
        }
        return deepest;
    }

    /**
     * 深すぎる枝を落とした木と、落とした項目の数を返す。上限の内の木はそのまま返す（落とした数 0）。
     *
     * @param menus 最上位のメニュー
     * @param limit 深さの上限（1 以上）
     * @return 落とした後の木と落とした数
     */
    public static Pruned prune(List<DslMenuItem> menus, int limit) {
        requireLimit(limit);
        int[] dropped = {0};
        List<DslMenuItem> kept = pruneLevel(menus, 1, limit, dropped);
        return dropped[0] == 0 ? new Pruned(menus, 0) : new Pruned(kept, dropped[0]);
    }

    private static <T> void collect(
            List<T> items,
            Function<T, List<T>> children,
            int depth,
            int limit,
            List<Integer> path,
            List<List<Integer>> found) {
        for (int i = 0; i < items.size(); i++) {
            path.add(i);
            if (depth > limit) {
                found.add(List.copyOf(path));
            } else {
                collect(children.apply(items.get(i)), children, depth + 1, limit, path, found);
            }
            path.removeLast();
        }
    }

    private static List<DslMenuItem> pruneLevel(List<DslMenuItem> items, int depth, int limit, int[] dropped) {
        List<DslMenuItem> kept = new ArrayList<>();
        for (DslMenuItem item : items) {
            if (depth > limit) {
                dropped[0] += count(item);
                continue;
            }
            List<DslMenuItem> children = pruneLevel(item.items(), depth + 1, limit, dropped);
            if (item.table() == null && children.isEmpty()) {
                // 子をすべて落としたまとまりは、テーブルも子も持たなくなるため、さかのぼって落とす。
                dropped[0]++;
                continue;
            }
            kept.add(
                    sameItems(children, item.items())
                            ? item
                            : new DslMenuItem(item.label(), item.icon(), item.table(), children));
        }
        return kept;
    }

    /** 子を1つも落とさず、作り直してもいない（同じ項目がそのまま並ぶ）かを判定する。 */
    private static boolean sameItems(List<DslMenuItem> kept, List<DslMenuItem> original) {
        if (kept.size() != original.size()) {
            return false;
        }
        for (int i = 0; i < kept.size(); i++) {
            if (kept.get(i) != original.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static int count(DslMenuItem item) {
        int total = 1;
        for (DslMenuItem child : item.items()) {
            total += count(child);
        }
        return total;
    }

    private static void requireLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("深さの上限は 1 以上です");
        }
    }

    /**
     * 深すぎる枝を落とした結果。
     *
     * @param menus 落とした後の最上位のメニュー
     * @param prunedCount 落とした項目の数（子孫とさかのぼって落としたまとまりを含む）
     */
    public record Pruned(List<DslMenuItem> menus, int prunedCount) {

        /** 一覧を変更できないものにし、数が 0 以上であることを確かめる。 */
        public Pruned {
            menus = List.copyOf(Objects.requireNonNull(menus, "menus は必須です"));
            if (prunedCount < 0) {
                throw new IllegalArgumentException("prunedCount は 0 以上です");
            }
        }
    }
}
