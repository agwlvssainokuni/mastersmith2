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
// 検索の入力から条件を作る・確かめる関数のテスト（Intent 261016-business-data-views の U3 の L6、F2 の 1、Q3: A、
// AC1.2.1〜AC1.2.6）。
// 日時の条件（Intent 261102-timezone-ui-polish の U4、計画 Step 10、D23〜D29・D41、NFR2.1.5）。タイムゾーンは引数で明示に渡す。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as timeZoneModule from '../../shared/format/timeZone'
import type { SearchField } from './businessDataTypes'
import {
  EMPTY_SEARCH_INPUT,
  buildConditions,
  compareDecimalText,
  countActiveConditions,
  defaultSearchInputs,
  isReadableAs,
  resolveSearch,
  validateSearchInputs,
} from './searchConditions'

// 変換（parseLocalDateTime）の呼び出しの回数を数える。中身は本物のまま（NFR2.1.5）
vi.mock('../../shared/format/timeZone', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../shared/format/timeZone')>()
  return { ...actual, parseLocalDateTime: vi.fn(actual.parseLocalDateTime) }
})

const TOKYO = 'Asia/Tokyo'
const NEW_YORK = 'America/New_York'

function field(column: string, overrides: Partial<SearchField> = {}): SearchField {
  return {
    column,
    displayName: { ja: column, en: column },
    operator: 'EQUALS',
    valueType: 'STRING',
    defaultValue: null,
    collapsed: false,
    options: [],
    ...overrides,
  }
}

beforeEach(() => {
  vi.mocked(timeZoneModule.parseLocalDateTime).mockClear()
})

describe('searchConditions', () => {
  it('fills the default values for each operator', () => {
    const inputs = defaultSearchInputs(
      [
        field('a', { defaultValue: 'A01' }),
        field('b', { operator: 'RANGE', valueType: 'INTEGER', defaultValue: '10' }),
        field('c', { operator: 'CHOICE', defaultValue: 'ON_SALE' }),
        field('d', { operator: 'CONTAINS' }),
      ],
      TOKYO,
    )
    expect(inputs.a.value).toBe('A01')
    expect(inputs.b).toEqual({ ...EMPTY_SEARCH_INPUT, from: '10' })
    expect(inputs.c.values).toEqual(['ON_SALE'])
    expect(inputs.d).toEqual(EMPTY_SEARCH_INPUT)
  })

  it('builds conditions only for filled inputs of the current fields and keeps % and _ as text', () => {
    const fields = [
      field('name', { operator: 'CONTAINS' }),
      field('price', { operator: 'RANGE', valueType: 'DECIMAL' }),
      field('state', { operator: 'CHOICE' }),
      field('empty'),
    ]
    const inputs = {
      name: { ...EMPTY_SEARCH_INPUT, value: '50%_off' },
      price: { ...EMPTY_SEARCH_INPUT, to: '100' },
      state: { ...EMPTY_SEARCH_INPUT, values: ['A', 'B'] },
      removed: { ...EMPTY_SEARCH_INPUT, value: 'x' },
    }
    const expected = [
      { column: 'name', value: '50%_off' },
      { column: 'price', to: '100' },
      { column: 'state', values: ['A', 'B'] },
    ]
    expect(buildConditions(fields, inputs, TOKYO)).toEqual(expected)
    expect(resolveSearch(fields, inputs, TOKYO)).toEqual({ kind: 'ok', conditions: expected })
    expect(countActiveConditions(fields, inputs)).toBe(3)
  })

  it('reports values that cannot be read as the type and reversed ranges', () => {
    const fields = [
      field('qty', { valueType: 'INTEGER' }),
      field('price', { operator: 'RANGE', valueType: 'DECIMAL' }),
      field('day', { operator: 'RANGE', valueType: 'DATE' }),
      field('name', { operator: 'CONTAINS' }),
    ]
    const errors = validateSearchInputs(
      fields,
      {
        qty: { ...EMPTY_SEARCH_INPUT, value: '1.5' },
        price: { ...EMPTY_SEARCH_INPUT, from: '10.5', to: '9.99' },
        day: { ...EMPTY_SEARCH_INPUT, from: '2026-10-02', to: '2026-10-01' },
        name: { ...EMPTY_SEARCH_INPUT, value: 'abc' },
      },
      TOKYO,
    )
    expect(errors).toEqual({ qty: 'INVALID_VALUE', price: 'RANGE_REVERSED', day: 'RANGE_REVERSED' })
  })

  it('accepts values of exactly 1,000 code points and refuses 1,001', () => {
    const fields = [field('name', { operator: 'CONTAINS' })]
    expect(
      validateSearchInputs(
        fields,
        { name: { ...EMPTY_SEARCH_INPUT, value: '𠮷'.repeat(1000) } },
        TOKYO,
      ),
    ).toEqual({})
    expect(
      validateSearchInputs(
        fields,
        { name: { ...EMPTY_SEARCH_INPUT, value: '𠮷'.repeat(1000) + 'a' } },
        TOKYO,
      ),
    ).toEqual({ name: 'TOO_LONG' })
  })

  it('reads values by type', () => {
    expect(isReadableAs('BIG_INTEGER', '-9223372036854775808')).toBe(true)
    expect(isReadableAs('DECIMAL', '1e5')).toBe(false)
    expect(isReadableAs('DATETIME', '2026-10-18T09:30')).toBe(true)
    expect(isReadableAs('DATETIME_TZ', '2026-10-18T09:30:00+09:00')).toBe(true)
    expect(isReadableAs('TIME', '25')).toBe(false)
    expect(isReadableAs('BOOLEAN', 'yes')).toBe(false)
    expect(isReadableAs('STRING', "'; DROP")).toBe(true)
  })

  it('compares decimal texts without losing digits', () => {
    expect(compareDecimalText('99999999999999999999', '100000000000000000000')).toBeLessThan(0)
    expect(compareDecimalText('-2', '-10')).toBeGreaterThan(0)
    expect(compareDecimalText('1.50', '1.5')).toBe(0)
    expect(compareDecimalText('-0', '0')).toBe(0)
    expect(compareDecimalText('007', '7')).toBe(0)
  })
})

describe('searchConditions for date time conditions (Intent 261102-timezone-ui-polish U4)', () => {
  const at = field('at', { operator: 'RANGE', valueType: 'DATETIME_TZ' })
  const atEquals = field('atEq', { valueType: 'DATETIME_TZ' })
  const local = field('local', { operator: 'RANGE', valueType: 'DATETIME' })

  it('sends the instants of both ends of a range, both ends included as entered (D23, D24)', () => {
    const inputs = {
      at: { ...EMPTY_SEARCH_INPUT, from: '2026-11-02 00:00:00', to: '2026-11-02 23:59:59.999999' },
    }
    expect(resolveSearch([at], inputs, TOKYO)).toEqual({
      kind: 'ok',
      conditions: [
        { column: 'at', from: '2026-11-01T15:00:00+00:00', to: '2026-11-02T14:59:59.999999+00:00' },
      ],
    })
    expect(
      resolveSearch([at], { at: { ...EMPTY_SEARCH_INPUT, to: '2026-11-02 23:59:59' } }, TOKYO),
    ).toEqual({ kind: 'ok', conditions: [{ column: 'at', to: '2026-11-02T14:59:59+00:00' }] })
  })

  it('compares the ends of a range as instants and accepts the same instant (D25)', () => {
    const reversed = {
      at: { ...EMPTY_SEARCH_INPUT, from: '2026-11-02 09:00:01', to: '2026-11-02 09:00:00' },
    }
    expect(validateSearchInputs([at], reversed, TOKYO)).toEqual({ at: 'RANGE_REVERSED' })
    const same = {
      at: { ...EMPTY_SEARCH_INPUT, from: '2026-11-02 09:00:00', to: '2026-11-02 09:00:00.000' },
    }
    expect(validateSearchInputs([at], same, TOKYO)).toEqual({})
    const localSame = {
      local: { ...EMPTY_SEARCH_INPUT, from: '2026-11-02 09:00:00.000', to: '2026-11-02 09:00:00' },
    }
    expect(validateSearchInputs([local], localSame, TOKYO)).toEqual({})
    const localReversed = {
      local: { ...EMPTY_SEARCH_INPUT, from: '2026-11-02 09:00:00.5', to: '2026-11-02 09:00:00.10' },
    }
    expect(validateSearchInputs([local], localReversed, TOKYO)).toEqual({ local: 'RANGE_REVERSED' })
  })

  it('rejects a skipped time in a range and in an equals condition (D26)', () => {
    expect(
      validateSearchInputs(
        [at, atEquals],
        {
          at: { ...EMPTY_SEARCH_INPUT, from: '2026-03-08 02:30:00' },
          atEq: { ...EMPTY_SEARCH_INPUT, value: '2026-03-08 02:00:00' },
        },
        NEW_YORK,
      ),
    ).toEqual({ at: 'DATE_TIME_GAP', atEq: 'DATE_TIME_GAP' })
  })

  it('takes the earlier time for the start and the later time for the end of a range (D27)', () => {
    const inputs = {
      at: { ...EMPTY_SEARCH_INPUT, from: '2026-11-01 01:30:00', to: '2026-11-01 01:30:00' },
    }
    expect(resolveSearch([at], inputs, NEW_YORK)).toEqual({
      kind: 'ok',
      conditions: [
        { column: 'at', from: '2026-11-01T05:30:00+00:00', to: '2026-11-01T06:30:00+00:00' },
      ],
    })
  })

  it('needs a choice for an overlapping time in an equals condition (D27)', () => {
    const pending = { atEq: { ...EMPTY_SEARCH_INPUT, value: '2026-11-01 01:30:00' } }
    expect(validateSearchInputs([atEquals], pending, NEW_YORK)).toEqual({
      atEq: 'DATE_TIME_OVERLAP',
    })
    expect(buildConditions([atEquals], pending, NEW_YORK)).toEqual([])
    const chosen = { atEq: { ...pending.atEq, overlapChoice: 'later' as const } }
    expect(buildConditions([atEquals], chosen, NEW_YORK)).toEqual([
      { column: 'atEq', value: '2026-11-01T06:30:00+00:00' },
    ])
  })

  it.each([
    '2026-11-02T09:30:00',
    '2026-11-02 09:30',
    "2026-11-02 09:30:00'; --",
    '2026-11-02 09:30:00\n',
    '"2026-11-02 09:30:00"',
  ])('stops %j as an unreadable value and does not send it (D19, D29)', (text) => {
    const inputs = {
      atEq: { ...EMPTY_SEARCH_INPUT, value: text },
      local: { ...EMPTY_SEARCH_INPUT, from: text },
    }
    expect(validateSearchInputs([atEquals, local], inputs, TOKYO)).toEqual({
      atEq: 'DATE_TIME_FORMAT',
      local: 'DATE_TIME_FORMAT',
    })
    expect(buildConditions([atEquals, local], inputs, TOKYO)).toEqual([])
  })

  it('cannot convert an instant without a display time zone but sends the others', () => {
    const inputs = {
      atEq: { ...EMPTY_SEARCH_INPUT, value: '2026-11-02 09:30:00' },
      local: { ...EMPTY_SEARCH_INPUT, from: '2026-11-02 09:30:00.123456' },
    }
    expect(validateSearchInputs([atEquals, local], inputs, null)).toEqual({
      atEq: 'TIME_ZONE_UNAVAILABLE',
    })
    // 時点の条件が空なら、表示のタイムゾーンが無くても検索できる
    expect(resolveSearch([atEquals, local], { local: inputs.local }, null)).toEqual({
      kind: 'ok',
      conditions: [{ column: 'local', from: '2026-11-02T09:30:00.123456' }],
    })
  })

  it('does not convert CONTAINS and CHOICE conditions of a date time type', () => {
    const contains = field('c', { operator: 'CONTAINS', valueType: 'DATETIME_TZ' })
    const choice = field('h', { operator: 'CHOICE', valueType: 'DATETIME_TZ' })
    expect(
      buildConditions(
        [contains, choice],
        {
          c: { ...EMPTY_SEARCH_INPUT, value: '2026-11' },
          h: { ...EMPTY_SEARCH_INPUT, values: ['2026-11-02T00:00:00Z'] },
        },
        TOKYO,
      ),
    ).toEqual([
      { column: 'c', value: '2026-11' },
      { column: 'h', values: ['2026-11-02T00:00:00Z'] },
    ])
    expect(timeZoneModule.parseLocalDateTime).not.toHaveBeenCalled()
  })

  it('puts the default value in the wall clock of the display time zone once it is known (D28)', () => {
    const fields = [
      field('a', {
        operator: 'RANGE',
        valueType: 'DATETIME_TZ',
        defaultValue: '2026-11-01T15:00:00Z',
      }),
      field('b', { valueType: 'DATETIME_TZ', defaultValue: '2026-11-01T15:00:00.123' }),
      field('c', { valueType: 'DATETIME_TZ', defaultValue: 'yesterday' }),
      field('d', {
        operator: 'RANGE',
        valueType: 'DATETIME',
        defaultValue: '2026-11-01T15:00:00.5',
      }),
      field('e', { valueType: 'DATE', defaultValue: '2026-11-01' }),
    ]
    const ready = defaultSearchInputs(fields, TOKYO)
    expect(ready.a.from).toBe('2026-11-02 00:00:00')
    // 時差が無い既定の値は UTC とみなす
    expect(ready.b.value).toBe('2026-11-02 00:00:00.123')
    // 読めない既定の値は元の文字のまま（検索の押下で誤りになる）
    expect(ready.c.value).toBe('yesterday')
    expect(ready.d.from).toBe('2026-11-01 15:00:00.5')
    expect(ready.e.value).toBe('2026-11-01')
    const waiting = defaultSearchInputs(fields, null)
    expect([waiting.a.from, waiting.b.value, waiting.c.value]).toEqual(['', '', ''])
    expect(waiting.d.from).toBe('2026-11-01 15:00:00.5')
  })

  it('converts each date time condition once per call and not for paging (NFR2.1.5)', () => {
    const fields = [at, atEquals, local, field('name')]
    const inputs = {
      at: { ...EMPTY_SEARCH_INPUT, from: '2026-11-02 00:00:00', to: '2026-11-03 00:00:00' },
      atEq: { ...EMPTY_SEARCH_INPUT, value: '2026-11-02 09:30:00' },
      local: { ...EMPTY_SEARCH_INPUT, to: '2026-11-02 09:30:00' },
      name: { ...EMPTY_SEARCH_INPUT, value: 'x' },
    }
    const resolved = resolveSearch(fields, inputs, TOKYO)
    expect(resolved.kind).toBe('ok')
    // 時点の範囲の両端（2回）＋一致（1回）＋時差を持たない日時の書式の確かめ（1回）
    expect(timeZoneModule.parseLocalDateTime).toHaveBeenCalledTimes(4)
    // ページ送りは作った条件を持ち回り、数えるだけの関数は変換しない
    vi.mocked(timeZoneModule.parseLocalDateTime).mockClear()
    expect(countActiveConditions(fields, inputs)).toBe(4)
    expect(timeZoneModule.parseLocalDateTime).not.toHaveBeenCalled()
  })
})
