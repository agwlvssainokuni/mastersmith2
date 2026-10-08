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
package cherry.mastersmith.role.domain;

import java.util.Objects;

/**
 * 権限の設定の対象の名前の組（{@code entities.md} の PermissionTarget）。下位の名前の無い階層は null で持ち、表には空の文字列で
 * 保存する（DSL の名前は1文字以上のため実の名前と重ならない。Q2: A）。名前の検証（長さ・制御文字）は
 * {@link PermissionTargetName} が受け持ち、この型は組の形（空の名前を持たない、カラムはテーブルの下だけ）だけを確かめる。
 *
 * @param schemaName スキーマの名前
 * @param tableName テーブルの名前（スキーマの階層は null）
 * @param columnName カラムの名前（スキーマ・テーブルの階層は null）
 */
public record PermissionTarget(String schemaName, String tableName, String columnName) {

    /** 組の形を確かめる。 */
    public PermissionTarget {
        if (schemaName == null || schemaName.isEmpty()) {
            throw new IllegalArgumentException("スキーマの名前は必須です");
        }
        if (tableName != null && tableName.isEmpty()) {
            throw new IllegalArgumentException("テーブルの名前は空にできません（無いときは null）");
        }
        if (columnName != null && (columnName.isEmpty() || tableName == null)) {
            throw new IllegalArgumentException("カラムはテーブルの下だけで、名前は空にできません");
        }
    }

    /**
     * スキーマの対象を作る。
     *
     * @param schemaName スキーマの名前
     * @return 対象
     */
    public static PermissionTarget schema(String schemaName) {
        return new PermissionTarget(schemaName, null, null);
    }

    /**
     * テーブルの対象を作る。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前
     * @return 対象
     */
    public static PermissionTarget table(String schemaName, String tableName) {
        return new PermissionTarget(schemaName, Objects.requireNonNull(tableName, "tableName"), null);
    }

    /**
     * カラムの対象を作る。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前
     * @param columnName カラムの名前
     * @return 対象
     */
    public static PermissionTarget column(String schemaName, String tableName, String columnName) {
        return new PermissionTarget(
                schemaName,
                Objects.requireNonNull(tableName, "tableName"),
                Objects.requireNonNull(columnName, "columnName"));
    }

    /**
     * 表に保存した名前の組から作る（空の文字列は下位の名前の無い階層）。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前（空の文字列はスキーマの階層）
     * @param columnName カラムの名前（空の文字列はスキーマ・テーブルの階層）
     * @return 対象
     */
    public static PermissionTarget fromStored(String schemaName, String tableName, String columnName) {
        return new PermissionTarget(schemaName, emptyToNull(tableName), emptyToNull(columnName));
    }

    /**
     * 階層を返す（保存しない値。名前の有無から導く）。
     *
     * @return 階層
     */
    public PermissionLevel level() {
        if (columnName != null) {
            return PermissionLevel.COLUMN;
        }
        return tableName != null ? PermissionLevel.TABLE : PermissionLevel.SCHEMA;
    }

    /**
     * 表に保存するテーブルの名前を返す。
     *
     * @return テーブルの名前（スキーマの階層は空の文字列）
     */
    public String storedTableName() {
        return tableName == null ? "" : tableName;
    }

    /**
     * 表に保存するカラムの名前を返す。
     *
     * @return カラムの名前（スキーマ・テーブルの階層は空の文字列）
     */
    public String storedColumnName() {
        return columnName == null ? "" : columnName;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
