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
// 利用者の管理の API（契約 C3 の一覧・5つの操作・氏名と言語）を呼ぶ関数（frontend-components.md の 5節、
// functional-spec.md の 6節、security-design.md の 6.1・6.3、NFR3.1・NFR3.3・NFR5.3）。
// すべて既存の ApiClient（apiRequest）を通し、アクセストークン・401 での更新・Accept-Language は ApiClient に任せる。
// 失敗は ApiClient の ApiError（kind・status・code、本文が Problem Details なら problem）としてそのまま投げる。
// 一覧の本文は JSON として読み、C3 の項目だけを写した新しい値にする（知らない項目は捨てる）。必要な項目が無い・型が違う
// 本文は通信の失敗（networkError）として投げる。lockedUntil は無いか null なら持たない、文字列ならその値、ほかの型は
// 形の違い（計画 8節の D-4）。5つの操作と氏名・言語の 204 は本文を読まない。userId は encodeURIComponent してパスに入れ、
// useAdminForbidden に渡すパスにも同じ関数を使う。応答・要求の値をコンソール・ブラウザの保存に出さない。
import { apiRequest } from '../../../shared/api-client/apiClient'
import { networkError } from '../../../shared/api-client/apiError'
import {
  USER_LANGUAGES,
  type AdminUser,
  type AdminUserPage,
  type ProfileRequest,
  type UserLanguage,
} from './types'

/** 利用者の管理の API の根 */
export const USER_ADMIN_API_ROOT = '/api/admin/users'

/** 5つの確かめの操作の種類 */
export type UserAdminOperation =
  'grantAdmin' | 'revokeAdmin' | 'suspend' | 'resume' | 'resetFailures'

/** 操作の種類ごとのパスの末尾（契約 C3） */
const OPERATION_SEGMENTS: Readonly<Record<UserAdminOperation | 'profile', string>> = {
  grantAdmin: 'grant-admin',
  revokeAdmin: 'revoke-admin',
  suspend: 'suspend',
  resume: 'resume',
  resetFailures: 'reset-login-failures',
  profile: 'profile',
}

/** 操作（と氏名・言語）の API のパス */
export function userAdminOperationPath(
  userId: number,
  operation: UserAdminOperation | 'profile',
): string {
  return `${USER_ADMIN_API_ROOT}/${encodeURIComponent(String(userId))}/${OPERATION_SEGMENTS[operation]}`
}

/** 一覧の API のパス（検索の文字が空なら q を付けない） */
export function userAdminListPath(page: number, searchText: string): string {
  const query = `page=${encodeURIComponent(String(page))}`
  return searchText === ''
    ? `${USER_ADMIN_API_ROOT}?${query}`
    : `${USER_ADMIN_API_ROOT}?${query}&q=${encodeURIComponent(searchText)}`
}

type Json = Readonly<Record<string, unknown>>

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value)
}

function isLanguage(value: unknown): value is UserLanguage {
  return typeof value === 'string' && (USER_LANGUAGES as readonly string[]).includes(value)
}

/** 応答の1行を、決まった項目だけの値にする。形が違えば undefined。 */
function toAdminUser(value: unknown): AdminUser | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const {
    userId,
    email,
    displayName,
    language,
    admin,
    suspended,
    locked,
    lockedUntil,
    resettable,
    registeredAt,
    self,
  } = value
  if (
    !isInteger(userId) ||
    typeof email !== 'string' ||
    typeof displayName !== 'string' ||
    !isLanguage(language) ||
    typeof admin !== 'boolean' ||
    typeof suspended !== 'boolean' ||
    typeof locked !== 'boolean' ||
    typeof resettable !== 'boolean' ||
    typeof registeredAt !== 'string' ||
    typeof self !== 'boolean' ||
    (lockedUntil !== undefined && lockedUntil !== null && typeof lockedUntil !== 'string')
  ) {
    return undefined
  }
  const row: AdminUser = {
    userId,
    email,
    displayName,
    language,
    admin,
    suspended,
    locked,
    resettable,
    registeredAt,
    self,
  }
  if (typeof lockedUntil === 'string') {
    row.lockedUntil = lockedUntil
  }
  return row
}

/** 一覧の応答を、決まった項目だけの値にする。形が違えば undefined。 */
function toAdminUserPage(value: unknown): AdminUserPage | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { items, page, size, total } = value
  if (!Array.isArray(items) || !isInteger(page) || !isInteger(size) || !isInteger(total)) {
    return undefined
  }
  const rows: AdminUser[] = []
  for (const item of items as unknown[]) {
    const row = toAdminUser(item)
    if (row === undefined) {
      return undefined
    }
    rows.push(row)
  }
  return { items: rows, page, size, total }
}

/** 利用者の一覧の1ページを読む（GET /api/admin/users?page=n[&q=…]）。 */
export async function listUsers(page: number, searchText: string): Promise<AdminUserPage> {
  const response = await apiRequest(userAdminListPath(page, searchText), { method: 'GET' })
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw networkError()
  }
  const converted = toAdminUserPage(body)
  if (converted === undefined) {
    throw networkError()
  }
  return converted
}

async function operate(userId: number, operation: UserAdminOperation): Promise<void> {
  await apiRequest(userAdminOperationPath(userId, operation), { method: 'POST' })
}

/** 管理者の印を付ける（POST …/{userId}/grant-admin、204）。本文なし。 */
export function grantAdmin(userId: number): Promise<void> {
  return operate(userId, 'grantAdmin')
}

/** 管理者の印を外す（POST …/{userId}/revoke-admin、204）。本文なし。 */
export function revokeAdmin(userId: number): Promise<void> {
  return operate(userId, 'revokeAdmin')
}

/** 利用を止める（POST …/{userId}/suspend、204）。本文なし。 */
export function suspendUser(userId: number): Promise<void> {
  return operate(userId, 'suspend')
}

/** 停止を解く（POST …/{userId}/resume、204）。本文なし。 */
export function resumeUser(userId: number): Promise<void> {
  return operate(userId, 'resume')
}

/** ロックを解除する（失敗回数を戻す。POST …/{userId}/reset-login-failures、204）。本文なし。 */
export function resetLoginFailures(userId: number): Promise<void> {
  return operate(userId, 'resetFailures')
}

/** 氏名と言語を直す（PUT …/{userId}/profile、204）。本文は displayName・language の2つだけ。 */
export async function updateProfile(userId: number, request: ProfileRequest): Promise<void> {
  const body: ProfileRequest = { displayName: request.displayName, language: request.language }
  await apiRequest(userAdminOperationPath(userId, 'profile'), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** 画面が使う API の関数の集まり（テストで差し替える） */
export const userAdminApi = {
  listUsers,
  grantAdmin,
  revokeAdmin,
  suspendUser,
  resumeUser,
  resetLoginFailures,
  updateProfile,
}

/** 画面が使う API の関数の集まりの型 */
export type UserAdminApi = typeof userAdminApi
