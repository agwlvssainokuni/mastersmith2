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
// 日時の入力の判定のテスト（Intent 261102-timezone-ui-polish の U4、計画 Step 8、D13・D14・D17・D19〜D22・D26・D27・D41・D43）。
// タイムゾーンは引数で明示に渡し、機械の既定のタイムゾーンに結果を預けない。
// fast-check は、失敗したときに seed と path を出力に示す（"Counterexample" と "seed"・"path"）。再現はその値を fc.assert の
// 第2引数（{ seed, path }）に一時的に書いて行い、seed を記録に残す（team.md の Testing Posture）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { formatInstantForInput } from '../../shared/format/dateTimeInput'
import { compareInstants } from '../../shared/format/timeZone'
import {
  dateTimeKindOf,
  resolveDateTimeInput,
  type DateTimeInput,
  type DateTimePurpose,
} from './resolveDateTimeInput'

const NEW_YORK = 'America/New_York'

function instant(
  text: string,
  timeZone: string | null,
  purpose: DateTimePurpose = 'form',
  overlapChoice: DateTimeInput['overlapChoice'] = null,
): ReturnType<typeof resolveDateTimeInput> {
  return resolveDateTimeInput({ text, kind: 'instant', timeZone, purpose, overlapChoice })
}

describe('dateTimeKindOf', () => {
  it('maps only DATETIME_TZ and DATETIME to date time fields', () => {
    expect(dateTimeKindOf('DATETIME_TZ')).toBe('instant')
    expect(dateTimeKindOf('DATETIME')).toBe('local')
    expect(dateTimeKindOf('DATE')).toBeNull()
    expect(dateTimeKindOf('TIME_TZ')).toBeNull()
    expect(dateTimeKindOf('STRING')).toBeNull()
  })
})

describe('resolveDateTimeInput for an instant', () => {
  it('turns a wall clock time of the display time zone into a UTC instant (D17)', () => {
    expect(instant('2026-11-02 00:00:00', 'Asia/Tokyo')).toEqual({
      kind: 'ok',
      wire: '2026-11-01T15:00:00+00:00',
      overlap: null,
    })
    expect(instant('2026-01-15 05:45:00', 'Asia/Kathmandu')).toMatchObject({
      wire: '2026-01-15T00:00:00+00:00',
    })
    expect(instant('2026-01-14 20:30:00', 'America/St_Johns')).toMatchObject({
      wire: '2026-01-15T00:00:00+00:00',
    })
  })

  it.each(['', '.123', '.123456'])('keeps the fraction digits %s as entered', (fraction) => {
    expect(instant(`2026-11-02 09:30:15${fraction}`, 'Asia/Tokyo')).toMatchObject({
      kind: 'ok',
      wire: `2026-11-02T00:30:15${fraction}+00:00`,
    })
  })

  it.each([
    '2026-11-02T09:30:15',
    '2026-11-02 09:30',
    '２０２６-11-02 09:30:15',
    '2026-02-29 09:30:15',
    '2026-11-02 09:30:15.1234567',
    "2026-11-02 09:30:15'; DROP TABLE x; --",
    '2026-11-02 09:30:15\n',
    '',
  ])('rejects %j as unreadable (D19, D29)', (text) => {
    expect(instant(text, 'Asia/Tokyo')).toEqual({ kind: 'invalid' })
  })

  it.each(['form', 'range-from', 'range-to', 'equals'] as const)(
    'rejects a time skipped by daylight saving time for %s (D20, D26)',
    (purpose) => {
      expect(instant('2026-03-08 02:30:00', NEW_YORK, purpose, 'earlier')).toEqual({ kind: 'gap' })
    },
  )

  it.each(['form', 'equals'] as const)(
    'asks which of the two times for %s until one is chosen (D21, D27)',
    (purpose) => {
      expect(instant('2026-11-01 01:30:00', NEW_YORK, purpose)).toEqual({
        kind: 'overlap-pending',
        overlap: { earlier: '-04:00', later: '-05:00' },
      })
      expect(instant('2026-11-01 01:30:00', NEW_YORK, purpose, 'earlier')).toEqual({
        kind: 'ok',
        wire: '2026-11-01T05:30:00+00:00',
        overlap: { earlier: '-04:00', later: '-05:00' },
      })
      expect(instant('2026-11-01 01:30:00.5', NEW_YORK, purpose, 'later')).toMatchObject({
        kind: 'ok',
        wire: '2026-11-01T06:30:00.5+00:00',
      })
    },
  )

  it('takes the earlier time for the start and the later time for the end of a range (D27)', () => {
    expect(instant('2026-11-01 01:30:00', NEW_YORK, 'range-from')).toMatchObject({
      kind: 'ok',
      wire: '2026-11-01T05:30:00+00:00',
    })
    // 範囲では2択の選択を使わない
    expect(instant('2026-11-01 01:30:00', NEW_YORK, 'range-to', 'earlier')).toMatchObject({
      kind: 'ok',
      wire: '2026-11-01T06:30:00+00:00',
    })
  })

  it('is unavailable without a display time zone or with one that cannot be interpreted (D11)', () => {
    expect(instant('2026-11-02 00:00:00', null)).toEqual({ kind: 'unavailable' })
    expect(instant('2026-11-02 00:00:00', 'Mars/Olympus')).toEqual({ kind: 'unavailable' })
  })
})

describe('resolveDateTimeInput for a date time without a zone', () => {
  it('sends the text with T and no conversion, whatever the time zone (D14)', () => {
    for (const timeZone of ['Asia/Tokyo', NEW_YORK, null]) {
      expect(
        resolveDateTimeInput({
          text: '2026-03-08 02:30:00.123456',
          kind: 'local',
          timeZone,
          purpose: 'form',
          overlapChoice: null,
        }),
      ).toEqual({ kind: 'ok', wire: '2026-03-08T02:30:00.123456', overlap: null })
    }
  })

  it('rejects a text of another shape with the same rules as an instant', () => {
    for (const text of ['2026-03-08T02:30:00', '2026-02-30 00:00:00', '2026-03-08 02:30']) {
      expect(
        resolveDateTimeInput({
          text,
          kind: 'local',
          timeZone: 'Asia/Tokyo',
          purpose: 'equals',
          overlapChoice: null,
        }),
      ).toEqual({ kind: 'invalid' })
    }
  })
})

// ---- 性質ベースのテスト（D43） ----

const ZONES = ['Asia/Tokyo', NEW_YORK, 'Europe/London', 'Australia/Sydney', 'Asia/Kathmandu', 'UTC']

/** 端数 0・3・6 桁 */
const fraction = fc.constantFrom('', '.123', '.000001', '.5')

/** 1990〜2099 年の時点（秒） */
const anyInstant = fc
  .date({
    min: new Date('1990-01-01T00:00:00Z'),
    max: new Date('2099-12-31T00:00:00Z'),
    noInvalidDate: true,
  })
  .map((date) => `${date.toISOString().slice(0, 19)}+00:00`)

/** America/New_York の秋の切り替わり（11 月の第1日曜日）の 05:00Z〜07:00Z（壁時計で 01 時台が2回ある時間） */
const fallBackInstant = fc
  .tuple(fc.integer({ min: 2008, max: 2099 }), fc.integer({ min: 0, max: 7199 }))
  .map(([year, seconds]) => {
    const firstOfNovember = Date.UTC(year, 10, 1)
    const day = new Date(firstOfNovember).getUTCDay()
    const sunday = firstOfNovember + ((7 - day) % 7) * 86_400_000
    const at = new Date(sunday + 5 * 3_600_000 + seconds * 1000)
    return `${at.toISOString().slice(0, 19)}+00:00`
  })

describe('resolveDateTimeInput properties', () => {
  it('returns the wall clock time entered when the sent instant is formatted back', () => {
    fc.assert(
      fc.property(
        fc.oneof(
          fc.tuple(anyInstant, fc.constantFrom(...ZONES)),
          fc.tuple(fallBackInstant, fc.constant(NEW_YORK)),
        ),
        fraction,
        fc.constantFrom<DateTimePurpose>('form', 'range-from', 'range-to', 'equals'),
        fc.constantFrom('earlier' as const, 'later' as const),
        ([source, timeZone], digits, purpose, choice) => {
          const text = `${formatInstantForInput(source, timeZone)}${digits}`
          const result = resolveDateTimeInput({
            text,
            kind: 'instant',
            timeZone,
            purpose,
            overlapChoice: choice,
          })
          expect(result.kind).toBe('ok')
          if (result.kind === 'ok') {
            expect(formatInstantForInput(result.wire, timeZone)).toBe(text)
          }
        },
      ),
    )
  })

  it('never puts the start of a range after its end for the same overlapping text', () => {
    fc.assert(
      fc.property(fc.oneof(fallBackInstant, anyInstant), fraction, (source, digits) => {
        const text = `${formatInstantForInput(source, NEW_YORK)}${digits}`
        const from = resolveDateTimeInput({
          text,
          kind: 'instant',
          timeZone: NEW_YORK,
          purpose: 'range-from',
          overlapChoice: null,
        })
        const to = resolveDateTimeInput({
          text,
          kind: 'instant',
          timeZone: NEW_YORK,
          purpose: 'range-to',
          overlapChoice: null,
        })
        expect(from.kind).toBe('ok')
        expect(to.kind).toBe('ok')
        if (from.kind === 'ok' && to.kind === 'ok') {
          const compared = compareInstants(from.wire, to.wire)
          expect(compared.kind === 'ok' && compared.order <= 0).toBe(true)
        }
      }),
    )
  })

  it('sends the same value for a date time without a zone whatever the time zone', () => {
    fc.assert(
      fc.property(
        anyInstant,
        fraction,
        fc.constantFrom<string | null>(...ZONES, null),
        (source, digits, timeZone) => {
          const text = `${formatInstantForInput(source, 'UTC')}${digits}`
          const base = resolveDateTimeInput({
            text,
            kind: 'local',
            timeZone: 'UTC',
            purpose: 'form',
            overlapChoice: null,
          })
          expect(
            resolveDateTimeInput({
              text,
              kind: 'local',
              timeZone,
              purpose: 'form',
              overlapChoice: null,
            }),
          ).toEqual(base)
        },
      ),
    )
  })
})
