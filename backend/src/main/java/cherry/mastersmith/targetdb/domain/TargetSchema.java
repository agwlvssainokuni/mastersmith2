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
import java.util.Optional;
import java.util.Set;

/**
 * 対象DB の設定したスキーマの写し（契約 C1 の {@code TargetSchema}）。読み取りのたびに作り、保存しない。接続先・資格情報は
 * 持たない。
 *
 * @param databaseProduct DB の種類
 * @param schemaName 設定したスキーマの名前（DB が返した大文字・小文字のまま）
 * @param tables テーブルとビュー（0件もありうる。並びは問わない）
 */
public record TargetSchema(DatabaseProduct databaseProduct, String schemaName, List<TargetTable> tables) {

    /** テーブルの名前の重なりを確かめ、一覧を変更できないものにする。 */
    public TargetSchema {
        Objects.requireNonNull(databaseProduct, "databaseProduct は必須です");
        DomainValues.requireName(schemaName, "schemaName");
        tables = List.copyOf(Objects.requireNonNull(tables, "tables は必須です"));
        Set<String> names = new HashSet<>();
        for (TargetTable table : tables) {
            if (!names.add(table.name())) {
                throw new IllegalArgumentException("tables の名前が重なっています");
            }
        }
    }

    /**
     * 名前でテーブル（ビューを含む）を探す。名前は大文字・小文字を区別する（BR2.3）。
     *
     * @param name 物理名
     * @return テーブル（無ければ空）
     */
    public Optional<TargetTable> table(String name) {
        return tables.stream().filter(table -> table.name().equals(name)).findFirst();
    }
}
