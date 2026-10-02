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
// 項目ごとの誤りの読み取りのテスト（security-design.md の 3.2、NFR9.2・NFR9.6）。
// 性質ベースのテスト（fast-check）は、失敗したときに seed と path をテストの出力に示す（再現は fc.assert の第2引数に渡す）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { readFieldErrors } from './fieldErrors'

// 読む項目の一覧はテストの中に持つ（共通の置き場のテストが機能のフォルダーを読み込まないため）。
// 値はプリファレンスの画面の2つのフォーム（formChecks.ts）と同じ項目の名前。
const PREFERENCES_FIELDS = ['displayName', 'language', 'theme', 'fontSize'] as const
const PASSWORD_FIELDS = ['currentPassword', 'newPassword', 'newPasswordConfirmation'] as const

const DETAIL_MARKER = 'server-detail-marker'

describe('readFieldErrors', () => {
  it('returns the known fields with their reasons in order', () => {
    const problem = {
      code: 'VALIDATION_FAILED',
      detail: DETAIL_MARKER,
      fieldErrors: [
        { field: 'displayName', reason: 'TOO_LONG' },
        { field: 'language', reason: 'INVALID_VALUE' },
      ],
    }
    expect(readFieldErrors(problem, PREFERENCES_FIELDS)).toEqual([
      { field: 'displayName', reason: 'TOO_LONG' },
      { field: 'language', reason: 'INVALID_VALUE' },
    ])
  })

  it('drops broken items and fields that the screen does not know', () => {
    const problem = {
      fieldErrors: [
        'displayName',
        null,
        [],
        { field: 1, reason: 'REQUIRED' },
        { field: 'displayName', reason: 5 },
        { field: 'email', reason: 'REQUIRED' },
        { field: 'currentPassword', reason: 'REQUIRED' },
        { field: 'theme' },
        { field: 'fontSize', reason: 'SOMETHING_NEW' },
      ],
    }
    expect(readFieldErrors(problem, PREFERENCES_FIELDS)).toEqual([
      { field: 'fontSize', reason: 'SOMETHING_NEW' },
    ])
    expect(readFieldErrors(problem, PASSWORD_FIELDS)).toEqual([
      { field: 'currentPassword', reason: 'REQUIRED' },
    ])
  })

  it('keeps only the first error of the same field', () => {
    const problem = {
      fieldErrors: [
        { field: 'newPassword', reason: 'TOO_SHORT' },
        { field: 'newPassword', reason: 'TOO_LONG' },
      ],
    }
    expect(readFieldErrors(problem, PASSWORD_FIELDS)).toEqual([
      { field: 'newPassword', reason: 'TOO_SHORT' },
    ])
  })

  it('returns an empty list when there is no problem or no list', () => {
    for (const problem of [
      undefined,
      null,
      42,
      'VALIDATION_FAILED',
      [],
      {},
      { fieldErrors: null },
      { fieldErrors: 'displayName' },
      { fieldErrors: { field: 'displayName', reason: 'REQUIRED' } },
    ]) {
      expect(readFieldErrors(problem, PREFERENCES_FIELDS)).toEqual([])
    }
  })

  it('does not copy the detail or any other value of the problem', () => {
    const problem = {
      detail: DETAIL_MARKER,
      traceId: 'trace-marker',
      fieldErrors: [{ field: 'displayName', reason: 'REQUIRED', detail: DETAIL_MARKER }],
    }
    const read = readFieldErrors(problem, PREFERENCES_FIELDS)
    expect(JSON.stringify(read)).not.toContain(DETAIL_MARKER)
    expect(JSON.stringify(read)).not.toContain('trace-marker')
    expect(read).toEqual([{ field: 'displayName', reason: 'REQUIRED' }])
  })

  it('keeps the reason as the server sent it', () => {
    const problem = { fieldErrors: [{ field: 'newPasswordConfirmation', reason: 'mismatch' }] }
    expect(readFieldErrors(problem, PASSWORD_FIELDS)).toEqual([
      { field: 'newPasswordConfirmation', reason: 'mismatch' },
    ])
  })

  it('never throws for any JSON value or any value (property)', () => {
    fc.assert(
      fc.property(fc.oneof(fc.jsonValue(), fc.anything()), (problem) => {
        readFieldErrors(problem, PREFERENCES_FIELDS)
        readFieldErrors({ fieldErrors: problem }, PASSWORD_FIELDS)
        return true
      }),
    )
  })

  it('returns only known field names without duplicates (property)', () => {
    const item = fc.record(
      {
        field: fc.oneof(
          fc.constantFrom(...PREFERENCES_FIELDS, ...PASSWORD_FIELDS, 'email', ''),
          fc.anything(),
        ),
        reason: fc.oneof(fc.string(), fc.anything()),
      },
      { requiredKeys: [] },
    )
    fc.assert(
      fc.property(fc.array(fc.oneof(item, fc.anything())), (list) => {
        const read = readFieldErrors({ fieldErrors: list }, PREFERENCES_FIELDS)
        const names = read.map((error) => error.field)
        return (
          names.every((name) => (PREFERENCES_FIELDS as readonly string[]).includes(name)) &&
          new Set(names).size === names.length &&
          read.every((error) => typeof error.reason === 'string')
        )
      }),
    )
  })
})
