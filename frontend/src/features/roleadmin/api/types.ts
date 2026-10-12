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
// ロールの管理の API（契約 C7）の画面の側の型（Intent 261004-role-menu の U6、frontend-components.md の 3節、L2、
// NFR1.9）。応答の知らない項目は型に書かず捨てる。日時は ISO 8601 の UTC の文字列のまま持つ。
// 出どころは、応答の `type`（`DIRECT`・`GROUP`。role の `RoleSourceResponse.from`）を画面の型の `kind` に写す
// （計画 2.3 の A9、11節 D-2・D-15）。

/** 主権限の値（null は設定なし） */
export const MAIN_VALUES = ['NONE', 'READ', 'FULL'] as const

/** 主権限の値 */
export type MainValue = (typeof MAIN_VALUES)[number]

/** 主権限の継承の元 */
export const INHERITED_FROM_VALUES = ['EXPLICIT', 'TABLE', 'SCHEMA', 'DEFAULT'] as const

/** 主権限の継承の元（主権限だけの値。補助権限の出どころは応答に無い。計画 2.3 の A5） */
export type InheritedFrom = (typeof INHERITED_FROM_VALUES)[number]

/** 明示の値（null は設定なし） */
export interface Explicit {
  main: MainValue | null
  create: boolean | null
  delete: boolean | null
}

/** 実効の値 */
export interface Effective {
  main: MainValue
  create: boolean
  delete: boolean
}

/** 権限の対象（スキーマ・テーブル・カラムの名前。下の段が無ければ null） */
export interface PermissionTarget {
  schemaName: string
  tableName: string | null
  columnName: string | null
}

/** 権限の木の1つの節 */
export interface PermissionNode extends PermissionTarget {
  /** 画面の言語の表示名（今の DSL に無い節は物理名） */
  displayName: string
  explicit: Explicit
  effective: Effective
  inheritedFrom: InheritedFrom
  /** 業務のメニューに出るか */
  inMenu: boolean
  /** 今の DSL にあるか */
  inCurrentDsl: boolean
  /** 子があるか */
  hasChildren: boolean
}

/** 保存の範囲（スキーマなら tableName が null。columnName は送らない。計画 2.3 の A6） */
export interface PermissionScope {
  schemaName: string
  tableName: string | null
}

/** 保存の1件（載せた対象だけを置き換える） */
export interface PermissionEntry extends PermissionTarget {
  main: MainValue | null
  create: boolean | null
  delete: boolean | null
}

/** ロール1件 */
export interface Role {
  roleId: number
  name: string
  createdAt: string
  updatedAt: string
}

/** ロールの一覧の1行 */
export interface RoleRow {
  roleId: number
  name: string
  userCount: number
  groupCount: number
}

/** ロールの一覧の1ページ */
export interface RolePage {
  items: RoleRow[]
  page: number
  size: number
  total: number
}

/** 割り当ての出どころ */
export type AssignmentSource = { kind: 'DIRECT' } | { kind: 'GROUP'; groupId: number; name: string }

/** 割り当てた利用者 */
export interface AssignedUser {
  userId: number
  displayName: string
  email: string
  suspended: boolean
  sources: AssignmentSource[]
}

/** 割り当てたグループ */
export interface AssignedGroup {
  groupId: number
  name: string
}

/** ロールの割り当て */
export interface RoleAssignments {
  users: AssignedUser[]
  groups: AssignedGroup[]
}

/** 利用者のロール（出どころつき） */
export interface UserRole {
  roleId: number
  name: string
  sources: AssignmentSource[]
}

/** 候補の利用者（利用者の管理の一覧の項目のうち、候補に使うものだけ） */
export interface UserCandidate {
  userId: number
  displayName: string
  email: string
  suspended: boolean
}

/** 候補のグループ */
export interface GroupCandidate {
  groupId: number
  name: string
}

/** 候補のグループの1ページ */
export interface GroupCandidatePage {
  items: GroupCandidate[]
  page: number
  size: number
  total: number
}
