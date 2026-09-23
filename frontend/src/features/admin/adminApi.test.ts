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
// 確認用 API の呼び出しのテスト。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiError } from '../../shared/api-client/apiError'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import { ADMIN_CHECK_PATH, requestAdminCheck } from './adminApi'

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  resetApiClient()
  registerAuthHandlers({
    getAccessToken: () => 'access-token',
    refresh: () => Promise.resolve(false),
    onUnauthenticated: () => {},
  })
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function problem(status: number, code: string): Response {
  return new Response(JSON.stringify({ code, status }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

describe('adminApi', () => {
  it('calls the check path with GET through the shared client', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await requestAdminCheck()

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(ADMIN_CHECK_PATH)
    expect(init.method).toBe('GET')
    expect(init.credentials).toBe('same-origin')
  })

  it('attaches the access token so that the server decides on the verified user', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await requestAdminCheck()

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer access-token')
  })

  it('resolves without a value when the server answers 204', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(requestAdminCheck()).resolves.toBeUndefined()
  })

  it('throws a response error carrying the status and the code for 403', async () => {
    fetchMock.mockResolvedValueOnce(problem(403, 'ACCESS_DENIED'))

    await expect(requestAdminCheck()).rejects.toEqual({
      kind: 'response',
      status: 403,
      code: 'ACCESS_DENIED',
    } satisfies ApiError)
  })

  it('throws a response error for a server failure', async () => {
    fetchMock.mockResolvedValueOnce(problem(500, 'INTERNAL_ERROR'))

    await expect(requestAdminCheck()).rejects.toMatchObject({ kind: 'response', status: 500 })
  })

  it('throws a network error when the request never reaches the server', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('failed to fetch'))

    await expect(requestAdminCheck()).rejects.toEqual({ kind: 'network' } satisfies ApiError)
  })
})
