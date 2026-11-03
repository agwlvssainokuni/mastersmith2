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
import {
  MAX_INSTANT_TEXT_LENGTH,
  compareInstants,
  formatUtcInstant,
  parseInstantText,
  preserveInstant,
} from './instantText'

describe('instantText', () => {
  it.each([
    ['2026-11-01T15:00:00+00:00', 'Z form with offset zero'],
    ['2026-11-01T15:00:00Z', 'upper case Z'],
    ['2026-11-01T15:00:00z', 'lower case z'],
    ['2026-11-02T00:00:00+09:00', 'positive offset'],
    ['2026-11-01T10:00:00-05:00', 'negative offset'],
  ])('reads %s as the same instant (%s)', (raw) => {
    expect(parseInstantText(raw)?.epochSeconds).toBe(
      parseInstantText('2026-11-01T15:00:00Z')?.epochSeconds,
    )
  })

  it.each(['', '1', '12', '123', '123456', '123456789'])(
    'keeps the fraction %j as written',
    (fraction) => {
      const raw = `2026-11-01T15:00:00${fraction === '' ? '' : `.${fraction}`}+09:00`
      const parsed = parseInstantText(raw)
      expect(parsed?.fraction).toBe(fraction)
      expect(formatUtcInstant(parsed?.epochSeconds ?? 0, parsed?.fraction ?? '')).toBe(
        `2026-11-01T06:00:00${fraction === '' ? '' : `.${fraction}`}+00:00`,
      )
    },
  )

  it.each([
    ['February 30', '2026-02-30T00:00:00Z'],
    ['February 29 in a common year', '2026-02-29T00:00:00Z'],
    ['month 13', '2026-13-01T00:00:00Z'],
    ['hour 24', '2026-01-01T24:00:00Z'],
    ['second 60', '2026-01-01T00:00:60Z'],
    ['year 0000', '0000-01-01T00:00:00Z'],
    ['offset hour 24', '2026-01-01T00:00:00+24:00'],
    ['offset minute 60', '2026-01-01T00:00:00+09:60'],
    ['fraction of 10 digits', '2026-01-01T00:00:00.1234567890Z'],
    ['space separator', '2026-01-01 00:00:00Z'],
    ['no offset', '2026-01-01T00:00:00'],
    ['full width digits', '２０２６-01-01T00:00:00Z'],
    ['UTC year before 0001', '0001-01-01T00:00:00+09:00'],
    ['UTC year after 9999', '9999-12-31T23:59:59-01:00'],
  ])('refuses %s', (_label, raw) => {
    expect(parseInstantText(raw)).toBeNull()
  })

  it('accepts February 29 in a leap year and the end of the year range', () => {
    expect(parseInstantText('2024-02-29T00:00:00Z')).not.toBeNull()
    expect(parseInstantText('9999-12-31T23:59:59Z')).not.toBeNull()
    expect(parseInstantText('0001-01-01T00:00:00Z')).not.toBeNull()
  })

  it('checks the length before the format (40 reaches the format check, 41 does not)', () => {
    const atLimit = `2026-11-01T15:00:00.123456789+09:00${' '.repeat(MAX_INSTANT_TEXT_LENGTH - 35)}`
    expect(atLimit).toHaveLength(MAX_INSTANT_TEXT_LENGTH)
    // 上限ちょうどの長さは書式の検査へ進み、書式（末尾の空白）に合わず null になる
    expect(parseInstantText(atLimit)).toBeNull()
    expect(parseInstantText(`${atLimit} `)).toBeNull()
    // 書式の最長（35 文字）は読める
    expect(parseInstantText('2026-11-01T15:00:00.123456789+09:00')).not.toBeNull()
  })

  it.each([
    ['100000 characters', '9'.repeat(100_000)],
    ['control characters', '2026-11-01T15:00:00\u0000Z'],
    ['script tag', '<script>alert(1)</script>'],
    ['single quote', "2026-11-01T15:00:00Z'"],
    ['double quote', '2026-11-01T15:00:00Z"'],
    ['newline', '2026-11-01T15:00:00Z\n'],
    ['surrogate pair', '2026-11-01T15:00:00😀'],
    ['null', null],
    ['number', 20261101],
    ['undefined', undefined],
    ['object', {}],
  ])('refuses the abnormal input: %s', (_label, raw) => {
    expect(() => parseInstantText(raw)).not.toThrow()
    expect(parseInstantText(raw)).toBeNull()
  })

  it('preserves the raw text as it is', () => {
    for (const raw of ['2026-11-01T15:00:00.100+09:00', 'not a time', '']) {
      expect(preserveInstant(raw)).toBe(raw)
    }
  })

  it('compares instants as points in time, not as text', () => {
    expect(compareInstants('2026-11-02T00:00:00+09:00', '2026-11-01T15:00:00Z')).toEqual({
      kind: 'ok',
      order: 0,
    })
    expect(compareInstants('2026-11-01T14:59:59Z', '2026-11-01T15:00:00Z')).toEqual({
      kind: 'ok',
      order: -1,
    })
    expect(compareInstants('2026-11-01T15:00:01Z', '2026-11-01T15:00:00Z')).toEqual({
      kind: 'ok',
      order: 1,
    })
  })

  it('compares by the fraction when the seconds are equal and treats a missing fraction as zero', () => {
    expect(compareInstants('2026-11-01T15:00:00.5Z', '2026-11-01T15:00:00.500000Z')).toEqual({
      kind: 'ok',
      order: 0,
    })
    expect(compareInstants('2026-11-01T15:00:00Z', '2026-11-01T15:00:00.000001Z')).toEqual({
      kind: 'ok',
      order: -1,
    })
    expect(compareInstants('2026-11-01T15:00:00.2Z', '2026-11-01T15:00:00.1Z')).toEqual({
      kind: 'ok',
      order: 1,
    })
  })

  it('reports which side is unreadable', () => {
    expect(compareInstants('broken', '2026-11-01T15:00:00Z')).toEqual({
      kind: 'unreadable',
      side: 'a',
    })
    expect(compareInstants('2026-11-01T15:00:00Z', null)).toEqual({ kind: 'unreadable', side: 'b' })
    expect(compareInstants('x', 'y')).toEqual({ kind: 'unreadable', side: 'both' })
  })
})
