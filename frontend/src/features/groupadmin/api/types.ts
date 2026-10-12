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
// グループの管理の API（契約 C6）と、グループの画面が読むロールの API（契約 C7 の利用者・グループのロール）の画面の
// 側の型（Intent 261004-role-menu の U6、frontend-components.md の 4節、L2、NFR1.9）。roleadmin の型は import しない
// （機能どうしの import の禁止）。応答の知らない項目は型に書かず捨てる。出どころは応答の `type` を `kind` に写す。

/** グループ1件 */
export interface Group {
  groupId: number
  name: string
  createdAt: string
  updatedAt: string
}

/** グループの一覧の1行 */
export interface GroupRow {
  groupId: number
  name: string
  memberCount: number
  assignedRoleCount: number
}

/** グループの一覧の1ページ */
export interface GroupPage {
  items: GroupRow[]
  page: number
  size: number
  total: number
}

/** メンバー */
export interface Member {
  userId: number
  displayName: string
  email: string
  suspended: boolean
}

/** グループの詳細（メンバーはページ送りなし） */
export interface GroupDetail {
  groupId: number
  name: string
  members: Member[]
}

/** グループに割り当てたロール */
export interface GroupRoleRef {
  roleId: number
  name: string
}

/** ロールの出どころ */
export type RoleSource = { kind: 'DIRECT' } | { kind: 'GROUP'; groupId: number; name: string }

/** 利用者のロール（出どころつき） */
export interface UserRole {
  roleId: number
  name: string
  sources: RoleSource[]
}

/** 候補の利用者 */
export interface UserCandidate {
  userId: number
  displayName: string
  email: string
  suspended: boolean
}
