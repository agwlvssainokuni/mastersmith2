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
// 表示の状態を決める純粋な関数のテスト（性質ベースのテストを含む）。「権限が無い」の判定は本物の
// isAdminForbidden を通す（骨組みの useAdminForbidden と同じ判定。差し替えない、unit-test-instructions.md 6節）。
// 性質ベースのテストが失敗したときは、報告の seed と path を fc.assert(property, { seed, path }) に一時的に渡し、
// このファイルを名指しして再現する（直した後に元に戻す）。
import fc from 'fast-check'
import { describe, expect, it, vi } from 'vitest'
import { isAdminForbidden } from '../../shared/api-client/adminForbidden'
import { ADMIN_CHECK_PATH } from './adminApi'
import { statusFromError } from './adminAreaStatus'

/** 本物の判定を通し、渡されたパスを記録する偽の report */
function realReport() {
  return vi.fn((error: unknown, apiPath: string) => isAdminForbidden(apiPath, error))
}

describe('adminAreaStatus', () => {
  it('maps a 403 ACCESS_DENIED to the forbidden state and passes the check path', () => {
    const report = realReport()

    expect(statusFromError({ kind: 'response', status: 403, code: 'ACCESS_DENIED' }, report)).toBe(
      'Forbidden',
    )
    expect(report).toHaveBeenCalledTimes(1)
    expect(report).toHaveBeenCalledWith(
      { kind: 'response', status: 403, code: 'ACCESS_DENIED' },
      ADMIN_CHECK_PATH,
    )
  })

  it('maps a server failure to the generic error', () => {
    expect(
      statusFromError({ kind: 'response', status: 500, code: 'INTERNAL_ERROR' }, realReport()),
    ).toBe('Error')
  })

  it('maps a network failure to the generic error', () => {
    expect(statusFromError({ kind: 'network' }, realReport())).toBe('Error')
  })

  it('maps every 403 without ACCESS_DENIED to the generic error', () => {
    fc.assert(
      fc.property(
        fc.option(
          fc.string().filter((code) => code !== 'ACCESS_DENIED'),
          { nil: undefined },
        ),
        (code) => {
          expect(statusFromError({ kind: 'response', status: 403, code }, realReport())).toBe(
            'Error',
          )
        },
      ),
    )
  })

  it('maps every other status to the generic error', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 100, max: 599 }).filter((status) => status !== 403),
        (status) => {
          expect(
            statusFromError({ kind: 'response', status, code: 'ACCESS_DENIED' }, realReport()),
          ).toBe('Error')
        },
      ),
    )
  })

  it('maps an unknown value to the generic error rather than failing', () => {
    fc.assert(
      fc.property(fc.anything(), (value) => {
        expect(['Error', 'Forbidden']).toContain(statusFromError(value, realReport()))
      }),
    )
    expect(statusFromError(undefined, realReport())).toBe('Error')
    expect(statusFromError(null, realReport())).toBe('Error')
  })
})
