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

import cherry.mastersmith.navigation.domain.NavigationMenu;
import java.util.List;

/**
 * 業務のメニューの API の応答（{@code entities.md} の ENT-001、BR3.3）。
 *
 * @param items 作業ロールの実効の主権限で絞った木（DSL の順。空を許す）
 * @param emptyReason 空の理由（{@code NOT_CONFIGURED}・{@code NOTHING_VISIBLE}。{@code items} が空でなければ null）
 */
public record NavigationResponse(List<NavNodeResponse> items, String emptyReason) {

    /**
     * 業務処理の結果から作る。
     *
     * @param menu 業務処理の結果
     * @return 応答
     */
    public static NavigationResponse from(NavigationMenu menu) {
        return new NavigationResponse(
                menu.items().stream().map(NavNodeResponse::from).toList(),
                menu.emptyReason() == null ? null : menu.emptyReason().name());
    }

    /** 項目の数と空の理由だけを出す（1,000 項目の木を毎回文字列にしないため。計画の D-6）。 */
    @Override
    public String toString() {
        return "NavigationResponse[items=" + items.size() + ", emptyReason=" + emptyReason + "]";
    }
}
