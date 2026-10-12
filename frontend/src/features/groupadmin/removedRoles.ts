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
//
// メンバーを外した後に「グループ経由で無くなったロール」を求める純粋な関数（Intent 261004-role-menu の U6 の D20、
// AC2.1.9・AC2.1.10、NFR6.2）。このグループに割り当てたロールのうち、外した後の利用者のロールに無いものだけを、
// グループのロールの並びのまま返す。直接の割り当てやほかのグループで残るロールは含めない。
import type { GroupRoleRef, UserRole } from './api/types'

/** このグループのロールのうち、外した後の利用者に無いもの。 */
export function removedRoles(
  groupRoles: readonly GroupRoleRef[],
  userRolesAfter: readonly UserRole[],
): GroupRoleRef[] {
  const remaining = new Set(userRolesAfter.map((role) => role.roleId))
  return groupRoles.filter((role) => !remaining.has(role.roleId))
}
