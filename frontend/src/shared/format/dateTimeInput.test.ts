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
// 日時の入力の初めの値と送る形の薄い関数のテスト（Intent 261102-timezone-ui-polish の U4、計画 Step 5、D14・D16・D41）。
// タイムゾーンは引数で明示に渡し、機械の既定のタイムゾーンに結果を預けない。
import { describe, expect, it } from 'vitest'
import {
  formatInstantForInput,
  formatLocalDateTimeForInput,
  isLocalDateTimeText,
  toLocalDateTimeWire,
} from './dateTimeInput'

describe('formatInstantForInput', () => {
  it.each([
    ['2026-11-02T00:30:15+00:00', '2026-11-02 09:30:15'],
    ['2026-11-02T00:30:15.123+00:00', '2026-11-02 09:30:15.123'],
    ['2026-11-02T00:30:15.123456+00:00', '2026-11-02 09:30:15.123456'],
  ])('keeps the fraction digits of %s as returned (0, 3 and 6 digits)', (instant, expected) => {
    expect(formatInstantForInput(instant, 'Asia/Tokyo')).toBe(expected)
  })

  it('moves the date at the day boundary, month end, year end and leap day', () => {
    expect(formatInstantForInput('2026-11-01T15:00:00+00:00', 'Asia/Tokyo')).toBe(
      '2026-11-02 00:00:00',
    )
    expect(formatInstantForInput('2026-12-31T15:00:00+00:00', 'Asia/Tokyo')).toBe(
      '2027-01-01 00:00:00',
    )
    expect(formatInstantForInput('2028-02-28T15:00:00+00:00', 'Asia/Tokyo')).toBe(
      '2028-02-29 00:00:00',
    )
    expect(formatInstantForInput('2026-03-01T02:00:00+00:00', 'America/New_York')).toBe(
      '2026-02-28 21:00:00',
    )
  })

  it('gives the same wall clock text for both instants of an overlapping time', () => {
    // America/New_York の 2026-11-01 01:30 は -04:00（05:30Z）と -05:00（06:30Z）の2回ある
    expect(formatInstantForInput('2026-11-01T05:30:00+00:00', 'America/New_York')).toBe(
      '2026-11-01 01:30:00',
    )
    expect(formatInstantForInput('2026-11-01T06:30:00+00:00', 'America/New_York')).toBe(
      '2026-11-01 01:30:00',
    )
  })

  it('uses offsets with minute fractions', () => {
    expect(formatInstantForInput('2026-01-15T00:00:00+00:00', 'Asia/Kathmandu')).toBe(
      '2026-01-15 05:45:00',
    )
    expect(formatInstantForInput('2026-01-15T00:00:00+00:00', 'America/St_Johns')).toBe(
      '2026-01-14 20:30:00',
    )
  })

  it('returns the original text for an unreadable value or a zone that cannot be interpreted', () => {
    expect(formatInstantForInput('0000-00-00 00:00:00', 'Asia/Tokyo')).toBe('0000-00-00 00:00:00')
    expect(formatInstantForInput('<script>alert(1)</script>', 'Asia/Tokyo')).toBe(
      '<script>alert(1)</script>',
    )
    expect(formatInstantForInput('', 'Asia/Tokyo')).toBe('')
    expect(formatInstantForInput('2026-11-02T00:30:15+00:00', 'Mars/Olympus')).toBe(
      '2026-11-02T00:30:15+00:00',
    )
  })

  it('does not depend on the machine default time zone (UTC on CI, Asia/Tokyo locally)', () => {
    const original = process.env.TZ
    try {
      const results = ['UTC', 'Asia/Tokyo'].map((machine) => {
        process.env.TZ = machine
        return [
          formatInstantForInput('2026-11-01T05:30:00.5+00:00', 'America/New_York'),
          formatLocalDateTimeForInput('2026-11-01T01:30:00.5'),
          toLocalDateTimeWire('2026-11-01 01:30:00.5'),
          String(isLocalDateTimeText('2026-03-08 02:30:00')),
        ].join('|')
      })
      expect(new Set(results)).toEqual(
        new Set(['2026-11-01 01:30:00.5|2026-11-01 01:30:00.5|2026-11-01T01:30:00.5|true']),
      )
    } finally {
      if (original === undefined) {
        delete process.env.TZ
      } else {
        process.env.TZ = original
      }
    }
  })
})

describe('formatLocalDateTimeForInput and toLocalDateTimeWire', () => {
  it.each([
    ['2026-11-02T09:30:15', '2026-11-02 09:30:15'],
    ['2026-11-02T09:30:15.123', '2026-11-02 09:30:15.123'],
    ['2026-11-02T09:30:15.123456', '2026-11-02 09:30:15.123456'],
  ])('turns %s into the input text without losing the fraction', (value, input) => {
    expect(formatLocalDateTimeForInput(value)).toBe(input)
    expect(toLocalDateTimeWire(input)).toBe(value)
  })

  it('returns a text of another shape as it is', () => {
    expect(formatLocalDateTimeForInput('0000-00-00 00:00:00')).toBe('0000-00-00 00:00:00')
    expect(formatLocalDateTimeForInput('2026-11-02T09:30:15+09:00')).toBe(
      '2026-11-02T09:30:15+09:00',
    )
    expect(toLocalDateTimeWire('2026-11-02T09:30:15')).toBe('2026-11-02T09:30:15')
    expect(toLocalDateTimeWire('2026/11/02 09:30:15')).toBe('2026/11/02 09:30:15')
  })
})

describe('isLocalDateTimeText', () => {
  it.each(['2026-11-02 09:30:15', '2026-11-02 09:30:15.1', '2026-11-02 09:30:15.123456'])(
    'accepts %s',
    (text) => {
      expect(isLocalDateTimeText(text)).toBe(true)
    },
  )

  it('accepts a time that a daylight saving time change skips (no zone for this type)', () => {
    expect(isLocalDateTimeText('2026-03-08 02:30:00')).toBe(true)
  })

  it.each([
    '2026-11-02T09:30:15',
    '2026-11-02 09:30',
    '２０２６-11-02 09:30:15',
    '2026-02-29 00:00:00',
    '2026-11-02 24:00:00',
    '2026-11-02 09:30:15.1234567',
    "2026-11-02 09:30:15'; --",
    '',
  ])('rejects %s', (text) => {
    expect(isLocalDateTimeText(text)).toBe(false)
  })
})
