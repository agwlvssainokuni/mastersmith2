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
// 確かめ・完了の失敗の振り分け（functional-spec.md の 5節の表、D5・D10、NFR3.1・NFR9.2、security-design.md の 3.3）。
// 状態コードと code だけで分け、Problem Details の detail・title・type・fieldErrors は読まない。React・window に触れない。
import type { ApiError } from '../../shared/api-client/apiError'

/** 確かめの失敗の行き先 */
export type VerifyFailureKind = 'unavailable' | 'loadFailed'

/** 完了の失敗の行き先 */
export type CompleteFailureKind = 'unavailable' | 'validationFailed' | 'submitFailed'

/** リンクが使えないことを表す code（契約 C6） */
export const REGISTRATION_LINK_INVALID = 'REGISTRATION_LINK_INVALID'

/** 入力の検証の失敗を表す code（契約 C6） */
export const VALIDATION_FAILED = 'VALIDATION_FAILED'

/** 応答の状態コードと code を読む（ApiError の応答でなければ undefined）。 */
function responseOf(error: unknown): { status: number; code?: string } | undefined {
  if (typeof error !== 'object' || error === null) {
    return undefined
  }
  const candidate = error as Partial<ApiError>
  if (candidate.kind !== 'response' || typeof candidate.status !== 'number') {
    return undefined
  }
  return { status: candidate.status, code: candidate.code }
}

function isLinkInvalid(error: unknown): boolean {
  const response = responseOf(error)
  return response?.status === 404 && response.code === REGISTRATION_LINK_INVALID
}

/** 確かめの失敗を振り分ける。404 REGISTRATION_LINK_INVALID は unavailable、ほかと通信の失敗は loadFailed。 */
export function verifyFailureKind(error: unknown): VerifyFailureKind {
  return isLinkInvalid(error) ? 'unavailable' : 'loadFailed'
}

/**
 * 完了の失敗を振り分ける。404 REGISTRATION_LINK_INVALID は unavailable、400 VALIDATION_FAILED は validationFailed、
 * ほかと通信の失敗は submitFailed。
 */
export function completeFailureKind(error: unknown): CompleteFailureKind {
  if (isLinkInvalid(error)) {
    return 'unavailable'
  }
  const response = responseOf(error)
  if (response?.status === 400 && response.code === VALIDATION_FAILED) {
    return 'validationFailed'
  }
  return 'submitFailed'
}
