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
// エラーの変換のテスト。
import { describe, expect, it } from 'vitest'
import { networkError, readErrorCode, toApiError } from './apiError'

function response(body: unknown, status = 401): Response {
  return new Response(typeof body === 'string' ? body : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

describe('apiError', () => {
  it('reads the code of a Problem Details body', async () => {
    const error = await toApiError(response({ code: 'REFRESH_FAILED', title: 'x' }))

    expect(error).toEqual({ kind: 'response', status: 401, code: 'REFRESH_FAILED' })
  })

  it('omits the code when the body is not Problem Details', async () => {
    expect(await toApiError(response('not json', 500))).toEqual({ kind: 'response', status: 500 })
    expect(await toApiError(response({ title: 'no code' }, 400))).toEqual({
      kind: 'response',
      status: 400,
    })
  })

  it('omits the code when it is not a string', async () => {
    expect(await readErrorCode(response({ code: 42 }))).toBeUndefined()
  })

  it('represents a failed connection as a network error', () => {
    expect(networkError()).toEqual({ kind: 'network' })
  })
})
