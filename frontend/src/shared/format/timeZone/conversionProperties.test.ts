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
// 性質ベースのテスト（fast-check）。失敗すると fast-check が乱数の種（seed）と反例を出力に含めるため、
// 同じ種を fc.assert の seed に渡せば手元で再現できる。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import {
  canInterpretTimeZone,
  compareInstants,
  formatInstant,
  parseLocalDateTime,
  preserveInstant,
} from './index'
import { formatUtcInstant } from './instantText'

const ZONES = ['UTC', ...Intl.supportedValuesOf('timeZone')]

/** 表示の年が 0001〜9999 に収まる時点の秒の範囲（両端から 2 日の余裕を取る） */
const MIN_SECONDS = -62135596800 + 2 * 86400
const MAX_SECONDS = 253402300799 - 2 * 86400

const instantArbitrary = fc.record({
  seconds: fc.integer({ min: MIN_SECONDS, max: MAX_SECONDS }),
  fraction: fc.stringMatching(/^[0-9]{0,6}$/),
  zone: fc.constantFrom(...ZONES),
})

const anyInput = fc.oneof(
  fc.string(),
  fc.string({ unit: 'binary', maxLength: 80 }),
  fc.constantFrom(null, undefined, 0, 1.5, {}, [], true),
)

describe('conversion properties', () => {
  it('never throws, whatever the input', () => {
    fc.assert(
      fc.property(anyInput, anyInput, anyInput, (a, b, c) => {
        const s = a as string
        const z = b as string
        expect(() => {
          canInterpretTimeZone(z)
          formatInstant(s, z)
          formatInstant(s, z, c as number)
          parseLocalDateTime(s, z)
          parseLocalDateTime(s, z, c as 'earlier')
          compareInstants(s, z)
          preserveInstant(s)
        }).not.toThrow()
      }),
      { numRuns: 300 },
    )
  })

  it('gives the same result for the same input', () => {
    fc.assert(
      fc.property(instantArbitrary, ({ seconds, fraction, zone }) => {
        const instant = formatUtcInstant(seconds, fraction)
        expect(formatInstant(instant, zone)).toEqual(formatInstant(instant, zone))
        const local = formatInstant(instant, zone)
        if (local.kind === 'ok') {
          const text = local.text.split(' ').slice(0, 2).join(' ')
          expect(parseLocalDateTime(text, zone)).toEqual(parseLocalDateTime(text, zone))
        }
      }),
      { numRuns: 200 },
    )
  })

  it('round trips: formatting an instant and parsing its local part gives the instant back', () => {
    fc.assert(
      fc.property(instantArbitrary, ({ seconds, fraction, zone }) => {
        const instant = formatUtcInstant(seconds, fraction)
        const formatted = formatInstant(instant, zone)
        expect(formatted.kind).toBe('ok')
        if (formatted.kind !== 'ok') {
          return
        }
        const [date, time] = formatted.text.split(' ')
        const local = `${date} ${time}`
        const direct = parseLocalDateTime(local, zone)
        if (direct.kind === 'ok') {
          expect(direct.instant).toBe(instant)
        } else {
          // 重なる時刻は、時差で先か後かを決めて戻す
          expect(direct.kind).toBe('overlap')
          const earlier = parseLocalDateTime(local, zone, 'earlier')
          const later = parseLocalDateTime(local, zone, 'later')
          expect([earlier, later]).toContainEqual({ kind: 'ok', instant })
        }
      }),
      { numRuns: 500 },
    )
  })

  it('keeps the digits of the fraction in both directions', () => {
    fc.assert(
      fc.property(instantArbitrary, ({ seconds, fraction, zone }) => {
        const instant = formatUtcInstant(seconds, fraction)
        const formatted = formatInstant(instant, zone)
        expect(formatted.kind).toBe('ok')
        if (formatted.kind !== 'ok') {
          return
        }
        const time = formatted.text.split(' ')[1] ?? ''
        expect(time.split('.')[1] ?? '').toBe(fraction)
        const parsed = parseLocalDateTime(
          `${formatted.text.split(' ')[0]} ${time}`,
          zone,
          'earlier',
        )
        expect(parsed.kind).toBe('ok')
        if (parsed.kind === 'ok') {
          expect((parsed.instant.split('T')[1] ?? '').split('+')[0]?.split('.')[1] ?? '').toBe(
            fraction,
          )
        }
      }),
      { numRuns: 200 },
    )
  })

  it('answers invalid-time-zone for every name that cannot be interpreted', () => {
    fc.assert(
      fc.property(
        fc.string({ maxLength: 80 }).filter((name) => !canInterpretTimeZone(name)),
        (name) => {
          expect(formatInstant('2026-11-01T15:00:00Z', name)).toEqual({ kind: 'invalid-time-zone' })
          expect(parseLocalDateTime('2026-11-02 00:00:00', name)).toEqual({
            kind: 'invalid-time-zone',
          })
          expect(parseLocalDateTime('bad', name, 'later')).toEqual({ kind: 'invalid-time-zone' })
        },
      ),
      { numRuns: 300 },
    )
  })

  it('orders instants consistently (reflexive and antisymmetric)', () => {
    fc.assert(
      fc.property(instantArbitrary, instantArbitrary, (a, b) => {
        const left = formatUtcInstant(a.seconds, a.fraction)
        const right = formatUtcInstant(b.seconds, b.fraction)
        const forward = compareInstants(left, right)
        const backward = compareInstants(right, left)
        expect(compareInstants(left, left)).toEqual({ kind: 'ok', order: 0 })
        if (forward.kind === 'ok' && backward.kind === 'ok') {
          // 向きを入れ替えた比べは符号が逆になる。同じ時点どうしは両方 0 で、-Math.sign(0) の -0 と +0 を
          // toBe（Object.is）が別の値とみなすため、符号の和が 0 になることで確かめる（CI の seed 1574882541 で落ちた）。
          expect(Math.sign(forward.order) + Math.sign(backward.order)).toBe(0)
        } else {
          expect.unreachable('well formed instants must be comparable')
        }
      }),
      {
        numRuns: 200,
        // CI で落ちた反例（同じ時点を 2 つ）を毎回はじめに流す
        examples: [
          [
            { seconds: 253402127978, fraction: '', zone: 'UTC' },
            { seconds: 253402127978, fraction: '', zone: 'UTC' },
          ],
        ],
      },
    )
  })
})
