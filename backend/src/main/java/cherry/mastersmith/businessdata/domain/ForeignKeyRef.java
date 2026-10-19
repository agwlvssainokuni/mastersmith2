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
package cherry.mastersmith.businessdata.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 違反の後の確かめに使う、写しの DSL の外部キー1つ（Intent 261016-business-data-views の U2、BR6.2）。参照先は同じスキーマのテーブルで、
 * 参照先のカラムは参照先のテーブルの DSL のカラムの一覧と照らしてから引用する（種類ごとの名前の一覧）。
 *
 * @param columns このテーブルのカラム（順は参照先のカラムと対応）
 * @param referencedTable 参照先のテーブル（同じスキーマ）
 * @param referencedColumns 参照先のカラム
 * @param referencedKnownColumns 参照先のテーブルの DSL のカラムの名前（引用してよい名前の一覧）
 */
public record ForeignKeyRef(
        List<String> columns,
        String referencedTable,
        List<String> referencedColumns,
        Set<String> referencedKnownColumns) {

    /** 必須の値を確かめ、一覧を変更できないものにする。 */
    public ForeignKeyRef {
        columns = List.copyOf(columns);
        Objects.requireNonNull(referencedTable, "referencedTable は必須です");
        referencedColumns = List.copyOf(referencedColumns);
        referencedKnownColumns = Set.copyOf(referencedKnownColumns);
        if (columns.isEmpty() || columns.size() != referencedColumns.size()) {
            throw new IllegalArgumentException("外部キーのカラムの数は参照先と同じで、1つ以上です");
        }
    }

    /** カラムの数だけを出す。 */
    @Override
    public String toString() {
        return "ForeignKeyRef[columns=" + columns.size() + "]";
    }
}
