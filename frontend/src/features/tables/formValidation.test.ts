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
// 作成・更新のフォームの画面の検証のテスト（Intent 261016-business-data-views の U3 の L5、計画 Step 29、D21）。
import { describe, expect, it } from 'vitest'
import { parseColumnDefinition } from './businessDataParse'
import type { ColumnDefinition } from './businessDataTypes'
import {
  compareDecimalText,
  effectiveMaxLength,
  validateForm,
  validateValue,
} from './formValidation'
import { wireColumn } from './testing/businessDataSamples'
import { wireBlocked } from './testing/referenceSamples'

function column(name: string, overrides: Record<string, unknown> = {}): ColumnDefinition {
  const parsed = parseColumnDefinition(
    wireColumn(name, {
      editableOnCreate: true,
      editableOnUpdate: true,
      formPart: 'text',
      ...overrides,
    }),
  )
  if (parsed === undefined) {
    throw new Error('the sample column does not pass the parser')
  }
  return parsed
}

describe('formValidation', () => {
  it('checks required and not null in the server order', () => {
    const required = column('r', { required: true, nullable: false })
    expect(validateValue(required, '')).toBe('REQUIRED')
    expect(validateValue(required, null)).toBe('REQUIRED')
    expect(validateValue(column('n', { nullable: false }), null)).toBe('NOT_NULL')
    expect(validateValue(column('o'), null)).toBeUndefined()
    expect(validateValue(column('i', { valueType: 'INTEGER', formPart: 'number' }), '')).toBe(
      'INVALID_VALUE',
    )
  })

  it('counts code points against the shorter of the DSL maximum and 10,000', () => {
    expect(effectiveMaxLength(column('a'))).toBe(10_000)
    expect(effectiveMaxLength(column('b', { maxLength: 20 }))).toBe(20)
    expect(effectiveMaxLength(column('c', { maxLength: 20_000 }))).toBe(10_000)
    expect(validateValue(column('d'), 'a'.repeat(10_000))).toBeUndefined()
    expect(validateValue(column('d'), 'a'.repeat(10_001))).toBe('TOO_LONG')
    expect(validateValue(column('e', { minLength: 3 }), 'ab')).toBe('TOO_SHORT')
    expect(validateValue(column('e', { minLength: 3 }), '')).toBeUndefined()
  })

  it('checks choices, types, digits and ranges without converting to numbers', () => {
    expect(
      validateValue(column('c', { choices: [{ value: 'A', label: { ja: 'A', en: 'A' } }] }), 'B'),
    ).toBe('NOT_A_CHOICE')
    const decimal = column('p', {
      valueType: 'DECIMAL',
      precision: 5,
      scale: 2,
      min: '-10',
      max: '999.99',
    })
    expect(validateValue(decimal, '1.2.3')).toBe('INVALID_VALUE')
    expect(validateValue(decimal, '999.99')).toBeUndefined()
    expect(validateValue(decimal, '1.234')).toBe('TOO_MANY_DIGITS')
    expect(validateValue(decimal, '1000')).toBe('TOO_MANY_DIGITS')
    expect(validateValue(decimal, '-10.01')).toBe('OUT_OF_RANGE')
    const big = column('b', { valueType: 'BIG_INTEGER', max: '9223372036854775807' })
    expect(validateValue(big, '9223372036854775807')).toBeUndefined()
    expect(validateValue(big, '9223372036854775808')).toBe('OUT_OF_RANGE')
    expect(validateValue(column('d', { valueType: 'DATE', formPart: 'date' }), '2026/10/18')).toBe(
      'INVALID_VALUE',
    )
    expect(
      validateValue(
        column('t', { valueType: 'DATETIME_TZ', formPart: 'datetime' }),
        '2026-10-18T09:30:00+00:00',
      ),
    ).toBeUndefined()
    expect(validateValue(column('x', { valueType: 'BOOLEAN', formPart: 'checkbox' }), 'yes')).toBe(
      'INVALID_VALUE',
    )
  })

  it('reports required columns that a create does not send, and checks only sent columns on update', () => {
    const columns = [
      column('code', { required: true }),
      column('name'),
      column('fixed', { editableOnCreate: false, editableOnUpdate: false, formPart: null }),
    ]
    expect(validateForm(columns, 'create', {})).toEqual({ code: 'REQUIRED' })
    expect(validateForm(columns, 'update', {})).toEqual({})
    expect(validateForm(columns, 'update', { name: 'a'.repeat(10_001) })).toEqual({
      name: 'TOO_LONG',
    })
  })

  it('compares decimal texts', () => {
    expect(compareDecimalText('-0', '0')).toBe(0)
    expect(compareDecimalText('0.10', '0.1')).toBe(0)
    expect(compareDecimalText('-2', '1')).toBe(-1)
    expect(compareDecimalText('12345678901234567890', '12345678901234567891')).toBe(-1)
    expect(compareDecimalText('1e3', '1')).toBeUndefined()
  })

  it('returns +0, not -0, for two equal negative decimal texts', () => {
    expect(Object.is(compareDecimalText('-28', '-28'), 0)).toBe(true)
    expect(Object.is(compareDecimalText('-1.50', '-1.5'), 0)).toBe(true)
    expect(compareDecimalText('-28', '-27')).toBe(-1)
    expect(compareDecimalText('-27', '-28')).toBe(1)
  })
})

// Intent 261029-reference-lookup-cleanup の U3（D15・X-5）
describe('validateForm with locked reference columns', () => {
  const blocked = column('product', {
    editableOnCreate: true,
    formPart: 'select',
    required: true,
    nullable: false,
    reference: wireBlocked('REFERENCE', 'NO_PERMISSION'),
  })

  it('does not check a locked column and keeps the old result without the argument', () => {
    expect(validateForm([blocked], 'create', {}, [], new Set(['product']))).toEqual({})
    expect(validateForm([blocked], 'create', {})).toEqual({ product: 'REQUIRED' })
  })
})
