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
// グループの管理の画面の道（Intent 261004-role-menu の U6 の D1・D21、functional-spec.md の 1.1）。登録と画面の中のリンクが
// 同じ関数で道を組む。道の中の ID は encodeURIComponent してから入れる。ロールの画面への道は文字列で持つ（機能どうしの
// import の禁止）。

/** S6 グループの一覧 */
export const GROUP_LIST_PATH = '/admin/groups'

/** S6 グループの詳細の登録の道 */
export const GROUP_DETAIL_ROUTE = '/admin/groups/:groupId'

/** S6 グループの詳細の道 */
export function groupDetailPath(groupId: number): string {
  return `${GROUP_LIST_PATH}/${encodeURIComponent(String(groupId))}`
}

/** ロールの割り当てのタブ（S5）の道（D21） */
export function roleAssignmentsPath(roleId: number): string {
  return `/admin/roles/${encodeURIComponent(String(roleId))}/assignments`
}
