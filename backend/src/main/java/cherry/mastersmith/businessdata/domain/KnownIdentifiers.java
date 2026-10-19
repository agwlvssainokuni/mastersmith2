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

import java.util.Objects;
import java.util.Set;

/**
 * SQL の部品が引用してよい名前の一覧（entities.md の KnownIdentifiers、BR3.3）。スキーマ・テーブル・カラムの種類ごとに別の集合で
 * 持ち、種類をまたいで照合を通さない。どれも写しの DSL の名前だけ。
 *
 * @param schemas 写しの DSL のスキーマ名
 * @param tables 照らしたスキーマのテーブル名
 * @param columns 照らしたテーブルのカラム名（ほかのテーブルのカラムを含めない）
 */
public record KnownIdentifiers(Set<String> schemas, Set<String> tables, Set<String> columns) {

    /** 集合を変更できないものにする。 */
    public KnownIdentifiers {
        schemas = Set.copyOf(Objects.requireNonNull(schemas, "schemas は必須です"));
        tables = Set.copyOf(Objects.requireNonNull(tables, "tables は必須です"));
        columns = Set.copyOf(Objects.requireNonNull(columns, "columns は必須です"));
    }

    /** 集合の大きさだけを出す（名前を出さない）。 */
    @Override
    public String toString() {
        return "KnownIdentifiers[schemas=" + schemas.size() + ", tables=" + tables.size() + ", columns="
                + columns.size() + "]";
    }
}
