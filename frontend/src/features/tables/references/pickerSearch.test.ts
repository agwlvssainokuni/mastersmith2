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
// 参照ピッカーの検索の条件とページの表示のテスト（Intent 261029-reference-lookup-cleanup の U3、D22〜D26、R-02）。
// fast-check は失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に書いて行い、seed を記録に残す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { VALUE_TYPES, type PickerSearchField, type ValueType } from '../businessDataTypes'
import {
  buildPickerConditions,
  pickerPaging,
  searchInputFor,
  validatePickerInputs,
  type PickerTime,
} from './pickerSearch'

const TOKYO_TIME: PickerTime = { timeZone: 'Asia/Tokyo', overlapChoices: {} }

function field(
  column: string,
  operator: 'EQUALS' | 'CONTAINS' = 'EQUALS',
  valueType: ValueType = 'STRING',
): PickerSearchField {
  return { column, displayName: { ja: column, en: column }, operator, valueType }
}

/** Table と同じ総ページの式（make-you-chic-ui の tableLogic.computeTotalPages） */
function computeTotalPages(totalCount: number, pageSize: number): number {
  return Math.max(1, Math.ceil(totalCount / pageSize))
}

describe('searchInputFor (D22)', () => {
  it.each([
    ['STRING', { kind: 'text', type: 'text', hintKeys: [] }],
    ['INTEGER', { kind: 'text', type: 'number', hintKeys: [] }],
    [
      'BIG_INTEGER',
      { kind: 'text', type: 'text', inputMode: 'decimal', hintKeys: ['tables.form.numberHint'] },
    ],
    [
      'DECIMAL',
      { kind: 'text', type: 'text', inputMode: 'decimal', hintKeys: ['tables.form.numberHint'] },
    ],
    ['BOOLEAN', { kind: 'boolean' }],
    ['DATE', { kind: 'text', type: 'date', hintKeys: ['tables.form.dateHint'] }],
    // 日時は文字の入力（Intent 261102-timezone-ui-polish の U4、D34。「UTC で入れる」の案内を出さない）
    ['DATETIME', { kind: 'text', type: 'text', hintKeys: [], dateTime: 'local' }],
    ['DATETIME_TZ', { kind: 'text', type: 'text', hintKeys: [], dateTime: 'instant' }],
    ['TIME', { kind: 'text', type: 'text', hintKeys: [] }],
    ['TIME_TZ', { kind: 'text', type: 'text', hintKeys: [] }],
    ['BINARY', { kind: 'text', type: 'text', hintKeys: [] }],
    ['OTHER', { kind: 'text', type: 'text', hintKeys: [] }],
  ] as const)('chooses the input for EQUALS %s', (valueType, expected) => {
    expect(searchInputFor(field('c', 'EQUALS', valueType))).toEqual(expected)
  })

  it.each(VALUE_TYPES)('always uses a text input for CONTAINS %s', (valueType) => {
    expect(searchInputFor(field('c', 'CONTAINS', valueType))).toEqual({
      kind: 'text',
      type: 'text',
      hintKeys: [],
    })
  })
})

describe('validatePickerInputs (D23)', () => {
  const fields = [field('code', 'EQUALS', 'INTEGER'), field('name', 'CONTAINS')]

  it('does not send when every condition is empty and treats spaces as a value', () => {
    expect(validatePickerInputs(fields, {}, TOKYO_TIME)).toEqual({ kind: 'empty' })
    expect(validatePickerInputs(fields, { code: '', name: '' }, TOKYO_TIME)).toEqual({
      kind: 'empty',
    })
    expect(validatePickerInputs(fields, { name: ' ' }, TOKYO_TIME)).toEqual({
      kind: 'ok',
      conditions: [{ column: 'name', value: ' ' }],
    })
  })

  it('accepts 1,000 code points and rejects 1,001, counting a surrogate pair as one', () => {
    const pair = '𠮷'
    expect(validatePickerInputs(fields, { name: pair.repeat(1000) }, TOKYO_TIME).kind).toBe('ok')
    expect(validatePickerInputs(fields, { name: pair.repeat(1001) }, TOKYO_TIME)).toEqual({
      kind: 'errors',
      errors: { name: 'too-long' },
    })
  })

  it('rejects an EQUALS value that cannot be read as its type but not a CONTAINS value', () => {
    expect(validatePickerInputs(fields, { code: '12a', name: '12a' }, TOKYO_TIME)).toEqual({
      kind: 'errors',
      errors: { code: 'invalid-value' },
    })
    expect(validatePickerInputs(fields, { code: '-12' }, TOKYO_TIME)).toEqual({
      kind: 'ok',
      conditions: [{ column: 'code', value: '-12' }],
    })
  })

  it('keeps % and _ as they are', () => {
    expect(buildPickerConditions(fields, { name: '%_\\' })).toEqual([
      { column: 'name', value: '%_\\' },
    ])
  })
})

// 日時の一致の条件（Intent 261102-timezone-ui-polish の U4、計画 Step 11、D23・D26・D27・D34）
describe('validatePickerInputs for date time conditions', () => {
  const at = field('at', 'EQUALS', 'DATETIME_TZ')
  const local = field('local', 'EQUALS', 'DATETIME')
  const atContains = field('atText', 'CONTAINS', 'DATETIME_TZ')
  const newYork: PickerTime = { timeZone: 'America/New_York', overlapChoices: {} }

  it('sends the instant of the wall clock time of the display time zone', () => {
    expect(
      validatePickerInputs(
        [at, local],
        { at: '2026-11-02 00:00:00.123', local: '2026-11-02 00:00:00' },
        TOKYO_TIME,
      ),
    ).toEqual({
      kind: 'ok',
      conditions: [
        { column: 'at', value: '2026-11-01T15:00:00.123+00:00' },
        { column: 'local', value: '2026-11-02T00:00:00' },
      ],
    })
  })

  it('rejects an unreadable value, a skipped time and an unchosen overlapping time', () => {
    expect(
      validatePickerInputs(
        [at, local],
        { at: '2026-11-02T00:00', local: '2026-11-02 9:00:00' },
        TOKYO_TIME,
      ),
    ).toEqual({
      kind: 'errors',
      errors: { at: 'DATE_TIME_FORMAT', local: 'DATE_TIME_FORMAT' },
    })
    expect(validatePickerInputs([at], { at: '2026-03-08 02:30:00' }, newYork)).toEqual({
      kind: 'errors',
      errors: { at: 'DATE_TIME_GAP' },
    })
    expect(validatePickerInputs([at], { at: '2026-11-01 01:30:00' }, newYork)).toEqual({
      kind: 'errors',
      errors: { at: 'DATE_TIME_OVERLAP' },
      overlaps: { at: { earlier: '-04:00', later: '-05:00' } },
    })
    expect(
      validatePickerInputs(
        [at],
        { at: '2026-11-01 01:30:00' },
        { ...newYork, overlapChoices: { at: 'earlier' } },
      ),
    ).toEqual({ kind: 'ok', conditions: [{ column: 'at', value: '2026-11-01T05:30:00+00:00' }] })
  })

  it('cannot convert without a display time zone and does not convert CONTAINS', () => {
    expect(
      validatePickerInputs(
        [at],
        { at: '2026-11-02 00:00:00' },
        { timeZone: null, overlapChoices: {} },
      ),
    ).toEqual({ kind: 'errors', errors: { at: 'TIME_ZONE_UNAVAILABLE' } })
    expect(validatePickerInputs([atContains], { atText: '2026-11' }, TOKYO_TIME)).toEqual({
      kind: 'ok',
      conditions: [{ column: 'atText', value: '2026-11' }],
    })
  })
})

describe('pickerSearch properties', () => {
  it('never puts an empty value into the conditions', () => {
    fc.assert(
      fc.property(fc.dictionary(fc.constantFrom('code', 'name', 'x'), fc.string()), (inputs) => {
        const conditions = buildPickerConditions([field('code'), field('name', 'CONTAINS')], inputs)
        expect(conditions.every((condition) => condition.value !== '')).toBe(true)
        expect(conditions.every((condition) => condition.column !== 'x')).toBe(true)
      }),
    )
  })

  const pagingArb = fc
    .tuple(fc.integer({ min: 1, max: 1_000_000 }), fc.integer({ min: 0, max: 20 }), fc.boolean())
    .map(([page, rowCount, hasNext]) => ({ page, rowCount: hasNext ? 20 : rowCount, hasNext }))

  it('gives Table a count whose total pages match the next page (1)', () => {
    fc.assert(
      fc.property(pagingArb, ({ page, rowCount, hasNext }) => {
        fc.pre(rowCount >= 1)
        const paging = pickerPaging(page, rowCount, hasNext)
        expect(paging.kind).toBe('table')
        if (paging.kind === 'table') {
          expect(computeTotalPages(paging.totalCount, 20)).toBe(hasNext ? page + 1 : page)
          expect(paging.statusKey).toBe(
            hasNext ? 'tables.picker.pageStatusMore' : 'tables.picker.pageStatusLast',
          )
          expect(paging.to - paging.from + 1).toBe(rowCount)
        }
      }),
    )
  })

  it('goes back one page without Table on an empty later page (2) and has no way back on page 1 (3)', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 1_000_000 }), (page) => {
        expect(pickerPaging(page, 0, false)).toEqual({
          kind: 'empty',
          backTo: page >= 2 ? page - 1 : null,
        })
      }),
    )
  })
})
