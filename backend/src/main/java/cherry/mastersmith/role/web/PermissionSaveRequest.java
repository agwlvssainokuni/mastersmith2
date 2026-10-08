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

import java.util.List;

/**
 * 権限の保存の要求の本文（契約 C7 の {@code PUT .../permissions}、BR4.4）。{@code scope} の中の、{@code entries} に載せた対象だけを置き換える。
 * ほかの項目は型に無いため読まれない（NFR1.4）。
 *
 * @param scope 範囲（スキーマかテーブル）
 * @param entries 対象ごとの値
 */
public record PermissionSaveRequest(PermissionTargetRequest scope, List<PermissionEntryRequest> entries) {}
