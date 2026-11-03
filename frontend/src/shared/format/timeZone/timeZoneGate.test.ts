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
import {
  MAX_FORMATTER_CACHE_SIZE,
  canInterpretTimeZone,
  clearFormatterCache,
  formatterCacheSize,
  getFormatter,
} from './timeZoneGate'

describe('timeZoneGate', () => {
  beforeEach(() => {
    clearFormatterCache()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it.each(['Asia/Tokyo', 'UTC', 'asia/tokyo', 'America/Argentina/ComodRivadavia'])(
    'accepts the IANA name %s',
    (name) => {
      expect(canInterpretTimeZone(name)).toBe(true)
    },
  )

  it.each([
    ['fixed offset with colon', '+09:00'],
    ['fixed offset without colon', '+0900'],
    ['hours-only offset', '-05'],
    ['Z', 'Z'],
    ['empty', ''],
    ['65 characters', 'A'.repeat(65)],
    ['space', 'Asia/ Tokyo'],
    ['quote', "Asia/Tokyo'"],
    ['angle brackets', '<script>'],
    ['full width letters', 'Ａｓｉａ/Ｔｏｋｙｏ'],
    ['unknown region', 'Mars/Olympus'],
  ])('rejects %s', (_label, name) => {
    expect(canInterpretTimeZone(name)).toBe(false)
  })

  it.each([null, undefined, 9, {}, ['UTC']])('rejects the non string value %j', (value) => {
    expect(canInterpretTimeZone(value)).toBe(false)
    expect(getFormatter(value)).toBeNull()
  })

  it('creates the formatter once for 10000 calls with the same name', () => {
    const spy = vi.spyOn(Intl, 'DateTimeFormat')
    for (let i = 0; i < 10_000; i++) {
      getFormatter('Asia/Tokyo')
    }
    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('gives the same result with and without the cache', () => {
    const first = getFormatter('Asia/Tokyo')?.format(new Date(0))
    clearFormatterCache()
    expect(formatterCacheSize()).toBe(0)
    const second = getFormatter('Asia/Tokyo')?.format(new Date(0))
    expect(first).toBeDefined()
    expect(second).toBe(first)
  })

  it('does not cache names that Intl rejects', () => {
    expect(getFormatter('Mars/Olympus')).toBeNull()
    expect(formatterCacheSize()).toBe(0)
  })

  it('keeps working past the cache limit by dropping the whole cache', () => {
    const names = Intl.supportedValuesOf('timeZone').slice(0, MAX_FORMATTER_CACHE_SIZE + 5)
    expect(names.length).toBe(MAX_FORMATTER_CACHE_SIZE + 5)
    const expected = names.map((n) => getFormatter(n)?.format(new Date(1_000_000_000_000)))
    expect(formatterCacheSize()).toBeLessThanOrEqual(MAX_FORMATTER_CACHE_SIZE)
    clearFormatterCache()
    const again = names.map((n) => getFormatter(n)?.format(new Date(1_000_000_000_000)))
    expect(again).toEqual(expected)
  })
})
