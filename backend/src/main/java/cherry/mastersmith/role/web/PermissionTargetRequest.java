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
 * 権限の設定の対象の要求（保存の {@code scope} と、消す操作の {@code targets} の1件。契約 C7）。
 *
 * @param schemaName スキーマの名前
 * @param tableName テーブルの名前（スキーマの対象は null）
 * @param columnName カラムの名前（スキーマ・テーブルの対象は null。{@code scope} では受けない）
 */
public record PermissionTargetRequest(String schemaName, String tableName, String columnName) {}
