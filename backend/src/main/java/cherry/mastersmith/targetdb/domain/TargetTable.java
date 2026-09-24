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
package cherry.mastersmith.targetdb.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * テーブルまたはビュー1つ（契約 C1 の {@code TargetTable}）。
 *
 * @param name 物理名（DB が返した大文字・小文字のまま。BR2.3）
 * @param view ビューなら true
 * @param comment コメント（無ければ null。空の文字列・空白だけは null に揃える。BR2.5）
 * @param columns カラム（定義の順、1件以上）
 * @param primaryKey 主キーのカラム名（主キーの中の順。無ければ空。ビューは空）
 * @param foreignKeys 外部キー（無ければ空）
 */
public record TargetTable(
        String name,
        boolean view,
        String comment,
        List<TargetColumn> columns,
        List<String> primaryKey,
        List<TargetForeignKey> foreignKeys) {

    /** カラムの名前の重なりと、主キー・外部キーのカラムがカラムにあることを確かめ、一覧を変更できないものにする。 */
    public TargetTable {
        DomainValues.requireName(name, "table.name");
        comment = DomainValues.normalizeComment(comment);
        columns = List.copyOf(Objects.requireNonNull(columns, "table.columns は必須です"));
        primaryKey = DomainValues.copyNames(primaryKey, "table.primaryKey");
        foreignKeys = List.copyOf(Objects.requireNonNull(foreignKeys, "table.foreignKeys は必須です"));
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("table.columns は1件以上です");
        }
        Set<String> names = new HashSet<>();
        for (TargetColumn column : columns) {
            if (!names.add(column.name())) {
                throw new IllegalArgumentException("table.columns の名前が重なっています");
            }
        }
        if (view && !primaryKey.isEmpty()) {
            throw new IllegalArgumentException("ビューは主キーを持ちません");
        }
        requireKnown(names, primaryKey, "table.primaryKey");
        for (TargetForeignKey foreignKey : foreignKeys) {
            requireKnown(names, foreignKey.columns(), "table.foreignKeys.columns");
        }
    }

    private static void requireKnown(Set<String> names, List<String> keyColumns, String field) {
        if (new HashSet<>(keyColumns).size() != keyColumns.size()) {
            throw new IllegalArgumentException(field + " のカラムが重なっています");
        }
        if (!names.containsAll(keyColumns)) {
            throw new IllegalArgumentException(field + " のカラムがテーブルのカラムにありません");
        }
    }
}
