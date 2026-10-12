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
// ロールの管理の API（契約 C7。ロール・権限・割り当て・利用者のロール、候補の利用者とグループ）を呼ぶ関数
// （Intent 261004-role-menu の U6、frontend-components.md の 3節、functional-spec.md の 1.2、L2、NFR1.4・NFR1.9・NFR2.2）。
// - すべて既存の ApiClient（apiRequest）を通し、トークン・401 での更新・Accept-Language は ApiClient に任せる。
// - 失敗は ApiClient の ApiError のまま投げる。応答は型の項目だけを写した新しい値にし（知らない項目は捨てる）、
//   必要な項目が無い・型が違う・列挙の知らない値の本文は通信の失敗（networkError）にする。
// - 道の中の ID は encodeURIComponent、権限の木のスキーマ名・テーブル名は URLSearchParams で問い合わせの引数にする
//   （role の RolePermissionController の schema・table。計画 2.3 の A2）。
// - 本文は関数の引数から決めた項目だけで組み立てる（名前は {name}、割り当ては {userId} か {groupId} の一方、保存の
//   scope は schemaName・tableName だけで columnName を送らない。計画 2.3 の A6）。
// - 木の応答は {items}（A3）、利用者・グループのロールは配列（A12）、出どころは応答の type を kind に写す（A9）。
// - 応答・要求の値をコンソール・ブラウザの保存に出さない。
import { apiRequest } from '../../../shared/api-client/apiClient'
import { networkError } from '../../../shared/api-client/apiError'
import {
  INHERITED_FROM_VALUES,
  MAIN_VALUES,
  type AssignedGroup,
  type AssignedUser,
  type AssignmentSource,
  type Effective,
  type Explicit,
  type GroupCandidate,
  type GroupCandidatePage,
  type InheritedFrom,
  type MainValue,
  type PermissionEntry,
  type PermissionNode,
  type PermissionScope,
  type PermissionTarget,
  type Role,
  type RoleAssignments,
  type RolePage,
  type RoleRow,
  type UserCandidate,
  type UserRole,
} from './types'

/** ロールの管理の API の根 */
export const ROLE_ADMIN_API_ROOT = '/api/admin/roles'

/** グループの一覧の API（候補） */
export const GROUP_LIST_API = '/api/admin/groups'

/** 利用者の一覧の API（候補） */
export const USER_LIST_API = '/api/admin/users'

type Json = Readonly<Record<string, unknown>>

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value)
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function isNullableBoolean(value: unknown): value is boolean | null {
  return value === null || typeof value === 'boolean'
}

function isMain(value: unknown): value is MainValue {
  return typeof value === 'string' && (MAIN_VALUES as readonly string[]).includes(value)
}

function isInheritedFrom(value: unknown): value is InheritedFrom {
  return typeof value === 'string' && (INHERITED_FROM_VALUES as readonly string[]).includes(value)
}

/** 本文を JSON として読む。読めなければ通信の失敗。 */
async function readJson(response: Response): Promise<unknown> {
  try {
    return (await response.json()) as unknown
  } catch {
    throw networkError()
  }
}

/** 写した値が undefined（形の違い）なら通信の失敗として投げる。 */
function required<T>(value: T | undefined): T {
  if (value === undefined) {
    throw networkError()
  }
  return value
}

/** 配列の各要素を写す。1つでも形が違えば undefined。 */
function mapAll<T>(value: unknown, convert: (item: unknown) => T | undefined): T[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  const result: T[] = []
  for (const item of value as unknown[]) {
    const converted = convert(item)
    if (converted === undefined) {
      return undefined
    }
    result.push(converted)
  }
  return result
}

/** ロールの道（ID は encodeURIComponent） */
export function rolePath(roleId: number, suffix = ''): string {
  return `${ROLE_ADMIN_API_ROOT}/${encodeURIComponent(String(roleId))}${suffix}`
}

/** ロールの一覧の道 */
export function roleListPath(page: number): string {
  return `${ROLE_ADMIN_API_ROOT}?page=${encodeURIComponent(String(page))}`
}

/** 権限の木の道（スキーマ・テーブル・カラムの段） */
export function permissionTreePath(
  roleId: number,
  level: 'schemas' | 'tables' | 'columns',
  names: { schema?: string; table?: string } = {},
): string {
  const query = new URLSearchParams()
  if (names.schema !== undefined) {
    query.set('schema', names.schema)
  }
  if (names.table !== undefined) {
    query.set('table', names.table)
  }
  const search = query.toString()
  return rolePath(roleId, `/permissions/${level}${search === '' ? '' : `?${search}`}`)
}

/** 利用者のロールの道 */
export function userRolesPath(userId: number): string {
  return `${USER_LIST_API}/${encodeURIComponent(String(userId))}/roles`
}

/** 候補の利用者の検索の道（1ページ目、検索の文字が空なら q を付けない） */
export function userSearchPath(searchText: string): string {
  return searchText === ''
    ? `${USER_LIST_API}?page=1`
    : `${USER_LIST_API}?page=1&q=${encodeURIComponent(searchText)}`
}

/** 候補のグループの一覧の道 */
export function groupListPath(page: number): string {
  return `${GROUP_LIST_API}?page=${encodeURIComponent(String(page))}`
}

function toRole(value: unknown): Role | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { roleId, name, createdAt, updatedAt } = value
  if (
    !isInteger(roleId) ||
    typeof name !== 'string' ||
    typeof createdAt !== 'string' ||
    typeof updatedAt !== 'string'
  ) {
    return undefined
  }
  return { roleId, name, createdAt, updatedAt }
}

function toRoleRow(value: unknown): RoleRow | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { roleId, name, userCount, groupCount } = value
  if (
    !isInteger(roleId) ||
    typeof name !== 'string' ||
    !isInteger(userCount) ||
    !isInteger(groupCount)
  ) {
    return undefined
  }
  return { roleId, name, userCount, groupCount }
}

function toPage<T>(
  value: unknown,
  convert: (item: unknown) => T | undefined,
): { items: T[]; page: number; size: number; total: number } | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { items, page, size, total } = value
  const rows = mapAll(items, convert)
  if (rows === undefined || !isInteger(page) || !isInteger(size) || !isInteger(total)) {
    return undefined
  }
  return { items: rows, page, size, total }
}

function toExplicit(value: unknown): Explicit | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { main, create, delete: del } = value
  if (!(main === null || isMain(main)) || !isNullableBoolean(create) || !isNullableBoolean(del)) {
    return undefined
  }
  return { main, create, delete: del }
}

function toEffective(value: unknown): Effective | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { main, create, delete: del } = value
  if (!isMain(main) || typeof create !== 'boolean' || typeof del !== 'boolean') {
    return undefined
  }
  return { main, create, delete: del }
}

function toPermissionNode(value: unknown): PermissionNode | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const {
    schemaName,
    tableName,
    columnName,
    displayName,
    inheritedFrom,
    inMenu,
    inCurrentDsl,
    hasChildren,
  } = value
  const explicit = toExplicit(value.explicit)
  const effective = toEffective(value.effective)
  if (
    typeof schemaName !== 'string' ||
    !isNullableString(tableName) ||
    !isNullableString(columnName) ||
    typeof displayName !== 'string' ||
    explicit === undefined ||
    effective === undefined ||
    !isInheritedFrom(inheritedFrom) ||
    typeof inMenu !== 'boolean' ||
    typeof inCurrentDsl !== 'boolean' ||
    typeof hasChildren !== 'boolean'
  ) {
    return undefined
  }
  return {
    schemaName,
    tableName,
    columnName,
    displayName,
    explicit,
    effective,
    inheritedFrom,
    inMenu,
    inCurrentDsl,
    hasChildren,
  }
}

/** 出どころを写す（応答の type を画面の kind に写す。DIRECT・GROUP 以外は形の違い）。 */
export function toAssignmentSource(value: unknown): AssignmentSource | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { type, groupId, name } = value
  if (type === 'DIRECT') {
    return { kind: 'DIRECT' }
  }
  if (type === 'GROUP' && isInteger(groupId) && typeof name === 'string') {
    return { kind: 'GROUP', groupId, name }
  }
  return undefined
}

function toAssignedUser(value: unknown): AssignedUser | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { userId, displayName, email, suspended } = value
  const sources = mapAll(value.sources, toAssignmentSource)
  if (
    !isInteger(userId) ||
    typeof displayName !== 'string' ||
    typeof email !== 'string' ||
    typeof suspended !== 'boolean' ||
    sources === undefined
  ) {
    return undefined
  }
  return { userId, displayName, email, suspended, sources }
}

function toAssignedGroup(value: unknown): AssignedGroup | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { groupId, name } = value
  if (!isInteger(groupId) || typeof name !== 'string') {
    return undefined
  }
  return { groupId, name }
}

function toAssignments(value: unknown): RoleAssignments | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const users = mapAll(value.users, toAssignedUser)
  const groups = mapAll(value.groups, toAssignedGroup)
  return users === undefined || groups === undefined ? undefined : { users, groups }
}

function toUserRole(value: unknown): UserRole | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { roleId, name } = value
  const sources = mapAll(value.sources, toAssignmentSource)
  if (!isInteger(roleId) || typeof name !== 'string' || sources === undefined) {
    return undefined
  }
  return { roleId, name, sources }
}

function toUserCandidate(value: unknown): UserCandidate | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { userId, displayName, email, suspended } = value
  if (
    !isInteger(userId) ||
    typeof displayName !== 'string' ||
    typeof email !== 'string' ||
    typeof suspended !== 'boolean'
  ) {
    return undefined
  }
  return { userId, displayName, email, suspended }
}

function toGroupCandidate(value: unknown): GroupCandidate | undefined {
  return toAssignedGroup(value)
}

function jsonInit(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

/** ロールの一覧の1ページを読む（GET /api/admin/roles?page=n）。 */
export async function listRoles(page: number): Promise<RolePage> {
  const response = await apiRequest(roleListPath(page), { method: 'GET' })
  return required(toPage(await readJson(response), toRoleRow))
}

/** ロール1件を読む（GET /api/admin/roles/{roleId}）。 */
export async function getRole(roleId: number): Promise<Role> {
  const response = await apiRequest(rolePath(roleId), { method: 'GET' })
  return required(toRole(await readJson(response)))
}

/** ロールを作る（POST /api/admin/roles、201）。本文は {name} だけ。 */
export async function createRole(name: string): Promise<Role> {
  const response = await apiRequest(ROLE_ADMIN_API_ROOT, jsonInit('POST', { name }))
  return required(toRole(await readJson(response)))
}

/** ロールの名前を変える（PUT /api/admin/roles/{roleId}、204）。本文は {name} だけ。 */
export async function renameRole(roleId: number, name: string): Promise<void> {
  await apiRequest(rolePath(roleId), jsonInit('PUT', { name }))
}

/** ロールを削除する（DELETE /api/admin/roles/{roleId}、204）。 */
export async function deleteRole(roleId: number): Promise<void> {
  await apiRequest(rolePath(roleId), { method: 'DELETE' })
}

async function readNodes(path: string): Promise<PermissionNode[]> {
  const response = await apiRequest(path, { method: 'GET' })
  const body = await readJson(response)
  return required(isObject(body) ? mapAll(body.items, toPermissionNode) : undefined)
}

/** 権限の木のスキーマの段を読む。 */
export function listSchemas(roleId: number): Promise<PermissionNode[]> {
  return readNodes(permissionTreePath(roleId, 'schemas'))
}

/** 権限の木のテーブルの段を読む（スキーマ名は問い合わせの引数）。 */
export function listTables(roleId: number, schema: string): Promise<PermissionNode[]> {
  return readNodes(permissionTreePath(roleId, 'tables', { schema }))
}

/** 権限の木のカラムの段を読む（スキーマ名とテーブル名は問い合わせの引数）。 */
export function listColumns(
  roleId: number,
  schema: string,
  table: string,
): Promise<PermissionNode[]> {
  return readNodes(permissionTreePath(roleId, 'columns', { schema, table }))
}

/** 権限を保存する（PUT …/permissions、204）。scope は schemaName・tableName だけ、entries は決めた項目だけ。 */
export async function savePermissions(
  roleId: number,
  scope: PermissionScope,
  entries: readonly PermissionEntry[],
): Promise<void> {
  await apiRequest(
    rolePath(roleId, '/permissions'),
    jsonInit('PUT', {
      scope: { schemaName: scope.schemaName, tableName: scope.tableName },
      entries: entries.map((entry) => ({
        schemaName: entry.schemaName,
        tableName: entry.tableName,
        columnName: entry.columnName,
        main: entry.main,
        create: entry.create,
        delete: entry.delete,
      })),
    }),
  )
}

/** 今の DSL に無い設定を消す（POST …/permissions/clear、204。Should）。 */
export async function clearPermissions(
  roleId: number,
  targets: readonly PermissionTarget[],
): Promise<void> {
  await apiRequest(
    rolePath(roleId, '/permissions/clear'),
    jsonInit('POST', {
      targets: targets.map((target) => ({
        schemaName: target.schemaName,
        tableName: target.tableName,
        columnName: target.columnName,
      })),
    }),
  )
}

/** ロールの割り当てを読む（GET …/assignments）。 */
export async function getAssignments(roleId: number): Promise<RoleAssignments> {
  const response = await apiRequest(rolePath(roleId, '/assignments'), { method: 'GET' })
  return required(toAssignments(await readJson(response)))
}

/** 利用者に割り当てる（POST …/assignments、本文は {userId} だけ）。 */
export async function assignUser(roleId: number, userId: number): Promise<void> {
  await apiRequest(rolePath(roleId, '/assignments'), jsonInit('POST', { userId }))
}

/** グループに割り当てる（POST …/assignments、本文は {groupId} だけ）。 */
export async function assignGroup(roleId: number, groupId: number): Promise<void> {
  await apiRequest(rolePath(roleId, '/assignments'), jsonInit('POST', { groupId }))
}

/** 利用者の直接の割り当てを外す（DELETE …/assignments/users/{userId}）。 */
export async function unassignUser(roleId: number, userId: number): Promise<void> {
  await apiRequest(rolePath(roleId, `/assignments/users/${encodeURIComponent(String(userId))}`), {
    method: 'DELETE',
  })
}

/** グループの割り当てを外す（DELETE …/assignments/groups/{groupId}）。 */
export async function unassignGroup(roleId: number, groupId: number): Promise<void> {
  await apiRequest(rolePath(roleId, `/assignments/groups/${encodeURIComponent(String(groupId))}`), {
    method: 'DELETE',
  })
}

/** 候補のグループの1ページを読む（GET /api/admin/groups?page=n）。 */
export async function listGroups(page: number): Promise<GroupCandidatePage> {
  const response = await apiRequest(groupListPath(page), { method: 'GET' })
  return required(toPage(await readJson(response), toGroupCandidate))
}

/** 候補の利用者を探す（GET /api/admin/users?page=1&q=、最大 20 件）。 */
export async function searchUsers(searchText: string): Promise<UserCandidate[]> {
  const response = await apiRequest(userSearchPath(searchText), { method: 'GET' })
  const body = await readJson(response)
  return required(isObject(body) ? mapAll(body.items, toUserCandidate) : undefined)
}

/** 利用者のロールを読む（GET /api/admin/users/{userId}/roles、配列）。 */
export async function getUserRoles(userId: number): Promise<UserRole[]> {
  const response = await apiRequest(userRolesPath(userId), { method: 'GET' })
  return required(mapAll(await readJson(response), toUserRole))
}

/** 画面が使う API の関数の集まり（テストで差し替える） */
export const roleAdminApi = {
  listRoles,
  getRole,
  createRole,
  renameRole,
  deleteRole,
  listSchemas,
  listTables,
  listColumns,
  savePermissions,
  clearPermissions,
  getAssignments,
  assignUser,
  assignGroup,
  unassignUser,
  unassignGroup,
  listGroups,
  searchUsers,
  getUserRoles,
}

/** 画面が使う API の関数の集まりの型 */
export type RoleAdminApi = typeof roleAdminApi
