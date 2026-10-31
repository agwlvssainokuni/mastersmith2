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
  parseReference,
  parseReferenceLabel,
  parseReferenceRowPage,
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
import {
  SAMPLE_OPTIONS,
  SAMPLE_PICKER,
  pickerRow,
  wireBlocked,
  wireLabel,
  wireLookup,
  wirePicker,
  wireReference,
  wireRowsPage,
} from './testing/referenceSamples'

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
  it('rejects the lookup form part without a reference and unknown value types or formats', () => {
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
    ['formPart is lookup without a reference', { formPart: 'lookup' }],
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

describe('parseReference (Intent 261029-reference-lookup-cleanup U3, D3, X-1)', () => {
  it('copies a REFERENCE field whose picker is missing from the body', () => {
    expect(parseReference(wireReference())).toEqual({
      kind: 'REFERENCE',
      status: 'AVAILABLE',
      options: SAMPLE_OPTIONS,
      picker: null,
    })
  })

  it('copies a LOOKUP field whose options and list operators are missing from the body', () => {
    expect(parseReference(wireLookup())).toEqual({
      kind: 'LOOKUP',
      status: 'AVAILABLE',
      options: [],
      picker: SAMPLE_PICKER,
    })
  })

  it.each([
    ['REFERENCE', 'NO_PERMISSION'],
    ['REFERENCE', 'TOO_MANY'],
    ['LOOKUP', 'NO_PERMISSION'],
    ['LOOKUP', 'TOO_MANY'],
  ] as const)('reads a blocked %s field (%s) without options or picker', (kind, status) => {
    expect(parseReference(wireBlocked(kind, status))).toEqual({
      kind,
      status,
      options: [],
      picker: null,
    })
  })

  it('turns an unknown status into NO_PERMISSION', () => {
    expect(parseReference(wireReference({ status: 'LATER' }))?.status).toBe('NO_PERMISSION')
    expect(parseReference(wireLookup({ status: 'available' }))?.status).toBe('NO_PERMISSION')
  })

  it.each(['FIXED', 'OTHER', 1])(
    'turns an unknown kind (%s) into an input that cannot be changed',
    (kind) => {
      expect(parseReference(wireReference({ kind }))).toEqual({
        kind: 'REFERENCE',
        status: 'NO_PERMISSION',
        options: [],
        picker: null,
      })
    },
  )

  it.each([
    ['REFERENCE AVAILABLE without options', wireReference({ options: undefined })],
    ['LOOKUP AVAILABLE without picker', wireLookup({ picker: undefined })],
    [
      'an option value that is not a string',
      wireReference({ options: [{ value: 1, label: 'a' }] }),
    ],
    ['an option label that is a number', wireReference({ options: [{ value: 'a', label: 1 }] })],
    [
      'a search field without valueType',
      wireLookup({
        picker: {
          ...wirePicker(),
          searchFields: [{ column: 'c', displayName: { ja: 'c', en: 'c' }, operator: 'EQUALS' }],
        },
      }),
    ],
    [
      'a search field with an unknown valueType',
      wireLookup({
        picker: {
          ...wirePicker(),
          searchFields: [
            {
              column: 'c',
              displayName: { ja: 'c', en: 'c' },
              operator: 'EQUALS',
              valueType: 'JSON',
            },
          ],
        },
      }),
    ],
    [
      'a search field with the RANGE operator',
      wireLookup({
        picker: {
          ...wirePicker(),
          searchFields: [
            {
              column: 'c',
              displayName: { ja: 'c', en: 'c' },
              operator: 'RANGE',
              valueType: 'STRING',
            },
          ],
        },
      }),
    ],
    [
      'a list column with an unknown valueType',
      wireLookup({
        picker: {
          ...wirePicker(),
          listColumns: [{ column: 'c', displayName: { ja: 'c', en: 'c' }, valueType: 'X' }],
        },
      }),
    ],
    ['a picker that is not an object', wireLookup({ picker: 'x' })],
    ['a non object', 'REFERENCE'],
  ])('treats %s as a broken shape', (_name, body) => {
    expect(parseReference(body)).toBeUndefined()
  })

  it('ignores an operator on a list column', () => {
    const picker = {
      ...wirePicker(),
      listColumns: [
        { column: 'c', displayName: { ja: 'c', en: 'c' }, operator: 'EQUALS', valueType: 'STRING' },
      ],
    }
    expect(parseReference(wireLookup({ picker }))?.picker?.listColumns).toEqual([
      { column: 'c', displayName: { ja: 'c', en: 'c' }, valueType: 'STRING' },
    ])
  })

  it('reads reference through parseColumnDefinition, including null and the lookup combinations', () => {
    expect(parseColumnDefinition(wireColumn('c', { reference: null }))?.reference).toBeNull()
    expect(
      parseColumnDefinition(wireColumn('c', { reference: wireReference(), formPart: 'select' }))
        ?.reference?.kind,
    ).toBe('REFERENCE')
    expect(
      parseColumnDefinition(wireColumn('c', { formPart: 'lookup', reference: wireLookup() }))
        ?.formPart,
    ).toBe('lookup')
    expect(
      parseColumnDefinition(
        wireColumn('c', { formPart: 'lookup', reference: wireBlocked('LOOKUP', 'TOO_MANY') }),
      )?.reference?.status,
    ).toBe('TOO_MANY')
    expect(
      parseColumnDefinition(wireColumn('c', { formPart: 'lookup', reference: null })),
    ).toBeUndefined()
    expect(
      parseColumnDefinition(wireColumn('c', { formPart: 'lookup', reference: wireReference() })),
    ).toBeUndefined()
    expect(
      parseColumnDefinition(wireColumn('c', { reference: wireReference({ options: 'x' }) })),
    ).toBeUndefined()
  })
})

describe('parseReferenceRowPage', () => {
  it('copies rows with null labels and null cells', () => {
    const rows = [pickerRow(0), pickerRow(1, { label: null, cells: ['P002', null, null] })]
    expect(parseReferenceRowPage(wireRowsPage(rows, 3, false))).toEqual({
      rows,
      page: 3,
      hasNext: false,
    })
  })

  it('accepts twenty rows with hasNext', () => {
    const rows = Array.from({ length: 20 }, (_, index) => pickerRow(index))
    expect(parseReferenceRowPage(wireRowsPage(rows, 1, true))?.hasNext).toBe(true)
  })

  it.each([
    ['page null', { ...wireRowsPage([pickerRow(0)]), page: null }],
    ['page 0', wireRowsPage([pickerRow(0)], 0)],
    ['page 1.5', wireRowsPage([pickerRow(0)], 1.5)],
    ['page above the maximum', wireRowsPage([pickerRow(0)], 1_000_001)],
    ['hasNext missing', { rows: [], page: 1 }],
    ['hasNext with fewer than twenty rows', wireRowsPage([pickerRow(0)], 1, true)],
    [
      'more than twenty rows',
      wireRowsPage(
        Array.from({ length: 21 }, (_, i) => pickerRow(i)),
        1,
      ),
    ],
    [
      'a cell that is a number',
      { rows: [{ value: 'a', label: null, cells: [1] }], page: 1, hasNext: false },
    ],
    ['cells missing', { rows: [{ value: 'a', label: null }], page: 1, hasNext: false }],
    [
      'a value that is not a string',
      { rows: [{ value: 1, label: null, cells: [] }], page: 1, hasNext: false },
    ],
  ])('treats %s as a broken shape', (_name, body) => {
    expect(parseReferenceRowPage(body)).toBeUndefined()
  })
})

describe('parseReferenceLabel (R-01)', () => {
  it('reads the label from the U1 success body with page null, no next and empty cells', () => {
    expect(parseReferenceLabel(wireLabel('P001', '商品 1'))).toEqual({
      value: 'P001',
      label: '商品 1',
    })
  })

  it('reads a null label and does not look at page, hasNext or cells', () => {
    expect(parseReferenceLabel(wireLabel('P001', null))).toEqual({ value: 'P001', label: null })
    expect(
      parseReferenceLabel({
        rows: [{ value: 'P001', label: 'x', cells: 'broken' }],
        page: 7,
        hasNext: true,
      }),
    ).toEqual({ value: 'P001', label: 'x' })
  })

  it.each([
    ['no rows', { rows: [], page: null, hasNext: false }],
    [
      'two rows',
      {
        rows: [
          { value: 'a', label: null },
          { value: 'b', label: null },
        ],
        page: null,
        hasNext: false,
      },
    ],
    [
      'a value that is not a string',
      { rows: [{ value: 1, label: null }], page: null, hasNext: false },
    ],
    ['a label that is a number', { rows: [{ value: 'a', label: 2 }], page: null, hasNext: false }],
    ['rows missing', { page: null, hasNext: false }],
    ['a non object', null],
  ])('fails on %s', (_name, body) => {
    expect(parseReferenceLabel(body)).toBeUndefined()
  })
})
