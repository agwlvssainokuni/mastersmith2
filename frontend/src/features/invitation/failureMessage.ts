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
// 失敗から文言の鍵を選ぶ（functional-spec.md の D4、NFR9.1）。サーバーの code から選び、知らない code・code の無い応答・
// 通信の失敗は、状態コードの種類（4xx・5xx・通信の失敗）に応じた一般の文言にする。`detail`・`title` は使わない
// （DSL の failureMessage.ts と同じ考え方で、この機能の code を持つ）。
import type { ApiError } from '../../shared/api-client/apiError'

/** 画面が扱う code */
export const INVITATION_ERROR_CODES = [
  'VALIDATION_FAILED',
  'INVITATION_EMAIL_REGISTERED',
  'INVITATION_ALREADY_PENDING',
  'INVITATION_NOT_CONFIGURED',
  'INVITATION_NOT_FOUND',
] as const

/** 画面が扱う code */
export type InvitationErrorCode = (typeof INVITATION_ERROR_CODES)[number]

const KNOWN_CODES: ReadonlySet<string> = new Set(INVITATION_ERROR_CODES)

/** 失敗の code ごとの文言の鍵（503 は理由の文を別に出すため、理由が読めないときの文） */
const CODE_MESSAGE_KEYS: Readonly<Record<InvitationErrorCode, string>> = {
  VALIDATION_FAILED: 'invitation.error.VALIDATION_FAILED',
  INVITATION_EMAIL_REGISTERED: 'invitation.error.INVITATION_EMAIL_REGISTERED',
  INVITATION_ALREADY_PENDING: 'invitation.error.INVITATION_ALREADY_PENDING',
  INVITATION_NOT_CONFIGURED: 'invitation.unavailable.unknown',
  INVITATION_NOT_FOUND: 'invitation.error.INVITATION_NOT_FOUND',
}

/** 失敗の code（画面が扱うものだけ。ほかは undefined） */
export function knownCode(error: unknown): InvitationErrorCode | undefined {
  const apiError = error as ApiError | undefined
  if (apiError?.kind === 'response' && apiError.code && KNOWN_CODES.has(apiError.code)) {
    return apiError.code as InvitationErrorCode
  }
  return undefined
}

/** 失敗の状態コード（通信の失敗などは undefined） */
export function failureStatus(error: unknown): number | undefined {
  const apiError = error as ApiError | undefined
  return apiError?.kind === 'response' ? apiError.status : undefined
}

/** 状態コードの種類ごとの一般の文言の鍵（code を見ない。一覧が読めなかったときなど） */
export function generalFailureKey(error: unknown): string {
  const status = failureStatus(error)
  if (status === undefined) {
    return 'invitation.errorGeneral.network'
  }
  return status >= 500 ? 'invitation.errorGeneral.server' : 'invitation.errorGeneral.client'
}

/** 失敗の文言の鍵（知っている code ならその文言、ほかは一般の文言） */
export function failureMessageKey(error: unknown): string {
  const code = knownCode(error)
  return code ? CODE_MESSAGE_KEYS[code] : generalFailureKey(error)
}
