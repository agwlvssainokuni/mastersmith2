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
} from './pickerSearch'

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
    ['DATETIME', { kind: 'text', type: 'datetime-local', step: 1, hintKeys: [] }],
    [
      'DATETIME_TZ',
      { kind: 'text', type: 'datetime-local', step: 1, hintKeys: ['tables.form.utc'] },
    ],
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
    expect(validatePickerInputs(fields, {})).toEqual({ kind: 'empty' })
    expect(validatePickerInputs(fields, { code: '', name: '' })).toEqual({ kind: 'empty' })
    expect(validatePickerInputs(fields, { name: ' ' })).toEqual({
      kind: 'ok',
      conditions: [{ column: 'name', value: ' ' }],
    })
  })

  it('accepts 1,000 code points and rejects 1,001, counting a surrogate pair as one', () => {
    const pair = '𠮷'
    expect(validatePickerInputs(fields, { name: pair.repeat(1000) }).kind).toBe('ok')
    expect(validatePickerInputs(fields, { name: pair.repeat(1001) })).toEqual({
      kind: 'errors',
      errors: { name: 'too-long' },
    })
  })

  it('rejects an EQUALS value that cannot be read as its type but not a CONTAINS value', () => {
    expect(validatePickerInputs(fields, { code: '12a', name: '12a' })).toEqual({
      kind: 'errors',
      errors: { code: 'invalid-value' },
    })
    expect(validatePickerInputs(fields, { code: '-12' })).toEqual({
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
