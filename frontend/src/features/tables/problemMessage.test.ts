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
// 読み取りの失敗の文と、定義の読み直しの判定のテスト（Intent 261016-business-data-views の U3 の L5、D4・D6・R-05、
// 計画 Step 7・4.6）。
import { describe, expect, it } from 'vitest'
import type { BusinessProblem } from './businessDataTypes'
import {
  fieldErrorMessageKey,
  needsDefinitionReload,
  readProblemKind,
  readProblemMessageKey,
} from './problemMessage'
import { sampleDefinition } from './testing/sampleDefinition'

function problem(
  status: number,
  code?: string,
  extra: Partial<BusinessProblem> = {},
): BusinessProblem {
  return {
    status,
    ...(code === undefined ? {} : { code }),
    fieldErrors: [],
    definitionChanged: false,
    ...extra,
  }
}

describe('readProblemKind', () => {
  it.each([
    [403, 'ACCESS_DENIED', 'forbidden', 'tables.forbidden'],
    [404, 'BUSINESS_TABLE_NOT_FOUND', 'table-not-found', 'tables.problem.tableNotFound'],
    [404, 'BUSINESS_ROW_NOT_FOUND', 'row-not-found', 'tables.problem.rowNotFound'],
    [404, 'BUSINESS_DSL_NOT_APPLIED', 'dsl-not-applied', 'tables.problem.dslNotApplied'],
    [503, 'BUSINESS_TARGET_DB_UNAVAILABLE', 'unavailable', 'tables.problem.unavailable'],
    [503, 'BUSINESS_TARGET_DB_TIMEOUT', 'unavailable', 'tables.problem.unavailable'],
    [409, 'BUSINESS_TARGET_DB_MISMATCH', 'mismatch', 'tables.problem.mismatch'],
    [409, 'BUSINESS_ROW_OPERATION_NOT_ALLOWED', 'not-unique', 'tables.problem.notUnique'],
    [409, 'BUSINESS_ROW_NOT_UNIQUE', 'not-unique', 'tables.problem.notUnique'],
    [413, 'PAYLOAD_TOO_LARGE', 'payload-too-large', 'tables.problem.payloadTooLarge'],
    [400, 'VALIDATION_FAILED', 'bad-request', 'tables.problem.badRequest'],
  ])('maps %i %s to %s', (status, code, kind, key) => {
    expect(readProblemKind(problem(status, code))).toBe(kind)
    expect(readProblemMessageKey(readProblemKind(problem(status, code)))).toBe(key)
  })

  it('maps unknown codes by the status class and network failures to the load error', () => {
    expect(readProblemKind(problem(418, 'TEAPOT'))).toBe('unknown-client')
    expect(readProblemKind(problem(502))).toBe('unknown-server')
    expect(readProblemKind(problem(403))).toBe('forbidden')
    expect(readProblemKind(problem(413))).toBe('payload-too-large')
    expect(readProblemKind(undefined)).toBe('network')
    expect(readProblemMessageKey('network')).toBe('tables.loadError')
    expect(readProblemKind(problem(400, 'toString'))).toBe('unknown-client')
  })
})

describe('fieldErrorMessageKey', () => {
  it('uses the reason key for known reasons and the invalid value key otherwise', () => {
    expect(fieldErrorMessageKey('RANGE_REVERSED')).toBe('tables.error.RANGE_REVERSED')
    expect(fieldErrorMessageKey('PATTERN_TIMEOUT')).toBe('tables.error.PATTERN_TIMEOUT')
    expect(fieldErrorMessageKey('SOMETHING_NEW')).toBe('tables.error.INVALID_VALUE')
  })
})

describe('needsDefinitionReload', () => {
  const definition = sampleDefinition()

  it('is true when definitionChanged is set, also on a 400', () => {
    expect(
      needsDefinitionReload(
        problem(400, 'VALIDATION_FAILED', { definitionChanged: true }),
        definition,
      ),
    ).toBe(true)
    expect(needsDefinitionReload(problem(409, 'BUSINESS_DEFINITION_CHANGED'), definition)).toBe(
      true,
    )
  })

  it('is true when a field error points to a field the current definition does not know', () => {
    expect(
      needsDefinitionReload(
        problem(400, 'VALIDATION_FAILED', {
          fieldErrors: [{ field: 'removed', reason: 'NOT_ALLOWED' }],
        }),
        definition,
      ),
    ).toBe(true)
  })

  it('is false for field errors of known fields only and for errors with an empty field', () => {
    expect(
      needsDefinitionReload(
        problem(400, 'VALIDATION_FAILED', {
          fieldErrors: [
            { field: 'code', reason: 'INVALID_VALUE' },
            { field: 'name', reason: 'TOO_LONG' },
          ],
        }),
        definition,
      ),
    ).toBe(false)
    expect(
      needsDefinitionReload(
        problem(400, 'VALIDATION_FAILED', { fieldErrors: [{ field: '', reason: 'NOT_ALLOWED' }] }),
        definition,
      ),
    ).toBe(false)
    expect(needsDefinitionReload(undefined, definition)).toBe(false)
  })
})
