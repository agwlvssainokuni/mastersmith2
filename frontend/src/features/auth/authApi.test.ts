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
// 認証の API の呼び出しのテスト。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import {
  LOGIN_PATH,
  LOGOUT_PATH,
  REFRESH_PATH,
  requestLogin,
  requestLogout,
  requestRefresh,
} from './authApi'

const tokenResult = {
  accessToken: 'access-token',
  expiresAt: '2026-09-22T00:05:00Z',
  user: { email: 'user@example.com', admin: false },
}

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

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('authApi', () => {
  it('posts the credentials to the login path with same-origin credentials', async () => {
    fetchMock.mockResolvedValueOnce(json(200, tokenResult))

    const result = await requestLogin('user@example.com', 'パスワード')

    expect(result).toEqual(tokenResult)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(LOGIN_PATH)
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('same-origin')
    expect(init.body).toBe(JSON.stringify({ email: 'user@example.com', password: 'パスワード' }))
  })

  it('posts to the refresh path and returns the new tokens', async () => {
    fetchMock.mockResolvedValueOnce(json(200, tokenResult))

    expect(await requestRefresh()).toEqual(tokenResult)
    expect(fetchMock.mock.calls[0][0]).toBe(REFRESH_PATH)
  })

  it('posts to the logout path', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await requestLogout()

    expect(fetchMock.mock.calls[0][0]).toBe(LOGOUT_PATH)
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('POST')
  })

  it('turns a failed login into a typed error with the code', async () => {
    fetchMock.mockResolvedValueOnce(json(401, { code: 'AUTHENTICATION_FAILED' }))

    await expect(requestLogin('user@example.com', 'まちがい')).rejects.toEqual({
      kind: 'response',
      status: 401,
      code: 'AUTHENTICATION_FAILED',
    })
  })

  it('turns a failed connection into a network error', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('failed to fetch'))

    await expect(requestRefresh()).rejects.toEqual({ kind: 'network' })
  })
})
