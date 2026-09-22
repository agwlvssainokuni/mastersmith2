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
// 登録のテスト（BR8.1、BR8.6、BR8.7、NFR7.1）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { resetApiClient } from '../../shared/api-client/apiClient'
import * as authApi from './authApi'
import { getAccessToken, getAuthSnapshot, login, resetAuthSession } from './authSession'
import { loginStateProvider } from './loginStateProvider'
import { registration } from './registration'

const tokens = {
  accessToken: 'access-token',
  expiresAt: '2026-09-22T00:05:00Z',
  user: { email: 'user@example.com', admin: false },
}

beforeEach(() => {
  resetAuthSession()
  resetApiClient()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('auth registration', () => {
  it('passes the checks of the application shell', () => {
    expect(() => validateRegistrations([registration], baseMessageKeys)).not.toThrow()
  })

  it('registers the login screen as a public standalone login route', () => {
    expect(registration.routes).toHaveLength(1)
    expect(registration.routes?.[0]).toMatchObject({
      path: '/login',
      role: 'LOGIN',
      layout: 'STANDALONE',
      access: 'PUBLIC',
    })
  })

  it('registers the login state provider of the session', () => {
    expect(registration.loginStateProvider).toBe(loginStateProvider)
  })

  it('has the same message keys in Japanese and in English', () => {
    const ja = Object.keys(registration.messages?.ja ?? {}).sort()
    const en = Object.keys(registration.messages?.en ?? {}).sort()

    expect(ja).toEqual(en)
    expect(ja.every((key) => key.startsWith('auth.'))).toBe(true)
  })

  it('logs out and drops the token from the user menu item', async () => {
    vi.spyOn(authApi, 'requestLogin').mockResolvedValue(tokens)
    const logoutRequest = vi.spyOn(authApi, 'requestLogout').mockResolvedValue()
    await login('user@example.com', 'パスワード')

    registration.userMenuItems?.[0].action()
    await vi.waitFor(() => expect(getAuthSnapshot().status).toBe('LoggedOut'))

    expect(logoutRequest).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBeNull()
    expect(registration.userMenuItems?.[0].labelKey).toBe('auth.menu.logout')
  })
})
