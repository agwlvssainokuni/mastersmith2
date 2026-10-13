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
// 置き場の問い合わせの API（契約 C9 の GET /api/me/table-access）を呼ぶ関数と応答の写し（Intent 261004-role-menu の
// U7 の L9、D3・D21、NFR1.4）。
// - 引数は URLSearchParams の schema・table だけ。画面の道の item は送らない。利用者を指す値を送らない。
// - ApiClient（apiRequest）を通す。拒否（403 ACCESS_DENIED・400 VALIDATION_FAILED）は ApiError のまま投げる。
// - 応答は型の項目だけを写し、崩れた本文・知らない main は通信の失敗にする。
import type { LocalizedLabel } from '../../app/navigation/navigationApi'
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { apiRequest } from '../../shared/api-client/apiClient'
import { networkError } from '../../shared/api-client/apiError'

/** 置き場の問い合わせの API の道 */
export const TABLE_ACCESS_API = '/api/me/table-access'

/** 見てよいテーブルの答え */
export interface TableAccess {
  schemaName: string
  tableName: string
  displayName: LocalizedLabel
  main: 'READ' | 'FULL'
}

function isObject(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * 置き場の問い合わせの応答を写す。形が違えば undefined。
 *
 * @param body 応答の本文
 */
export function parseTableAccess(body: unknown): TableAccess | undefined {
  if (!isObject(body) || !isObject(body.displayName)) {
    return undefined
  }
  const { schemaName, tableName, main } = body
  const { ja, en } = body.displayName
  if (
    typeof schemaName !== 'string' ||
    typeof tableName !== 'string' ||
    typeof ja !== 'string' ||
    typeof en !== 'string' ||
    (main !== 'READ' && main !== 'FULL')
  ) {
    return undefined
  }
  return { schemaName, tableName, displayName: { ja, en }, main }
}

/** 問い合わせの道（引数は URLSearchParams でエンコードする） */
export function tableAccessPath(ref: TableRef): string {
  const query = new URLSearchParams({ schema: ref.schemaName, table: ref.tableName })
  return `${TABLE_ACCESS_API}?${query.toString()}`
}

/**
 * そのテーブルを今の作業ロールで見てよいかを問い合わせる。
 *
 * @param ref スキーマ名とテーブル名
 */
export async function getTableAccess(ref: TableRef): Promise<TableAccess> {
  const response = await apiRequest(tableAccessPath(ref), { method: 'GET' })
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw networkError()
  }
  const access = parseTableAccess(body)
  if (access === undefined) {
    throw networkError()
  }
  return access
}
