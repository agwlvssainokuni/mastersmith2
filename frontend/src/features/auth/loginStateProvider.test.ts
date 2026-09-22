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
// ログイン状態の提供元のテスト（BR8.7）。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetApiClient } from '../../shared/api-client/apiClient'
import * as authApi from './authApi'
import { login, resetAuthSession, subscribe } from './authSession'
import { loginStateProvider, toLoginState } from './loginStateProvider'

const tokens = {
  accessToken: 'access-token',
  expiresAt: '2026-09-22T00:05:00Z',
  user: { email: 'admin@example.com', admin: true },
}

beforeEach(() => {
  resetAuthSession()
  resetApiClient()
  vi.restoreAllMocks()
})

describe('loginStateProvider', () => {
  it('waits for the first restore before answering', async () => {
    let resolveRefresh: (value: typeof tokens) => void = () => {}
    vi.spyOn(authApi, 'requestRefresh').mockReturnValue(
      new Promise<typeof tokens>((resolve) => {
        resolveRefresh = resolve
      }),
    )

    const pending = loginStateProvider.getLoginState()
    resolveRefresh(tokens)

    expect(await pending).toEqual({ loggedIn: true, admin: true, displayName: 'admin@example.com' })
  })

  it('answers not logged in when the restore fails', async () => {
    vi.spyOn(authApi, 'requestRefresh').mockRejectedValue({ kind: 'response', status: 401 })

    expect(await loginStateProvider.getLoginState()).toEqual({ loggedIn: false, admin: false })
  })

  it('reports admin as false while nobody is logged in', () => {
    expect(toLoginState()).toEqual({ loggedIn: false, admin: false })
  })

  it('uses the email address as the display name', async () => {
    vi.spyOn(authApi, 'requestLogin').mockResolvedValue(tokens)

    await login('admin@example.com', 'パスワード')

    expect(toLoginState().displayName).toBe('admin@example.com')
  })

  it('publishes the same subscribe function as the session', () => {
    expect(loginStateProvider.subscribe).toBe(subscribe)
  })
})
