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
import { describe, expect, it } from 'vitest'
import { formatInstant } from './formatInstant'

function text(instant: string, zone: string, digits?: number): string {
  const result = formatInstant(instant, zone, digits)
  if (result.kind !== 'ok') {
    throw new Error(`expected ok but got ${result.kind}`)
  }
  return result.text
}

describe('formatInstant', () => {
  it.each([
    [
      'UTC 15:00 becomes the next day in Japan',
      '2026-11-01T15:00:00+00:00',
      'Asia/Tokyo',
      '2026-11-02 00:00:00 +09:00',
    ],
    ['end of month', '2026-01-31T15:30:00Z', 'Asia/Tokyo', '2026-02-01 00:30:00 +09:00'],
    ['end of year', '2026-12-31T20:00:00Z', 'Asia/Tokyo', '2027-01-01 05:00:00 +09:00'],
    ['leap day', '2028-02-28T16:00:00Z', 'Asia/Tokyo', '2028-02-29 01:00:00 +09:00'],
    [
      'going back across midnight',
      '2026-11-02T03:00:00+09:00',
      'America/New_York',
      '2026-11-01 13:00:00 -05:00',
    ],
  ])('crosses the date line: %s', (_label, instant, zone, expected) => {
    expect(text(instant, zone)).toBe(expected)
  })

  it('uses the offset of each instant around a daylight saving time change', () => {
    expect(text('2026-03-08T06:59:59Z', 'America/New_York')).toBe('2026-03-08 01:59:59 -05:00')
    expect(text('2026-03-08T07:00:00Z', 'America/New_York')).toBe('2026-03-08 03:00:00 -04:00')
    expect(text('2026-11-01T05:30:00Z', 'America/New_York')).toBe('2026-11-01 01:30:00 -04:00')
    expect(text('2026-11-01T06:30:00Z', 'America/New_York')).toBe('2026-11-01 01:30:00 -05:00')
  })

  it.each([
    ['Asia/Kathmandu', '2026-06-01T00:00:00Z', '2026-06-01 05:45:00 +05:45'],
    ['Australia/Adelaide', '2026-07-01T00:00:00Z', '2026-07-01 09:30:00 +09:30'],
    ['America/St_Johns', '2026-01-15T12:00:00Z', '2026-01-15 08:30:00 -03:30'],
    ['UTC', '2026-01-15T12:00:00Z', '2026-01-15 12:00:00 +00:00'],
  ])('keeps the minutes of the offset of %s', (zone, instant, expected) => {
    expect(text(instant, zone)).toBe(expected)
  })

  it('shows the seconds of a historical offset only when they are not zero', () => {
    expect(text('1800-01-01T12:00:00Z', 'Asia/Tokyo')).toBe('1800-01-01 21:18:59 +09:18:59')
  })

  it.each([
    ['', '2026-11-01T15:00:00Z', '2026-11-02 00:00:00 +09:00'],
    ['123', '2026-11-01T15:00:00.123Z', '2026-11-02 00:00:00.123 +09:00'],
    ['120', '2026-11-01T15:00:00.120Z', '2026-11-02 00:00:00.120 +09:00'],
    ['123456', '2026-11-01T15:00:00.123456+00:00', '2026-11-02 00:00:00.123456 +09:00'],
    ['000000', '2026-11-01T15:00:00.000000+00:00', '2026-11-02 00:00:00.000000 +09:00'],
  ])('does not drop or round the fraction %j', (_fraction, instant, expected) => {
    expect(text(instant, 'Asia/Tokyo')).toBe(expected)
  })

  it('pads the fraction with zeros only when the given digits are longer', () => {
    expect(text('2026-11-01T15:00:00Z', 'Asia/Tokyo', 3)).toBe('2026-11-02 00:00:00.000 +09:00')
    expect(text('2026-11-01T15:00:00.5Z', 'Asia/Tokyo', 3)).toBe('2026-11-02 00:00:00.500 +09:00')
    expect(text('2026-11-01T15:00:00.123456Z', 'Asia/Tokyo', 3)).toBe(
      '2026-11-02 00:00:00.123456 +09:00',
    )
    expect(text('2026-11-01T15:00:00.123Z', 'Asia/Tokyo', 0)).toBe('2026-11-02 00:00:00.123 +09:00')
    expect(text('2026-11-01T15:00:00Z', 'Asia/Tokyo', 99)).toBe(
      '2026-11-02 00:00:00.000000000 +09:00',
    )
  })

  it('returns the unreadable raw text unchanged, even when it looks like markup', () => {
    for (const raw of [
      '<img src=x onerror=alert(1)>',
      '2026-02-30T00:00:00Z',
      "'; DROP TABLE",
      '',
    ]) {
      expect(formatInstant(raw, 'Asia/Tokyo')).toEqual({ kind: 'unreadable', raw })
    }
    expect(formatInstant(null as unknown as string, 'Asia/Tokyo')).toEqual({
      kind: 'unreadable',
      raw: '',
    })
  })

  it('reports an invalid time zone before looking at the instant', () => {
    expect(formatInstant('2026-11-01T15:00:00Z', '+09:00')).toEqual({ kind: 'invalid-time-zone' })
    expect(formatInstant('broken', 'Mars/Olympus')).toEqual({ kind: 'invalid-time-zone' })
    expect(formatInstant('2026-11-01T15:00:00Z', undefined as unknown as string)).toEqual({
      kind: 'invalid-time-zone',
    })
  })

  it('treats a display year beyond 9999 as unreadable', () => {
    const raw = '9999-12-31T23:59:59Z'
    expect(formatInstant(raw, 'Asia/Kolkata')).toEqual({ kind: 'unreadable', raw })
    expect(text(raw, 'UTC')).toBe('9999-12-31 23:59:59 +00:00')
  })

  it('is the same on every call (pure)', () => {
    const first = formatInstant('2026-11-01T15:00:00.123Z', 'Europe/London')
    expect(formatInstant('2026-11-01T15:00:00.123Z', 'Europe/London')).toEqual(first)
  })
})
