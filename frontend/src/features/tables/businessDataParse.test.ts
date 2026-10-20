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
// 写しの関数の例のテスト（Intent 261016-business-data-views の U3 の L2、計画 Step 3、U3-NFR4.24）。
import { describe, expect, it } from 'vitest'
import {
  MAX_PAGE,
  parseBusinessProblem,
  parseColumnDefinition,
  parseCreatedKey,
  parseRowDetail,
  parseRowPage,
  parseTableDefinition,
} from './businessDataParse'
import {
  SAMPLE_REF,
  wireColumn,
  wireDefinition,
  wireDetail,
  wirePage,
  wireRow,
} from './testing/businessDataSamples'

describe('parseTableDefinition', () => {
  it('copies a well-formed definition including the fields added by U1', () => {
    const parsed = parseTableDefinition(wireDefinition(), SAMPLE_REF)
    expect(parsed).toMatchObject({
      definitionId: { dslHash: 'hash-1', workRoleId: 1 },
      schema: 'shop',
      table: 'item',
      hasPrimaryKey: true,
      primaryKeyColumns: ['code'],
      operations: ['LIST', 'DETAIL'],
      restrictionReason: null,
      defaultSort: [{ column: 'code', direction: 'ASC' }],
    })
    expect(parsed?.listColumns[2]).toMatchObject({
      valueType: 'DECIMAL',
      listFormat: 'NUMBER_GROUPED',
      width: null,
      formPart: null,
    })
  })

  it('accepts missing nullable fields as null and ignores unknown fields', () => {
    const body = wireDefinition({ extra: 'x' })
    delete body.restrictionReason
    const column = wireColumn('code')
    delete column.listFormat
    delete column.width
    delete column.formPart
    body.listColumns = [column]
    const parsed = parseTableDefinition(body, SAMPLE_REF)
    expect(parsed?.restrictionReason).toBeNull()
    expect(parsed?.listColumns[0]).toMatchObject({ listFormat: null, width: null })
    expect(parsed?.listColumns[0]).not.toHaveProperty('formPart')
    expect(parsed).not.toHaveProperty('extra')
  })

  it('rejects a definition of another table than the requested one', () => {
    expect(parseTableDefinition(wireDefinition({ table: 'other' }), SAMPLE_REF)).toBeUndefined()
    expect(parseTableDefinition(wireDefinition({ schema: 'SHOP' }), SAMPLE_REF)).toBeUndefined()
  })

  it.each([
    ['an unknown operation', { operations: ['LIST', 'EXPORT'] }],
    ['an unknown restriction reason', { restrictionReason: 'SECRET' }],
    ['a missing display name language', { displayName: { ja: '商品' } }],
    ['a broken definition id', { definitionId: { dslHash: 1 } }],
    ['a non boolean flag', { writeConfigured: 'false' }],
    ['a broken sort key', { defaultSort: [{ column: 'code', direction: 'UP' }] }],
    ['a broken search field', { searchFields: [{ column: 'code' }] }],
  ])('rejects %s', (_, overrides) => {
    expect(parseTableDefinition(wireDefinition(overrides), SAMPLE_REF)).toBeUndefined()
  })

  it('rejects null and non objects', () => {
    for (const body of [null, 'x', 1, [], undefined]) {
      expect(parseTableDefinition(body, SAMPLE_REF)).toBeUndefined()
    }
  })
})

describe('parseColumnDefinition', () => {
  it('rejects the lookup form part and unknown value types or formats', () => {
    expect(parseColumnDefinition(wireColumn('c', { formPart: 'lookup' }))).toBeUndefined()
    expect(parseColumnDefinition(wireColumn('c', { formPart: 'rich' }))).toBeUndefined()
    expect(parseColumnDefinition(wireColumn('c', { valueType: 'JSON' }))).toBeUndefined()
    expect(parseColumnDefinition(wireColumn('c', { listFormat: 'MONEY' }))).toBeUndefined()
    expect(parseColumnDefinition(wireColumn('c', { formPart: 'text' }))?.formPart).toBe('text')
  })

  it('rejects wrongly typed validation materials and widths', () => {
    expect(parseColumnDefinition(wireColumn('c', { maxLength: '10' }))).toBeUndefined()
    expect(parseColumnDefinition(wireColumn('c', { min: 1 }))).toBeUndefined()
    expect(parseColumnDefinition(wireColumn('c', { required: 'yes' }))).toBeUndefined()
    expect(parseColumnDefinition(wireColumn('c', { choices: [{ value: 1 }] }))).toBeUndefined()
    expect(parseColumnDefinition(wireColumn('c', { width: 0 }))).toBeUndefined()
    expect(parseColumnDefinition(wireColumn('c', { width: 1.5 }))).toBeUndefined()
  })

  it('keeps column names such as __proto__ as plain strings', () => {
    expect(parseColumnDefinition(wireColumn('__proto__'))?.column).toBe('__proto__')
  })
})

describe('parseRowPage', () => {
  it('copies rows with text and null values without turning them into numbers', () => {
    const page = parseRowPage(
      wirePage([
        wireRow('A01', { values: { code: 'A01', price: '12345678901234567890.123', name: null } }),
        wireRow('A02', { key: null }),
      ]),
    )
    expect(page?.items[0]?.values).toEqual({
      code: 'A01',
      price: '12345678901234567890.123',
      name: null,
    })
    expect(page?.items[1]?.key).toBeNull()
    expect(page?.total).toBe(2)
  })

  it('accepts a missing key as null', () => {
    const row = wireRow('A01')
    delete row.key
    expect(parseRowPage(wirePage([row]))?.items[0]?.key).toBeNull()
  })

  it.each([
    ['a numeric value', { items: [wireRow('A', { values: { code: 1 } })] }],
    ['a numeric key', { items: [wireRow('A', { key: { code: 1 } })] }],
    ['page 0', { page: 0 }],
    ['a page beyond the limit', { page: MAX_PAGE + 1 }],
    ['an unknown size', { size: 30 }],
    ['an unsafe total', { total: Number.MAX_SAFE_INTEGER + 1 }],
    ['a negative total', { total: -1 }],
    ['a fractional page', { page: 1.5 }],
    [
      'more items than the size',
      { size: 20, items: Array.from({ length: 21 }, () => wireRow('A')) },
    ],
  ])('rejects %s', (_, overrides) => {
    expect(parseRowPage(wirePage([wireRow('A01')], overrides))).toBeUndefined()
  })
})

describe('parseRowDetail', () => {
  it('copies a detail and rejects a missing key', () => {
    expect(parseRowDetail(wireDetail('A01'))?.key).toEqual({ code: 'A01' })
    expect(parseRowDetail(wireDetail('A01', { key: null }))).toBeUndefined()
    expect(parseRowDetail(wireDetail('A01', { truncatedColumns: 'name' }))).toBeUndefined()
  })
})

describe('parseBusinessProblem', () => {
  function responseError(status: number, problem: unknown, code?: string) {
    const error: Record<string, unknown> = { kind: 'response', status }
    if (code !== undefined) {
      error.code = code
    }
    Object.defineProperty(error, 'problem', { value: problem, enumerable: false })
    return error
  }

  it('reads the code, field errors and definitionChanged also on a 400', () => {
    const problem = parseBusinessProblem(
      responseError(
        400,
        {
          code: 'VALIDATION_FAILED',
          definitionChanged: true,
          fieldErrors: [
            { field: 'name', reason: 'TOO_LONG' },
            { field: 'code', reason: 'PATTERN_MISMATCH', message: { ja: '英数字', en: 'Alnum' } },
            { field: 1, reason: 'X' },
          ],
        },
        'VALIDATION_FAILED',
      ),
    )
    expect(problem).toEqual({
      status: 400,
      code: 'VALIDATION_FAILED',
      definitionChanged: true,
      fieldErrors: [
        { field: 'name', reason: 'TOO_LONG' },
        { field: 'code', reason: 'PATTERN_MISMATCH', message: { ja: '英数字', en: 'Alnum' } },
      ],
    })
  })

  it('treats a missing problem as no field errors and network failures as undefined', () => {
    expect(parseBusinessProblem(responseError(503, undefined))).toEqual({
      status: 503,
      fieldErrors: [],
      definitionChanged: false,
    })
    expect(parseBusinessProblem({ kind: 'network' })).toBeUndefined()
    expect(parseBusinessProblem(new Error('x'))).toBeUndefined()
  })
})

describe('businessDataParse write material (B6)', () => {
  const editable = (overrides: Record<string, unknown>) =>
    wireDefinition({
      detailColumns: [
        wireColumn('code'),
        wireColumn('name', { editableOnCreate: true, formPart: 'text', ...overrides }),
      ],
    })

  it('accepts an editable column with formPart, required and nullable', () => {
    expect(parseTableDefinition(editable({}), SAMPLE_REF)).toBeDefined()
  })

  it.each([
    ['formPart is null', { formPart: null }],
    ['formPart is missing', { formPart: undefined }],
    ['formPart is lookup', { formPart: 'lookup' }],
    ['formPart is unknown', { formPart: 'slider' }],
    ['required is missing', { required: undefined }],
    ['nullable is missing', { nullable: undefined }],
    ['nullable is not a boolean', { nullable: 'yes' }],
  ])('treats an editable column whose %s as a broken shape', (_name, overrides) => {
    expect(parseTableDefinition(editable(overrides), SAMPLE_REF)).toBeUndefined()
  })

  it('accepts formPart null for a column that is not editable', () => {
    expect(
      parseTableDefinition(
        wireDefinition({ detailColumns: [wireColumn('code', { formPart: null })] }),
        SAMPLE_REF,
      ),
    ).toBeDefined()
  })

  it('reads the key of a created row and returns null for no key or a broken key', () => {
    expect(parseCreatedKey({ key: { code: '<A>' } })).toEqual({ code: '<A>' })
    expect(parseCreatedKey({})).toBeNull()
    expect(parseCreatedKey({ key: null })).toBeNull()
    expect(parseCreatedKey({ key: {} })).toBeNull()
    expect(parseCreatedKey({ key: { code: 1 } })).toBeNull()
    expect(parseCreatedKey('text')).toBeNull()
  })
})
