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
package cherry.mastersmith.useradmin.web;

import cherry.mastersmith.useradmin.service.UserAdminPage;
import java.util.List;

/**
 * 利用者の一覧の応答（契約 C3 の AdminUserPage、BR1.1・BR1.8）。文字列にすると行の数だけを出す（行の値を出さない）。
 *
 * @param items 行（最後のページより後では空）
 * @param page 受けたページの番号
 * @param size 1ページの件数（20）
 * @param total 検索の条件に当たる全体の件数
 */
public record AdminUserPage(List<AdminUser> items, int page, int size, long total) {

    /** 一覧を変えられない写しにする。 */
    public AdminUserPage {
        items = List.copyOf(items);
    }

    /**
     * 業務処理の1ページから作る。
     *
     * @param page 1ページ
     * @return 応答
     */
    static AdminUserPage from(UserAdminPage page) {
        return new AdminUserPage(
                page.items().stream().map(AdminUser::from).toList(), page.page(), page.size(), page.total());
    }

    /** 行の値を出さずに文字列にする。 */
    @Override
    public String toString() {
        return "AdminUserPage[items=" + items.size() + ", page=" + page + ", size=" + size + ", total=" + total + "]";
    }
}
