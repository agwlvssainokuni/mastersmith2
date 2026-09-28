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
// 見た目の設定の読み取りのテスト（W3・D10・D11、NFR6.2・NFR9.1・NFR9.3）。fetch を差し替える。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import {
  NO_APPEARANCE,
  peekAppearance,
  resetAppearanceLoad,
  resolvedAppearance,
  startAppearanceLoad,
} from './appearanceLoad'

let fetchMock: ReturnType<typeof vi.fn>

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  resetApiClient()
  resetAppearanceLoad()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  resetApiClient()
  resetAppearanceLoad()
})

describe('startAppearanceLoad', () => {
  it('sends the request only once even when called twice', async () => {
    fetchMock.mockResolvedValue(json(200, { brandColor: 'green', fontFamily: 'serif' }))

    const first = startAppearanceLoad()
    const second = startAppearanceLoad()

    expect(second).toBe(first)
    await expect(first).resolves.toEqual({ brandColor: 'green', fontFamily: 'serif' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/appearance')
  })

  it('does not send the access token even while logged in', async () => {
    registerAuthHandlers({
      getAccessToken: () => 'test-access-token-for-display',
      refresh: () => Promise.resolve(true),
      onUnauthenticated: () => undefined,
    })
    fetchMock.mockResolvedValue(json(200, { brandColor: 'blue', fontFamily: 'sans' }))

    await startAppearanceLoad()

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Headers(init.headers).get('Authorization')).toBeNull()
  })

  it('applies only the allowed items', async () => {
    fetchMock.mockResolvedValue(json(200, { brandColor: 'red', fontFamily: 'serif', x: 1 }))

    await expect(startAppearanceLoad()).resolves.toEqual({ fontFamily: 'serif' })
  })

  it('applies nothing for a server error, a network failure or a body that is not JSON', async () => {
    fetchMock.mockResolvedValueOnce(json(500, { brandColor: 'green' }))
    await expect(startAppearanceLoad()).resolves.toEqual(NO_APPEARANCE)

    resetAppearanceLoad()
    fetchMock.mockRejectedValueOnce(new TypeError('network'))
    await expect(startAppearanceLoad()).resolves.toEqual(NO_APPEARANCE)

    resetAppearanceLoad()
    fetchMock.mockResolvedValueOnce(new Response('<html>', { status: 200 }))
    await expect(startAppearanceLoad()).resolves.toEqual(NO_APPEARANCE)
  })

  it('never ends with an exception for a body of the wrong shape', async () => {
    fetchMock.mockResolvedValueOnce(json(200, [{ brandColor: 'blue' }]))
    await expect(startAppearanceLoad()).resolves.toEqual({})

    resetAppearanceLoad()
    fetchMock.mockResolvedValueOnce(json(200, null))
    await expect(startAppearanceLoad()).resolves.toEqual({})
  })

  it('tells whether the answer has already come', async () => {
    fetchMock.mockResolvedValue(json(200, { brandColor: 'orange' }))
    const load = startAppearanceLoad()
    expect(peekAppearance(load)).toBeUndefined()

    await load
    await Promise.resolve()

    expect(peekAppearance(load)).toEqual({ brandColor: 'orange' })
    expect(peekAppearance(resolvedAppearance({ brandColor: 'purple' }))).toEqual({
      brandColor: 'purple',
    })
  })
})
