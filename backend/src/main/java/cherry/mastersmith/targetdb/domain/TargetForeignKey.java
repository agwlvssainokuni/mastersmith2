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

import java.util.List;

/**
 * 外部キー1つ（契約 C1 の {@code TargetForeignKey}）。参照先は同じスキーマのテーブルだけで、別のスキーマを参照する外部キーは
 * 写しに含めない（BR2.6）。
 *
 * @param name 制約の名前（無ければ null）
 * @param columns 参照元のカラム名（外部キーの中の順、1件以上）
 * @param referencedTable 参照先のテーブル名
 * @param referencedColumns 参照先のカラム名（{@code columns} と同じ件数・同じ順）
 */
public record TargetForeignKey(
        String name, List<String> columns, String referencedTable, List<String> referencedColumns) {

    /** 必須の値と件数の一致を確かめ、一覧を変更できないものにする。 */
    public TargetForeignKey {
        columns = DomainValues.copyNames(columns, "foreignKey.columns");
        DomainValues.requireName(referencedTable, "foreignKey.referencedTable");
        referencedColumns = DomainValues.copyNames(referencedColumns, "foreignKey.referencedColumns");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("foreignKey.columns は1件以上です");
        }
        if (columns.size() != referencedColumns.size()) {
            throw new IllegalArgumentException("foreignKey.columns と referencedColumns の件数が一致しません");
        }
    }
}
