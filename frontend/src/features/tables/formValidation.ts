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
// 作成・更新のフォームの画面の検証（Intent 261016-business-data-views の U3 の L5、D21、FR10.1・SM4、AC2.4.1・AC2.4.2）。
// - 定義の検証の材料（required・nullable・minLength・maxLength・precision・scale・min・max・choices・formPart）で、
//   U2 の WriteRequestValidator と同じ順に当て、最初に当たった誤りだけを返す（reason はサーバーと同じ名前）。
// - 長さはコードポイントで数える（countCodePoints。Java の String.codePointCount と同じ）。文字の上限は DSL の maxLength と
//   10,000（MAX_WRITE_VALUE_LENGTH）の短い方。
// - pattern は画面で当てない（保存のときのサーバーの誤りを入力に出す）。判定はサーバーを正とする。
// - 数・範囲は文字のまま比べ、数に直さない（桁を失わない）。min・max の形が読めなければ範囲は当てない（サーバーに任せる）。
// - 入力不可の参照の項目（Intent 261029-reference-lookup-cleanup の U3、D15・X-5）は任意の最後の引数で渡し、検証の対象にしない
//   （作成の必須の入力不可の項目の誤り D13 は画面が別に足す）。渡さなければ今と同じ結果。
// - 必須の判定（isRequiredInput）は、参照の項目（reference がある項目）に限り isRequiredColumn（NULL を許さないか required が真）
//   で見る。参照でない項目は今どおり required だけを見る（Intent 261029-reference-lookup-cleanup の直しの Bolt B7、U3 R-01）。
import { countCodePoints } from '../../shared/validation/codePoints'
import { MAX_WRITE_VALUE_LENGTH } from './businessDataLimits'
import type { CellValue, ColumnDefinition, RowValues } from './businessDataTypes'
import { NO_LOCKED, isBooleanInput, isEditable, type FormMode } from './formDiff'
import { isRequiredColumn } from './references/referenceField'

/** 画面の検証の誤りの理由（サーバーの BusinessDataFieldErrorReason の一部） */
export type FormErrorReason =
  | 'REQUIRED'
  | 'NOT_NULL'
  | 'INVALID_VALUE'
  | 'TOO_LONG'
  | 'TOO_SHORT'
  | 'NOT_A_CHOICE'
  | 'TOO_MANY_DIGITS'
  | 'OUT_OF_RANGE'

/** カラムごとの誤り（カラムの名前と理由） */
export type FormErrors = Readonly<Record<string, FormErrorReason>>

const INTEGER_TEXT = /^[+-]?[0-9]+$/
const DECIMAL_TEXT = /^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)$/
const DATE_TEXT = /^\d{4}-\d{2}-\d{2}$/
const DATETIME_TEXT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?([+-]\d{2}:\d{2}|[Zz])?$/
const TIME_TEXT = /^\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?([+-]\d{2}:\d{2}|[Zz])?$/

/** 文字の上限（DSL の maxLength と 10,000 の短い方） */
export function effectiveMaxLength(column: ColumnDefinition): number {
  const dsl = column.maxLength ?? null
  return dsl === null ? MAX_WRITE_VALUE_LENGTH : Math.min(dsl, MAX_WRITE_VALUE_LENGTH)
}

/** 小数の文字を、符号・整数部（先頭の 0 を除く）・小数部（末尾の 0 を除く）に分ける */
function splitDecimal(text: string): { negative: boolean; integer: string; fraction: string } {
  const negative = text.startsWith('-')
  const unsigned = text.replace(/^[+-]/, '')
  const [integerPart, fractionPart = ''] = unsigned.split('.')
  return {
    negative,
    integer: integerPart.replace(/^0+/, ''),
    fraction: fractionPart.replace(/0+$/, ''),
  }
}

/** 0 以上の2つの小数を比べる（整数部と小数部の文字で） */
function compareMagnitude(
  a: { integer: string; fraction: string },
  b: { integer: string; fraction: string },
): number {
  if (a.integer.length !== b.integer.length) {
    return a.integer.length < b.integer.length ? -1 : 1
  }
  if (a.integer !== b.integer) {
    return a.integer < b.integer ? -1 : 1
  }
  const width = Math.max(a.fraction.length, b.fraction.length)
  const fa = a.fraction.padEnd(width, '0')
  const fb = b.fraction.padEnd(width, '0')
  return fa === fb ? 0 : fa < fb ? -1 : 1
}

/**
 * 2つの小数の文字を比べる（数に直さない）。どちらかが小数の形でなければ undefined。
 *
 * @returns a が小さければ負、等しければ 0、大きければ正
 */
export function compareDecimalText(a: string, b: string): number | undefined {
  if (!DECIMAL_TEXT.test(a) || !DECIMAL_TEXT.test(b)) {
    return undefined
  }
  const x = splitDecimal(a)
  const y = splitDecimal(b)
  const xZero = x.integer === '' && x.fraction === ''
  const yZero = y.integer === '' && y.fraction === ''
  const xNegative = x.negative && !xZero
  const yNegative = y.negative && !yZero
  if (xNegative !== yNegative) {
    return xNegative ? -1 : 1
  }
  const magnitude = compareMagnitude(x, y)
  // 等しいときは +0 を返す（負の数どうしで -magnitude にすると -0 になるため。性質のテストの seed 1380462492 で見つかった）
  if (magnitude === 0) {
    return 0
  }
  return xNegative ? -magnitude : magnitude
}

/** DECIMAL(p,s) の桁を超えるか（小数の桁が s を超える・整数部が p−s を超える。丸めない） */
function tooManyDigits(column: ColumnDefinition, text: string): boolean {
  const precision = column.precision ?? null
  if (precision === null) {
    return false
  }
  const scale = column.scale ?? 0
  const { integer, fraction } = splitDecimal(text)
  return fraction.length > scale || integer.length > precision - scale
}

/** 型として読めるか */
function readable(column: ColumnDefinition, text: string): boolean {
  switch (column.valueType) {
    case 'INTEGER':
    case 'BIG_INTEGER':
      return INTEGER_TEXT.test(text)
    case 'DECIMAL':
      return DECIMAL_TEXT.test(text)
    case 'BOOLEAN':
      return text === 'true' || text === 'false'
    case 'DATE':
      return DATE_TEXT.test(text)
    case 'DATETIME':
    case 'DATETIME_TZ':
      return DATETIME_TEXT.test(text)
    case 'TIME':
    case 'TIME_TZ':
      return TIME_TEXT.test(text)
    default:
      return true
  }
}

/**
 * 値を入れなければならない項目か（空の値・作成で値が無いときに REQUIRED にする条件）。参照の項目は isRequiredColumn
 * （NULL を許さないか required が真）、参照でない項目は required だけで見る（直しの Bolt B7、U3 R-01）。
 *
 * @param column カラムの定義
 */
export function isRequiredInput(column: ColumnDefinition): boolean {
  if (column.reference !== undefined && column.reference !== null) {
    return isRequiredColumn(column)
  }
  return column.required === true
}

/**
 * 送る値1つを確かめる（U2 の WriteRequestValidator.check と同じ順。最初に当たった誤りだけ）。
 *
 * @param column カラムの定義
 * @param value 送る値（null は NULL）
 */
export function validateValue(
  column: ColumnDefinition,
  value: CellValue,
): FormErrorReason | undefined {
  const empty = value === null || value === ''
  if (empty && isRequiredInput(column)) {
    return 'REQUIRED'
  }
  if (value === null) {
    return column.nullable === false ? 'NOT_NULL' : undefined
  }
  if (value === '' && column.valueType !== 'STRING') {
    return 'INVALID_VALUE'
  }
  if (isBooleanInput(column) && value !== 'true' && value !== 'false') {
    return 'INVALID_VALUE'
  }
  if (column.valueType === 'STRING') {
    const length = countCodePoints(value)
    if (length > effectiveMaxLength(column)) {
      return 'TOO_LONG'
    }
    const min = column.minLength ?? null
    if (min !== null && length > 0 && length < min) {
      return 'TOO_SHORT'
    }
  }
  const choices = column.choices ?? []
  if (choices.length > 0 && !choices.some((choice) => choice.value === value)) {
    return 'NOT_A_CHOICE'
  }
  if (!readable(column, value)) {
    return 'INVALID_VALUE'
  }
  const numeric =
    column.valueType === 'INTEGER' ||
    column.valueType === 'BIG_INTEGER' ||
    column.valueType === 'DECIMAL'
  if (numeric) {
    if (column.valueType === 'DECIMAL' && tooManyDigits(column, value)) {
      return 'TOO_MANY_DIGITS'
    }
    const min = column.min ?? null
    const max = column.max ?? null
    const belowMin = min === null ? undefined : compareDecimalText(value, min)
    const aboveMax = max === null ? undefined : compareDecimalText(value, max)
    if ((belowMin !== undefined && belowMin < 0) || (aboveMax !== undefined && aboveMax > 0)) {
      return 'OUT_OF_RANGE'
    }
  }
  return undefined
}

/**
 * フォーム全体を確かめる（保存のとき）。送る値を確かめ、作成では送らない必須のカラムを REQUIRED にする。
 *
 * @param columns 詳細のカラム
 * @param mode 作成か更新か
 * @param values 送る値（diffForm の結果）
 * @param truncatedColumns 先頭だけを受けたカラム
 * @param lockedColumns 入力不可の参照の項目（X-5）
 */
export function validateForm(
  columns: readonly ColumnDefinition[],
  mode: FormMode,
  values: RowValues,
  truncatedColumns: readonly string[] = [],
  lockedColumns: ReadonlySet<string> = NO_LOCKED,
): FormErrors {
  const entries: [string, FormErrorReason][] = []
  for (const column of columns) {
    if (!isEditable(column, mode, truncatedColumns) || lockedColumns.has(column.column)) {
      continue
    }
    if (Object.hasOwn(values, column.column)) {
      const reason = validateValue(column, values[column.column])
      if (reason !== undefined) {
        entries.push([column.column, reason])
      }
    } else if (mode === 'create' && isRequiredInput(column)) {
      entries.push([column.column, 'REQUIRED'])
    }
  }
  return Object.fromEntries(entries)
}
