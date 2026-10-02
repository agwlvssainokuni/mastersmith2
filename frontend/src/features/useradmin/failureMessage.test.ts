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
// 失敗から文言の鍵を選ぶテスト（functional-spec.md の D8・7.5・7.6、frontend-components.md の 5.2、NFR3.2、
// 計画 9節の Q-C）。detail・title に目印を入れても鍵が変わらない（読まない）ことも確かめる。
import { describe, expect, it } from 'vitest'
import type { ApiError } from '../../shared/api-client/apiError'
import {
  failureMessageKey,
  failureStatus,
  knownCode,
  USER_ADMIN_ERROR_CODES,
} from './failureMessage'

function apiError(status: number, code?: string): ApiError {
  const error: ApiError = code ? { kind: 'response', status, code } : { kind: 'response', status }
  Object.defineProperty(error, 'problem', {
    value: { status, code, detail: 'user-admin-detail-marker', title: 'user-admin-detail-marker' },
    enumerable: false,
  })
  return error
}

const NETWORK: ApiError = { kind: 'network' }

describe('failureMessageKey', () => {
  it('picks the reason message for each of the six operation codes', () => {
    expect(failureMessageKey(apiError(404, 'USER_NOT_FOUND'), 'operation')).toBe(
      'useradmin.error.USER_NOT_FOUND',
    )
    for (const code of [
      'USER_ADMIN_SELF_OPERATION',
      'USER_ADMIN_TARGET_SUSPENDED',
      'USER_ADMIN_NO_CHANGE',
      'USER_ADMIN_LAST_ADMIN',
      'USER_ADMIN_BUSY',
    ]) {
      expect(failureMessageKey(apiError(409, code), 'operation')).toBe(`useradmin.error.${code}`)
    }
  })

  it('uses the general operation message for unknown codes, no code, 5xx, 400 and network failures', () => {
    expect(failureMessageKey(apiError(409, 'SOMETHING_NEW'), 'operation')).toBe(
      'useradmin.error.general',
    )
    expect(failureMessageKey(apiError(400), 'operation')).toBe('useradmin.error.general')
    expect(failureMessageKey(apiError(400, 'VALIDATION_FAILED'), 'operation')).toBe(
      'useradmin.error.general',
    )
    expect(failureMessageKey(apiError(500), 'operation')).toBe('useradmin.error.general')
    expect(failureMessageKey(NETWORK, 'operation')).toBe('useradmin.error.general')
  })

  it('shows the search error for a list 400 VALIDATION_FAILED and the load failure otherwise', () => {
    expect(failureMessageKey(apiError(400, 'VALIDATION_FAILED'), 'list')).toBe(
      'useradmin.search.invalid',
    )
    expect(failureMessageKey(apiError(500), 'list')).toBe('useradmin.list.loadFailed')
    expect(failureMessageKey(NETWORK, 'list')).toBe('useradmin.list.loadFailed')
  })

  it('treats a list 400 without a code (an HTML 400) as a load failure', () => {
    expect(failureMessageKey(apiError(400), 'list')).toBe('useradmin.list.loadFailed')
  })

  it('picks not-found, the form message and the general save message when saving', () => {
    expect(failureMessageKey(apiError(404, 'USER_NOT_FOUND'), 'save')).toBe(
      'useradmin.edit.notFound',
    )
    expect(failureMessageKey(apiError(400, 'VALIDATION_FAILED'), 'save')).toBe(
      'useradmin.edit.formInvalid',
    )
    expect(failureMessageKey(apiError(400), 'save')).toBe('useradmin.edit.failed')
    expect(failureMessageKey(apiError(409, 'USER_ADMIN_BUSY'), 'save')).toBe(
      'useradmin.edit.failed',
    )
    expect(failureMessageKey(NETWORK, 'save')).toBe('useradmin.edit.failed')
  })

  it('reads only the code and the status, not detail or title', () => {
    const error = apiError(409, 'USER_ADMIN_LAST_ADMIN')
    expect(failureMessageKey(error, 'operation')).not.toContain('marker')
    expect(knownCode(error)).toBe('USER_ADMIN_LAST_ADMIN')
    expect(failureStatus(error)).toBe(409)
  })

  it('knows only the listed codes and tolerates values that are not errors', () => {
    expect(USER_ADMIN_ERROR_CODES).toHaveLength(7)
    expect(knownCode(apiError(403, 'ACCESS_DENIED'))).toBeUndefined()
    expect(knownCode(undefined)).toBeUndefined()
    expect(knownCode(new Error('boom'))).toBeUndefined()
    expect(failureStatus(NETWORK)).toBeUndefined()
    expect(failureStatus(null)).toBeUndefined()
    expect(failureMessageKey(new Error('boom'), 'operation')).toBe('useradmin.error.general')
  })

  it('uses a key that exists for every code of the operation scene', () => {
    const keys = USER_ADMIN_ERROR_CODES.filter((code) => code !== 'VALIDATION_FAILED').map((code) =>
      failureMessageKey(apiError(409, code), 'operation'),
    )
    expect(new Set(keys).size).toBe(6)
  })
})
