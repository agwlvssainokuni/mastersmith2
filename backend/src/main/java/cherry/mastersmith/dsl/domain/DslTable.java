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
import java.util.Map;
import java.util.Objects;

/**
 * テーブルまたはビュー1つの定義。
 *
 * @param name 物理名
 * @param label 表示名
 * @param view ビューなら true
 * @param primaryKey 主キーのカラムの物理名（無ければ空）
 * @param foreignKeys 外部キー（無ければ空）
 * @param columns カラム（キーは物理名、1件以上。DSL の順を保つ）
 */
public record DslTable(
        String name,
        DisplayName label,
        boolean view,
        List<String> primaryKey,
        List<DslForeignKey> foreignKeys,
        Map<String, DslColumn> columns) {

    /** 必須の値を確かめ、一覧と対応表を変更できないもの（対応表は DSL の順を保つ）にする。 */
    public DslTable {
        ModelValues.requireName(name, "table.name");
        Objects.requireNonNull(label, "table.label は必須です");
        primaryKey = ModelValues.copyNames(primaryKey, "table.primaryKey");
        foreignKeys = ModelValues.copyList(foreignKeys, "table.foreignKeys");
        columns = ModelValues.copyNamedMap(columns, DslColumn::name, "table.columns");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("table.columns は1件以上です");
        }
    }
}
