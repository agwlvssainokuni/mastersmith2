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
// 書き込みの結果の分け方のテスト（Intent 261016-business-data-views の U3 の L5、計画 Step 27、4.3 の R-01・R-03）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { networkError, type ApiResponseError } from '../../shared/api-client/apiError'
import {
  NOT_SAVED_CODES,
  classifyWriteFailure,
  classifyWriteSuccess,
  type WriteOperation,
} from './writeOutcome'

function responseError(
  status: number,
  code?: string,
  problem?: Record<string, unknown>,
): ApiResponseError {
  const error: ApiResponseError =
    code === undefined ? { kind: 'response', status } : { kind: 'response', status, code }
  if (problem !== undefined) {
    Object.defineProperty(error, 'problem', { value: problem, enumerable: false })
  }
  return error
}

describe('classifyWriteFailure', () => {
  it.each(Object.entries(NOT_SAVED_CODES))(
    'treats %s with status %d as not saved',
    (code, status) => {
      const outcome = classifyWriteFailure(responseError(status, code, { code, status }))
      expect(outcome.kind).toBe('not-saved')
      if (outcome.kind === 'not-saved') {
        expect(outcome.problem.code).toBe(code)
      }
    },
  )

  it.each([
    ['a network failure', networkError()],
    ['503 timeout of the commit step', responseError(503, 'BUSINESS_TARGET_DB_TIMEOUT')],
    ['503 without a code', responseError(503)],
    ['503 with an unknown code', responseError(503, 'SOMETHING_ELSE')],
    ['500', responseError(500, 'INTERNAL_ERROR')],
    ['502 without a body', responseError(502)],
    ['504 without a body', responseError(504)],
    ['401 after the refresh failed', responseError(401, 'AUTHENTICATION_REQUIRED')],
    ['a known code with an unexpected status', responseError(500, 'BUSINESS_SAVE_REJECTED')],
    ['400 with an unlisted code', responseError(400, 'MALFORMED_REQUEST')],
    ['a value that is not an API error', new Error('x')],
  ])('treats %s as unknown', (_name, error) => {
    expect(classifyWriteFailure(error)).toEqual({ kind: 'unknown' })
  })

  it('keeps the field errors of a not-saved response', () => {
    const outcome = classifyWriteFailure(
      responseError(409, 'BUSINESS_DUPLICATE_VALUE', {
        code: 'BUSINESS_DUPLICATE_VALUE',
        fieldErrors: [{ field: 'code', reason: 'DUPLICATE' }],
      }),
    )
    expect(outcome).toMatchObject({
      kind: 'not-saved',
      problem: { fieldErrors: [{ field: 'code', reason: 'DUPLICATE' }] },
    })
  })
})

describe('classifyWriteSuccess', () => {
  it('reads the key of a created row', () => {
    expect(
      classifyWriteSuccess('create', {
        status: 201,
        contentType: 'application/json',
        body: { definitionId: { dslHash: 'h', workRoleId: 1 }, key: { code: 'A/1 #' } },
      }),
    ).toEqual({ kind: 'saved', key: { code: 'A/1 #' } })
  })

  it('returns no key when the created row has none, the body is empty or the shape differs', () => {
    for (const body of [
      { definitionId: { dslHash: 'h', workRoleId: 1 } },
      undefined,
      [1],
      { key: { a: 1 } },
      { key: {} },
    ]) {
      expect(
        classifyWriteSuccess('create', {
          status: 201,
          contentType: 'application/json; charset=UTF-8',
          body,
        }),
      ).toEqual({ kind: 'saved', key: null })
    }
  })

  it.each<[WriteOperation, number]>([
    ['update', 200],
    ['delete', 200],
  ])('treats %s with status %d and a JSON body as saved', (operation, status) => {
    expect(
      classifyWriteSuccess(operation, { status, contentType: 'application/json', body: {} }),
    ).toEqual({ kind: 'saved', key: null })
  })

  it.each([
    ['an HTML body', 201, 'text/html'],
    ['no content type', 201, null],
    ['a problem+json body', 201, 'application/problem+json'],
    ['an unexpected 2xx status', 200, 'application/json'],
    ['204 without a body', 204, 'application/json'],
  ])('treats %s as unknown', (_name, status, contentType) => {
    expect(classifyWriteSuccess('create', { status, contentType, body: undefined })).toEqual({
      kind: 'unknown',
    })
  })
})

describe('writeOutcome properties', () => {
  it('never says "not saved" unless the status and the code are a known pair', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 100, max: 599 }),
        fc.option(fc.oneof(fc.string(), fc.constantFrom(...Object.keys(NOT_SAVED_CODES))), {
          nil: undefined,
        }),
        fc.jsonValue(),
        (status, code, problem) => {
          const outcome = classifyWriteFailure(
            responseError(
              status,
              code,
              typeof problem === 'object' && problem !== null && !Array.isArray(problem)
                ? (problem as Record<string, unknown>)
                : undefined,
            ),
          )
          if (outcome.kind === 'not-saved') {
            expect(code).toBeDefined()
            expect(Object.hasOwn(NOT_SAVED_CODES, code as string)).toBe(true)
            expect(NOT_SAVED_CODES[code as string]).toBe(status)
          } else {
            expect(outcome.kind).toBe('unknown')
          }
        },
      ),
    )
  })

  it('never throws for any success response', () => {
    fc.assert(
      fc.property(
        fc.constantFrom<WriteOperation>('create', 'update', 'delete'),
        fc.integer({ min: 200, max: 299 }),
        fc.option(fc.string(), { nil: null }),
        fc.jsonValue(),
        (operation, status, contentType, body) => {
          const outcome = classifyWriteSuccess(operation, { status, contentType, body })
          expect(['saved', 'unknown']).toContain(outcome.kind)
        },
      ),
    )
  })
})
