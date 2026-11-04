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
// - 日時の条件（Intent 261102-timezone-ui-polish の U4、計画 Step 10、D23〜D29）: EQUALS・RANGE の時点を表す型
//   （DATETIME_TZ）は、表示のタイムゾーンの壁時計の入力を resolveDateTimeInput で時点（UTC の時差つき）に直して送る。
//   範囲の始まりは先・終わりは後（2回ある時刻。Q4: A）、一致は2択の選択（SearchInput.overlapChoice）が要る。範囲の向きは
//   U1 の compareInstants で比べる。時差を持たない日時（DATETIME）は変換せず、区切りを T にして送る（Q2: A）。
//   CONTAINS・CHOICE は日時の型でも変換しない（今のまま文字として扱う）。
// - 変換は resolveSearch の1回の呼び出しで、日時の条件1つあたり1回（範囲は始まりと終わりの2回）だけ行う（NFR2.1.5）。
//   作った条件はその結果を持ち回り、ページ送り・並べ替えで作り直さないのは呼ぶ側（一覧の状態）の受け持ち。
import {
  formatInstantForInput,
  formatLocalDateTimeForInput,
} from '../../shared/format/dateTimeInput'
import { compareInstants } from '../../shared/format/timeZone'
import { countCodePoints } from '../../shared/validation/codePoints'
import { MAX_SEARCH_VALUE_LENGTH } from './businessDataLimits'
import type {
  SearchCondition,
  SearchField,
  SearchInput,
  SearchInputs,
  ValueType,
} from './businessDataTypes'
import {
  dateTimeErrorReason,
  dateTimeKindOf,
  resolveDateTimeInput,
  type DateTimeErrorReason,
  type DateTimeKind,
  type DateTimePurpose,
} from './resolveDateTimeInput'

/** 画面で見つける検索の入力の誤り（サーバーの reason と同じ名前と、日時の条件の画面だけの理由） */
export type SearchInputError = 'INVALID_VALUE' | 'RANGE_REVERSED' | 'TOO_LONG' | DateTimeErrorReason

/** 検索の入力を条件にした結果（ok は送る条件、invalid は項目ごとの誤り） */
export type SearchResolution =
  | { readonly kind: 'ok'; readonly conditions: readonly SearchCondition[] }
  | { readonly kind: 'invalid'; readonly errors: Readonly<Record<string, SearchInputError>> }

/** 空の入力 */
export const EMPTY_SEARCH_INPUT: SearchInput = { value: '', from: '', to: '', values: [] }

/** 時差の無い時点の文字（既定の値。時差が無ければ UTC とみなす。D28） */
const INSTANT_WITHOUT_OFFSET = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?$/

/**
 * 既定の値を入力の文字にする（D28）。時点を表す型は表示のタイムゾーンの壁時計（時差が無ければ UTC とみなす。読めなければ
 * 元の文字）。表示のタイムゾーンがまだ無ければ空にする（ready になってから入れ直す）。時差を持たない日時は区切りを空白に。
 */
function defaultText(field: SearchField, value: string, timeZone: string | null): string {
  if (value === '' || (field.operator !== 'EQUALS' && field.operator !== 'RANGE')) {
    return value
  }
  switch (dateTimeKindOf(field.valueType)) {
    case 'instant': {
      if (timeZone === null) {
        return ''
      }
      const instant = INSTANT_WITHOUT_OFFSET.test(value) ? `${value}Z` : value
      const text = formatInstantForInput(instant, timeZone)
      return text === instant ? value : text
    }
    case 'local':
      return formatLocalDateTimeForInput(value)
    default:
      return value
  }
}

/**
 * 既定の値を入れた入力を作る。
 *
 * @param fields 検索の項目
 * @param timeZone 表示のタイムゾーン（時点を表す型の既定の値に使う。まだ無ければ null で、その項目は空にする）
 */
export function defaultSearchInputs(
  fields: readonly SearchField[],
  timeZone: string | null,
): SearchInputs {
  return Object.fromEntries(
    fields.map((field): [string, SearchInput] => {
      const value = defaultText(field, field.defaultValue ?? '', timeZone)
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

/**
 * 値が型として読めるか（読めない値はサーバーも INVALID_VALUE にする）。送る形（受け渡しの文字）を確かめる。
 * 画面の日時の入力（表示のタイムゾーンの壁時計）は resolveDateTimeInput で確かめる。
 */
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

/** 時差を持たない日時の送る形（区切りが T）を、端数を 9 桁にそろえた比べの鍵にする */
function localDateTimeKey(wire: string): string {
  const [base, fraction = ''] = wire.split('.')
  return `${base}.${fraction.padEnd(9, '0')}`
}

/** 範囲の向きが逆か（両方が送る形に直せたときだけ判定する。from・to は送る形） */
function isReversed(type: ValueType, from: string, to: string): boolean {
  switch (type) {
    case 'INTEGER':
    case 'BIG_INTEGER':
    case 'DECIMAL':
      return compareDecimalText(from, to) > 0
    case 'DATE':
    case 'TIME':
      return from.replace(' ', 'T') > to.replace(' ', 'T')
    case 'DATETIME':
      return localDateTimeKey(from) > localDateTimeKey(to)
    case 'DATETIME_TZ': {
      // 時点として比べる。同じ時点は逆にしない（D25）
      const compared = compareInstants(from, to)
      return compared.kind === 'ok' && compared.order > 0
    }
    default:
      // TIME_TZ は時差をそろえないと比べられないため、サーバーの判定に任せる。
      return false
  }
}

function tooLong(value: string): boolean {
  return countCodePoints(value) > MAX_SEARCH_VALUE_LENGTH
}

/** 日時の値1つを送る形に直す（ok は送る値、誤りは理由） */
function resolveDateTimeValue(
  kind: DateTimeKind,
  text: string,
  purpose: DateTimePurpose,
  timeZone: string | null,
  overlapChoice: SearchInput['overlapChoice'],
): { wire: string } | { error: SearchInputError } {
  const resolved = resolveDateTimeInput({
    text,
    kind,
    timeZone,
    purpose,
    overlapChoice: overlapChoice ?? null,
  })
  const reason = dateTimeErrorReason(resolved)
  if (reason !== undefined || resolved.kind !== 'ok') {
    return { error: reason ?? 'DATE_TIME_FORMAT' }
  }
  return { wire: resolved.wire }
}

/**
 * 1つの項目の条件を作る（送らないときは null、誤りは error）。
 */
function resolveField(
  field: SearchField,
  input: SearchInput,
  timeZone: string | null,
): { condition: SearchCondition | null } | { error: SearchInputError } {
  switch (field.operator) {
    case 'CHOICE':
      return {
        condition:
          input.values.length > 0 ? { column: field.column, values: [...input.values] } : null,
      }
    case 'CONTAINS':
      if (input.value === '') {
        return { condition: null }
      }
      // CONTAINS は文字の型だけ。型の確かめも変換もしない
      return tooLong(input.value)
        ? { error: 'TOO_LONG' }
        : { condition: { column: field.column, value: input.value } }
    case 'RANGE': {
      const filled = [input.from, input.to].filter((value) => value !== '')
      if (filled.length === 0) {
        return { condition: null }
      }
      if (filled.some(tooLong)) {
        return { error: 'TOO_LONG' }
      }
      const kind = dateTimeKindOf(field.valueType)
      let from = input.from
      let to = input.to
      if (kind !== null) {
        for (const side of ['from', 'to'] as const) {
          const text = side === 'from' ? input.from : input.to
          if (text === '') {
            continue
          }
          const purpose = side === 'from' ? 'range-from' : 'range-to'
          const resolved = resolveDateTimeValue(kind, text, purpose, timeZone, null)
          if ('error' in resolved) {
            return resolved
          }
          if (side === 'from') {
            from = resolved.wire
          } else {
            to = resolved.wire
          }
        }
      } else if (filled.some((value) => !isReadableAs(field.valueType, value))) {
        return { error: 'INVALID_VALUE' }
      }
      if (from !== '' && to !== '' && isReversed(field.valueType, from, to)) {
        return { error: 'RANGE_REVERSED' }
      }
      return {
        condition: {
          column: field.column,
          ...(from === '' ? {} : { from }),
          ...(to === '' ? {} : { to }),
        },
      }
    }
    default: {
      if (input.value === '') {
        return { condition: null }
      }
      if (tooLong(input.value)) {
        return { error: 'TOO_LONG' }
      }
      const kind = dateTimeKindOf(field.valueType)
      if (kind === null) {
        return isReadableAs(field.valueType, input.value)
          ? { condition: { column: field.column, value: input.value } }
          : { error: 'INVALID_VALUE' }
      }
      const resolved = resolveDateTimeValue(
        kind,
        input.value,
        'equals',
        timeZone,
        input.overlapChoice,
      )
      return 'error' in resolved
        ? resolved
        : { condition: { column: field.column, value: resolved.wire } }
    }
  }
}

/**
 * 検索の入力を確かめ、送る条件を作る（1回の呼び出しで、日時の条件1つあたり1回だけ変換する。NFR2.1.5）。
 *
 * @param fields 今の定義の検索の項目
 * @param inputs 入力
 * @param timeZone 表示のタイムゾーン（時点を表す型の条件に使う。読み込みの失敗・読み込み中は null）
 */
export function resolveSearch(
  fields: readonly SearchField[],
  inputs: SearchInputs,
  timeZone: string | null,
): SearchResolution {
  const conditions: SearchCondition[] = []
  const errors: [string, SearchInputError][] = []
  for (const field of fields) {
    const result = resolveField(field, inputOf(inputs, field.column), timeZone)
    if ('error' in result) {
      errors.push([field.column, result.error])
    } else if (result.condition !== null) {
      conditions.push(result.condition)
    }
  }
  return errors.length > 0
    ? { kind: 'invalid', errors: Object.fromEntries(errors) }
    : { kind: 'ok', conditions }
}

/**
 * 送る条件を作る。空の入力の項目は送らない。誤りのある項目（resolveSearch の invalid）は送らない
 * （呼ぶ側は先に validateSearchInputs・resolveSearch で確かめ、誤りがあれば検索させない）。
 *
 * @param fields 今の定義の検索の項目
 * @param inputs 入力
 * @param timeZone 表示のタイムゾーン（時点を表す型の条件に使う）
 */
export function buildConditions(
  fields: readonly SearchField[],
  inputs: SearchInputs,
  timeZone: string | null,
): SearchCondition[] {
  const conditions: SearchCondition[] = []
  for (const field of fields) {
    const result = resolveField(field, inputOf(inputs, field.column), timeZone)
    if (!('error' in result) && result.condition !== null) {
      conditions.push(result.condition)
    }
  }
  return conditions
}

/** 項目の入力に値があるか（変換しない） */
function isFilled(field: SearchField, input: SearchInput): boolean {
  switch (field.operator) {
    case 'RANGE':
      return input.from !== '' || input.to !== ''
    case 'CHOICE':
      return input.values.length > 0
    default:
      return input.value !== ''
  }
}

/** 条件のある項目の数（閉じた検索の見出しに出す。変換しない） */
export function countActiveConditions(
  fields: readonly SearchField[],
  inputs: SearchInputs,
): number {
  return fields.filter((field) => isFilled(field, inputOf(inputs, field.column))).length
}

/**
 * 検索の入力を画面で確かめる。
 *
 * @param fields 今の定義の検索の項目
 * @param inputs 入力
 * @param timeZone 表示のタイムゾーン（時点を表す型の条件に使う）
 * @returns 誤りのある項目（カラムの名前と誤り）。無ければ空
 */
export function validateSearchInputs(
  fields: readonly SearchField[],
  inputs: SearchInputs,
  timeZone: string | null,
): Readonly<Record<string, SearchInputError>> {
  const resolved = resolveSearch(fields, inputs, timeZone)
  return resolved.kind === 'invalid' ? resolved.errors : {}
}
