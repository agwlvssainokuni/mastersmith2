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
// ロールの管理の画面のテストの値（テストからだけ使う。画面からは読み込まない。計画 unit-test-instructions.md の 7節）。
// 値はテストごとに関数で新しく作り、共有の可変の見本を持たない。メールアドレスは予約のドメイン（example.com）だけ、
// 氏名は架空の2語の値。名前には 64 文字のもの、HTML に見える記号（<script>・<img onerror>・&・"・'）を含むもの、
// U+200B（書式文字）を含むものを入れる。見本は小さく保つ（行は数件〜100 件）。
import { vi, type Mock } from 'vitest'
import type { ApiError } from '../../../shared/api-client/apiError'
import type { RoleAdminApi } from '../api/roleAdminApi'
import type {
  AssignedUser,
  Explicit,
  PermissionNode,
  Role,
  RoleAssignments,
  RolePage,
  RoleRow,
} from '../api/types'

/** 64 文字のロールの名前 */
export const LONG_ROLE_NAME = 'あ'.repeat(64)

/** スクリプトのタグに見える名前（文字のまま出ることの確かめ） */
export const SCRIPT_NAME = '<script>alert(1)</script>'

/** 画像のタグに見える名前 */
export const IMG_NAME = '<img src=x onerror=alert(1)>'

/** 記号の名前 */
export const SYMBOL_NAME = '営業 & "経理" \'総務\''

/** 書式文字（U+200B）を含む名前（サーバーの規則で受け付ける） */
export const ZERO_WIDTH_NAME = '営業\u200B部'

/** 画面に出てはいけない目印（応答の detail・title に入れる） */
export const DETAIL_MARKER = 'role-admin-detail-marker'

/** ロールの一覧の1行 */
export function roleRowOf(overrides: Partial<RoleRow> = {}): RoleRow {
  return { roleId: 1, name: '営業', userCount: 2, groupCount: 1, ...overrides }
}

/** 見本の行（記号・64 文字・書式文字の名前を含む） */
export function sampleRoleRows(): RoleRow[] {
  return [
    roleRowOf(),
    roleRowOf({ roleId: 2, name: '経理', userCount: 0, groupCount: 0 }),
    roleRowOf({ roleId: 3, name: LONG_ROLE_NAME, userCount: 12, groupCount: 3 }),
    roleRowOf({ roleId: 4, name: SCRIPT_NAME, userCount: 1, groupCount: 0 }),
    roleRowOf({ roleId: 5, name: IMG_NAME, userCount: 0, groupCount: 2 }),
    roleRowOf({ roleId: 6, name: SYMBOL_NAME, userCount: 0, groupCount: 0 }),
    roleRowOf({ roleId: 7, name: ZERO_WIDTH_NAME, userCount: 0, groupCount: 0 }),
  ]
}

/** n 件の行（ID は start から） */
export function roleRowsOf(count: number, start = 1): RoleRow[] {
  return Array.from({ length: count }, (_, index) =>
    roleRowOf({
      roleId: start + index,
      name: `ロール${start + index}`,
      userCount: 0,
      groupCount: 0,
    }),
  )
}

/** ロールの一覧の1ページ */
export function rolePageOf(items: RoleRow[], overrides: Partial<RolePage> = {}): RolePage {
  return { items, page: 1, size: 20, total: items.length, ...overrides }
}

/** 失敗の値（detail と title に目印を入れる。画面に出てはいけない） */
export function apiError(
  status: number,
  code?: string,
  extra: Readonly<Record<string, unknown>> = {},
): ApiError {
  const error: ApiError =
    code !== undefined ? { kind: 'response', status, code } : { kind: 'response', status }
  Object.defineProperty(error, 'problem', {
    value: { status, code, detail: DETAIL_MARKER, title: DETAIL_MARKER, ...extra },
    enumerable: false,
  })
  return error
}

/** 通信の失敗 */
export const NETWORK_ERROR: ApiError = { kind: 'network' }

/** 答えを外から返す約束 */
export interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (error: unknown) => void
}

/** 答えを外から返す約束を作る。 */
export function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/** 差し替えた API（各関数は vi.fn。呼ばれると失敗する既定を持ち、テストが必要な関数だけ答えを決める） */
export type FakeRoleAdminApi = { [K in keyof RoleAdminApi]: Mock<RoleAdminApi[K]> }

/** 差し替えた API を作る。決めていない関数が呼ばれたら通信の失敗にする。 */
export function fakeRoleAdminApi(overrides: Partial<RoleAdminApi> = {}): FakeRoleAdminApi {
  const fail = () => Promise.reject(NETWORK_ERROR)
  const base: RoleAdminApi = {
    listRoles: fail,
    getRole: fail,
    createRole: fail,
    renameRole: fail,
    deleteRole: fail,
    listSchemas: fail,
    listTables: fail,
    listColumns: fail,
    savePermissions: fail,
    clearPermissions: fail,
    getAssignments: fail,
    assignUser: fail,
    assignGroup: fail,
    unassignUser: fail,
    unassignGroup: fail,
    listGroups: fail,
    searchUsers: fail,
    getUserRoles: fail,
    ...overrides,
  }
  const result: Record<string, Mock> = {}
  for (const [key, value] of Object.entries(base)) {
    result[key] = vi.fn(value as (...args: unknown[]) => unknown)
  }
  return result as unknown as FakeRoleAdminApi
}

/** ロール1件 */
export function roleOf(overrides: Partial<Role> = {}): Role {
  return {
    roleId: 7,
    name: '営業',
    createdAt: '2026-10-05T00:00:00Z',
    updatedAt: '2026-10-05T00:00:00Z',
    ...overrides,
  }
}

/** 明示の値（既定はすべて設定なし） */
export function explicitOf(overrides: Partial<Explicit> = {}): Explicit {
  return { main: null, create: null, delete: null, ...overrides }
}

/** 権限の節（既定はスキーマ SALES、既定の値） */
export function nodeOf(overrides: Partial<PermissionNode> = {}): PermissionNode {
  return {
    schemaName: 'SALES',
    tableName: null,
    columnName: null,
    displayName: '販売DB',
    explicit: explicitOf(),
    effective: { main: 'NONE', create: false, delete: false },
    inheritedFrom: 'DEFAULT',
    inMenu: false,
    inCurrentDsl: true,
    hasChildren: true,
    ...overrides,
  }
}

/** テーブルの節 */
export function tableNodeOf(overrides: Partial<PermissionNode> = {}): PermissionNode {
  return nodeOf({
    tableName: 'ORDER_LINE',
    displayName: '受注明細',
    hasChildren: true,
    inMenu: true,
    ...overrides,
  })
}

/** カラムの節 */
export function columnNodeOf(overrides: Partial<PermissionNode> = {}): PermissionNode {
  return nodeOf({
    tableName: 'ORDER_LINE',
    columnName: 'ORDER_NO',
    displayName: '受注番号',
    hasChildren: false,
    ...overrides,
  })
}

/** 割り当てた利用者 */
export function assignedUserOf(overrides: Partial<AssignedUser> = {}): AssignedUser {
  return {
    userId: 21,
    displayName: '田中 一郎',
    email: 'tanaka.ichiro@example.com',
    suspended: false,
    sources: [{ kind: 'DIRECT' }],
    ...overrides,
  }
}

/** 割り当て */
export function assignmentsOf(overrides: Partial<RoleAssignments> = {}): RoleAssignments {
  return { users: [], groups: [], ...overrides }
}
