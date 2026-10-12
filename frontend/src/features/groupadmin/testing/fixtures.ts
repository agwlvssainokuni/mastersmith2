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
// グループの管理の画面のテストの値（テストからだけ使う。画面からは読み込まない。計画 unit-test-instructions.md の 7節）。
// 値はテストごとに関数で新しく作り、共有の可変の見本を持たない。メールアドレスは予約のドメイン（example.com）だけ、
// 氏名は架空の2語の値。名前には 64 文字のもの、HTML に見える記号（<script>・<img onerror>・&・"・'）を含むもの、
// U+200B（書式文字）を含むものを入れる。見本は小さく保つ（行は数件〜100 件）。
import { vi, type Mock } from 'vitest'
import type { ApiError } from '../../../shared/api-client/apiError'
import type { GroupAdminApi } from '../api/groupAdminApi'
import type { GroupDetail, GroupPage, GroupRow, Member } from '../api/types'

/** 64 文字のグループの名前 */
export const LONG_GROUP_NAME = 'あ'.repeat(64)

/** スクリプトのタグに見える名前（文字のまま出ることの確かめ） */
export const SCRIPT_NAME = '<script>alert(1)</script>'

/** 画像のタグに見える名前 */
export const IMG_NAME = '<img src=x onerror=alert(1)>'

/** 記号の名前 */
export const SYMBOL_NAME = '営業 & "経理" \'総務\''

/** 書式文字（U+200B）を含む名前（サーバーの規則で受け付ける） */
export const ZERO_WIDTH_NAME = '営業\u200B部'

/** 画面に出てはいけない目印（応答の detail・title に入れる） */
export const DETAIL_MARKER = 'group-admin-detail-marker'

/** グループの一覧の1行 */
export function groupRowOf(overrides: Partial<GroupRow> = {}): GroupRow {
  return { groupId: 1, name: '営業部', memberCount: 2, assignedRoleCount: 1, ...overrides }
}

/** 見本の行（記号・64 文字・書式文字の名前を含む） */
export function sampleGroupRows(): GroupRow[] {
  return [
    groupRowOf(),
    groupRowOf({ groupId: 2, name: '経理部', memberCount: 0, assignedRoleCount: 0 }),
    groupRowOf({ groupId: 3, name: LONG_GROUP_NAME, memberCount: 12, assignedRoleCount: 3 }),
    groupRowOf({ groupId: 4, name: SCRIPT_NAME, memberCount: 1, assignedRoleCount: 0 }),
    groupRowOf({ groupId: 5, name: IMG_NAME, memberCount: 0, assignedRoleCount: 2 }),
    groupRowOf({ groupId: 6, name: SYMBOL_NAME, memberCount: 0, assignedRoleCount: 0 }),
    groupRowOf({ groupId: 7, name: ZERO_WIDTH_NAME, memberCount: 0, assignedRoleCount: 0 }),
  ]
}

/** n 件の行（ID は start から） */
export function groupRowsOf(count: number, start = 1): GroupRow[] {
  return Array.from({ length: count }, (_, index) =>
    groupRowOf({
      groupId: start + index,
      name: `グループ${start + index}`,
      memberCount: 0,
      assignedRoleCount: 0,
    }),
  )
}

/** グループの一覧の1ページ */
export function groupPageOf(items: GroupRow[], overrides: Partial<GroupPage> = {}): GroupPage {
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
export type FakeGroupAdminApi = { [K in keyof GroupAdminApi]: Mock<GroupAdminApi[K]> }

/** 差し替えた API を作る。決めていない関数が呼ばれたら通信の失敗にする。 */
export function fakeGroupAdminApi(overrides: Partial<GroupAdminApi> = {}): FakeGroupAdminApi {
  const fail = () => Promise.reject(NETWORK_ERROR)
  const base: GroupAdminApi = {
    listGroups: fail,
    createGroup: fail,
    getGroup: fail,
    renameGroup: fail,
    deleteGroup: fail,
    addMember: fail,
    removeMember: fail,
    getGroupRoles: fail,
    getUserRoles: fail,
    searchUsers: fail,
    ...overrides,
  }
  const result: Record<string, Mock> = {}
  for (const [key, value] of Object.entries(base)) {
    result[key] = vi.fn(value as (...args: unknown[]) => unknown)
  }
  return result as unknown as FakeGroupAdminApi
}

/** メンバー */
export function memberOf(overrides: Partial<Member> = {}): Member {
  return {
    userId: 21,
    displayName: '田中 一郎',
    email: 'tanaka.ichiro@example.com',
    suspended: false,
    ...overrides,
  }
}

/** グループの詳細 */
export function detailOf(overrides: Partial<GroupDetail> = {}): GroupDetail {
  return { groupId: 3, name: '営業部', members: [memberOf()], ...overrides }
}
