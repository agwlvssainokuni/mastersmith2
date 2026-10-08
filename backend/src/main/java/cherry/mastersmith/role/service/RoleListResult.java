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
package cherry.mastersmith.role.service;

import java.util.List;
import java.util.Objects;

/** ロールの一覧の結果（FS の 2.4、BR3.4・BR3.5）。 */
public sealed interface RoleListResult permits RoleListResult.Listed, RoleListResult.InvalidPage {

    /**
     * 1ページ。
     *
     * @param page ページ
     */
    record Listed(Page page) implements RoleListResult {

        /** 値が null でないことを確かめる。 */
        public Listed {
            Objects.requireNonNull(page, "page");
        }
    }

    /** {@code page} が 1 以上の整数でない（400。監査なし）。 */
    record InvalidPage() implements RoleListResult {}

    /**
     * 一覧の1ページ（BR3.5。size は 20 に固定）。
     *
     * @param items 行（ロールの ID の順）
     * @param page ページ
     * @param size 1ページの件数
     * @param total 全体の件数
     */
    record Page(List<Row> items, int page, int size, long total) {

        /** 行を写して持つ。 */
        public Page {
            items = List.copyOf(items);
        }

        /** 件数だけを出す（D-5）。 */
        @Override
        public String toString() {
            return "Page[items=" + items.size() + ", page=" + page + ", total=" + total + "]";
        }
    }

    /**
     * 一覧の1行（BR3.4。数は直接の割り当ての数で、グループ経由の利用者は数えない）。
     *
     * @param roleId ロールの ID
     * @param name 名前
     * @param userCount 利用者への直接の割り当ての数
     * @param groupCount グループへの割り当ての数
     */
    record Row(long roleId, String name, long userCount, long groupCount) {

        /** 名前が null でないことを確かめる。 */
        public Row {
            Objects.requireNonNull(name, "name");
        }
    }
}
