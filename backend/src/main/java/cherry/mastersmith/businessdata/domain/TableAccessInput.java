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

import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 出し分けの関数（{@link TableAccessPolicy}）の入力。権限の写しとその写しの DSL、DB の種類から作る（BR2・BR3.1）。
 *
 * @param schema 照らしたスキーマ名
 * @param table 照らしたテーブルの DSL の定義
 * @param definitionId 今の定義の識別
 * @param tableMain テーブルの実効の主権限
 * @param canCreate 補助権限 CREATE
 * @param canDelete 補助権限 DELETE
 * @param columnMain カラムの名前ごとの実効の主権限（無いカラムは NONE として扱う）
 * @param kinds カラムの名前ごとの受け渡しの型（無いカラムは OTHER・完全一致に使えない として扱う）
 * @param writeConfigured 書き込みの接続の設定があるか
 * @param knownIdentifiers SQL の部品に渡す名前の一覧
 * @param referencedColumns 外部キーの参照先のテーブル（同じスキーマ）ごとの DSL のカラムの名前（U2 の違反の後の確かめに使う。無い参照先の
 *     外部キーは確かめに使わない）
 */
public record TableAccessInput(
        String schema,
        DslTable table,
        DefinitionId definitionId,
        MainPermission tableMain,
        boolean canCreate,
        boolean canDelete,
        Map<String, MainPermission> columnMain,
        Map<String, ColumnKind> kinds,
        boolean writeConfigured,
        KnownIdentifiers knownIdentifiers,
        Map<String, Set<String>> referencedColumns) {

    /** 必須の値を確かめ、対応表を変更できないものにする。 */
    public TableAccessInput {
        Objects.requireNonNull(schema, "schema は必須です");
        Objects.requireNonNull(table, "table は必須です");
        Objects.requireNonNull(definitionId, "definitionId は必須です");
        Objects.requireNonNull(tableMain, "tableMain は必須です");
        Objects.requireNonNull(knownIdentifiers, "knownIdentifiers は必須です");
        columnMain = Map.copyOf(columnMain);
        kinds = Map.copyOf(kinds);
        Map<String, Set<String>> copied = new HashMap<>();
        (referencedColumns == null ? Map.<String, Set<String>>of() : referencedColumns)
                .forEach((referenced, names) -> copied.put(referenced, Set.copyOf(names)));
        referencedColumns = Map.copyOf(copied);
    }

    /**
     * 外部キーの参照先の名前を持たない入力を作る（違反の後の確かめに外部キーを使わない）。
     *
     * @param schema 照らしたスキーマ名
     * @param table 照らしたテーブルの DSL の定義
     * @param definitionId 今の定義の識別
     * @param tableMain テーブルの実効の主権限
     * @param canCreate 補助権限 CREATE
     * @param canDelete 補助権限 DELETE
     * @param columnMain カラムの名前ごとの実効の主権限
     * @param kinds カラムの名前ごとの受け渡しの型
     * @param writeConfigured 書き込みの接続の設定があるか
     * @param knownIdentifiers SQL の部品に渡す名前の一覧
     */
    public TableAccessInput(
            String schema,
            DslTable table,
            DefinitionId definitionId,
            MainPermission tableMain,
            boolean canCreate,
            boolean canDelete,
            Map<String, MainPermission> columnMain,
            Map<String, ColumnKind> kinds,
            boolean writeConfigured,
            KnownIdentifiers knownIdentifiers) {
        this(
                schema,
                table,
                definitionId,
                tableMain,
                canCreate,
                canDelete,
                columnMain,
                kinds,
                writeConfigured,
                knownIdentifiers,
                Map.of());
    }

    /** テーブルの名前と数だけを出す。 */
    @Override
    public String toString() {
        return "TableAccessInput[schema=" + schema + ", table=" + table.name() + ", tableMain=" + tableMain + "]";
    }
}
