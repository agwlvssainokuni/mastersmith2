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
// AuthSession のテスト（BR8.3、BR8.4、BR8.6）。認証の API の呼び出しを差し替えて状態の移り変わりを確かめる。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetApiClient } from '../../shared/api-client/apiClient'
import type { TokenResult } from './authApi'
import {
  getAccessToken,
  getAuthSnapshot,
  initializeAuthSession,
  login,
  logout,
  refresh,
  resetAuthSession,
  restore,
  subscribe,
} from './authSession'
import * as authApi from './authApi'

const tokens: TokenResult = {
  accessToken: 'access-token',
  expiresAt: '2026-09-22T00:05:00Z',
  user: { email: 'user@example.com', admin: true },
}

beforeEach(() => {
  resetAuthSession()
  resetApiClient()
})

afterEach(() => {
  vi.restoreAllMocks()
  window.localStorage.clear()
  window.sessionStorage.clear()
})

describe('authSession', () => {
  it('starts in Restoring and becomes LoggedIn when the refresh succeeds', async () => {
    vi.spyOn(authApi, 'requestRefresh').mockResolvedValue(tokens)

    expect(getAuthSnapshot().status).toBe('Restoring')
    await restore()

    expect(getAuthSnapshot()).toEqual({ status: 'LoggedIn', user: tokens.user })
    expect(getAccessToken()).toBe('access-token')
  })

  it('becomes LoggedOut when the refresh fails and tries only once', async () => {
    const request = vi.spyOn(authApi, 'requestRefresh').mockRejectedValue({ kind: 'network' })

    await restore()
    await restore()

    expect(getAuthSnapshot()).toEqual({ status: 'LoggedOut', user: null })
    expect(getAccessToken()).toBeNull()
    expect(request).toHaveBeenCalledTimes(1)
  })

  it('keeps the token in memory only and writes nothing to browser storage', async () => {
    vi.spyOn(authApi, 'requestLogin').mockResolvedValue(tokens)

    await login('user@example.com', 'パスワード')

    expect(getAuthSnapshot().status).toBe('LoggedIn')
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
    expect(document.cookie).not.toContain('access-token')
  })

  it('propagates a failed login and stays logged out', async () => {
    vi.spyOn(authApi, 'requestLogin').mockRejectedValue({
      kind: 'response',
      status: 401,
      code: 'AUTHENTICATION_FAILED',
    })

    await expect(login('user@example.com', 'まちがい')).rejects.toMatchObject({ status: 401 })
    expect(getAuthSnapshot().status).toBe('Restoring')
    expect(getAccessToken()).toBeNull()
  })

  it('clears the token on logout even when the API call fails', async () => {
    vi.spyOn(authApi, 'requestLogin').mockResolvedValue(tokens)
    vi.spyOn(authApi, 'requestLogout').mockRejectedValue({ kind: 'network' })
    await login('user@example.com', 'パスワード')

    await logout()

    expect(getAuthSnapshot()).toEqual({ status: 'LoggedOut', user: null })
    expect(getAccessToken()).toBeNull()
  })

  it('becomes LoggedOut when a later refresh fails', async () => {
    vi.spyOn(authApi, 'requestLogin').mockResolvedValue(tokens)
    await login('user@example.com', 'パスワード')
    vi.spyOn(authApi, 'requestRefresh').mockRejectedValue({ kind: 'response', status: 401 })

    expect(await refresh()).toBe(false)
    expect(getAuthSnapshot().status).toBe('LoggedOut')
  })

  it('notifies subscribers on every change until they unsubscribe', async () => {
    const listener = vi.fn()
    const unsubscribe = subscribe(listener)
    vi.spyOn(authApi, 'requestLogin').mockResolvedValue(tokens)
    vi.spyOn(authApi, 'requestLogout').mockResolvedValue()

    await login('user@example.com', 'パスワード')
    await logout()
    unsubscribe()
    await login('user@example.com', 'パスワード')

    expect(listener).toHaveBeenCalledTimes(2)
  })

  it('registers the handlers so that the ApiClient can refresh and be told about logouts', async () => {
    vi.spyOn(authApi, 'requestLogin').mockResolvedValue(tokens)
    vi.spyOn(authApi, 'requestRefresh').mockRejectedValue({ kind: 'response', status: 401 })
    initializeAuthSession()
    await login('user@example.com', 'パスワード')

    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' }), {
        status: 401,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { apiFetch } = await import('../../shared/api-client/apiClient')
    await apiFetch('/api/items')
    vi.unstubAllGlobals()

    expect(getAuthSnapshot().status).toBe('LoggedOut')
  })
})
