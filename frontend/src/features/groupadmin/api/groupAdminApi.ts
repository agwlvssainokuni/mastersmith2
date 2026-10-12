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
// グループの管理の API（契約 C6）と、グループの画面が読むロールの API（契約 C7 のグループ・利用者のロール）、候補の利用者の
// 検索を呼ぶ関数（Intent 261004-role-menu の U6、frontend-components.md の 4節、L2、NFR1.4・NFR1.9）。roleadmin の
// API の関数は import しない（機能どうしの import の禁止）。
// - すべて既存の ApiClient（apiRequest）を通す。失敗は ApiError のまま投げる。応答は型の項目だけを写し、形が違えば
//   通信の失敗にする。道の中の ID は encodeURIComponent。本文は決めた項目だけ（{name}・{userId}）。
// - グループ・利用者のロールの応答は配列（role の RoleLookupController）、出どころは応答の type を kind に写す。
import { apiRequest } from '../../../shared/api-client/apiClient'
import { networkError } from '../../../shared/api-client/apiError'
import type {
  Group,
  GroupDetail,
  GroupPage,
  GroupRoleRef,
  GroupRow,
  Member,
  RoleSource,
  UserCandidate,
  UserRole,
} from './types'

/** グループの管理の API の根 */
export const GROUP_ADMIN_API_ROOT = '/api/admin/groups'

/** 利用者の一覧の API（候補と利用者のロール） */
export const USER_LIST_API = '/api/admin/users'

type Json = Readonly<Record<string, unknown>>

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value)
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return (await response.json()) as unknown
  } catch {
    throw networkError()
  }
}

function required<T>(value: T | undefined): T {
  if (value === undefined) {
    throw networkError()
  }
  return value
}

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

/** グループの道（ID は encodeURIComponent） */
export function groupPath(groupId: number, suffix = ''): string {
  return `${GROUP_ADMIN_API_ROOT}/${encodeURIComponent(String(groupId))}${suffix}`
}

/** グループの一覧の道 */
export function groupListPath(page: number): string {
  return `${GROUP_ADMIN_API_ROOT}?page=${encodeURIComponent(String(page))}`
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

function toGroup(value: unknown): Group | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { groupId, name, createdAt, updatedAt } = value
  if (
    !isInteger(groupId) ||
    typeof name !== 'string' ||
    typeof createdAt !== 'string' ||
    typeof updatedAt !== 'string'
  ) {
    return undefined
  }
  return { groupId, name, createdAt, updatedAt }
}

function toGroupRow(value: unknown): GroupRow | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { groupId, name, memberCount, assignedRoleCount } = value
  if (
    !isInteger(groupId) ||
    typeof name !== 'string' ||
    !isInteger(memberCount) ||
    !isInteger(assignedRoleCount)
  ) {
    return undefined
  }
  return { groupId, name, memberCount, assignedRoleCount }
}

function toGroupPage(value: unknown): GroupPage | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { page, size, total } = value
  const items = mapAll(value.items, toGroupRow)
  if (items === undefined || !isInteger(page) || !isInteger(size) || !isInteger(total)) {
    return undefined
  }
  return { items, page, size, total }
}

function toMember(value: unknown): Member | undefined {
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

function toGroupDetail(value: unknown): GroupDetail | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { groupId, name } = value
  const members = mapAll(value.members, toMember)
  if (!isInteger(groupId) || typeof name !== 'string' || members === undefined) {
    return undefined
  }
  return { groupId, name, members }
}

function toRoleRef(value: unknown): GroupRoleRef | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { roleId, name } = value
  if (!isInteger(roleId) || typeof name !== 'string') {
    return undefined
  }
  return { roleId, name }
}

function toRoleSource(value: unknown): RoleSource | undefined {
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

function toUserRole(value: unknown): UserRole | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { roleId, name } = value
  const sources = mapAll(value.sources, toRoleSource)
  if (!isInteger(roleId) || typeof name !== 'string' || sources === undefined) {
    return undefined
  }
  return { roleId, name, sources }
}

function jsonInit(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

/** グループの一覧の1ページを読む（GET /api/admin/groups?page=n）。 */
export async function listGroups(page: number): Promise<GroupPage> {
  const response = await apiRequest(groupListPath(page), { method: 'GET' })
  return required(toGroupPage(await readJson(response)))
}

/** グループを作る（POST /api/admin/groups、201）。本文は {name} だけ。 */
export async function createGroup(name: string): Promise<Group> {
  const response = await apiRequest(GROUP_ADMIN_API_ROOT, jsonInit('POST', { name }))
  return required(toGroup(await readJson(response)))
}

/** グループの詳細を読む（GET /api/admin/groups/{groupId}）。 */
export async function getGroup(groupId: number): Promise<GroupDetail> {
  const response = await apiRequest(groupPath(groupId), { method: 'GET' })
  return required(toGroupDetail(await readJson(response)))
}

/** グループの名前を変える（PUT /api/admin/groups/{groupId}、204）。本文は {name} だけ。 */
export async function renameGroup(groupId: number, name: string): Promise<void> {
  await apiRequest(groupPath(groupId), jsonInit('PUT', { name }))
}

/** グループを削除する（DELETE /api/admin/groups/{groupId}、204）。 */
export async function deleteGroup(groupId: number): Promise<void> {
  await apiRequest(groupPath(groupId), { method: 'DELETE' })
}

/** メンバーを足す（POST …/members、本文は {userId} だけ）。 */
export async function addMember(groupId: number, userId: number): Promise<void> {
  await apiRequest(groupPath(groupId, '/members'), jsonInit('POST', { userId }))
}

/** メンバーを外す（DELETE …/members/{userId}）。 */
export async function removeMember(groupId: number, userId: number): Promise<void> {
  await apiRequest(groupPath(groupId, `/members/${encodeURIComponent(String(userId))}`), {
    method: 'DELETE',
  })
}

/** グループに割り当てたロールを読む（GET …/roles、配列）。 */
export async function getGroupRoles(groupId: number): Promise<GroupRoleRef[]> {
  const response = await apiRequest(groupPath(groupId, '/roles'), { method: 'GET' })
  return required(mapAll(await readJson(response), toRoleRef))
}

/** 利用者のロールを読む（GET /api/admin/users/{userId}/roles、配列）。 */
export async function getUserRoles(userId: number): Promise<UserRole[]> {
  const response = await apiRequest(userRolesPath(userId), { method: 'GET' })
  return required(mapAll(await readJson(response), toUserRole))
}

/** 候補の利用者を探す（GET /api/admin/users?page=1&q=、最大 20 件）。 */
export async function searchUsers(searchText: string): Promise<UserCandidate[]> {
  const response = await apiRequest(userSearchPath(searchText), { method: 'GET' })
  const body = await readJson(response)
  return required(isObject(body) ? mapAll(body.items, toMember) : undefined)
}

/** 画面が使う API の関数の集まり（テストで差し替える） */
export const groupAdminApi = {
  listGroups,
  createGroup,
  getGroup,
  renameGroup,
  deleteGroup,
  addMember,
  removeMember,
  getGroupRoles,
  getUserRoles,
  searchUsers,
}

/** 画面が使う API の関数の集まりの型 */
export type GroupAdminApi = typeof groupAdminApi
