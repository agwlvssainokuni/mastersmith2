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
// 業務データの読み取りの API 3本を呼ぶ関数（Intent 261016-business-data-views の U3 の L1、frontend-components.md の 3節、
// U3-NFR1.9・4.23・4.24）。
// - スキーマ名・テーブル名・definitionId・主キー・検索の値は、すべて本文で送る（道・問い合わせに載せない。契約 CQ1: A）。
// - 本文は JSON.stringify を1回だけ行い、その文字列の UTF-8 のバイト数を測ってから同じ文字列を送る。上限を超えたら
//   送らずに、413 PAYLOAD_TOO_LARGE と同じ形の失敗を投げる（制御の文字と組になっていないサロゲートのエスケープも数える）。
// - 今の ApiClient（apiRequest）を通す。失敗は今の ApiError のまま投げ、自動では送り直さない。
// - 応答は unknown として受け、写しの関数で確かめる。形が違えば通信の失敗（D5）。
// - 値を console・ブラウザの保存に出さない。
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { apiRequest } from '../../shared/api-client/apiClient'
import { networkError, type ApiResponseError } from '../../shared/api-client/apiError'
import { utf8ByteLength } from '../../shared/validation/codePoints'
import { MAX_READ_BODY_BYTES } from './businessDataLimits'
import { parseRowDetail, parseRowPage, parseTableDefinition } from './businessDataParse'
import type {
  DefinitionId,
  PageSize,
  RowDetail,
  RowKey,
  RowPage,
  SearchCondition,
  SortKey,
  TableDefinition,
} from './businessDataTypes'

/** 業務データの API の道（backend の BusinessDataPaths） */
export const BUSINESS_DATA_PATHS = {
  definition: '/api/tables/definition',
  search: '/api/tables/rows/search',
  get: '/api/tables/rows/get',
} as const

/** 本文の上限を超えたときの失敗（サーバーの 413 と同じ形） */
export function payloadTooLargeError(): ApiResponseError {
  return { kind: 'response', status: 413, code: 'PAYLOAD_TOO_LARGE' }
}

/**
 * 本文を1回だけ直列化し、上限の中なら POST で送り、応答の本文を unknown で返す。
 *
 * @param path API の道
 * @param payload 送る本文
 * @param limit 本文の上限（バイト）
 */
export async function postWithinLimit(
  path: string,
  payload: unknown,
  limit: number,
): Promise<unknown> {
  const body = JSON.stringify(payload)
  if (utf8ByteLength(body) > limit) {
    throw payloadTooLargeError()
  }
  const response = await apiRequest(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
  })
  try {
    return (await response.json()) as unknown
  } catch {
    throw networkError()
  }
}

/** 写しの関数を通し、形が違えば通信の失敗にする */
function orNetworkError<T>(value: T | undefined): T {
  if (value === undefined) {
    throw networkError()
  }
  return value
}

/**
 * テーブルの定義を読む。
 *
 * @param ref スキーマ名とテーブル名
 * @param definitionId 記憶にある定義の識別（あれば送り、変わっていれば definitionChanged が true で返る）
 */
export async function getDefinition(
  ref: TableRef,
  definitionId?: DefinitionId,
): Promise<TableDefinition> {
  const payload =
    definitionId === undefined
      ? { schema: ref.schemaName, table: ref.tableName }
      : { schema: ref.schemaName, table: ref.tableName, definitionId }
  const body = await postWithinLimit(BUSINESS_DATA_PATHS.definition, payload, MAX_READ_BODY_BYTES)
  return orNetworkError(parseTableDefinition(body, ref))
}

/** 一覧と検索の要求 */
export interface SearchRowsRequest {
  ref: TableRef
  definitionId: DefinitionId
  conditions: readonly SearchCondition[]
  /** 並べ替え（無ければサーバーの既定の並び） */
  sort: SortKey | null
  page: number
  size: PageSize
}

/**
 * 行を検索する（一覧）。
 *
 * @param request 一覧と検索の要求
 */
export async function searchRows(request: SearchRowsRequest): Promise<RowPage> {
  const payload = {
    schema: request.ref.schemaName,
    table: request.ref.tableName,
    definitionId: request.definitionId,
    conditions: request.conditions,
    ...(request.sort === null ? {} : { sort: request.sort }),
    page: request.page,
    size: request.size,
  }
  const body = await postWithinLimit(BUSINESS_DATA_PATHS.search, payload, MAX_READ_BODY_BYTES)
  return orNetworkError(parseRowPage(body))
}

/**
 * 行の詳細を読む。
 *
 * @param ref スキーマ名とテーブル名
 * @param definitionId 定義の識別
 * @param key 行を指す主キー
 */
export async function getRow(
  ref: TableRef,
  definitionId: DefinitionId,
  key: RowKey,
): Promise<RowDetail> {
  const payload = { schema: ref.schemaName, table: ref.tableName, definitionId, key }
  const body = await postWithinLimit(BUSINESS_DATA_PATHS.get, payload, MAX_READ_BODY_BYTES)
  return orNetworkError(parseRowDetail(body))
}
