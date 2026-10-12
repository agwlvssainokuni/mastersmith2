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
// 権限の受け渡しの API（契約 C7 の書き出し・確かめ・適用）を呼ぶ関数（Intent 261004-role-menu の U6、
// frontend-components.md の 5節、L2・L8・L10、NFR1.5・NFR1.9・NFR1.10・NFR6.6）。
// - 書き出しは apiDownload で受け、保存の名前は safeFileName、上限を超えた書き出しはヘッダー
//   `X-Role-Transfer-Exceeds-Import-Limit` が `true` のときだけ知らせる（判定はサーバーの1か所）。
// - 確かめと適用は本文を YAML のテキストのまま `Content-Type: application/yaml` で送り、適用は指紋をヘッダー
//   `X-Role-Transfer-Fingerprint` に入れる。応答は型の項目だけを写し、形が違えば通信の失敗にする。
// - ROLE_TRANSFER_INVALID の誤りの一覧は Problem Details の追加の項目 errors から、決めた項目だけを写して読む
//   （行・列・場所は null がありうる。計画 2.3 の A15）。
// - 指紋・本文・結果は呼び出し側の画面の状態にだけ持ち、ブラウザの保存・コンソールに出さない。
import { apiDownload, apiRequest } from '../../../shared/api-client/apiClient'
import { networkError } from '../../../shared/api-client/apiError'
import { safeFileName } from '../safeFileName'
import {
  TRANSFER_ACTIONS,
  TRANSFER_CHANGE_REASONS,
  TRANSFER_MAIN_VALUES,
  type TransferAction,
  type TransferChange,
  type TransferChangeReason,
  type TransferCheck,
  type TransferErrorItem,
  type TransferExport,
  type TransferMainValue,
  type TransferResult,
  type TransferRolePlan,
  type TransferTarget,
  type TransferValues,
} from './types'

/** 権限の受け渡しの API の根 */
export const ROLE_TRANSFER_API_ROOT = '/api/admin/role-transfer'

/** 書き出しの道 */
export const EXPORT_PATH = `${ROLE_TRANSFER_API_ROOT}/export`

/** 確かめの道 */
export const CHECK_PATH = `${ROLE_TRANSFER_API_ROOT}/check`

/** 適用の道 */
export const APPLY_PATH = `${ROLE_TRANSFER_API_ROOT}/apply`

/** 指紋のヘッダー */
export const FINGERPRINT_HEADER = 'X-Role-Transfer-Fingerprint'

/** 上限を超えた書き出しのヘッダー */
export const EXCEEDS_HEADER = 'X-Role-Transfer-Exceeds-Import-Limit'

/** 本文の型 */
export const YAML_CONTENT_TYPE = 'application/yaml'

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

function oneOf<T extends string>(values: readonly T[], value: unknown): value is T {
  return typeof value === 'string' && (values as readonly string[]).includes(value)
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

function toTarget(value: unknown): TransferTarget | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { schemaName, tableName, columnName } = value
  if (
    typeof schemaName !== 'string' ||
    !isNullableString(tableName) ||
    !isNullableString(columnName)
  ) {
    return undefined
  }
  return { schemaName, tableName, columnName }
}

function toValues(value: unknown): TransferValues | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { main, create, delete: del } = value
  if (
    !(main === null || oneOf<TransferMainValue>(TRANSFER_MAIN_VALUES, main)) ||
    !isNullableBoolean(create) ||
    !isNullableBoolean(del)
  ) {
    return undefined
  }
  return { main, create, delete: del }
}

function toChange(value: unknown): TransferChange | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const target = toTarget(value.target)
  const before = toValues(value.before)
  const after = toValues(value.after)
  const { reason, inCurrentDsl } = value
  if (
    target === undefined ||
    before === undefined ||
    after === undefined ||
    !oneOf<TransferChangeReason>(TRANSFER_CHANGE_REASONS, reason) ||
    typeof inCurrentDsl !== 'boolean'
  ) {
    return undefined
  }
  return { target, before, after, reason, inCurrentDsl }
}

function toRolePlan(value: unknown): TransferRolePlan | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { name, roleId, action, added, changed, cleared, notInDsl } = value
  const changes = mapAll(value.changes, toChange)
  if (
    typeof name !== 'string' ||
    !(roleId === null || isInteger(roleId)) ||
    !oneOf<TransferAction>(TRANSFER_ACTIONS, action) ||
    !isInteger(added) ||
    !isInteger(changed) ||
    !isInteger(cleared) ||
    !isInteger(notInDsl) ||
    changes === undefined
  ) {
    return undefined
  }
  return { name, roleId, action, added, changed, cleared, notInDsl, changes }
}

function toCheck(value: unknown): TransferCheck | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { fingerprint } = value
  const roles = mapAll(value.roles, toRolePlan)
  const untouchedRoles = mapAll(value.untouchedRoles, (item) =>
    typeof item === 'string' ? item : undefined,
  )
  if (typeof fingerprint !== 'string' || roles === undefined || untouchedRoles === undefined) {
    return undefined
  }
  return { fingerprint, roles, untouchedRoles }
}

function toResult(value: unknown): TransferResult | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { created, replaced } = value
  return isInteger(created) && isInteger(replaced) ? { created, replaced } : undefined
}

function toErrorItem(value: unknown): TransferErrorItem | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { reason, line, column, path } = value
  if (
    typeof reason !== 'string' ||
    !(line === null || line === undefined || isInteger(line)) ||
    !(column === null || column === undefined || isInteger(column)) ||
    !(path === null || path === undefined || typeof path === 'string')
  ) {
    return undefined
  }
  return { reason, line: line ?? null, column: column ?? null, path: path ?? null }
}

/** 書き出す（GET …/export）。保存の名前と上限を超えたかを添える。 */
export async function exportRoles(): Promise<TransferExport> {
  const download = await apiDownload(EXPORT_PATH, { method: 'GET' })
  return {
    blob: download.blob,
    fileName: safeFileName(download.contentDisposition),
    exceedsImportLimit: download.headers.get(EXCEEDS_HEADER) === 'true',
  }
}

/** 確かめる（POST …/check、本文は YAML のテキスト）。 */
export async function checkTransfer(text: string): Promise<TransferCheck> {
  const response = await apiRequest(CHECK_PATH, {
    method: 'POST',
    headers: { 'Content-Type': YAML_CONTENT_TYPE },
    body: text,
  })
  return required(toCheck(await readJson(response)))
}

/** 適用する（POST …/apply、確かめと同じ本文と、指紋のヘッダー）。 */
export async function applyTransfer(text: string, fingerprint: string): Promise<TransferResult> {
  const response = await apiRequest(APPLY_PATH, {
    method: 'POST',
    headers: { 'Content-Type': YAML_CONTENT_TYPE, [FINGERPRINT_HEADER]: fingerprint },
    body: text,
  })
  return required(toResult(await readJson(response)))
}

/**
 * ROLE_TRANSFER_INVALID の誤りの一覧を読む（Problem Details の errors）。一覧が無い・1つでも形の違う要素があれば
 * 空の一覧にする（画面は件数 0 の警告と汎用の文言を出す）。
 */
export function transferErrors(error: unknown): TransferErrorItem[] {
  const problem = (error as { problem?: unknown } | null | undefined)?.problem
  if (!isObject(problem)) {
    return []
  }
  return mapAll(problem.errors, toErrorItem) ?? []
}

/** 画面が使う API の関数の集まり（テストで差し替える） */
export const roleTransferApi = {
  exportRoles,
  checkTransfer,
  applyTransfer,
}

/** 画面が使う API の関数の集まりの型 */
export type RoleTransferApi = typeof roleTransferApi
