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
// ロールの管理の画面の道（Intent 261004-role-menu の U6 の D1、functional-spec.md の 1.1）。登録と画面の中のリンクが同じ
// 関数で道を組む。道の中の ID は encodeURIComponent してから入れる。ほかの機能の画面への道は文字列で持つ（機能どうしの
// import の禁止）。

/** S3 ロールの一覧 */
export const ROLE_LIST_PATH = '/admin/roles'

/** S4 ロールの詳細「権限の設定」の登録の道 */
export const ROLE_DETAIL_ROUTE = '/admin/roles/:roleId'

/** S5 ロールの詳細「割り当て」の登録の道 */
export const ROLE_ASSIGNMENTS_ROUTE = '/admin/roles/:roleId/assignments'

/** DSL の管理の画面（noDsl の案内のリンク） */
export const DSL_ADMIN_PATH = '/admin/dsl'

/** S4 の道 */
export function roleDetailPath(roleId: number): string {
  return `${ROLE_LIST_PATH}/${encodeURIComponent(String(roleId))}`
}

/** S5 の道 */
export function roleAssignmentsPath(roleId: number): string {
  return `${roleDetailPath(roleId)}/assignments`
}
