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
package cherry.mastersmith.role.web;

/**
 * 権限の保存の1件の要求（契約 C7 の entries の1件。BR4.1・BR4.4）。null は「設定なし」。
 *
 * @param schemaName スキーマの名前
 * @param tableName テーブルの名前（スキーマの対象は null）
 * @param columnName カラムの名前（スキーマ・テーブルの対象は null）
 * @param main 主権限（{@code NONE}・{@code READ}・{@code FULL}、null は設定なし）
 * @param create CREATE（null は設定なし。カラムでは受けない）
 * @param delete DELETE（null は設定なし。カラムでは受けない）
 */
public record PermissionEntryRequest(
        String schemaName, String tableName, String columnName, String main, Boolean create, Boolean delete) {}
