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
// 失敗の文言の鍵のテスト（D4、NFR9.1）。知っている code はその文言、ほかは状態コードの種類ごとの一般の文言。
// `detail`・`title` は見ない。
import { describe, expect, it } from 'vitest'
import type { ApiError } from '../../shared/api-client/apiError'
import { failureMessageKey, failureStatus, generalFailureKey, knownCode } from './failureMessage'

function response(status: number, code?: string, detail?: string): ApiError {
  const error: ApiError = code ? { kind: 'response', status, code } : { kind: 'response', status }
  if (detail !== undefined) {
    Object.defineProperty(error, 'problem', {
      value: { status, code, detail, title: detail },
      enumerable: false,
    })
  }
  return error
}

describe('failureMessage', () => {
  it('picks the message of each known code', () => {
    expect(failureMessageKey(response(400, 'VALIDATION_FAILED'))).toBe(
      'invitation.error.VALIDATION_FAILED',
    )
    expect(failureMessageKey(response(409, 'INVITATION_EMAIL_REGISTERED'))).toBe(
      'invitation.error.INVITATION_EMAIL_REGISTERED',
    )
    expect(failureMessageKey(response(409, 'INVITATION_ALREADY_PENDING'))).toBe(
      'invitation.error.INVITATION_ALREADY_PENDING',
    )
    expect(failureMessageKey(response(404, 'INVITATION_NOT_FOUND'))).toBe(
      'invitation.error.INVITATION_NOT_FOUND',
    )
    expect(failureMessageKey(response(503, 'INVITATION_NOT_CONFIGURED'))).toBe(
      'invitation.unavailable.unknown',
    )
  })

  it('uses the general client message for an unknown code or no code on a 4xx', () => {
    expect(failureMessageKey(response(403, 'ACCESS_DENIED'))).toBe('invitation.errorGeneral.client')
    expect(failureMessageKey(response(409))).toBe('invitation.errorGeneral.client')
  })

  it('uses the general server message on a 5xx', () => {
    expect(failureMessageKey(response(500, 'INTERNAL_ERROR'))).toBe(
      'invitation.errorGeneral.server',
    )
    expect(failureMessageKey(response(502))).toBe('invitation.errorGeneral.server')
  })

  it('uses the general network message when no response came', () => {
    expect(failureMessageKey({ kind: 'network' })).toBe('invitation.errorGeneral.network')
    expect(failureMessageKey(new Error('boom'))).toBe('invitation.errorGeneral.network')
    expect(failureStatus({ kind: 'network' })).toBeUndefined()
  })

  it('does not let the detail or title change the key', () => {
    expect(failureMessageKey(response(404, 'INVITATION_NOT_FOUND', 'server-detail-marker'))).toBe(
      'invitation.error.INVITATION_NOT_FOUND',
    )
    expect(failureMessageKey(response(418, undefined, 'server-detail-marker'))).toBe(
      'invitation.errorGeneral.client',
    )
  })

  it('reads the general key by the kind of status only, ignoring the code', () => {
    expect(generalFailureKey(response(400, 'VALIDATION_FAILED'))).toBe(
      'invitation.errorGeneral.client',
    )
    expect(knownCode(response(400, 'VALIDATION_FAILED'))).toBe('VALIDATION_FAILED')
    expect(knownCode(response(400, 'OTHER'))).toBeUndefined()
  })
})
