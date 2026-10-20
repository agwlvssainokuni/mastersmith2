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
// 検索の入力から送る条件を作る・画面で確かめる純粋な関数（Intent 261016-business-data-views の U3 の L6、F2 の 1、
// Q3: A、AC1.2.1〜AC1.2.6・AC1.2.13）。判定はサーバーを正とし、画面は型と範囲の向きと長さだけを先に確かめる。
// - 送るのは、今の定義の検索の項目にある項目だけ（定義が変わって消えた項目の入力は送らない。F6）。
// - CONTAINS の `%`・`_` は文字のまま送る（エスケープはサーバーが行う。AC1.2.4）。
// - 値の長さはコードポイントで数え、上限（1,000）を超えたら TOO_LONG（U1 と同じ数え方）。
// - 既定の値（search.default）は、EQUALS・CONTAINS は value、RANGE は from、CHOICE は選択肢の1つとして入れる。
import { countCodePoints } from '../../shared/validation/codePoints'
import { MAX_SEARCH_VALUE_LENGTH } from './businessDataLimits'
import type {
  SearchCondition,
  SearchField,
  SearchInput,
  SearchInputs,
  ValueType,
} from './businessDataTypes'

/** 画面で見つける検索の入力の誤り（サーバーの reason と同じ名前） */
export type SearchInputError = 'INVALID_VALUE' | 'RANGE_REVERSED' | 'TOO_LONG'

/** 空の入力 */
export const EMPTY_SEARCH_INPUT: SearchInput = { value: '', from: '', to: '', values: [] }

/**
 * 既定の値を入れた入力を作る。
 *
 * @param fields 検索の項目
 */
export function defaultSearchInputs(fields: readonly SearchField[]): SearchInputs {
  return Object.fromEntries(
    fields.map((field): [string, SearchInput] => {
      const value = field.defaultValue ?? ''
      switch (field.operator) {
        case 'RANGE':
          return [field.column, { ...EMPTY_SEARCH_INPUT, from: value }]
        case 'CHOICE':
          return [field.column, { ...EMPTY_SEARCH_INPUT, values: value === '' ? [] : [value] }]
        default:
          return [field.column, { ...EMPTY_SEARCH_INPUT, value }]
      }
    }),
  )
}

/** 項目の入力を読む（無ければ空の入力） */
export function inputOf(inputs: SearchInputs, column: string): SearchInput {
  return Object.hasOwn(inputs, column) ? inputs[column] : EMPTY_SEARCH_INPUT
}

/**
 * 送る条件を作る。空の入力の項目は送らない。
 *
 * @param fields 今の定義の検索の項目
 * @param inputs 入力
 */
export function buildConditions(
  fields: readonly SearchField[],
  inputs: SearchInputs,
): SearchCondition[] {
  const conditions: SearchCondition[] = []
  for (const field of fields) {
    const input = inputOf(inputs, field.column)
    switch (field.operator) {
      case 'RANGE': {
        if (input.from === '' && input.to === '') {
          break
        }
        conditions.push({
          column: field.column,
          ...(input.from === '' ? {} : { from: input.from }),
          ...(input.to === '' ? {} : { to: input.to }),
        })
        break
      }
      case 'CHOICE':
        if (input.values.length > 0) {
          conditions.push({ column: field.column, values: [...input.values] })
        }
        break
      default:
        if (input.value !== '') {
          conditions.push({ column: field.column, value: input.value })
        }
    }
  }
  return conditions
}

/** 条件のある項目の数（閉じた検索の見出しに出す） */
export function countActiveConditions(
  fields: readonly SearchField[],
  inputs: SearchInputs,
): number {
  return buildConditions(fields, inputs).length
}

const INTEGER_TEXT = /^[+-]?\d+$/
const DECIMAL_TEXT = /^[+-]?\d+(\.\d+)?$/
const DATE_TEXT = /^\d{4}-\d{2}-\d{2}$/
const TIME_TEXT = /^\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/
const OFFSET = '([Zz]|[+-]\\d{2}:\\d{2})?'
const TIME_TZ_TEXT = new RegExp(`^\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?${OFFSET}$`)
const DATETIME_TEXT = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/
const DATETIME_TZ_TEXT = new RegExp(
  `^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?${OFFSET}$`,
)

/** 値が型として読めるか（読めない値はサーバーも INVALID_VALUE にする） */
export function isReadableAs(type: ValueType, value: string): boolean {
  switch (type) {
    case 'INTEGER':
    case 'BIG_INTEGER':
      return INTEGER_TEXT.test(value)
    case 'DECIMAL':
      return DECIMAL_TEXT.test(value)
    case 'BOOLEAN':
      return value === 'true' || value === 'false'
    case 'DATE':
      return DATE_TEXT.test(value)
    case 'TIME':
      return TIME_TEXT.test(value)
    case 'TIME_TZ':
      return TIME_TZ_TEXT.test(value)
    case 'DATETIME':
      return DATETIME_TEXT.test(value)
    case 'DATETIME_TZ':
      return DATETIME_TZ_TEXT.test(value)
    default:
      return true
  }
}

/** 小数の文字どうしを比べる（数に直さず桁を保つ）。負なら a < b */
export function compareDecimalText(a: string, b: string): number {
  const parse = (text: string) => {
    const negative = text.startsWith('-')
    const unsigned = text.replace(/^[+-]/, '')
    const [integer, fraction = ''] = unsigned.split('.')
    return { negative, integer: integer.replace(/^0+(?=\d)/, ''), fraction }
  }
  const left = parse(a)
  const right = parse(b)
  const isZero = (n: { integer: string; fraction: string }) =>
    /^0*$/.test(n.integer) && /^0*$/.test(n.fraction)
  const leftNegative = left.negative && !isZero(left)
  const rightNegative = right.negative && !isZero(right)
  if (leftNegative !== rightNegative) {
    return leftNegative ? -1 : 1
  }
  const width = Math.max(left.fraction.length, right.fraction.length)
  const magnitude = (n: { integer: string; fraction: string }) =>
    `${n.integer}${n.fraction.padEnd(width, '0')}`
  const leftDigits = magnitude(left)
  const rightDigits = magnitude(right)
  const compared =
    leftDigits.length !== rightDigits.length
      ? leftDigits.length - rightDigits.length
      : leftDigits < rightDigits
        ? -1
        : leftDigits > rightDigits
          ? 1
          : 0
  return leftNegative ? -compared : compared
}

/** 範囲の向きが逆か（両方が読めるときだけ判定する） */
function isReversed(type: ValueType, from: string, to: string): boolean {
  switch (type) {
    case 'INTEGER':
    case 'BIG_INTEGER':
    case 'DECIMAL':
      return compareDecimalText(from, to) > 0
    case 'DATE':
    case 'TIME':
    case 'DATETIME':
      return from.replace(' ', 'T') > to.replace(' ', 'T')
    default:
      // 時差つきの型は時差をそろえないと比べられないため、サーバーの判定に任せる。
      return false
  }
}

function tooLong(value: string): boolean {
  return countCodePoints(value) > MAX_SEARCH_VALUE_LENGTH
}

/**
 * 検索の入力を画面で確かめる。
 *
 * @param fields 今の定義の検索の項目
 * @param inputs 入力
 * @returns 誤りのある項目（カラムの名前と誤り）。無ければ空
 */
export function validateSearchInputs(
  fields: readonly SearchField[],
  inputs: SearchInputs,
): Readonly<Record<string, SearchInputError>> {
  const errors: [string, SearchInputError][] = []
  for (const field of fields) {
    const input = inputOf(inputs, field.column)
    const values =
      field.operator === 'RANGE'
        ? [input.from, input.to]
        : field.operator === 'CHOICE'
          ? []
          : [input.value]
    const filled = values.filter((value) => value !== '')
    if (filled.some(tooLong)) {
      errors.push([field.column, 'TOO_LONG'])
      continue
    }
    // CONTAINS は文字の型だけ。型の確かめは値を読む演算子（EQUALS・RANGE）に当てる。
    if (
      field.operator !== 'CONTAINS' &&
      filled.some((value) => !isReadableAs(field.valueType, value))
    ) {
      errors.push([field.column, 'INVALID_VALUE'])
      continue
    }
    if (
      field.operator === 'RANGE' &&
      input.from !== '' &&
      input.to !== '' &&
      isReversed(field.valueType, input.from, input.to)
    ) {
      errors.push([field.column, 'RANGE_REVERSED'])
    }
  }
  return Object.fromEntries(errors)
}
