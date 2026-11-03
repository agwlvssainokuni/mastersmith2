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
import { describe, expect, it } from 'vitest'
import { formatInstantToMinutes } from './formatInstantToMinutes'

describe('formatInstantToMinutes', () => {
  it('keeps the date, hour, minute and offset in the given zone', () => {
    expect(formatInstantToMinutes('2026-09-24T01:15:30Z', 'Asia/Tokyo')).toBe(
      '2026-09-24 10:15 +09:00',
    )
    expect(formatInstantToMinutes('2026-09-24T01:15:30.123456Z', 'America/New_York')).toBe(
      '2026-09-23 21:15 -04:00',
    )
    expect(formatInstantToMinutes('2026-01-15T00:00:00Z', 'Asia/Kathmandu')).toBe(
      '2026-01-15 05:45 +05:45',
    )
    expect(formatInstantToMinutes('2026-01-15T00:00:00Z', 'UTC')).toBe('2026-01-15 00:00 +00:00')
  })

  it('moves the date at the day boundary', () => {
    expect(formatInstantToMinutes('2026-12-31T15:00:00Z', 'Asia/Tokyo')).toBe(
      '2027-01-01 00:00 +09:00',
    )
  })

  it('returns the original text for an unreadable instant', () => {
    expect(formatInstantToMinutes('not a time', 'Asia/Tokyo')).toBe('not a time')
    expect(formatInstantToMinutes('', 'Asia/Tokyo')).toBe('')
  })

  it('returns the original text for a zone that cannot be interpreted', () => {
    expect(formatInstantToMinutes('2026-09-24T01:15:30Z', 'Mars/Olympus')).toBe(
      '2026-09-24T01:15:30Z',
    )
    expect(formatInstantToMinutes('2026-09-24T01:15:30Z', '+09:00')).toBe('2026-09-24T01:15:30Z')
  })

  it('does not depend on the machine default time zone', () => {
    const original = process.env.TZ
    try {
      const results = ['Asia/Tokyo', 'America/Los_Angeles', 'UTC'].map((machine) => {
        process.env.TZ = machine
        return formatInstantToMinutes('2026-09-24T01:15:30Z', 'Asia/Kolkata')
      })
      expect(new Set(results)).toEqual(new Set(['2026-09-24 06:45 +05:30']))
    } finally {
      if (original === undefined) {
        delete process.env.TZ
      } else {
        process.env.TZ = original
      }
    }
  })

  it('always returns a string of the minute form or the original text', () => {
    fc.assert(
      fc.property(
        fc.date({
          min: new Date('1990-01-01T00:00:00Z'),
          max: new Date('2100-01-01T00:00:00Z'),
          noInvalidDate: true,
        }),
        (date) => {
          const iso = date.toISOString()
          const text = formatInstantToMinutes(iso, 'Asia/Tokyo')
          expect(text).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2} \+09:00$/)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('requires the time zone argument at the type level (no implicit default)', () => {
    // @ts-expect-error timeZone は省略できない（省略すると tsc が止める）
    const call = () => formatInstantToMinutes('2026-09-24T01:15:30Z')

    expect(typeof call).toBe('function')
  })
})
