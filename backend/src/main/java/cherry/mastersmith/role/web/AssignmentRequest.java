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
 * 割り当ての要求の本文（契約 C7、BR2.3）。{@code userId} と {@code groupId} のちょうど一方だけを受け、ほかの項目は型に無いため読まれない
 * （一括代入の防止）。整数でない値は既存の本文の読み取りの誤りの扱い（400）。
 *
 * @param userId 割り当てる利用者 ID（グループのときは null）
 * @param groupId 割り当てるグループの ID（利用者のときは null）
 */
public record AssignmentRequest(Long userId, Long groupId) {}
