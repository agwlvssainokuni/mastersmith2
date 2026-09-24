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
package cherry.mastersmith.dsl.domain;

import java.util.List;
import java.util.Objects;

/**
 * メニューの項目（N 階層）。テーブルに紐付く項目か、子を持つまとまり（少なくとも一方を持つ。BR3.2）。
 *
 * @param label 表示名
 * @param icon アイコンの名前（無ければ null）
 * @param table 紐付くテーブルの物理名（無ければ null）
 * @param items 子の項目（無ければ空。DSL の順）
 */
public record DslMenuItem(DisplayName label, String icon, String table, List<DslMenuItem> items) {

    /** 必須の値と、テーブルか子の少なくとも一方を持つことを確かめ、子を変更できない一覧にする。 */
    public DslMenuItem {
        Objects.requireNonNull(label, "menu.label は必須です");
        if (table != null) {
            ModelValues.requireName(table, "menu.table");
        }
        items = ModelValues.copyList(items, "menu.items");
        if (table == null && items.isEmpty()) {
            throw new IllegalArgumentException("メニューの項目はテーブルか子の少なくとも一方を持ちます");
        }
    }
}
