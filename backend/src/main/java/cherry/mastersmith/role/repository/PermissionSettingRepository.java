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

import cherry.mastersmith.role.domain.PermissionSetting;
import cherry.mastersmith.role.domain.PermissionSettingId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 権限の設定の表の読み取り（BR4.4・BR4.7・BR4.10、NFR2.6）。どの方法も主キーの先頭（ロール・スキーマ名・テーブル名）の範囲を1回で
 * 読む。書き込みの方法を持たない。
 */
public interface PermissionSettingRepository extends Repository<PermissionSetting, PermissionSettingId> {

    /**
     * 1段目（スキーマ）の子をスキーマの名前ごとに読む（スキーマの行の値と、そのスキーマの行の数）。今の DSL に無いスキーマでも、下に設定が
     * あれば行になる。
     *
     * @param roleId ロールの ID
     * @return スキーマの名前ごとの行
     */
    @Query("select new cherry.mastersmith.role.repository.PermissionLevelRow("
            + "x.schemaName, e.mainPermission, e.createPermission, e.deletePermission, count(x))"
            + " from PermissionSetting x left join PermissionSetting e"
            + " on e.roleId = x.roleId and e.schemaName = x.schemaName and e.tableName = '' and e.columnName = ''"
            + " where x.roleId = :roleId"
            + " group by x.schemaName, e.mainPermission, e.createPermission, e.deletePermission")
    List<PermissionLevelRow> findSchemaLevel(@Param("roleId") long roleId);

    /**
     * スキーマの下の子をテーブルの名前ごとに読む（テーブルの行の値と、そのテーブルの行の数）。名前が空の文字列の行は、継承に使うスキーマの
     * 行。
     *
     * @param roleId ロールの ID
     * @param schemaName スキーマの名前
     * @return テーブルの名前ごとの行（空の文字列はスキーマの行）
     */
    @Query("select new cherry.mastersmith.role.repository.PermissionLevelRow("
            + "x.tableName, e.mainPermission, e.createPermission, e.deletePermission, count(x))"
            + " from PermissionSetting x left join PermissionSetting e"
            + " on e.roleId = x.roleId and e.schemaName = x.schemaName and e.tableName = x.tableName"
            + " and e.columnName = ''"
            + " where x.roleId = :roleId and x.schemaName = :schemaName"
            + " group by x.tableName, e.mainPermission, e.createPermission, e.deletePermission")
    List<PermissionLevelRow> findTableLevel(@Param("roleId") long roleId, @Param("schemaName") String schemaName);

    /**
     * テーブルの下のカラムの行と、継承に使うスキーマの行・テーブルの行を読む。
     *
     * @param roleId ロールの ID
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前
     * @return 設定の行
     */
    @Query("select new cherry.mastersmith.role.repository.PermissionSettingRow(s.schemaName, s.tableName, s.columnName,"
            + " s.mainPermission, s.createPermission, s.deletePermission) from PermissionSetting s"
            + " where s.roleId = :roleId and s.schemaName = :schemaName"
            + " and (s.tableName = :tableName or s.tableName = '')")
    List<PermissionSettingRow> findColumnLevel(
            @Param("roleId") long roleId, @Param("schemaName") String schemaName, @Param("tableName") String tableName);

    /**
     * 保存の範囲の今の値を読む（スキーマの範囲はテーブルの名前に空の文字列を渡し、スキーマの行だけ。テーブルの範囲はそのテーブルの行と
     * カラムの行。BR4.4）。
     *
     * @param roleId ロールの ID
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前（スキーマの範囲は空の文字列）
     * @return 設定の行
     */
    @Query("select new cherry.mastersmith.role.repository.PermissionSettingRow(s.schemaName, s.tableName, s.columnName,"
            + " s.mainPermission, s.createPermission, s.deletePermission) from PermissionSetting s"
            + " where s.roleId = :roleId and s.schemaName = :schemaName and s.tableName = :tableName")
    List<PermissionSettingRow> findInScope(
            @Param("roleId") long roleId, @Param("schemaName") String schemaName, @Param("tableName") String tableName);

    /**
     * 1つの対象の行を読む（消す操作の前の値。BR4.7）。
     *
     * @param roleId ロールの ID
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前（無い階層は空の文字列）
     * @param columnName カラムの名前（無い階層は空の文字列）
     * @return 設定の行（無ければ空）
     */
    @Query("select new cherry.mastersmith.role.repository.PermissionSettingRow(s.schemaName, s.tableName, s.columnName,"
            + " s.mainPermission, s.createPermission, s.deletePermission) from PermissionSetting s"
            + " where s.roleId = :roleId and s.schemaName = :schemaName and s.tableName = :tableName"
            + " and s.columnName = :columnName")
    Optional<PermissionSettingRow> findRow(
            @Param("roleId") long roleId,
            @Param("schemaName") String schemaName,
            @Param("tableName") String tableName,
            @Param("columnName") String columnName);
}
