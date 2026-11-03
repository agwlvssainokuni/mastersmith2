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
import fc from 'fast-check'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as timeZone from '../../shared/format/timeZone'
import { clearFormatterCache } from '../../shared/format/timeZone/timeZoneGate'
import { buildTimeZoneOptions, readSupportedTimeZoneNames } from './buildTimeZoneOptions'

const JANUARY = new Date('2026-01-15T00:00:00Z')
const JULY = new Date('2026-07-15T00:00:00Z')
const BROWSER = 'ブラウザに合わせる'

function labelOf(
  options: ReturnType<typeof buildTimeZoneOptions>,
  value: string,
): string | undefined {
  return options.find((option) => option.value === value)?.label
}

describe('buildTimeZoneOptions', () => {
  beforeEach(() => {
    clearFormatterCache()
  })

  it('puts the browser choice first with an empty value', () => {
    const options = buildTimeZoneOptions(['Asia/Tokyo'], null, JANUARY, BROWSER)

    expect(options[0]).toEqual({ value: '', label: BROWSER })
  })

  it('sorts by code point, adds UTC only when missing, and leaves no duplicates', () => {
    const withoutUtc = buildTimeZoneOptions(
      ['b', 'Asia/Tokyo', 'Africa/Cairo'].filter((n) => n !== 'b'),
      null,
      JANUARY,
      BROWSER,
    )
    const withUtc = buildTimeZoneOptions(['UTC', 'Asia/Tokyo', 'UTC'], null, JANUARY, BROWSER)

    expect(withoutUtc.slice(1).map((o) => o.value)).toEqual(['Africa/Cairo', 'Asia/Tokyo', 'UTC'])
    expect(withUtc.slice(1).map((o) => o.value)).toEqual(['Asia/Tokyo', 'UTC'])
  })

  it('does not list names this browser cannot interpret', () => {
    const options = buildTimeZoneOptions(
      ['Asia/Tokyo', 'Mars/Olympus', '+09:00'],
      null,
      JANUARY,
      BROWSER,
    )

    expect(options.map((o) => o.value)).toEqual(['', 'Asia/Tokyo', 'UTC'])
  })

  it('labels each name with its offset at the given time (summer time and minute offsets)', () => {
    const names = [
      'America/New_York',
      'Asia/Kolkata',
      'Asia/Kathmandu',
      'America/St_Johns',
      'Asia/Tokyo',
    ]
    const january = buildTimeZoneOptions(names, null, JANUARY, BROWSER)
    const july = buildTimeZoneOptions([...names], null, JULY, BROWSER)

    expect(labelOf(january, 'America/New_York')).toBe('America/New_York (-05:00)')
    expect(labelOf(july, 'America/New_York')).toBe('America/New_York (-04:00)')
    expect(labelOf(january, 'Asia/Kolkata')).toBe('Asia/Kolkata (+05:30)')
    expect(labelOf(january, 'Asia/Kathmandu')).toBe('Asia/Kathmandu (+05:45)')
    expect(labelOf(january, 'America/St_Johns')).toBe('America/St_Johns (-03:30)')
    expect(labelOf(january, 'Asia/Tokyo')).toBe('Asia/Tokyo (+09:00)')
    expect(labelOf(january, 'UTC')).toBe('UTC (+00:00)')
  })

  it('adds a saved name that is not in the list right after the browser choice when it can be interpreted', () => {
    const options = buildTimeZoneOptions(['Asia/Tokyo'], 'Europe/Paris', JANUARY, BROWSER)

    expect(options.slice(0, 3).map((o) => o.value)).toEqual(['', 'Europe/Paris', 'Asia/Tokyo'])
    expect(labelOf(options, 'Europe/Paris')).toBe('Europe/Paris (+01:00)')
  })

  it('does not add a saved name twice, nor one that cannot be interpreted, nor UTC again', () => {
    const names = ['Asia/Tokyo']

    expect(
      buildTimeZoneOptions(names, 'Asia/Tokyo', JANUARY, BROWSER).filter(
        (o) => o.value === 'Asia/Tokyo',
      ),
    ).toHaveLength(1)
    expect(
      buildTimeZoneOptions(names, 'Mars/Olympus', JANUARY, BROWSER).map((o) => o.value),
    ).not.toContain('Mars/Olympus')
    expect(
      buildTimeZoneOptions(names, 'UTC', JANUARY, BROWSER).filter((o) => o.value === 'UTC'),
    ).toHaveLength(1)
  })

  it('builds the offsets once per array and does not format again for the same array', () => {
    const names = Array.from(new Set(['Asia/Tokyo', 'Asia/Kolkata', 'America/New_York']))
    const spy = vi.spyOn(timeZone, 'formatInstant')
    buildTimeZoneOptions(names, null, JANUARY, BROWSER)
    const first = spy.mock.calls.length

    buildTimeZoneOptions(names, 'Asia/Kolkata', JULY, BROWSER)
    buildTimeZoneOptions(names, null, JULY, BROWSER)

    expect(first).toBeGreaterThan(0)
    expect(spy.mock.calls.length).toBe(first)
    spy.mockRestore()
  })

  it('reads the names the browser supports, including at least UTC-capable zones', () => {
    const names = readSupportedTimeZoneNames()

    expect(names.length).toBeGreaterThan(100)
    expect(names).toContain('Asia/Tokyo')
  })

  it('falls back to an empty list when the browser cannot answer', () => {
    const original = Intl.supportedValuesOf
    Object.defineProperty(Intl, 'supportedValuesOf', {
      configurable: true,
      value: () => {
        throw new RangeError('no')
      },
    })
    try {
      expect(readSupportedTimeZoneNames()).toEqual([])
    } finally {
      Object.defineProperty(Intl, 'supportedValuesOf', { configurable: true, value: original })
    }
  })

  it('has properties: the head is the browser choice, values are unique and ascending, UTC is always present', () => {
    fc.assert(
      fc.property(
        fc.array(fc.string({ maxLength: 20 }), { maxLength: 8 }),
        fc.option(fc.string({ maxLength: 20 }), { nil: null }),
        (raw, saved) => {
          const options = buildTimeZoneOptions([...raw], saved, JANUARY, BROWSER)
          const values = options.map((o) => o.value)

          expect(values[0]).toBe('')
          expect(new Set(values).size).toBe(values.length)
          expect(values).toContain('UTC')
          const listed = options
            .slice(saved !== null && values[1] === saved ? 2 : 1)
            .map((o) => o.value)
          expect(listed).toEqual([...listed].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0)))
        },
      ),
      { numRuns: 100 },
    )
  })
})
