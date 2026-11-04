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
import { compareInstants } from '../../shared/format/timeZone'
import { diffForm, initialInputs, resolveFormDateTime, toInputValue } from './formDiff'
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

// ---- 日時の欄（Intent 261102-timezone-ui-polish の U4、計画 Step 9、D43） ----

const ZONES = ['Asia/Tokyo', 'America/New_York', 'Europe/London', 'Asia/Kathmandu', 'UTC']

/** 1990〜2099 年の UTC の時点（端数 0・3・6 桁）。秋の切り替わりの 01 時台（America/New_York）を含める */
const instantValue = fc
  .tuple(
    fc.oneof(
      fc.date({
        min: new Date('1990-01-01T00:00:00Z'),
        max: new Date('2099-12-31T00:00:00Z'),
        noInvalidDate: true,
      }),
      fc
        .integer({ min: 0, max: 7199 })
        .map((seconds) => new Date(Date.UTC(2026, 10, 1, 5, 0, seconds))),
    ),
    fc.constantFrom('', '.123', '.123456'),
  )
  .map(([date, fraction]) => `${date.toISOString().slice(0, 19)}${fraction}+00:00`)

describe('formDiff date time properties', () => {
  const at = column('at', { valueType: 'DATETIME_TZ', formPart: 'datetime' })
  const local = column('local', { valueType: 'DATETIME', formPart: 'datetime' })
  const name = column('name')

  it('never sends an untouched date time field whatever the time zone', () => {
    fc.assert(
      fc.property(
        instantValue,
        fc.constantFrom(...ZONES),
        fc.string(),
        fc.constantFrom<'earlier' | 'later' | null>('earlier', 'later', null),
        (value, timeZone, text, choice) => {
          const fields = [at, local, name]
          const values = { at: value, local: value.slice(0, -'+00:00'.length), name: 'a' }
          const initial = initialInputs(fields, 'update', values, [], timeZone)
          const body = diffForm(
            fields,
            'update',
            initial,
            { ...initial, name: text },
            [],
            undefined,
            {
              timeZone,
              overlapChoices: { at: choice, local: choice },
            },
          )
          expect(Object.hasOwn(body, 'at')).toBe(false)
          expect(Object.hasOwn(body, 'local')).toBe(false)
        },
      ),
    )
  })

  it('returns to the original instant when the first value of an instant is resolved, except for overlaps', () => {
    fc.assert(
      fc.property(instantValue, fc.constantFrom(...ZONES), (value, timeZone) => {
        const text = toInputValue(at, value, timeZone)
        const resolved = resolveFormDateTime(at, text, { timeZone, overlapChoices: {} })
        // 2回ある時刻は送らない（変えていない欄として保たれる）ため、性質の対象から外す
        fc.pre(resolved?.kind !== 'overlap-pending')
        expect(resolved?.kind).toBe('ok')
        if (resolved?.kind === 'ok') {
          expect(resolved.wire).toBe(value)
          expect(compareInstants(resolved.wire, value)).toEqual({ kind: 'ok', order: 0 })
        }
      }),
    )
  })

  it('keeps the first value and the sent value of a date time without a zone in every time zone', () => {
    fc.assert(
      fc.property(
        instantValue.map((value) => value.slice(0, -'+00:00'.length)),
        fc.constantFrom(...ZONES),
        (value, timeZone) => {
          const text = toInputValue(local, value, timeZone)
          expect(text).toBe(toInputValue(local, value, 'UTC'))
          const body = diffForm([local], 'create', {}, { local: text }, [], undefined, {
            timeZone,
            overlapChoices: {},
          })
          expect(body).toEqual({ local: value })
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
