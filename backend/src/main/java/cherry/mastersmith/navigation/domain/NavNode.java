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

import java.util.List;
import java.util.Objects;

/**
 * 絞った後のメニューの項目1つ（{@code entities.md} の ENT-002、BR2.3・BR3.1・BR3.5）。
 *
 * <p>形の決まり: 移れる項目（{@code navigable} が true）だけが {@code table} を持ち、移れない項目（まとまり）は子を1つ以上持つ（子の無い
 * まとまりを作れない）。
 *
 * @param id 絞る前の DSL の木での位置の道（例 {@code 2.0}。1文字以上）
 * @param label 表示名の組
 * @param icon 照らした後のアイコンの名前（1文字以上）
 * @param table 移れる項目のテーブルの組（移れない項目は null）
 * @param navigable テーブルを指し、実効の主権限が READ・FULL のとき true
 * @param items 子（DSL の順。移れる項目では空を許す）
 */
public record NavNode(
        String id, DisplayLabel label, String icon, NavTableRef table, boolean navigable, List<NavNode> items) {

    /** 必須の値と形の決まりを確かめ、子を変更できない一覧にする。 */
    public NavNode {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id は必須です");
        }
        Objects.requireNonNull(label, "label は必須です");
        if (icon == null || icon.isEmpty()) {
            throw new IllegalArgumentException("icon は必須です");
        }
        items = List.copyOf(Objects.requireNonNull(items, "items は必須です"));
        if (navigable != (table != null)) {
            throw new IllegalArgumentException("table は移れる項目だけが持ちます");
        }
        if (!navigable && items.isEmpty()) {
            throw new IllegalArgumentException("移れない項目（まとまり）は子を1つ以上持ちます");
        }
    }

    /** 位置の道・移れるか・子の数だけを出す（1,000 項目の木を毎回文字列にしないため。計画の D-6）。 */
    @Override
    public String toString() {
        return "NavNode[id=" + id + ", navigable=" + navigable + ", items=" + items.size() + "]";
    }
}
