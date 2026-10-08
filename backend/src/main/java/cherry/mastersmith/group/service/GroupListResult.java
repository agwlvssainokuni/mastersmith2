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
package cherry.mastersmith.group.service;

import java.util.List;
import java.util.Objects;

/** グループの一覧の結果（FS の 2.6、BR7.1〜BR7.3）。 */
public sealed interface GroupListResult permits GroupListResult.Listed, GroupListResult.InvalidPage {

    /**
     * 1ページ。
     *
     * @param page ページ
     */
    record Listed(Page page) implements GroupListResult {

        /** 値が null でないことを確かめる。 */
        public Listed {
            Objects.requireNonNull(page, "page");
        }
    }

    /** {@code page} が 1 以上の整数でない（400。監査なし）。 */
    record InvalidPage() implements GroupListResult {}

    /**
     * 一覧の1ページ（BR7.1。size は 20 に固定）。
     *
     * @param items 行（グループの ID の順）
     * @param page ページ
     * @param size 1ページの件数
     * @param total 全体の件数
     */
    record Page(List<Row> items, int page, int size, long total) {

        /** 行を写して持つ。 */
        public Page {
            items = List.copyOf(items);
        }
    }

    /**
     * 一覧の1行（BR7.3）。
     *
     * @param groupId グループの ID
     * @param name 名前
     * @param memberCount メンバーの数
     * @param assignedRoleCount 割り当てたロールの数
     */
    record Row(long groupId, String name, long memberCount, int assignedRoleCount) {

        /** 名前が null でないことを確かめる。 */
        public Row {
            Objects.requireNonNull(name, "name");
        }
    }
}
