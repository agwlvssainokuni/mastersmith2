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
// 日時の欄（Intent 261102-timezone-ui-polish の U4、計画 Step 9、D13〜D17・D21・D22・D35）。タイムゾーンは引数で明示に渡す。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as timeZoneModule from '../../shared/format/timeZone'
import { parseColumnDefinition } from './businessDataParse'
import type { ColumnDefinition } from './businessDataTypes'
import {
  dateTimeInputKind,
  diffForm,
  hasFormChanges,
  initialInputs,
  resolveFormDateTimes,
  toInputValue,
  type FormTime,
} from './formDiff'
import { wireColumn } from './testing/businessDataSamples'
import { wireBlocked, wireLookup, wireReference } from './testing/referenceSamples'

// 変換（parseLocalDateTime）の呼び出しの回数を数える。中身は本物のまま（NFR2.1.4、performance-design.md 2.2）
vi.mock('../../shared/format/timeZone', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../shared/format/timeZone')>()
  return { ...actual, parseLocalDateTime: vi.fn(actual.parseLocalDateTime) }
})

beforeEach(() => {
  vi.mocked(timeZoneModule.parseLocalDateTime).mockClear()
})

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

const TOKYO = 'Asia/Tokyo'
const NEW_YORK = 'America/New_York'

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

  it('sends only non empty values on create and converts date times in the form time zone', () => {
    const initial = initialInputs(columns, 'create', {}, [], TOKYO)
    const values = diffForm(
      columns,
      'create',
      initial,
      {
        ...initial,
        code: 'A/1',
        price: '12345678901234567890.123456789',
        at: '2026-10-18 09:30:00.123',
        local: '2026-10-18 09:30:15.123456',
      },
      [],
      undefined,
      { timeZone: TOKYO, overlapChoices: {} },
    )
    expect(values).toEqual({
      code: 'A/1',
      price: '12345678901234567890.123456789',
      flag: 'false',
      at: '2026-10-18T00:30:00.123+00:00',
      local: '2026-10-18T09:30:15.123456',
    })
  })

  it('fills an update form from the detail values and leaves out key and read-only columns', () => {
    const inputs = initialInputs(
      columns,
      'update',
      {
        code: 'A',
        name: null,
        price: '1.50',
        active: null,
        flag: 'true',
        at: '2026-10-18T00:30:00.123456+00:00',
        local: '2026-10-18T09:30:00.123',
      },
      [],
      TOKYO,
    )
    expect(inputs).toEqual({
      name: '',
      price: '1.50',
      active: '',
      flag: 'true',
      secret: '',
      // 表示のタイムゾーンの壁時計。端数はサーバーが返した桁のまま（D16）
      at: '2026-10-18 09:30:00.123456',
      local: '2026-10-18 09:30:00.123',
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

describe('formDiff date time fields (Intent 261102-timezone-ui-polish U4)', () => {
  const at = column('at', { valueType: 'DATETIME_TZ', formPart: 'datetime' })
  const local = column('local', { valueType: 'DATETIME', formPart: 'datetime' })
  const name = column('name')
  const fields = [at, local, name]
  const newYork: FormTime = { timeZone: NEW_YORK, overlapChoices: {} }

  it('keeps an unreadable instant as the original text and leaves it unconverted without a zone', () => {
    expect(toInputValue(at, '0000-00-00 00:00:00', TOKYO)).toBe('0000-00-00 00:00:00')
    expect(toInputValue(at, '2026-10-18T00:30:00+00:00')).toBe('2026-10-18T00:30:00+00:00')
  })

  it('does not send an untouched instant after another field is changed (D35)', () => {
    const initial = initialInputs(
      fields,
      'update',
      { at: '2026-11-01T05:30:00+00:00', local: '2026-11-01T01:30:00.5', name: 'a' },
      [],
      NEW_YORK,
    )
    // 2回ある時刻の値でも、変えていなければ送らず、2択も判定しない（D22）
    expect(initial.at).toBe('2026-11-01 01:30:00')
    const current = { ...initial, name: 'b' }
    expect(diffForm(fields, 'update', initial, current, [], undefined, newYork)).toEqual({
      name: 'b',
    })
    expect(
      resolveFormDateTimes(fields, 'update', initial, current, [], undefined, newYork),
    ).toEqual({})
  })

  it('does not send an instant changed and changed back to the first text (D35)', () => {
    const initial = initialInputs(fields, 'update', { at: '2026-10-18T00:30:00+00:00' }, [], TOKYO)
    const back = { ...initial, at: initial.at }
    expect(diffForm(fields, 'update', initial, back, [], undefined, newYork)).toEqual({})
  })

  it('sends the chosen time of an overlapping wall clock time and holds it back until chosen (D21)', () => {
    const initial = initialInputs(fields, 'create', {}, [], NEW_YORK)
    const current = { ...initial, at: '2026-11-01 01:30:00' }
    expect(diffForm(fields, 'create', initial, current, [], undefined, newYork)).toEqual({})
    expect(
      resolveFormDateTimes(fields, 'create', initial, current, [], undefined, newYork),
    ).toEqual({ at: { kind: 'overlap-pending', overlap: { earlier: '-04:00', later: '-05:00' } } })
    const chosen: FormTime = { timeZone: NEW_YORK, overlapChoices: { at: 'later' } }
    expect(diffForm(fields, 'create', initial, current, [], undefined, chosen)).toEqual({
      at: '2026-11-01T06:30:00+00:00',
    })
  })

  it('does not send an instant that cannot be resolved and reports why', () => {
    const initial = initialInputs(fields, 'update', { at: '2026-10-18T00:30:00+00:00' }, [], TOKYO)
    const cases: [string, FormTime, string][] = [
      ['2026-03-08 02:30:00', newYork, 'gap'],
      ['2026-10-18T09:30', newYork, 'invalid'],
      ['2026-10-18 10:30:00', { timeZone: null, overlapChoices: {} }, 'unavailable'],
    ]
    for (const [text, time, kind] of cases) {
      const current = { ...initial, at: text }
      expect(diffForm(fields, 'update', initial, current, [], undefined, time)).toEqual({})
      expect(
        resolveFormDateTimes(fields, 'update', initial, current, [], undefined, time).at?.kind,
      ).toBe(kind)
    }
  })

  it('clears a changed date time with null and does not resolve an empty field', () => {
    const initial = initialInputs(fields, 'update', { at: '2026-10-18T00:30:00+00:00' }, [], TOKYO)
    const current = { ...initial, at: '' }
    expect(diffForm(fields, 'update', initial, current, [], undefined, newYork)).toEqual({
      at: null,
    })
    expect(
      resolveFormDateTimes(fields, 'update', initial, current, [], undefined, newYork),
    ).toEqual({})
  })

  it('reuses the resolutions on save so each changed date time field is converted once (NFR2.1.4)', () => {
    const initial = initialInputs(
      fields,
      'update',
      { at: '2026-10-18T00:30:00+00:00', local: '2026-10-18T09:30:00', name: 'a' },
      [],
      NEW_YORK,
    )
    const chosen: FormTime = { timeZone: NEW_YORK, overlapChoices: { at: 'later' } }
    const current = { ...initial, at: '2026-11-01 01:30:00.5', local: '2026-11-02 09:00:00' }
    const parse = vi.mocked(timeZoneModule.parseLocalDateTime)
    parse.mockClear()
    const resolutions = resolveFormDateTimes(
      fields,
      'update',
      initial,
      current,
      [],
      undefined,
      chosen,
    )
    expect(parse).toHaveBeenCalledTimes(2)
    const body = diffForm(fields, 'update', initial, current, [], undefined, chosen, resolutions)
    // 判定の結果を渡せば変換し直さない（変えた日時の欄の数 2 と同じ回数のまま）
    expect(parse).toHaveBeenCalledTimes(2)
    expect(body).toEqual({ at: '2026-11-01T06:30:00.5+00:00', local: '2026-11-02T09:00:00' })
    // 渡さなければ今と同じく変換し、同じ本文になる
    expect(diffForm(fields, 'update', initial, current, [], undefined, chosen)).toEqual(body)
    expect(parse).toHaveBeenCalledTimes(4)
  })

  it('uses only the ok wire of a given resolution and leaves out the others', () => {
    const initial = initialInputs(fields, 'create', {}, [], NEW_YORK)
    const current = { ...initial, at: '2026-11-01 01:30:00', local: '2026-11-02 09:00:00' }
    const resolutions = resolveFormDateTimes(
      fields,
      'create',
      initial,
      current,
      [],
      undefined,
      newYork,
    )
    expect(resolutions.at?.kind).toBe('overlap-pending')
    vi.mocked(timeZoneModule.parseLocalDateTime).mockClear()
    expect(
      diffForm(fields, 'create', initial, current, [], undefined, newYork, resolutions),
    ).toEqual({ local: '2026-11-02T09:00:00' })
    expect(timeZoneModule.parseLocalDateTime).not.toHaveBeenCalled()
  })

  it('sends a date time without a zone with T whatever the form time zone (D14)', () => {
    const initial = initialInputs(fields, 'create', {}, [], TOKYO)
    const current = { ...initial, local: '2026-03-08 02:30:00.123' }
    for (const time of [newYork, { timeZone: null, overlapChoices: {} }]) {
      expect(diffForm(fields, 'create', initial, current, [], undefined, time)).toEqual({
        local: '2026-03-08T02:30:00.123',
      })
    }
  })
})

// Intent 261029-reference-lookup-cleanup の U3（D15・X-5）
// 計画 Step 13 で見つけた直し: 選択肢・参照の部品の日時のカラムは変換しない（D13・D33）
describe('dateTimeInputKind (Intent 261102-timezone-ui-polish U4)', () => {
  const STORED = '2026-11-01T15:00:00+00:00'
  const choiceColumn = column('slot', {
    valueType: 'DATETIME_TZ',
    formPart: 'select',
    choices: [{ value: STORED, label: { ja: '午前', en: 'AM' } }],
  })
  const lookupColumn = column('ref', {
    valueType: 'DATETIME_TZ',
    formPart: 'lookup',
    reference: wireLookup(),
  })
  const referenceColumn = column('pick', {
    valueType: 'DATETIME',
    formPart: 'select',
    reference: wireReference(),
  })

  it('treats only text parts of instant and local date time columns as date time inputs', () => {
    expect(dateTimeInputKind(column('a', { valueType: 'DATETIME_TZ', formPart: 'text' }))).toBe(
      'instant',
    )
    expect(dateTimeInputKind(column('b', { valueType: 'DATETIME_TZ', formPart: 'textarea' }))).toBe(
      'instant',
    )
    expect(dateTimeInputKind(column('c', { valueType: 'DATETIME', formPart: null }))).toBe('local')
    expect(dateTimeInputKind(column('d', { valueType: 'DATETIME', formPart: 'datetime' }))).toBe(
      'local',
    )
    expect(dateTimeInputKind(column('e', { valueType: 'DATE', formPart: 'date' }))).toBeNull()
    expect(dateTimeInputKind(choiceColumn)).toBeNull()
    expect(dateTimeInputKind(lookupColumn)).toBeNull()
    expect(dateTimeInputKind(referenceColumn)).toBeNull()
  })

  it('keeps the values of choice and reference date time columns as returned', () => {
    const time: FormTime = { timeZone: TOKYO, overlapChoices: {} }
    const all = [choiceColumn, lookupColumn, referenceColumn]
    const values = { slot: STORED, ref: STORED, pick: '2026-11-01T09:00:00' }
    const initial = initialInputs(all, 'update', values, [], TOKYO)
    expect(initial).toEqual(values)
    const current = { slot: STORED, ref: '2026-11-02T00:00:00+00:00', pick: '2026-11-01T09:00:00' }
    expect(diffForm(all, 'update', initial, current, [], undefined, time)).toEqual({
      ref: '2026-11-02T00:00:00+00:00',
    })
    expect(resolveFormDateTimes(all, 'update', initial, current, [], undefined, time)).toEqual({})
  })
})

describe('formDiff with locked reference columns', () => {
  const referenceColumns = [
    column('customer', { formPart: 'select', reference: wireReference() }),
    column('product', { formPart: 'select', reference: wireBlocked('REFERENCE', 'TOO_MANY') }),
  ]
  const locked = new Set(['product'])

  it('does not send a locked column and gives the same result without the argument', () => {
    const initial = initialInputs(referenceColumns, 'update', { customer: 'C001', product: 'P001' })
    const current = { ...initial, customer: 'C002', product: 'P999' }
    expect(diffForm(referenceColumns, 'update', initial, current, [], locked)).toEqual({
      customer: 'C002',
    })
    expect(diffForm(referenceColumns, 'update', initial, current)).toEqual({
      customer: 'C002',
      product: 'P999',
    })
    const created = { customer: 'C001', product: 'P001' }
    expect(diffForm(referenceColumns, 'create', {}, created, [], locked)).toEqual({
      customer: 'C001',
    })
  })

  it('does not count a change of a locked column', () => {
    const initial = { customer: 'C001', product: 'P001' }
    const current = { customer: 'C001', product: 'P999' }
    expect(hasFormChanges(referenceColumns, 'update', initial, current, [], locked)).toBe(false)
    expect(hasFormChanges(referenceColumns, 'update', initial, current)).toBe(true)
  })
})
