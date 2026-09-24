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
// 失敗から文言の鍵を選ぶ（BR5.5、NFR5.6）。サーバーの code から選び、知らない code・code の無い応答・通信の失敗は、
// 状態コードの種類に応じた一般の文言にする。`detail`・内部の文言・接続先は使わない。
import type { ApiError } from '../../shared/api-client/apiError'

/** 画面が文言を持つ code（DSL_BUSY を含む 9 つ） */
export const DSL_ERROR_CODES = [
  'DSL_INVALID',
  'DSL_TOO_LARGE',
  'DSL_PREVIEW_NOT_FOUND',
  'DSL_PREVIEW_CHANGED',
  'DSL_APPLIED_NOT_FOUND',
  'DSL_REVISION_NOT_FOUND',
  'TARGET_DB_UNCONFIGURED',
  'TARGET_DB_UNAVAILABLE',
  'DSL_BUSY',
] as const

/** 画面が文言を持つ code */
export type DslErrorCode = (typeof DSL_ERROR_CODES)[number]

const KNOWN_CODES: ReadonlySet<string> = new Set(DSL_ERROR_CODES)

/** 失敗の code（画面が文言を持つものだけ。ほかは undefined） */
export function knownCode(error: unknown): DslErrorCode | undefined {
  const apiError = error as ApiError | undefined
  if (apiError?.kind === 'response' && apiError.code && KNOWN_CODES.has(apiError.code)) {
    return apiError.code as DslErrorCode
  }
  return undefined
}

/** 失敗の状態コード（通信の失敗などは undefined） */
export function failureStatus(error: unknown): number | undefined {
  const apiError = error as ApiError | undefined
  return apiError?.kind === 'response' ? apiError.status : undefined
}

/** 失敗の文言の鍵 */
export function failureMessageKey(error: unknown): string {
  const code = knownCode(error)
  if (code) {
    return `dsl.error.${code}`
  }
  const status = failureStatus(error)
  if (status === undefined) {
    return 'dsl.errorGeneral.network'
  }
  return status >= 500 ? 'dsl.errorGeneral.server' : 'dsl.errorGeneral.client'
}
