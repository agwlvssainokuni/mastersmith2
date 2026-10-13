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
// 自分の権限の API（契約 C8 の GET /api/me/permissions/schemas・tables・columns）を呼ぶ関数と応答の写し
// （Intent 261004-role-menu の U7 の L10、D3・D24、NFR1.4・NFR6.8、計画 3.1 の A5・12節 D-3）。
// - 名前は道に入れず、URLSearchParams の schema・table の引数で送る。利用者を指す値を送らない。
// - displayName は要求の言語（Accept-Language、ApiClient が付ける）でサーバーが選んだ文字列1つ（D-3）。
// - 応答は型の項目だけを写し、崩れた本文・知らない main は通信の失敗にする。
import { apiRequest } from '../../shared/api-client/apiClient'
import { networkError } from '../../shared/api-client/apiError'

/** 自分の権限の API の根 */
export const MY_PERMISSIONS_API = '/api/me/permissions'

/** 主権限 */
export type MainPermission = 'NONE' | 'READ' | 'FULL'

const MAIN_VALUES: readonly string[] = ['NONE', 'READ', 'FULL']

/** 実際に効く権限 */
export interface MyEffective {
  main: MainPermission
  create: boolean
  delete: boolean
}

/** 自分の権限の対象の節 */
export interface MyPermissionNode {
  schemaName: string
  tableName: string | null
  columnName: string | null
  /** 要求の言語で選ばれた表示名（空のことがある） */
  displayName: string
  effective: MyEffective
  inMenu: boolean
  hasChildren: boolean
}

/** 自分の権限の応答 */
export interface MyPermissionNodes {
  workRole: { roleId: number; name: string } | null
  items: readonly MyPermissionNode[]
}

function isObject(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function parseNode(value: unknown): MyPermissionNode | undefined {
  if (!isObject(value) || !isObject(value.effective)) {
    return undefined
  }
  const { schemaName, tableName, columnName, displayName, inMenu, hasChildren } = value
  const { main, create, delete: remove } = value.effective
  if (
    typeof schemaName !== 'string' ||
    !isNullableString(tableName) ||
    !isNullableString(columnName) ||
    !isNullableString(displayName) ||
    typeof inMenu !== 'boolean' ||
    typeof hasChildren !== 'boolean' ||
    typeof main !== 'string' ||
    !MAIN_VALUES.includes(main) ||
    typeof create !== 'boolean' ||
    typeof remove !== 'boolean'
  ) {
    return undefined
  }
  return {
    schemaName,
    tableName,
    columnName,
    displayName: displayName ?? '',
    effective: { main: main as MainPermission, create, delete: remove },
    inMenu,
    hasChildren,
  }
}

/**
 * 自分の権限の応答を写す。形が違えば undefined。
 *
 * @param body 応答の本文
 */
export function parseMyPermissions(body: unknown): MyPermissionNodes | undefined {
  if (!isObject(body) || !Array.isArray(body.items)) {
    return undefined
  }
  let workRole: MyPermissionNodes['workRole'] = null
  if (body.workRole !== null) {
    const role = body.workRole
    if (
      !isObject(role) ||
      typeof role.roleId !== 'number' ||
      !Number.isInteger(role.roleId) ||
      typeof role.name !== 'string'
    ) {
      return undefined
    }
    workRole = { roleId: role.roleId, name: role.name }
  }
  const items: MyPermissionNode[] = []
  for (const item of body.items as unknown[]) {
    const node = parseNode(item)
    if (node === undefined) {
      return undefined
    }
    items.push(node)
  }
  return { workRole, items }
}

/** 段ごとの道（名前は問い合わせの引数） */
export function myPermissionsPath(
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
  const suffix = query.size > 0 ? `?${query.toString()}` : ''
  return `${MY_PERMISSIONS_API}/${level}${suffix}`
}

async function read(path: string): Promise<MyPermissionNodes> {
  const response = await apiRequest(path, { method: 'GET' })
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw networkError()
  }
  const nodes = parseMyPermissions(body)
  if (nodes === undefined) {
    throw networkError()
  }
  return nodes
}

/** 最上位（スキーマの段）を読む。 */
export function listMySchemas(): Promise<MyPermissionNodes> {
  return read(myPermissionsPath('schemas'))
}

/** スキーマの中のテーブルの段を読む。 */
export function listMyTables(schema: string): Promise<MyPermissionNodes> {
  return read(myPermissionsPath('tables', { schema }))
}

/** テーブルの中のカラムの段を読む。 */
export function listMyColumns(schema: string, table: string): Promise<MyPermissionNodes> {
  return read(myPermissionsPath('columns', { schema, table }))
}
