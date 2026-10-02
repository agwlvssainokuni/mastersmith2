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
package cherry.mastersmith.user.service;

import java.util.List;

/**
 * 利用者の一覧の1ページの読み出しの結果（Intent 260930-user-admin の U3、契約 C8 の findAdminPage の戻り値）。
 *
 * @param items 利用者の要約（0〜件数。並びは登録した日時・利用者 ID の順）
 * @param total 検索の条件に当たる全体の件数
 */
public record UserAdminSlice(List<UserAdminSummary> items, long total) {

    /** 一覧を変えられない写しにする。 */
    public UserAdminSlice {
        items = List.copyOf(items);
        if (total < 0) {
            throw new IllegalArgumentException("件数は 0 以上です: " + total);
        }
    }
}
