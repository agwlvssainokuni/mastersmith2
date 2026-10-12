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
// 権限の受け渡しの画面の失敗の文言の鍵のテスト（Intent 261004-role-menu の U6 の D3・D4・D25・D26、NFR1.8）。
import { describe, expect, it } from 'vitest'
import { networkError, type ApiResponseError } from '../../shared/api-client/apiError'
import { failureMessageKey, GENERAL_FAILURE_KEY } from './failureMessage'

function responseError(
  status: number,
  code?: string,
  problem?: Readonly<Record<string, unknown>>,
): ApiResponseError {
  const error: ApiResponseError =
    code === undefined ? { kind: 'response', status } : { kind: 'response', status, code }
  if (problem !== undefined) {
    Object.defineProperty(error, 'problem', { value: problem, enumerable: false })
  }
  return error
}

describe('roletransfer failureMessage', () => {
  it('chooses the message by the code', () => {
    expect(failureMessageKey(responseError(409, 'ROLE_TRANSFER_STALE'))).toBe('roletransfer.stale')
    expect(failureMessageKey(responseError(409, 'DSL_NOT_APPLIED'))).toBe('roletransfer.noDsl')
    expect(failureMessageKey(responseError(409, 'ROLE_NO_CHANGE'))).toBe('roletransfer.noChange')
    expect(failureMessageKey(responseError(409, 'ROLE_BUSY'))).toBe('roletransfer.busy')
    expect(failureMessageKey(responseError(400, 'ROLE_TRANSFER_INVALID'))).toBe(
      'roletransfer.invalid.summary',
    )
    expect(failureMessageKey(responseError(413, 'PAYLOAD_TOO_LARGE'))).toBe(
      'roletransfer.file.tooLarge',
    )
  })

  it('uses the too-large message for 413 without a code and the general message for 415', () => {
    expect(failureMessageKey(responseError(413))).toBe('roletransfer.file.tooLarge')
    expect(failureMessageKey(responseError(415, 'UNSUPPORTED_MEDIA_TYPE'))).toBe(
      GENERAL_FAILURE_KEY,
    )
  })

  it('does not change the message by the detail or the title of the server', () => {
    expect(
      failureMessageKey(responseError(409, 'ROLE_BUSY', { detail: 'MARK', title: 'MARK' })),
    ).toBe('roletransfer.busy')
  })

  it('uses the general message for unknown codes, responses without a code and network errors', () => {
    expect(failureMessageKey(responseError(409, 'GROUP_BUSY'))).toBe(GENERAL_FAILURE_KEY)
    expect(failureMessageKey(responseError(500))).toBe(GENERAL_FAILURE_KEY)
    expect(failureMessageKey(networkError())).toBe(GENERAL_FAILURE_KEY)
    expect(failureMessageKey(null)).toBe(GENERAL_FAILURE_KEY)
  })
})
