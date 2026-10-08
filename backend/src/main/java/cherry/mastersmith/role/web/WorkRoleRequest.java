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
 * 作業ロールの切り替えの要求の本文（契約 C8、BR2.2・BR2.3）。{@code roleId} だけを受け、利用者を指す値（{@code userId} など）は型に無いため
 * 読まれない。整数でない値は既存の本文の読み取りの誤りの扱い（400）。
 *
 * @param roleId 選ぶロールの ID（無ければ null）
 */
public record WorkRoleRequest(Long roleId) {}
