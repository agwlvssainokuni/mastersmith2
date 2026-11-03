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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../shared/api-client/apiClient'
import {
  DisplayTimeZoneShapeError,
  MAX_DISPLAY_TIME_ZONE_LENGTH,
  fetchDisplayTimeZone,
} from './displayTimeZoneApi'

vi.mock('../../shared/api-client/apiClient', () => ({ apiRequest: vi.fn() }))

function respond(body: unknown): void {
  vi.mocked(apiRequest).mockResolvedValue(new Response(JSON.stringify(body)))
}

describe('fetchDisplayTimeZone', () => {
  beforeEach(() => {
    vi.mocked(apiRequest).mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('reads only displayTimeZone from GET /api/me/preferences', async () => {
    respond({
      displayName: 'x',
      language: 'ja',
      theme: 'light',
      fontSize: 'md',
      displayTimeZone: 'Asia/Tokyo',
    })

    await expect(fetchDisplayTimeZone()).resolves.toBe('Asia/Tokyo')
    expect(apiRequest).toHaveBeenCalledWith('/api/me/preferences', { method: 'GET' })
  })

  it('accepts null as unset', async () => {
    respond({ displayTimeZone: null })

    await expect(fetchDisplayTimeZone()).resolves.toBeNull()
  })

  it('accepts a name of exactly the maximum length', async () => {
    const name = 'a'.repeat(MAX_DISPLAY_TIME_ZONE_LENGTH)
    respond({ displayTimeZone: name })

    await expect(fetchDisplayTimeZone()).resolves.toBe(name)
  })

  it.each([
    ['a name of 65 characters', { displayTimeZone: 'a'.repeat(65) }],
    ['an absent item', { displayName: 'x' }],
    ['a number', { displayTimeZone: 9 }],
    ['a boolean', { displayTimeZone: true }],
    ['an object', { displayTimeZone: { a: 1 } }],
    ['an array', { displayTimeZone: ['Asia/Tokyo'] }],
    ['a body that is an array', ['Asia/Tokyo']],
    ['a body that is a string', 'Asia/Tokyo'],
    ['a body that is null', null],
  ])('rejects %s as a shape error', async (_label, body) => {
    respond(body)

    await expect(fetchDisplayTimeZone()).rejects.toBeInstanceOf(DisplayTimeZoneShapeError)
  })

  it('rejects a body that is not JSON as a shape error', async () => {
    vi.mocked(apiRequest).mockResolvedValue(new Response('not json'))

    await expect(fetchDisplayTimeZone()).rejects.toBeInstanceOf(DisplayTimeZoneShapeError)
  })

  it('passes a failure of the API client through unchanged', async () => {
    const failure = new Error('network')
    vi.mocked(apiRequest).mockRejectedValue(failure)

    await expect(fetchDisplayTimeZone()).rejects.toBe(failure)
  })
})
