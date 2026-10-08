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
package cherry.mastersmith.role.web;

import cherry.mastersmith.role.service.RoleListResult;
import java.util.List;

/**
 * 一覧の1ページの応答（契約 C7 の RolePage、BR3.5。size は 20 に固定）。
 *
 * @param items 行（ロールの ID の順）
 * @param page ページ
 * @param size 1ページの件数
 * @param total 全体の件数
 */
public record RolePageResponse(List<RoleRowResponse> items, int page, int size, long total) {

    /** 行を写して持つ。 */
    public RolePageResponse {
        items = List.copyOf(items);
    }

    /**
     * 業務処理の1ページから作る。
     *
     * @param page 1ページ
     * @return 応答
     */
    static RolePageResponse from(RoleListResult.Page page) {
        return new RolePageResponse(
                page.items().stream().map(RoleRowResponse::from).toList(), page.page(), page.size(), page.total());
    }
}
