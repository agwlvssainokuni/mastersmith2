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
 * 業務のメニューの業務処理の結果（{@code entities.md} の ENT-001、BR3.3）。{@code emptyReason} は {@code items} が空のときだけ値を持つ。
 *
 * @param items 絞った後の木（DSL の順）
 * @param emptyReason 空の理由（{@code items} が空でなければ null）
 */
public record NavigationMenu(List<NavNode> items, EmptyReason emptyReason) {

    /** 一覧を変更できないものにし、空の理由が項目の有無と合うことを確かめる。 */
    public NavigationMenu {
        items = List.copyOf(Objects.requireNonNull(items, "items は必須です"));
        if (items.isEmpty() != (emptyReason != null)) {
            throw new IllegalArgumentException("emptyReason は items が空のときだけ値を持ちます");
        }
    }

    /**
     * 絞った結果から作る（空なら NOTHING_VISIBLE）。
     *
     * @param items 絞った後の木
     * @return 結果
     */
    public static NavigationMenu filtered(List<NavNode> items) {
        return new NavigationMenu(items, items.isEmpty() ? EmptyReason.NOTHING_VISIBLE : null);
    }

    /**
     * DSL が無い・menus が空のときの結果を作る。
     *
     * @return 空の木と NOT_CONFIGURED
     */
    public static NavigationMenu notConfigured() {
        return new NavigationMenu(List.of(), EmptyReason.NOT_CONFIGURED);
    }

    /** 項目の数と空の理由だけを出す（1,000 項目の木を毎回文字列にしないため。計画の D-6）。 */
    @Override
    public String toString() {
        return "NavigationMenu[items=" + items.size() + ", emptyReason=" + emptyReason + "]";
    }
}
