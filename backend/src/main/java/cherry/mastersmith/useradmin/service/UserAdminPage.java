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
package cherry.mastersmith.useradmin.service;

import java.util.List;

/**
 * 利用者の一覧の1ページ（Intent 260930-user-admin の U3、契約 C3 の AdminUserPage、BR1.1・BR1.8）。
 *
 * @param items 行（最後のページより後では空）
 * @param page 受けたページの番号
 * @param size 1ページの件数（20）
 * @param total 検索の条件に当たる全体の件数
 */
public record UserAdminPage(List<UserAdminEntry> items, int page, int size, long total) {

    /** 一覧を変えられない写しにする。 */
    public UserAdminPage {
        items = List.copyOf(items);
    }
}
