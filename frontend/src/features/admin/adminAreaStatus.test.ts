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
// 表示の状態を決める純粋な関数のテスト（性質ベースのテストを含む）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { statusFromError } from './adminAreaStatus'

describe('adminAreaStatus', () => {
  it('maps 403 to the not found screen', () => {
    expect(statusFromError({ kind: 'response', status: 403, code: 'ACCESS_DENIED' })).toBe(
      'NotFound',
    )
  })

  it('maps a server failure to the generic error', () => {
    expect(statusFromError({ kind: 'response', status: 500, code: 'INTERNAL_ERROR' })).toBe('Error')
  })

  it('maps a network failure to the generic error', () => {
    expect(statusFromError({ kind: 'network' })).toBe('Error')
  })

  it('always maps 403 to the not found screen whatever the code is', () => {
    fc.assert(
      fc.property(fc.option(fc.string(), { nil: undefined }), (code) => {
        expect(statusFromError({ kind: 'response', status: 403, code })).toBe('NotFound')
      }),
    )
  })

  it('maps every other status to the generic error', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 100, max: 599 }).filter((status) => status !== 403),
        (status) => {
          expect(statusFromError({ kind: 'response', status })).toBe('Error')
        },
      ),
    )
  })

  it('maps an unknown value to the generic error rather than failing', () => {
    fc.assert(
      fc.property(fc.anything(), (value) => {
        expect(['Error', 'NotFound']).toContain(statusFromError(value))
      }),
    )
    expect(statusFromError(undefined)).toBe('Error')
    expect(statusFromError(null)).toBe('Error')
  })
})
