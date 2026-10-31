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
// 本文の写しと画面の検証の性質ベースのテスト（Intent 261016-business-data-views の U3 の L5、計画 Step 29、D29）。
// fast-check は、失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に一時的に書いて行い、
// seed を記録に残す（team.md の Testing Posture）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { countCodePoints } from '../../shared/validation/codePoints'
import { parseColumnDefinition } from './businessDataParse'
import type { ColumnDefinition } from './businessDataTypes'
import { diffForm, initialInputs } from './formDiff'
import { compareDecimalText, effectiveMaxLength, validateValue } from './formValidation'
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

const names = fc.uniqueArray(fc.stringMatching(/^[a-z][a-z0-9_]{0,8}$/), {
  minLength: 1,
  maxLength: 6,
})

const decimalText = fc
  .tuple(
    fc.boolean(),
    fc.stringMatching(/^[0-9]{1,40}$/),
    fc.option(fc.stringMatching(/^[0-9]{1,12}$/), { nil: undefined }),
  )
  .map(
    ([negative, integer, fraction]) =>
      `${negative ? '-' : ''}${integer}${fraction === undefined ? '' : `.${fraction}`}`,
  )

describe('formDiff properties', () => {
  it('never puts an unchanged column into the update body', () => {
    fc.assert(
      fc.property(
        names,
        fc.dictionary(fc.string(), fc.oneof(fc.string(), fc.constant(null))),
        fc.array(fc.string()),
        (columnNames, values, edits) => {
          const columns = columnNames.map((name) => column(name))
          const initial = initialInputs(columns, 'update', values)
          const current: Record<string, string> = { ...initial }
          columnNames.forEach((name, index) => {
            if (index < edits.length) {
              current[name] = edits[index]
            }
          })
          const body = diffForm(columns, 'update', initial, current)
          for (const name of Object.keys(body)) {
            expect(current[name]).not.toBe(initial[name])
          }
          for (const name of columnNames) {
            if (current[name] !== initial[name]) {
              expect(Object.hasOwn(body, name)).toBe(true)
            }
          }
        },
      ),
    )
  })

  it('keeps every digit of BIGINT and DECIMAL values', () => {
    fc.assert(
      fc.property(decimalText, (text) => {
        const columns = [column('n', { valueType: 'DECIMAL' })]
        const initial = initialInputs(columns, 'update', { n: '0' })
        expect(diffForm(columns, 'update', initial, { n: text })).toEqual(
          text === '0' ? {} : { n: text },
        )
      }),
    )
  })
})

describe('formValidation properties', () => {
  it('accepts exactly the maximum length and refuses one code point more, surrogate pairs included', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 200 }),
        fc.constantFrom('a', '𠮷', 'あ', '\ud800'),
        (maxLength, unit) => {
          const target = column('s', { maxLength })
          expect(effectiveMaxLength(target)).toBe(maxLength)
          const exact = unit.repeat(maxLength)
          expect(countCodePoints(exact)).toBe(maxLength)
          expect(validateValue(target, exact)).toBeUndefined()
          expect(validateValue(target, exact + unit)).toBe('TOO_LONG')
        },
      ),
    )
  })

  it('compares decimal texts like their numeric values without losing digits', () => {
    fc.assert(
      fc.property(
        fc.bigInt({ min: -(10n ** 30n), max: 10n ** 30n }),
        fc.bigInt({ min: -(10n ** 30n), max: 10n ** 30n }),
        (a, b) => {
          const result = compareDecimalText(a.toString(), b.toString())
          expect(Math.sign(result ?? Number.NaN)).toBe(a < b ? -1 : a > b ? 1 : 0)
        },
      ),
    )
  })
})

// Intent 261029-reference-lookup-cleanup の U3（D15・X-5）
describe('formDiff locked columns property', () => {
  it('never puts a locked column into the body', () => {
    fc.assert(
      fc.property(
        names,
        fc.constantFrom<'create' | 'update'>('create', 'update'),
        fc.array(fc.string(), { minLength: 6, maxLength: 6 }),
        fc.array(fc.boolean(), { minLength: 6, maxLength: 6 }),
        (columnNames, mode, values, lockFlags) => {
          const columns = columnNames.map((name) => column(name))
          const locked = new Set(columnNames.filter((_, index) => lockFlags[index]))
          const current = Object.fromEntries(
            columnNames.map((name, index) => [name, values[index]]),
          )
          const body = diffForm(columns, mode, {}, current, [], locked)
          for (const name of locked) {
            expect(Object.hasOwn(body, name)).toBe(false)
          }
        },
      ),
    )
  })
})
