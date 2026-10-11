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
package cherry.mastersmith.navigation.web;

import cherry.mastersmith.navigation.domain.NavNode;
import java.util.List;

/**
 * メニューの項目1つの応答（{@code entities.md} の ENT-002、BR3.4）。DSL の識別・ロールの ID・権限の値（{@code navigable} 以外）を載せない。
 *
 * @param id 絞る前の DSL の木での位置の道
 * @param label 表示名の組
 * @param icon 照らした後のアイコンの名前
 * @param table 移れる項目のテーブルの組（移れない項目は null）
 * @param navigable 移れるか
 * @param items 子
 */
public record NavNodeResponse(
        String id,
        DisplayLabelResponse label,
        String icon,
        NavTableRefResponse table,
        boolean navigable,
        List<NavNodeResponse> items) {

    /**
     * 項目から作る（子も同じく写す）。
     *
     * @param node 項目
     * @return 応答
     */
    public static NavNodeResponse from(NavNode node) {
        return new NavNodeResponse(
                node.id(),
                DisplayLabelResponse.from(node.label()),
                node.icon(),
                NavTableRefResponse.from(node.table()),
                node.navigable(),
                node.items().stream().map(NavNodeResponse::from).toList());
    }

    /** 位置の道・移れるか・子の数だけを出す（木を毎回文字列にしないため。計画の D-6）。 */
    @Override
    public String toString() {
        return "NavNodeResponse[id=" + id + ", navigable=" + navigable + ", items=" + items.size() + "]";
    }
}
