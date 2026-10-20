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
// 入力の値と送る本文の写しのテスト（Intent 261016-business-data-views の U3 の L5、計画 Step 29、D20）。
import { describe, expect, it } from 'vitest'
import { parseColumnDefinition } from './businessDataParse'
import type { ColumnDefinition } from './businessDataTypes'
import { diffForm, hasFormChanges, initialInputs, toInputValue } from './formDiff'
import { wireColumn } from './testing/businessDataSamples'

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

const columns = [
  column('code', { editableOnUpdate: false, required: true, nullable: false }),
  column('name'),
  column('price', { valueType: 'DECIMAL' }),
  column('active', { valueType: 'BOOLEAN', formPart: 'checkbox' }),
  column('flag', { valueType: 'BOOLEAN', formPart: 'switch', required: true, nullable: false }),
  column('secret', { formPart: 'password', masked: true }),
  column('at', { valueType: 'DATETIME_TZ', formPart: 'datetime' }),
  column('local', { valueType: 'DATETIME', formPart: 'datetime' }),
  column('fixed', { editableOnCreate: false, editableOnUpdate: false, formPart: null }),
]

describe('formDiff', () => {
  it('starts a create form empty except required booleans, which start false', () => {
    expect(initialInputs(columns, 'create')).toEqual({
      code: '',
      name: '',
      price: '',
      active: '',
      flag: 'false',
      secret: '',
      at: '',
      local: '',
    })
  })

  it('sends only non empty values on create and converts date times', () => {
    const initial = initialInputs(columns, 'create')
    const values = diffForm(columns, 'create', initial, {
      ...initial,
      code: 'A/1',
      price: '12345678901234567890.123456789',
      at: '2026-10-18T09:30',
      local: '2026-10-18T09:30:15',
    })
    expect(values).toEqual({
      code: 'A/1',
      price: '12345678901234567890.123456789',
      flag: 'false',
      at: '2026-10-18T09:30:00+00:00',
      local: '2026-10-18T09:30:15',
    })
  })

  it('fills an update form from the detail values and leaves out key and read-only columns', () => {
    const inputs = initialInputs(columns, 'update', {
      code: 'A',
      name: null,
      price: '1.50',
      active: null,
      flag: 'true',
      at: '2026-10-18T09:30:00+00:00',
      local: '2026-10-18T09:30:00.123',
    })
    expect(inputs).toEqual({
      name: '',
      price: '1.50',
      active: '',
      flag: 'true',
      secret: '',
      at: '2026-10-18T09:30:00',
      // 入力は秒まで（D20）。変えなければ送らないため、DB の秒の端数は保たれる。
      local: '2026-10-18T09:30:00',
    })
  })

  it('sends only changed columns on update and clears strings with "" and others with null', () => {
    const initial = initialInputs(columns, 'update', {
      name: 'old',
      price: '1',
      active: null,
      flag: 'true',
    })
    const values = diffForm(columns, 'update', initial, {
      ...initial,
      name: '',
      price: '',
      flag: 'false',
    })
    expect(values).toEqual({ name: '', price: null, flag: 'false' })
  })

  it('does not send an untouched null boolean nor an empty password, and sends a typed password', () => {
    const initial = initialInputs(columns, 'update', { active: null })
    expect(diffForm(columns, 'update', initial, initial)).toEqual({})
    expect(diffForm(columns, 'update', initial, { ...initial, secret: 'p@ss' })).toEqual({
      secret: 'p@ss',
    })
  })

  it('treats a truncated column as read-only on update', () => {
    const initial = initialInputs(columns, 'update', { name: 'long' }, ['name'])
    expect(initial).not.toHaveProperty('name')
    expect(diffForm(columns, 'update', initial, { ...initial, name: 'x' }, ['name'])).toEqual({})
  })

  it('reports changes against the first input values', () => {
    const initial = initialInputs(columns, 'update', { name: 'a' })
    expect(hasFormChanges(columns, 'update', initial, initial)).toBe(false)
    expect(hasFormChanges(columns, 'update', initial, { ...initial, name: 'b' })).toBe(true)
    expect(hasFormChanges(columns, 'update', initial, { ...initial, name: 'a' })).toBe(false)
  })

  it('keeps masked values out of the first input value', () => {
    expect(toInputValue(columns[5], 'anything')).toBe('')
  })
})
