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
import { afterEach, describe, expect, it, vi } from 'vitest'
import { readBrowserTimeZone } from './browserTimeZone'

describe('readBrowserTimeZone', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns the name the browser reports when it can be interpreted', () => {
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({
      timeZone: 'Asia/Kolkata',
    } as Intl.ResolvedDateTimeFormatOptions)

    expect(readBrowserTimeZone()).toBe('Asia/Kolkata')
  })

  it('returns UTC when the browser reports a name that cannot be interpreted', () => {
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({
      timeZone: 'Mars/Olympus',
    } as Intl.ResolvedDateTimeFormatOptions)

    expect(readBrowserTimeZone()).toBe('UTC')
  })

  it('returns UTC when the browser reports no name', () => {
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue(
      {} as Intl.ResolvedDateTimeFormatOptions,
    )

    expect(readBrowserTimeZone()).toBe('UTC')
  })

  it('follows the machine default time zone of the process', () => {
    const original = process.env.TZ
    try {
      process.env.TZ = 'Asia/Tokyo'
      const tokyo = readBrowserTimeZone()
      process.env.TZ = 'America/Los_Angeles'
      const la = readBrowserTimeZone()
      expect([tokyo, la]).toEqual(['Asia/Tokyo', 'America/Los_Angeles'])
    } finally {
      if (original === undefined) {
        delete process.env.TZ
      } else {
        process.env.TZ = original
      }
    }
  })
})
