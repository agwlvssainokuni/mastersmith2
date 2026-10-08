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
package cherry.mastersmith.role.repository;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import java.util.Objects;

/**
 * 設定の1行の射影（エンティティにしない。表に保存した空の文字列のまま持つ）。
 *
 * @param schemaName スキーマの名前
 * @param tableName テーブルの名前（スキーマの行は空の文字列）
 * @param columnName カラムの名前（スキーマ・テーブルの行は空の文字列）
 * @param main 主権限（null は設定なし）
 * @param create CREATE（同上）
 * @param delete DELETE（同上）
 */
public record PermissionSettingRow(
        String schemaName, String tableName, String columnName, MainPermission main, Boolean create, Boolean delete) {

    /** 名前が null でないことを確かめる。 */
    public PermissionSettingRow {
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(columnName, "columnName");
    }

    /**
     * 対象を返す。
     *
     * @return 対象
     */
    public PermissionTarget target() {
        return PermissionTarget.fromStored(schemaName, tableName, columnName);
    }

    /**
     * 値の組を返す。
     *
     * @return 値の組
     */
    public PermissionValues values() {
        return new PermissionValues(main, create, delete);
    }
}
