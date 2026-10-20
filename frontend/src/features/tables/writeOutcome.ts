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
// 書き込み（作成・更新・削除）の結果を「保存した」「保存されていない」「保存されたか分からない」の3つに分ける純粋な関数
// （Intent 261016-business-data-views の U3 の L5、計画 4.3 の R-01・R-03、4.6 の表、U3-NFR1.9）。
// - 「保存されていない」と言うのは、状態コードと code の組が下の表の既知の組のときだけ（1つの code に1つの状態コード。
//   team.md の Code Style）。応答が届かない・500・502・504・ほかの 5xx・503 で code が無いか知らない code・本文が
//   Problem Details でない応答・表に無い 4xx は、すべて「分からない」に倒す（保存されたかもしれないものを「保存されていない」と
//   言わない）。503 BUSINESS_TARGET_DB_TIMEOUT は確定の段の失敗で、「分からない」。
// - 「保存した」とみなすのは、期待する状態（作成 201、更新・削除 200）で、本文の種類（content-type）が JSON（problem+json を
//   除く）のとき。本文が読めない・形が違うときも保存したとみなし、作成の主キーは null（一覧へ戻る）にする。content-type が
//   JSON でない 2xx（ログインのやり直しの HTML など）と、期待と違う 2xx は「分からない」。
// - 値・主キー・行の値を結果に入れない（主キーは作成の成功の key だけ）。
import { parseBusinessProblem, parseCreatedKey } from './businessDataParse'
import type { BusinessProblem, RowKey } from './businessDataTypes'

/** 書き込みの操作 */
export type WriteOperation = 'create' | 'update' | 'delete'

/** 書き込みの結果 */
export type WriteOutcome =
  /** 保存した（作成では、詳細へ移れる行の主キー。無ければ null） */
  | { kind: 'saved'; key: RowKey | null }
  /** 保存されていない（既知の状態コードと code の組） */
  | { kind: 'not-saved'; problem: BusinessProblem }
  /** 保存されたか分からない */
  | { kind: 'unknown' }

/** 「保存されていない」と言える、状態コードと code の組（計画 4.3 の R-01） */
export const NOT_SAVED_CODES: Readonly<Record<string, number>> = {
  BUSINESS_TARGET_DB_UNAVAILABLE: 503,
  BUSINESS_ROW_OPERATION_NOT_ALLOWED: 409,
  BUSINESS_ROW_NOT_UNIQUE: 409,
  BUSINESS_TARGET_DB_MISMATCH: 409,
  BUSINESS_DEFINITION_CHANGED: 409,
  BUSINESS_WRITE_NOT_CONFIGURED: 409,
  BUSINESS_DUPLICATE_VALUE: 409,
  BUSINESS_REFERENCE_NOT_FOUND: 409,
  BUSINESS_SAVE_REJECTED: 409,
  VALIDATION_FAILED: 400,
  PAYLOAD_TOO_LARGE: 413,
  ACCESS_DENIED: 403,
  BUSINESS_ROW_NOT_FOUND: 404,
  BUSINESS_TABLE_NOT_FOUND: 404,
  BUSINESS_DSL_NOT_APPLIED: 404,
}

/** 操作ごとの成功の状態コード */
const SUCCESS_STATUS: Readonly<Record<WriteOperation, number>> = {
  create: 201,
  update: 200,
  delete: 200,
}

/**
 * 失敗（API の呼び出しが投げた値）を分ける。
 *
 * @param error API の呼び出しの失敗（ApiError か、それ以外の値）
 */
export function classifyWriteFailure(error: unknown): WriteOutcome {
  const problem = parseBusinessProblem(error)
  if (problem === undefined || problem.code === undefined) {
    return { kind: 'unknown' }
  }
  const expected = Object.hasOwn(NOT_SAVED_CODES, problem.code)
    ? NOT_SAVED_CODES[problem.code]
    : undefined
  return expected === problem.status ? { kind: 'not-saved', problem } : { kind: 'unknown' }
}

/** 成功の状態コードの応答の中身 */
export interface WriteSuccessResponse {
  status: number
  /** 応答の Content-Type（無ければ null） */
  contentType: string | null
  /** JSON として読めた本文（読めないか空なら undefined） */
  body: unknown
}

/** content-type が JSON（problem+json を除く）か */
function isJsonContentType(contentType: string | null): boolean {
  if (contentType === null) {
    return false
  }
  const mediaType = contentType.split(';')[0].trim().toLowerCase()
  if (mediaType === 'application/problem+json') {
    return false
  }
  return mediaType === 'application/json' || /^application\/[a-z0-9.+-]+\+json$/.test(mediaType)
}

/**
 * 成功の状態コード（2xx）の応答を分ける。
 *
 * @param operation 書き込みの操作
 * @param response 応答の状態コード・種類・本文
 */
export function classifyWriteSuccess(
  operation: WriteOperation,
  response: WriteSuccessResponse,
): WriteOutcome {
  if (response.status !== SUCCESS_STATUS[operation] || !isJsonContentType(response.contentType)) {
    return { kind: 'unknown' }
  }
  return { kind: 'saved', key: operation === 'create' ? parseCreatedKey(response.body) : null }
}
