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
// 確かめ・完了の失敗の振り分けのテスト（functional-spec.md の 5節の表、NFR3.1・NFR9.2）。
import { describe, expect, it } from 'vitest'
import type { ApiError } from '../../shared/api-client/apiError'
import { completeFailureKind, verifyFailureKind } from './failureKind'

function response(status: number, code?: string, detail?: string): ApiError {
  const error: ApiError =
    code === undefined ? { kind: 'response', status } : { kind: 'response', status, code }
  if (detail !== undefined) {
    Object.defineProperty(error, 'problem', {
      value: { status, code, detail, title: detail, type: detail },
      enumerable: false,
    })
  }
  return error
}

const network: ApiError = { kind: 'network' }

describe('verifyFailureKind', () => {
  it('sends 404 REGISTRATION_LINK_INVALID to unavailable', () => {
    expect(verifyFailureKind(response(404, 'REGISTRATION_LINK_INVALID'))).toBe('unavailable')
  })

  it('sends 404 with another code or without a code to loadFailed', () => {
    expect(verifyFailureKind(response(404, 'NOT_FOUND'))).toBe('loadFailed')
    expect(verifyFailureKind(response(404))).toBe('loadFailed')
  })

  it('sends the other statuses, network failures and unknown values to loadFailed', () => {
    for (const status of [400, 401, 403, 429, 500, 503]) {
      expect(verifyFailureKind(response(status, 'VALIDATION_FAILED'))).toBe('loadFailed')
      expect(verifyFailureKind(response(status))).toBe('loadFailed')
    }
    expect(verifyFailureKind(response(400, 'REGISTRATION_LINK_INVALID'))).toBe('loadFailed')
    expect(verifyFailureKind(network)).toBe('loadFailed')
    expect(verifyFailureKind(new Error('boom'))).toBe('loadFailed')
    expect(verifyFailureKind(undefined)).toBe('loadFailed')
  })
})

describe('completeFailureKind', () => {
  it('sends 404 REGISTRATION_LINK_INVALID to unavailable and 400 VALIDATION_FAILED to validationFailed', () => {
    expect(completeFailureKind(response(404, 'REGISTRATION_LINK_INVALID'))).toBe('unavailable')
    expect(completeFailureKind(response(400, 'VALIDATION_FAILED'))).toBe('validationFailed')
  })

  it('sends 400 and 404 with another code or without a code to submitFailed', () => {
    expect(completeFailureKind(response(400, 'OTHER'))).toBe('submitFailed')
    expect(completeFailureKind(response(400))).toBe('submitFailed')
    expect(completeFailureKind(response(404, 'VALIDATION_FAILED'))).toBe('submitFailed')
    expect(completeFailureKind(response(404))).toBe('submitFailed')
  })

  it('sends the other statuses and network failures to submitFailed', () => {
    for (const status of [401, 403, 409, 429, 500, 503]) {
      expect(completeFailureKind(response(status, 'REGISTRATION_LINK_INVALID'))).toBe(
        'submitFailed',
      )
    }
    expect(completeFailureKind(network)).toBe('submitFailed')
    expect(completeFailureKind(null)).toBe('submitFailed')
  })

  it('does not change the result by the detail, title or type of the problem', () => {
    const marker = 'server-detail-marker'
    expect(verifyFailureKind(response(404, 'REGISTRATION_LINK_INVALID', marker))).toBe(
      'unavailable',
    )
    expect(verifyFailureKind(response(500, undefined, marker))).toBe('loadFailed')
    expect(completeFailureKind(response(400, 'VALIDATION_FAILED', marker))).toBe('validationFailed')
    expect(completeFailureKind(response(404, 'REGISTRATION_LINK_INVALID', 'expired'))).toBe(
      completeFailureKind(response(404, 'REGISTRATION_LINK_INVALID', 'already used')),
    )
  })
})
