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
// 参照の項目の部品の選び方と選択肢の文のテスト（Intent 261029-reference-lookup-cleanup の U3、D5・D6・D13、R-05）。
// fast-check は失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に書いて行い、seed を記録に残す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import type { ColumnDefinition, ReferenceField } from '../businessDataTypes'
import { lookupField, referenceField, XSS_SAMPLES } from '../testing/referenceSamples'
import {
  chooseFieldKind,
  isBlockedRequired,
  isReadonlyReference,
  lockedReferenceColumns,
  optionText,
  readonlyReason,
} from './referenceField'

function column(overrides: Partial<ColumnDefinition> = {}): ColumnDefinition {
  return {
    column: 'customer',
    displayName: { ja: '顧客', en: 'Customer' },
    valueType: 'STRING',
    listFormat: null,
    width: null,
    editableOnCreate: true,
    editableOnUpdate: true,
    masked: false,
    formPart: 'select',
    required: false,
    nullable: true,
    reference: referenceField(),
    ...overrides,
  }
}

describe('chooseFieldKind (D5)', () => {
  it.each([
    ['create, not editable on create', column({ editableOnCreate: false }), 'create', [], 'hidden'],
    ['update, not editable on update', column({ editableOnUpdate: false }), 'update', [], 'locked'],
    ['update, truncated value', column(), 'update', ['customer'], 'locked'],
    ['no reference', column({ reference: null, formPart: 'text' }), 'create', [], 'plain'],
    [
      'reference missing',
      column({ reference: undefined, formPart: 'text' }),
      'update',
      [],
      'plain',
    ],
    [
      'no permission',
      column({ reference: referenceField({ status: 'NO_PERMISSION', options: [] }) }),
      'create',
      [],
      'reference-readonly',
    ],
    [
      'too many',
      column({ reference: lookupField({ status: 'TOO_MANY', picker: null }) }),
      'update',
      [],
      'reference-readonly',
    ],
    ['REFERENCE available', column(), 'create', [], 'reference-select'],
    [
      'LOOKUP available',
      column({ formPart: 'lookup', reference: lookupField() }),
      'update',
      [],
      'lookup',
    ],
  ] as const)('chooses the right kind for %s', (_name, target, mode, truncated, expected) => {
    expect(chooseFieldKind(target, mode, truncated).kind).toBe(expected)
  })

  it('looks at whether the column can be entered before the reference status (R-05)', () => {
    const blocked = column({
      editableOnCreate: false,
      editableOnUpdate: false,
      reference: referenceField({ status: 'NO_PERMISSION', options: [] }),
    })
    expect(chooseFieldKind(blocked, 'create').kind).toBe('hidden')
    expect(chooseFieldKind(blocked, 'update').kind).toBe('locked')
    expect(isBlockedRequired({ ...blocked, nullable: false }, 'create')).toBe(false)
  })

  it('reads the reason of a readonly reference', () => {
    expect(readonlyReason('TOO_MANY')).toBe('too-many')
    expect(readonlyReason('NO_PERMISSION')).toBe('permission')
    expect(
      chooseFieldKind(
        column({ reference: referenceField({ status: 'TOO_MANY', options: [] }) }),
        'create',
      ),
    ).toEqual({ kind: 'reference-readonly', reason: 'too-many' })
  })

  it('marks a required readonly reference as blocked only on the create screen (D13, D14)', () => {
    const required = column({
      nullable: false,
      reference: referenceField({ status: 'NO_PERMISSION', options: [] }),
    })
    expect(isBlockedRequired(required, 'create')).toBe(true)
    expect(isBlockedRequired(required, 'update')).toBe(false)
    expect(isBlockedRequired({ ...required, nullable: true }, 'create')).toBe(false)
    expect(isBlockedRequired({ ...required, nullable: true, required: true }, 'create')).toBe(true)
    expect(isBlockedRequired(column({ nullable: false }), 'create')).toBe(false)
  })

  it('collects only readonly references as locked columns', () => {
    const columns = [
      column({ column: 'a' }),
      column({ column: 'b', reference: referenceField({ status: 'TOO_MANY', options: [] }) }),
      column({ column: 'c', reference: null, formPart: 'text' }),
      column({
        column: 'd',
        editableOnUpdate: false,
        reference: referenceField({ status: 'NO_PERMISSION', options: [] }),
      }),
    ]
    expect([...lockedReferenceColumns(columns, 'update')]).toEqual(['b'])
    expect(isReadonlyReference(columns[1], 'update')).toBe(true)
  })
})

describe('optionText (D6)', () => {
  it('shows the label and the value, or the value only for a null label', () => {
    expect(optionText({ value: 'C001', label: '東京 本店' })).toBe('東京 本店 (C001)')
    expect(optionText({ value: 'C002', label: null })).toBe('C002')
    expect(optionText({ value: '', label: '空の値' })).toBe('空の値 ()')
  })

  it.each(XSS_SAMPLES)('keeps %s as plain text', (label) => {
    expect(optionText({ value: 'X', label })).toBe(`${label} (X)`)
  })
})

const statusArb = fc.constantFrom<ReferenceField['status']>(
  'AVAILABLE',
  'NO_PERMISSION',
  'TOO_MANY',
)
const kindArb = fc.constantFrom<ReferenceField['kind']>('REFERENCE', 'LOOKUP')
const modeArb = fc.constantFrom<'create' | 'update'>('create', 'update')

describe('referenceField properties', () => {
  it('never returns an input part unless the reference is available', () => {
    fc.assert(
      fc.property(
        kindArb,
        statusArb,
        modeArb,
        fc.boolean(),
        fc.boolean(),
        (kind, status, mode, onCreate, onUpdate) => {
          const target = column({
            editableOnCreate: onCreate,
            editableOnUpdate: onUpdate,
            reference: kind === 'REFERENCE' ? referenceField({ status }) : lookupField({ status }),
          })
          const chosen = chooseFieldKind(target, mode).kind
          if (status !== 'AVAILABLE') {
            expect(['reference-select', 'lookup', 'plain']).not.toContain(chosen)
          }
        },
      ),
    )
  })

  it('shows the existing readonly display for a column that cannot be entered, whatever the status (R-05)', () => {
    fc.assert(
      fc.property(kindArb, statusArb, modeArb, fc.boolean(), (kind, status, mode, truncate) => {
        const target = column({
          editableOnCreate: false,
          // 更新では、入力できないカラムか、入力できても先頭だけを受けたカラム
          editableOnUpdate: truncate,
          reference: kind === 'REFERENCE' ? referenceField({ status }) : lookupField({ status }),
        })
        const chosen = chooseFieldKind(target, mode, truncate ? ['customer'] : []).kind
        expect(chosen).toBe(mode === 'create' ? 'hidden' : 'locked')
        expect(isBlockedRequired({ ...target, nullable: false }, mode)).toBe(false)
      }),
    )
  })

  it('keeps any label with <, " or \' as text', () => {
    fc.assert(
      fc.property(
        fc.string(),
        fc.constantFrom('<', '"', "'", '<img onerror=x>'),
        fc.string(),
        (head, mark, value) => {
          const label = `${head}${mark}`
          expect(optionText({ value, label })).toBe(`${label} (${value})`)
        },
      ),
    )
  })
})
