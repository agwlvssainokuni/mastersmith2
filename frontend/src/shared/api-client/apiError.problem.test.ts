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
// Problem Details の本文を渡す口のテスト（DSL の管理画面の BR2.6・BR2.7）。
import { describe, expect, it } from 'vitest'
import { readProblem, toApiError } from './apiError'

function response(body: unknown, status: number): Response {
  return new Response(typeof body === 'string' ? body : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

describe('apiError problem details', () => {
  it('passes the whole Problem Details body including the extra members', async () => {
    const body = {
      type: 'about:blank',
      title: 'Unprocessable',
      status: 422,
      code: 'DSL_INVALID',
      total: 2,
      errors: [{ kind: 'SYNTAX', line: 1, column: 2, path: null, message: '誤り' }],
    }

    const error = await toApiError(response(body, 422))

    expect(error.code).toBe('DSL_INVALID')
    expect(error.problem).toEqual(body)
    expect(error.problem?.total).toBe(2)
  })

  it('keeps the enumerable shape of the error unchanged', async () => {
    const error = await toApiError(response({ code: 'DSL_BUSY', status: 503 }, 503))

    expect(Object.keys(error).sort()).toEqual(['code', 'kind', 'status'])
    expect(JSON.parse(JSON.stringify(error))).toEqual({
      kind: 'response',
      status: 503,
      code: 'DSL_BUSY',
    })
    expect(error.problem).toBeDefined()
  })

  it('has no problem when the body cannot be read as JSON', async () => {
    const error = await toApiError(response('<html>proxy error</html>', 502))

    expect(error.problem).toBeUndefined()
    expect(error).toEqual({ kind: 'response', status: 502 })
  })

  it('has no problem when the JSON body is not Problem Details', async () => {
    expect((await toApiError(response([1, 2, 3], 500))).problem).toBeUndefined()
    expect((await toApiError(response({ total: 3 }, 500))).problem).toBeUndefined()
    expect((await toApiError(response({ status: '500' }, 500))).problem).toBeUndefined()
    expect((await toApiError(response('null', 500))).problem).toBeUndefined()
  })

  it('reads a body with only a standard member as Problem Details', async () => {
    expect(await readProblem(response({ title: 'Bad Request' }, 400))).toEqual({
      title: 'Bad Request',
    })
    expect(await readProblem(response({ status: 404 }, 404))).toEqual({ status: 404 })
  })
})
