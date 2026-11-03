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
import { parseLocalDateTime } from './parseLocalDateTime'

describe('parseLocalDateTime', () => {
  it('converts a local time to UTC with +00:00', () => {
    expect(parseLocalDateTime('2026-11-02 00:00:00', 'Asia/Tokyo')).toEqual({
      kind: 'ok',
      instant: '2026-11-01T15:00:00+00:00',
    })
  })

  it.each([1, 2, 3, 4, 5, 6])('keeps a fraction of %i digits as written', (digits) => {
    const fraction = '123456'.slice(0, digits)
    expect(parseLocalDateTime(`2026-11-02 00:00:00.${fraction}`, 'Asia/Tokyo')).toEqual({
      kind: 'ok',
      instant: `2026-11-01T15:00:00.${fraction}+00:00`,
    })
  })

  it.each([
    ['T separator', '2026-11-02T00:00:00'],
    ['seconds omitted', '2026-11-02 00:00'],
    ['7 digit fraction', '2026-11-02 00:00:00.1234567'],
    ['empty fraction', '2026-11-02 00:00:00.'],
    ['full width digits', '２０２６-11-02 00:00:00'],
    ['leading space', ' 2026-11-02 00:00:00'],
    ['trailing space', '2026-11-02 00:00:00 '],
    ['two spaces', '2026-11-02  00:00:00'],
    ['empty', ''],
    ['too long', `2026-11-02 00:00:00.123456${'0'.repeat(100_000)}`],
  ])('refuses the format: %s', (_label, text) => {
    expect(parseLocalDateTime(text, 'Asia/Tokyo')).toEqual({ kind: 'invalid', reason: 'format' })
  })

  it('refuses non string input without throwing', () => {
    expect(parseLocalDateTime(null as unknown as string, 'Asia/Tokyo')).toEqual({
      kind: 'invalid',
      reason: 'format',
    })
  })

  it.each([
    ['February 29 in a common year', '2026-02-29 00:00:00'],
    ['February 30', '2028-02-30 00:00:00'],
    ['day 31 in a 30 day month', '2026-04-31 00:00:00'],
    ['month 13', '2026-13-01 00:00:00'],
    ['hour 24', '2026-01-01 24:00:00'],
    ['second 60', '2026-01-01 00:00:60'],
    ['minute 60', '2026-01-01 00:60:00'],
    ['year 0000', '0000-01-01 00:00:00'],
  ])('refuses the range: %s', (_label, text) => {
    expect(parseLocalDateTime(text, 'Asia/Tokyo')).toEqual({
      kind: 'invalid',
      reason: 'out-of-range',
    })
  })

  it('accepts February 29 in a leap year and the end of a month', () => {
    expect(parseLocalDateTime('2028-02-29 12:00:00', 'UTC')).toEqual({
      kind: 'ok',
      instant: '2028-02-29T12:00:00+00:00',
    })
    expect(parseLocalDateTime('2026-04-30 23:59:59', 'UTC')).toEqual({
      kind: 'ok',
      instant: '2026-04-30T23:59:59+00:00',
    })
  })

  it('returns gap for a time skipped by daylight saving time', () => {
    expect(parseLocalDateTime('2026-03-08 02:30:00', 'America/New_York')).toEqual({ kind: 'gap' })
  })

  it('returns overlap with the earlier and later instants for a repeated time', () => {
    expect(parseLocalDateTime('2026-11-01 01:30:00', 'America/New_York')).toEqual({
      kind: 'overlap',
      earlier: '2026-11-01T05:30:00+00:00',
      later: '2026-11-01T06:30:00+00:00',
    })
  })

  it.each([
    ['earlier', '2026-11-01T05:30:00+00:00'],
    ['later', '2026-11-01T06:30:00+00:00'],
  ] as const)('chooses the %s side of a repeated time', (choice, instant) => {
    expect(parseLocalDateTime('2026-11-01 01:30:00', 'America/New_York', choice)).toEqual({
      kind: 'ok',
      instant,
    })
  })

  it('ignores overlapChoice for a time that is not repeated', () => {
    expect(parseLocalDateTime('2026-11-02 00:00:00', 'Asia/Tokyo', 'later')).toEqual({
      kind: 'ok',
      instant: '2026-11-01T15:00:00+00:00',
    })
    expect(parseLocalDateTime('2026-03-08 02:30:00', 'America/New_York', 'earlier')).toEqual({
      kind: 'gap',
    })
  })

  it('does not honor an unknown overlapChoice value', () => {
    const result = parseLocalDateTime(
      '2026-11-01 01:30:00',
      'America/New_York',
      'x' as unknown as 'earlier',
    )
    expect(result.kind).toBe('overlap')
  })

  it('returns invalid-time-zone for fixed offsets, unknown names and the like, before the text is checked', () => {
    expect(parseLocalDateTime('2026-11-02 00:00:00', '+09:00')).toEqual({
      kind: 'invalid-time-zone',
    })
    expect(parseLocalDateTime('broken', 'Mars/Olympus')).toEqual({ kind: 'invalid-time-zone' })
    expect(parseLocalDateTime('2026-11-02 00:00:00', '')).toEqual({ kind: 'invalid-time-zone' })
  })

  it('refuses a UTC year beyond 9999 or before 0001 as out-of-range', () => {
    expect(parseLocalDateTime('9999-12-31 23:00:00', 'America/New_York')).toEqual({
      kind: 'invalid',
      reason: 'out-of-range',
    })
    expect(parseLocalDateTime('0001-01-01 00:00:00', 'Asia/Tokyo')).toEqual({
      kind: 'invalid',
      reason: 'out-of-range',
    })
  })

  it('accepts the boundary of the year range in UTC and across offsets', () => {
    expect(parseLocalDateTime('9999-12-31 23:59:59', 'UTC')).toEqual({
      kind: 'ok',
      instant: '9999-12-31T23:59:59+00:00',
    })
    expect(parseLocalDateTime('9999-12-31 23:59:59', 'Asia/Tokyo')).toEqual({
      kind: 'ok',
      instant: '9999-12-31T14:59:59+00:00',
    })
    expect(parseLocalDateTime('0001-01-01 00:00:00', 'UTC')).toEqual({
      kind: 'ok',
      instant: '0001-01-01T00:00:00+00:00',
    })
    expect(parseLocalDateTime('0001-01-01 12:00:00', 'America/New_York')).toEqual({
      kind: 'ok',
      instant: '0001-01-01T16:56:02+00:00',
    })
  })
})
