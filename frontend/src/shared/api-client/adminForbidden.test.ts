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
// 管理の API の「権限が無い」の判定のテスト（U4 の D1、NFR1.2・NFR9.9）。性質ベースのテストを含む。
// 性質ベースのテスト（fast-check、既定の 100 回）が失敗すると、報告に seed と path が出る。
// 再現するときは、その fc.assert(property) を fc.assert(property, { seed: <seed>, path: '<path>' }) に
// 一時的に替えて、このファイルを名指しして流す。原因を直した後に元に戻す（指定を残してコミットしない）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import {
  ACCESS_DENIED,
  ADMIN_API_PREFIX,
  FORBIDDEN_STATUS,
  isAdminForbidden,
} from './adminForbidden'

const FORBIDDEN = { kind: 'response', status: 403, code: 'ACCESS_DENIED' } as const

describe('isAdminForbidden', () => {
  it('is true only for a 403 ACCESS_DENIED under the admin API', () => {
    expect(isAdminForbidden('/api/admin/check', FORBIDDEN)).toBe(true)
    expect(isAdminForbidden('/api/admin/dsl', FORBIDDEN)).toBe(true)
    expect(ADMIN_API_PREFIX).toBe('/api/admin/')
    expect(ACCESS_DENIED).toBe('ACCESS_DENIED')
    expect(FORBIDDEN_STATUS).toBe(403)
  })

  it('is false for a 403 outside the admin API and for similar paths', () => {
    expect(isAdminForbidden('/api/me/preferences', FORBIDDEN)).toBe(false)
    expect(isAdminForbidden('/api/administrator', FORBIDDEN)).toBe(false)
    expect(isAdminForbidden('/api/admin', FORBIDDEN)).toBe(false)
    expect(isAdminForbidden('/api/adminx/check', FORBIDDEN)).toBe(false)
    expect(isAdminForbidden('', FORBIDDEN)).toBe(false)
  })

  it('is false for a 403 without a code or with another code', () => {
    expect(isAdminForbidden('/api/admin/check', { kind: 'response', status: 403 })).toBe(false)
    expect(
      isAdminForbidden('/api/admin/check', {
        kind: 'response',
        status: 403,
        code: 'ORIGIN_NOT_ALLOWED',
      }),
    ).toBe(false)
    expect(
      isAdminForbidden('/api/admin/check', {
        kind: 'response',
        status: 403,
        code: 'access_denied',
      }),
    ).toBe(false)
  })

  it('is false for other statuses even with ACCESS_DENIED', () => {
    for (const status of [401, 404, 409, 500]) {
      expect(
        isAdminForbidden('/api/admin/check', { kind: 'response', status, code: ACCESS_DENIED }),
      ).toBe(false)
    }
    expect(
      isAdminForbidden('/api/admin/check', {
        kind: 'response',
        status: 401,
        code: 'AUTHENTICATION_REQUIRED',
      }),
    ).toBe(false)
    expect(
      isAdminForbidden('/api/admin/check', {
        kind: 'response',
        status: '403',
        code: ACCESS_DENIED,
      }),
    ).toBe(false)
  })

  it('is false for a network failure', () => {
    expect(isAdminForbidden('/api/admin/check', { kind: 'network' })).toBe(false)
    expect(
      isAdminForbidden('/api/admin/check', { kind: 'network', status: 403, code: ACCESS_DENIED }),
    ).toBe(false)
  })

  it('is false without throwing for unknown values', () => {
    for (const value of [null, undefined, 'ACCESS_DENIED', 403, [403], [], {}, true, Symbol('x')]) {
      expect(() => isAdminForbidden('/api/admin/check', value)).not.toThrow()
      expect(isAdminForbidden('/api/admin/check', value)).toBe(false)
    }
    expect(isAdminForbidden(undefined as unknown as string, FORBIDDEN)).toBe(false)
  })

  it('is true exactly when the path, the status and the code all match (property)', () => {
    const adminPath = fc.string().map((rest) => `${ADMIN_API_PREFIX}${rest}`)
    const otherPath = fc.oneof(
      fc.constantFrom('/api/me/preferences', '/api/administrator', '/api/admin', '/api/auth/login'),
      fc.string().filter((path) => !path.startsWith(ADMIN_API_PREFIX)),
    )
    const path = fc.oneof(
      adminPath.map((value) => ({ value, matches: true })),
      otherPath.map((value) => ({ value, matches: false })),
    )
    const status = fc.integer({ min: 100, max: 599 })
    const code = fc.option(fc.oneof(fc.constant(ACCESS_DENIED), fc.string()), { nil: undefined })
    const kind = fc.constantFrom('response', 'network', 'other')
    fc.assert(
      fc.property(path, status, code, kind, (p, s, c, k) => {
        const error = c === undefined ? { kind: k, status: s } : { kind: k, status: s, code: c }
        const expected = p.matches && k === 'response' && s === 403 && c === ACCESS_DENIED
        expect(isAdminForbidden(p.value, error)).toBe(expected)
      }),
    )
  })

  it('returns a boolean without throwing for any value and any path (property)', () => {
    fc.assert(
      fc.property(fc.string(), fc.anything(), (path, value) => {
        let result: unknown
        expect(() => {
          result = isAdminForbidden(path, value)
        }).not.toThrow()
        expect(typeof result).toBe('boolean')
      }),
    )
  })
})
