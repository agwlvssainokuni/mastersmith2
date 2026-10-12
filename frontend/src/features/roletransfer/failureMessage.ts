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
// 権限の受け渡しの画面の失敗の文言の鍵を選ぶ純粋な関数（Intent 261004-role-menu の U6 の D3・D4・D25・D26、
// functional-spec.md 5節、L3、NFR1.8）。応答の code と状態コードだけを見て、サーバーの detail・title は読まない。
// ROLE_TRANSFER_INVALID の誤りの一覧は画面が別に読む。ROLE_BUSY は行の排他と入口の排他（RoleTransferSlot）を区別
// しない文言にする（NFR 設計 security-design.md 4節）。413 は code が無くても大きすぎるの文言、415 は汎用。
import type { ApiError } from '../../shared/api-client/apiError'

/** 画面が扱う code */
export const ROLE_TRANSFER_ERROR_CODES = [
  'ROLE_TRANSFER_INVALID',
  'ROLE_TRANSFER_STALE',
  'DSL_NOT_APPLIED',
  'ROLE_NO_CHANGE',
  'ROLE_BUSY',
  'PAYLOAD_TOO_LARGE',
] as const

/** 画面が扱う code */
export type RoleTransferErrorCode = (typeof ROLE_TRANSFER_ERROR_CODES)[number]

const KNOWN_CODES: ReadonlySet<string> = new Set(ROLE_TRANSFER_ERROR_CODES)

/** 汎用の失敗の文言の鍵（D3） */
export const GENERAL_FAILURE_KEY = 'roletransfer.error.general'

/** 失敗の code（画面が扱うものだけ。ほかは undefined） */
export function knownCode(error: unknown): RoleTransferErrorCode | undefined {
  const apiError = error as ApiError | undefined
  if (
    typeof apiError === 'object' &&
    apiError !== null &&
    apiError.kind === 'response' &&
    typeof apiError.code === 'string' &&
    KNOWN_CODES.has(apiError.code)
  ) {
    return apiError.code as RoleTransferErrorCode
  }
  return undefined
}

function status(error: unknown): number | undefined {
  const apiError = error as ApiError | undefined
  return typeof apiError === 'object' && apiError !== null && apiError.kind === 'response'
    ? apiError.status
    : undefined
}

/** 失敗の文言の鍵を選ぶ（5節の表。ROLE_TRANSFER_INVALID は一覧の見出しの文言）。 */
export function failureMessageKey(error: unknown): string {
  switch (knownCode(error)) {
    case 'ROLE_TRANSFER_INVALID':
      return 'roletransfer.invalid.summary'
    case 'ROLE_TRANSFER_STALE':
      return 'roletransfer.stale'
    case 'DSL_NOT_APPLIED':
      return 'roletransfer.noDsl'
    case 'ROLE_NO_CHANGE':
      return 'roletransfer.noChange'
    case 'ROLE_BUSY':
      return 'roletransfer.busy'
    case 'PAYLOAD_TOO_LARGE':
      return 'roletransfer.file.tooLarge'
    case undefined:
      return status(error) === 413 ? 'roletransfer.file.tooLarge' : GENERAL_FAILURE_KEY
  }
}
