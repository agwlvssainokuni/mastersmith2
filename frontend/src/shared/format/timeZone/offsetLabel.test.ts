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
// 時差のラベル（offsetLabel.ts）のテスト（Intent 261104-tz-options-perf の Step 5）。Temporal の道と Intl の道を、道を選ぶ口
// （timeZoneOffsetTextWith）に Temporal を渡すか null を渡すかで切り替えて、両方を流す（globalThis.Temporal は触らない）。
// 性質ベースのテストは、失敗すると fast-check が乱数の種（seed）と反例を出力に含めるため、同じ種を fc.assert の seed に渡せば再現できる。
import fc from 'fast-check'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { formatInstant, timeZoneOffsetText } from './index'
import { formatUtcInstant } from './instantText'
import { temporalFrom, timeZoneOffsetTextWith, type TemporalLike } from './offsetLabel'
import { clearFormatterCache, formatterCacheSize, getFormatter } from './timeZoneGate'

const RUNTIME_TEMPORAL = temporalFrom((globalThis as { Temporal?: unknown }).Temporal)

/** テストの環境（Node 26）は Temporal を持つ前提。無ければ Temporal の道のテストが Intl の道を流すことになるため、先に落とす */
function requireTemporal(): TemporalLike {
  if (RUNTIME_TEMPORAL === null) {
    throw new Error('this test environment must provide Temporal')
  }
  return RUNTIME_TEMPORAL
}

const PATHS: ReadonlyArray<readonly [string, () => TemporalLike | null]> = [
  ['temporal', requireTemporal],
  ['intl', () => null],
]

/** formatInstant の ok の文字の末尾から切り出した時差（今の選択肢の作りと同じ求め方）。求まらなければ null */
function expectedOffset(timeZone: string, epochSeconds: number): string | null {
  const result = formatInstant(formatUtcInstant(epochSeconds, ''), timeZone)
  if (result.kind !== 'ok') {
    return null
  }
  const match = /([+-]\d{2}:\d{2}(?::\d{2})?)$/.exec(result.text)
  return match?.[1] ?? null
}

function seconds(iso: string): number {
  return Date.parse(iso) / 1000
}

const ZONES = ['UTC', ...Intl.supportedValuesOf('timeZone')]

/** 時点の文字が読める秒の範囲（0001-01-01T00:00:00Z〜9999-12-31T23:59:59Z） */
const MIN_SECONDS = -62135596800
const MAX_SECONDS = 253402300799

const BAD_NAMES = [
  'Mars/Olympus',
  'Etc/Unknown',
  'Factory',
  '+09:00',
  '+0900',
  '+09',
  'UTC+9',
  'Asia/Tokyo ',
  'Asia/東京',
  'Asia/Tokyo\n',
  '../etc/passwd',
  '<script>',
  '',
  'A'.repeat(65),
  `Asia/${'x'.repeat(59)}`,
]

describe.each(PATHS)('timeZoneOffsetTextWith (%s path)', (_path, temporalOf) => {
  beforeEach(() => {
    clearFormatterCache()
  })

  it.each([
    ['UTC', '2026-07-15T00:00:00Z', '+00:00'],
    ['Asia/Tokyo', '2026-07-15T00:00:00Z', '+09:00'],
    ['Asia/Kathmandu', '2026-07-15T00:00:00Z', '+05:45'],
    ['Australia/Adelaide', '2026-01-15T00:00:00Z', '+10:30'],
    ['Australia/Adelaide', '2026-07-15T00:00:00Z', '+09:30'],
    ['America/St_Johns', '2026-01-15T00:00:00Z', '-03:30'],
    ['America/New_York', '2026-03-08T06:59:59Z', '-05:00'],
    ['America/New_York', '2026-03-08T07:00:00Z', '-04:00'],
    ['America/New_York', '2026-11-01T05:59:59Z', '-04:00'],
    ['America/New_York', '2026-11-01T06:00:00Z', '-05:00'],
    ['Asia/Tokyo', '1880-01-01T00:00:00Z', '+09:18:59'],
  ])('gives %s at %s the offset %s, as formatInstant does', (name, iso, offset) => {
    const at = seconds(iso)

    expect(timeZoneOffsetTextWith(name, at, temporalOf())).toBe(offset)
    expect(expectedOffset(name, at)).toBe(offset)
  })

  it.each(BAD_NAMES)('gives null for the name %j that cannot be interpreted', (name) => {
    expect(timeZoneOffsetTextWith(name, seconds('2026-07-15T00:00:00Z'), temporalOf())).toBeNull()
  })

  it.each([null, undefined, 9, {}, ['UTC']])('gives null for the non string value %j', (value) => {
    expect(timeZoneOffsetTextWith(value, 0, temporalOf())).toBeNull()
  })

  it.each([Number.NaN, 0.5, Number.POSITIVE_INFINITY, MIN_SECONDS - 1, MAX_SECONDS + 1])(
    'gives null for the instant %d that formatInstant cannot read',
    (at) => {
      expect(timeZoneOffsetTextWith('UTC', at, temporalOf())).toBeNull()
    },
  )

  it('gives null when the local year leaves 0001 to 9999, as formatInstant does', () => {
    expect(timeZoneOffsetTextWith('Pacific/Kiritimati', MAX_SECONDS, temporalOf())).toBeNull()
    expect(expectedOffset('Pacific/Kiritimati', MAX_SECONDS)).toBeNull()
    expect(timeZoneOffsetTextWith('America/New_York', MIN_SECONDS, temporalOf())).toBeNull()
    expect(expectedOffset('America/New_York', MIN_SECONDS)).toBeNull()
    expect(timeZoneOffsetTextWith('UTC', MIN_SECONDS, temporalOf())).toBe('+00:00')
  })

  it('has the property: the offset equals the one cut from formatInstant for any name and instant', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...ZONES),
        fc.integer({ min: MIN_SECONDS, max: MAX_SECONDS }),
        (name, at) => {
          clearFormatterCache()
          const expected = expectedOffset(name, at)
          clearFormatterCache()
          expect(timeZoneOffsetTextWith(name, at, temporalOf())).toBe(expected)
        },
      ),
      { numRuns: 300 },
    )
  })

  it('leaves the shared formatter cache untouched', () => {
    const tokyo = getFormatter('Asia/Tokyo')
    const before = formatterCacheSize()

    for (const name of ['Asia/Tokyo', 'Europe/Paris', 'America/New_York', 'Mars/Olympus']) {
      timeZoneOffsetTextWith(name, seconds('2026-07-15T00:00:00Z'), temporalOf())
    }

    expect(formatterCacheSize()).toBe(before)
    expect(getFormatter('Asia/Tokyo')).toBe(tokyo)
    expect(getFormatter('Europe/Paris')).not.toBeNull()
    expect(formatterCacheSize()).toBe(before + 1)
  })
})

describe('timeZoneOffsetTextWith call counts', () => {
  let created: ReturnType<typeof vi.spyOn>
  let parts: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    clearFormatterCache()
    created = vi.spyOn(Intl, 'DateTimeFormat')
    parts = vi.spyOn(Intl.DateTimeFormat.prototype, 'formatToParts')
  })

  afterEach(() => {
    created.mockRestore()
    parts.mockRestore()
  })

  it('creates no formatter and calls formatToParts zero times on the temporal path', () => {
    const at = seconds('2026-07-15T00:00:00Z')
    for (const name of ZONES) {
      timeZoneOffsetTextWith(name, at, requireTemporal())
    }

    expect(created).not.toHaveBeenCalled()
    expect(parts).not.toHaveBeenCalled()
  })

  it('creates at most one formatter and calls formatToParts at most once per name on the intl path', () => {
    const at = seconds('2026-07-15T00:00:00Z')
    for (const name of ZONES) {
      timeZoneOffsetTextWith(name, at, null)
    }

    expect(created.mock.calls.length).toBeLessThanOrEqual(ZONES.length)
    expect(parts.mock.calls.length).toBeLessThanOrEqual(ZONES.length)
    expect(created.mock.calls.length).toBeGreaterThan(0)
  })

  it('creates at most one formatter and calls formatToParts at most once for each of all the names on the intl path, as the options builder does without Temporal', () => {
    // 組み立て（buildTimeZoneOptions）は名前ごとに timeZoneOffsetText を 1 回だけ呼ぶ（buildTimeZoneOptions.test.ts が確かめる）。
    // Temporal が無い環境の組み立て全体の回数は、ここで全部の名前を Intl の道で 1 回ずつ呼んだ回数と同じになる（レビューの R-01）
    const at = seconds('2026-07-15T00:00:00Z')
    const over: string[] = []
    let labelled = 0
    for (const name of ZONES) {
      const createdBefore = created.mock.calls.length
      const partsBefore = parts.mock.calls.length
      if (timeZoneOffsetTextWith(name, at, null) !== null) {
        labelled += 1
      }
      if (
        created.mock.calls.length - createdBefore > 1 ||
        parts.mock.calls.length - partsBefore > 1
      ) {
        over.push(name)
      }
    }

    expect(over).toEqual([])
    expect(labelled).toBe(ZONES.length)
    expect(created.mock.calls.length).toBeLessThanOrEqual(ZONES.length)
    expect(parts.mock.calls.length).toBeLessThanOrEqual(ZONES.length)
  })

  it('does not ask Intl about names that fail the name check on the intl path', () => {
    for (const name of ['', '+09:00', '<script>', 'A'.repeat(65)]) {
      timeZoneOffsetTextWith(name, 0, null)
    }

    expect(created).not.toHaveBeenCalled()
  })

  it('takes the temporal path when the runtime has Temporal (no formatter is created)', () => {
    expect(timeZoneOffsetText('Asia/Tokyo', seconds('2026-07-15T00:00:00Z'))).toBe('+09:00')
    expect(created).not.toHaveBeenCalled()
    expect(parts).not.toHaveBeenCalled()
  })
})

describe('timeZoneOffsetTextWith agreement between the two paths', () => {
  // 全部の名前 × 4 つの時点（冬・北米の夏時間の切り替えの直前と直後・夏）で、Temporal の道と Intl の道のラベルが一致し、
  // どの名前も求まる（秒未満の時差などで黙って選択肢から落ちる名前が無い）ことを確かめる（レビューの R-03）。
  // 時点の数は、Intl の道で名前ごとに整形器を作るテストの時間を抑えるため 4 つに絞る。
  it.each([
    ['2026-01-15T00:00:00Z'],
    ['2026-03-08T06:59:59Z'],
    ['2026-03-08T07:00:00Z'],
    ['2026-07-15T00:00:00Z'],
  ])('gives the same label on both paths for every supported name at %s', (iso) => {
    const at = seconds(iso)
    const temporal = requireTemporal()
    const differing: string[] = []
    const missing: string[] = []
    for (const name of ZONES) {
      const byTemporal = timeZoneOffsetTextWith(name, at, temporal)
      const byIntl = timeZoneOffsetTextWith(name, at, null)
      if (byTemporal !== byIntl) {
        differing.push(`${name}: ${String(byTemporal)} / ${String(byIntl)}`)
      }
      if (byTemporal === null || byIntl === null) {
        missing.push(name)
      }
    }

    expect(differing).toEqual([])
    expect(missing).toEqual([])
  })
})

describe('temporalFrom', () => {
  it.each([undefined, null, 1, 'Temporal', {}, { Instant: null }, { Instant: {} }])(
    'gives null for %j, which lacks Temporal.Instant.fromEpochMilliseconds',
    (candidate) => {
      expect(temporalFrom(candidate)).toBeNull()
    },
  )

  it('accepts an object with Instant.fromEpochMilliseconds', () => {
    const fake = { Instant: { fromEpochMilliseconds: () => ({}) } }

    expect(temporalFrom(fake)).toBe(fake)
    expect(RUNTIME_TEMPORAL).not.toBeNull()
  })
})

describe('timeZoneOffsetTextWith with an unusual Temporal', () => {
  function fakeTemporal(offsetNanoseconds: () => number): TemporalLike {
    return {
      Instant: {
        fromEpochMilliseconds: () => ({
          toZonedDateTimeISO: () => ({
            get offsetNanoseconds() {
              return offsetNanoseconds()
            },
          }),
        }),
      },
    }
  }

  it('gives null when Temporal throws', () => {
    const throwing = fakeTemporal(() => {
      throw new RangeError('unknown time zone')
    })

    expect(timeZoneOffsetTextWith('Asia/Tokyo', 0, throwing)).toBeNull()
  })

  it('gives null for an offset with a fraction of a second, which the label cannot show', () => {
    expect(
      timeZoneOffsetTextWith(
        'Asia/Tokyo',
        0,
        fakeTemporal(() => 500_000_000),
      ),
    ).toBeNull()
  })

  it('formats whole-second offsets from Temporal', () => {
    expect(
      timeZoneOffsetTextWith(
        'Asia/Tokyo',
        0,
        fakeTemporal(() => -(3 * 3600 + 30 * 60) * 1e9),
      ),
    ).toBe('-03:30')
  })
})
