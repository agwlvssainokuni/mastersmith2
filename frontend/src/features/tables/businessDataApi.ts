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
// - 書き込みの3本（B6）は、例外を投げずに結果を「保存した」「保存されていない」「分からない」に分けて返す（writeOutcome.ts）。
//   definitionId は必ず送る（無いと 409 BUSINESS_DEFINITION_CHANGED。U2 の G12）。本文の上限は作成・更新が 4,194,304 バイト、
//   削除が 65,536 バイト。上限を超えたら送らずに「保存されていない」（413）にする。
// - 他テーブル参照（Intent 261029-reference-lookup-cleanup の U3、frontend-components.md の 3.3）: 定義の用途（purpose）、
//   参照先の検索とラベルの1件読み。参照元の名前・条件の値・ラベルを引く値は本文だけに置く。ラベルの1件読みだけが
//   取り消しの signal を受ける（performance-design.md 2.1・R-01）。
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { apiRequest } from '../../shared/api-client/apiClient'
import { networkError, type ApiResponseError } from '../../shared/api-client/apiError'
import { utf8ByteLength } from '../../shared/validation/codePoints'
import { MAX_READ_BODY_BYTES, MAX_WRITE_BODY_BYTES } from './businessDataLimits'
import {
  parseReferenceLabel,
  parseReferenceRowPage,
  parseRowDetail,
  parseRowPage,
  parseTableDefinition,
} from './businessDataParse'
import {
  classifyWriteFailure,
  classifyWriteSuccess,
  type WriteOperation,
  type WriteOutcome,
} from './writeOutcome'
import type {
  DefinitionId,
  DefinitionPurpose,
  PageSize,
  ReferenceCondition,
  ReferenceLabel,
  ReferenceRowPage,
  ReferenceSource,
  RowDetail,
  RowKey,
  RowPage,
  RowValues,
  SearchCondition,
  SortKey,
  TableDefinition,
} from './businessDataTypes'

/** 業務データの API の道（backend の BusinessDataPaths） */
export const BUSINESS_DATA_PATHS = {
  definition: '/api/tables/definition',
  search: '/api/tables/rows/search',
  get: '/api/tables/rows/get',
  /** 作成（POST）と更新（PATCH）。U2 */
  rows: '/api/tables/rows',
  /** 削除（POST）。U2 */
  delete: '/api/tables/rows/delete',
  /** 参照先の検索とラベルの1件読み（POST）。Intent 261029-reference-lookup-cleanup の U1 */
  referenceRows: '/api/tables/reference/rows',
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
 * @param signal 取り消しの合図（ラベルの1件読みだけが渡す。無ければ今までと同じ）
 */
export async function postWithinLimit(
  path: string,
  payload: unknown,
  limit: number,
  signal?: AbortSignal,
): Promise<unknown> {
  const body = JSON.stringify(payload)
  if (utf8ByteLength(body) > limit) {
    throw payloadTooLargeError()
  }
  const response = await apiRequest(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
    ...(signal === undefined ? {} : { signal }),
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
 * @param purpose 用途（作成・更新の画面だけが付ける。無ければ今の本文のまま。D1）
 */
export async function getDefinition(
  ref: TableRef,
  definitionId?: DefinitionId,
  purpose?: DefinitionPurpose,
): Promise<TableDefinition> {
  const payload = {
    schema: ref.schemaName,
    table: ref.tableName,
    ...(definitionId === undefined ? {} : { definitionId }),
    ...(purpose === undefined ? {} : { purpose }),
  }
  const body = await postWithinLimit(BUSINESS_DATA_PATHS.definition, payload, MAX_READ_BODY_BYTES)
  return orNetworkError(parseTableDefinition(body, ref))
}

/**
 * 参照先を検索する（ピッカー。D22〜D27）。値が空の条件は送らない。
 *
 * @param source 参照元のスキーマ・テーブル・カラム
 * @param conditions 検索の条件
 * @param page ページ（1 から）
 */
export async function searchReferenceRows(
  source: ReferenceSource,
  conditions: readonly ReferenceCondition[],
  page: number,
): Promise<ReferenceRowPage> {
  const payload = {
    schema: source.schemaName,
    table: source.tableName,
    column: source.column,
    conditions: conditions
      .filter((condition) => condition.value !== '')
      .map((condition) => ({ column: condition.column, value: condition.value })),
    page,
  }
  const body = await postWithinLimit(
    BUSINESS_DATA_PATHS.referenceRows,
    payload,
    MAX_READ_BODY_BYTES,
  )
  return orNetworkError(parseReferenceRowPage(body))
}

/**
 * 参照先のラベルを1件読む（D18）。写せなければ（0件・2件以上・形の崩れ）通信の失敗にする。
 *
 * @param source 参照元のスキーマ・テーブル・カラム
 * @param value 保存されている値
 * @param signal 取り消しの合図（画面を離れた・読み直したとき）
 */
export async function getReferenceLabel(
  source: ReferenceSource,
  value: string,
  signal?: AbortSignal,
): Promise<ReferenceLabel> {
  const payload = {
    schema: source.schemaName,
    table: source.tableName,
    column: source.column,
    value,
  }
  const body = await postWithinLimit(
    BUSINESS_DATA_PATHS.referenceRows,
    payload,
    MAX_READ_BODY_BYTES,
    signal,
  )
  return orNetworkError(parseReferenceLabel(body))
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

/**
 * 書き込みの要求を送り、結果を分ける（例外を投げない）。
 *
 * @param operation 書き込みの操作
 * @param method HTTP のメソッド
 * @param path API の道
 * @param payload 送る本文
 * @param limit 本文の上限（バイト）
 */
async function sendWrite(
  operation: WriteOperation,
  method: 'POST' | 'PATCH',
  path: string,
  payload: unknown,
  limit: number,
): Promise<WriteOutcome> {
  const body = JSON.stringify(payload)
  if (utf8ByteLength(body) > limit) {
    return classifyWriteFailure(payloadTooLargeError())
  }
  let response: Response
  try {
    response = await apiRequest(path, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body,
    })
  } catch (error: unknown) {
    return classifyWriteFailure(error)
  }
  let parsed: unknown = undefined
  try {
    const text = await response.text()
    parsed = text === '' ? undefined : (JSON.parse(text) as unknown)
  } catch {
    parsed = undefined
  }
  return classifyWriteSuccess(operation, {
    status: response.status,
    contentType: response.headers.get('Content-Type'),
    body: parsed,
  })
}

/**
 * 行を作る。
 *
 * @param ref スキーマ名とテーブル名
 * @param definitionId 定義の識別
 * @param values 送るカラムの値（変えたカラムだけ。formDiff で作る）
 */
export function createRow(
  ref: TableRef,
  definitionId: DefinitionId,
  values: RowValues,
): Promise<WriteOutcome> {
  const payload = { schema: ref.schemaName, table: ref.tableName, definitionId, values }
  return sendWrite('create', 'POST', BUSINESS_DATA_PATHS.rows, payload, MAX_WRITE_BODY_BYTES)
}

/**
 * 行を更新する。
 *
 * @param ref スキーマ名とテーブル名
 * @param definitionId 定義の識別
 * @param key 行を指す主キー
 * @param values 送るカラムの値（変えたカラムだけ）
 */
export function updateRow(
  ref: TableRef,
  definitionId: DefinitionId,
  key: RowKey,
  values: RowValues,
): Promise<WriteOutcome> {
  const payload = { schema: ref.schemaName, table: ref.tableName, definitionId, key, values }
  return sendWrite('update', 'PATCH', BUSINESS_DATA_PATHS.rows, payload, MAX_WRITE_BODY_BYTES)
}

/**
 * 行を削除する。
 *
 * @param ref スキーマ名とテーブル名
 * @param definitionId 定義の識別
 * @param key 行を指す主キー
 */
export function deleteRow(
  ref: TableRef,
  definitionId: DefinitionId,
  key: RowKey,
): Promise<WriteOutcome> {
  const payload = { schema: ref.schemaName, table: ref.tableName, definitionId, key }
  return sendWrite('delete', 'POST', BUSINESS_DATA_PATHS.delete, payload, MAX_READ_BODY_BYTES)
}
