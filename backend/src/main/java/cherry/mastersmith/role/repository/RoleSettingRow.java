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
 * ロールの ID つきの権限の設定の行の値（権限の YAML の書き出しと照らし合わせ。エンティティにしない射影。BR9.6・BR9.14）。
 *
 * @param roleId ロールの ID
 * @param schemaName スキーマの名前
 * @param tableName テーブルの名前（スキーマの階層は空の文字列）
 * @param columnName カラムの名前（スキーマ・テーブルの階層は空の文字列）
 * @param main 主権限（設定なしは null）
 * @param create 補助権限 CREATE（設定なしは null）
 * @param delete 補助権限 DELETE（設定なしは null）
 */
public record RoleSettingRow(
        long roleId,
        String schemaName,
        String tableName,
        String columnName,
        MainPermission main,
        Boolean create,
        Boolean delete) {

    /** 名前が null でないことを確かめる。 */
    public RoleSettingRow {
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
     * 値を返す。
     *
     * @return 値
     */
    public PermissionValues values() {
        return new PermissionValues(main, create, delete);
    }

    /** ロールの ID だけを出す（権限の YAML の書き出しと照らし合わせで TRACE に出るため、名前と値を出さない。NFR1.9）。 */
    @Override
    public String toString() {
        return "RoleSettingRow[roleId=" + roleId + "]";
    }
}
