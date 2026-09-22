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
// ApiClient のテスト（BR8.5）。fetch を差し替えて、トークンの付与と 401 での更新・送り直しを確かめる。
import fc from 'fast-check'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, apiRequest, registerAuthHandlers, resetApiClient } from './apiClient'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function unauthorized(): Response {
  return jsonResponse(401, { code: 'AUTHENTICATION_REQUIRED' })
}

function ok(): Response {
  return jsonResponse(200, { ok: true })
}

const refresh = vi.fn<() => Promise<boolean>>()
const onUnauthenticated = vi.fn()
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  resetApiClient()
  refresh.mockReset()
  onUnauthenticated.mockReset()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  registerAuthHandlers({
    getAccessToken: () => 'access-token',
    refresh,
    onUnauthenticated,
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function headerOf(call: number): string | null {
  const init = fetchMock.mock.calls[call][1] as RequestInit
  return new Headers(init.headers).get('Authorization')
}

describe('apiClient', () => {
  it('sends the access token and the same-origin credentials', async () => {
    fetchMock.mockResolvedValueOnce(ok())

    await apiFetch('/api/items')

    expect(headerOf(0)).toBe('Bearer access-token')
    expect((fetchMock.mock.calls[0][1] as RequestInit).credentials).toBe('same-origin')
  })

  it('never sends the token to the authentication APIs', async () => {
    fetchMock.mockResolvedValue(ok())

    await apiFetch('/api/auth/login', { method: 'POST' })
    await apiFetch('/api/auth/session/refresh', { method: 'POST' })
    await apiFetch('/api/auth/session/logout', { method: 'POST' })

    expect(headerOf(0)).toBeNull()
    expect(headerOf(1)).toBeNull()
    expect(headerOf(2)).toBeNull()
    expect(refresh).not.toHaveBeenCalled()
  })

  it('refreshes once and retries the original request after AUTHENTICATION_REQUIRED', async () => {
    fetchMock.mockResolvedValueOnce(unauthorized()).mockResolvedValueOnce(ok())
    refresh.mockResolvedValue(true)

    const response = await apiFetch('/api/items')

    expect(response.status).toBe(200)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(onUnauthenticated).not.toHaveBeenCalled()
  })

  it('reports being unauthenticated when the retry is rejected again', async () => {
    fetchMock.mockResolvedValue(unauthorized())
    refresh.mockResolvedValue(true)

    const response = await apiFetch('/api/items')

    expect(response.status).toBe(401)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(onUnauthenticated).toHaveBeenCalledTimes(1)
  })

  it('reports being unauthenticated when the refresh fails and does not retry', async () => {
    fetchMock.mockResolvedValueOnce(unauthorized())
    refresh.mockResolvedValue(false)

    await apiFetch('/api/items')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(onUnauthenticated).toHaveBeenCalledTimes(1)
  })

  it('does not refresh on a 401 with another code or on a 403', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { code: 'REFRESH_FAILED' }))
    await apiFetch('/api/items')
    fetchMock.mockResolvedValueOnce(jsonResponse(403, { code: 'ACCESS_DENIED' }))
    await apiFetch('/api/items')

    expect(refresh).not.toHaveBeenCalled()
    expect(onUnauthenticated).not.toHaveBeenCalled()
  })

  it('throws a typed error for a failed response and for a failed connection', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { code: 'AUTHENTICATION_FAILED' }))
    await expect(apiRequest('/api/auth/login', { method: 'POST' })).rejects.toEqual({
      kind: 'response',
      status: 401,
      code: 'AUTHENTICATION_FAILED',
    })

    fetchMock.mockRejectedValueOnce(new TypeError('failed to fetch'))
    await expect(apiRequest('/api/auth/login', { method: 'POST' })).rejects.toEqual({
      kind: 'network',
    })
  })

  it('merges simultaneous 401s into a single refresh whatever their number', async () => {
    await fc.assert(
      fc.asyncProperty(fc.integer({ min: 2, max: 6 }), async (count) => {
        resetApiClient()
        refresh.mockReset()
        fetchMock.mockReset()
        registerAuthHandlers({ getAccessToken: () => 'access-token', refresh, onUnauthenticated })
        let resolveRefresh: (value: boolean) => void = () => {}
        const pending = new Promise<boolean>((resolve) => {
          resolveRefresh = resolve
        })
        refresh.mockImplementation(() => pending)
        let calls = 0
        fetchMock.mockImplementation(() => {
          calls += 1
          return Promise.resolve(calls <= count ? unauthorized() : ok())
        })

        const requests = Array.from({ length: count }, () => apiFetch('/api/items'))
        for (let i = 0; i < 20; i += 1) {
          await Promise.resolve()
        }
        resolveRefresh(true)
        await Promise.all(requests)

        expect(refresh).toHaveBeenCalledTimes(1)
      }),
      { numRuns: 5 },
    )
  })
})
