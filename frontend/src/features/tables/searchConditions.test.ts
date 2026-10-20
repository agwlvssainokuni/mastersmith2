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
import { describe, expect, it } from 'vitest'
import type { SearchField } from './businessDataTypes'
import {
  EMPTY_SEARCH_INPUT,
  buildConditions,
  compareDecimalText,
  countActiveConditions,
  defaultSearchInputs,
  isReadableAs,
  validateSearchInputs,
} from './searchConditions'

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

describe('searchConditions', () => {
  it('fills the default values for each operator', () => {
    const inputs = defaultSearchInputs([
      field('a', { defaultValue: 'A01' }),
      field('b', { operator: 'RANGE', valueType: 'INTEGER', defaultValue: '10' }),
      field('c', { operator: 'CHOICE', defaultValue: 'ON_SALE' }),
      field('d', { operator: 'CONTAINS' }),
    ])
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
    expect(buildConditions(fields, inputs)).toEqual([
      { column: 'name', value: '50%_off' },
      { column: 'price', to: '100' },
      { column: 'state', values: ['A', 'B'] },
    ])
    expect(countActiveConditions(fields, inputs)).toBe(3)
  })

  it('reports values that cannot be read as the type and reversed ranges', () => {
    const fields = [
      field('qty', { valueType: 'INTEGER' }),
      field('price', { operator: 'RANGE', valueType: 'DECIMAL' }),
      field('day', { operator: 'RANGE', valueType: 'DATE' }),
      field('name', { operator: 'CONTAINS' }),
    ]
    const errors = validateSearchInputs(fields, {
      qty: { ...EMPTY_SEARCH_INPUT, value: '1.5' },
      price: { ...EMPTY_SEARCH_INPUT, from: '10.5', to: '9.99' },
      day: { ...EMPTY_SEARCH_INPUT, from: '2026-10-02', to: '2026-10-01' },
      name: { ...EMPTY_SEARCH_INPUT, value: 'abc' },
    })
    expect(errors).toEqual({ qty: 'INVALID_VALUE', price: 'RANGE_REVERSED', day: 'RANGE_REVERSED' })
  })

  it('accepts values of exactly 1,000 code points and refuses 1,001', () => {
    const fields = [field('name', { operator: 'CONTAINS' })]
    expect(
      validateSearchInputs(fields, { name: { ...EMPTY_SEARCH_INPUT, value: '𠮷'.repeat(1000) } }),
    ).toEqual({})
    expect(
      validateSearchInputs(fields, {
        name: { ...EMPTY_SEARCH_INPUT, value: '𠮷'.repeat(1000) + 'a' },
      }),
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
