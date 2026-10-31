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
// 作成・更新のフォームの入力の値と、送る本文の写し（Intent 261016-business-data-views の U3 の L5、D19・D20、SM1〜SM3・SM7、
// AC2.2.2・AC2.2.8〜AC2.2.11）。純粋な関数で、値は文字のまま扱い数に直さない（BIGINT・DECIMAL の桁を失わない）。
// - 入力の値は文字で持つ。真偽値の入力は 'true'・'false'、NULL の初めの値は空の文字（外れた状態で描く）。
// - 変更の有無は、初めに入力へ入れた値と今の値を比べて決める（DB の生の値とは比べない。D20）。
// - 作成: 入力にするカラムのうち、空でない値だけを送る（空のままのカラムは送らず、対象DB の既定値に任せる）。
//   必須の真偽値の入力は 'false' で始め、送る（チェックを外したままで送れるようにする。generation-notes の B6 の差）。
// - 更新: 変えたカラムだけを送る。空にしたカラムは、文字の型（STRING）なら ''、ほかの型なら null。
// - masked（password）: 空で始め、値を入れたときだけ送る（作成・更新とも）。
// - 日時（DATETIME・DATETIME_TZ）は秒まで（datetime-local の値に秒が無ければ ':00' を足す）。DATETIME_TZ は '+00:00' を付ける。
// - 入力不可の参照の項目（Intent 261029-reference-lookup-cleanup の U3、D15・X-5）は、任意の最後の引数 lockedColumns で渡し、
//   入力できないカラムと同じに扱う（本文に入れず、変更の有無にも数えない）。渡さなければ今と同じ結果。
import type { CellValue, ColumnDefinition, RowValues } from './businessDataTypes'

/** フォームの種類 */
export type FormMode = 'create' | 'update'

/** フォームの入力の値（カラムの名前ごと） */
export type FormInputs = Readonly<Record<string, string>>

/** 入力不可の参照の項目が無いとき（既定の引数） */
export const NO_LOCKED: ReadonlySet<string> = new Set()

/** 真偽値の入力（checkbox・switch）か */
export function isBooleanInput(column: ColumnDefinition): boolean {
  return column.formPart === 'checkbox' || column.formPart === 'switch'
}

/** そのフォームで入力にするカラムか（作成は editableOnCreate、更新は editableOnUpdate で、省略した値でない） */
export function isEditable(
  column: ColumnDefinition,
  mode: FormMode,
  truncatedColumns: readonly string[] = [],
): boolean {
  if (mode === 'create') {
    return column.editableOnCreate
  }
  return column.editableOnUpdate && !truncatedColumns.includes(column.column)
}

/** 日時の値を datetime-local の入力の形（YYYY-MM-DDTHH:mm:ss）にする。形が違えばそのまま返す */
function toDateTimeInput(value: string): string {
  const match = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}(?::\d{2})?)/.exec(value)
  if (match === null) {
    return value
  }
  const time = match[2].length === 5 ? `${match[2]}:00` : match[2]
  return `${match[1]}T${time}`
}

/**
 * DB の値を入力の初めの値にする。
 *
 * @param column カラムの定義
 * @param value 詳細の値（無い・null は NULL）
 */
export function toInputValue(column: ColumnDefinition, value: CellValue | undefined): string {
  if (column.masked || value === null || value === undefined) {
    return ''
  }
  if (column.valueType === 'DATETIME' || column.valueType === 'DATETIME_TZ') {
    return toDateTimeInput(value)
  }
  return value
}

/**
 * フォームの初めの入力の値を作る（入力にするカラムだけ）。
 *
 * @param columns 詳細のカラム
 * @param mode 作成か更新か
 * @param values 更新のときの今の値（作成では渡さない）
 * @param truncatedColumns 先頭だけを受けたカラム（更新では読み取りにする）
 */
export function initialInputs(
  columns: readonly ColumnDefinition[],
  mode: FormMode,
  values: Readonly<Record<string, CellValue>> = {},
  truncatedColumns: readonly string[] = [],
): FormInputs {
  const entries: [string, string][] = []
  for (const column of columns) {
    if (!isEditable(column, mode, truncatedColumns)) {
      continue
    }
    if (mode === 'create') {
      entries.push([
        column.column,
        isBooleanInput(column) && column.required === true ? 'false' : '',
      ])
    } else {
      const value = Object.hasOwn(values, column.column) ? values[column.column] : undefined
      entries.push([column.column, toInputValue(column, value)])
    }
  }
  return Object.fromEntries(entries)
}

/** 日時の入力の値を送る形にする（秒まで。DATETIME_TZ は UTC の印を付ける） */
function toWireValue(column: ColumnDefinition, input: string): string {
  if (column.valueType === 'DATETIME' || column.valueType === 'DATETIME_TZ') {
    const value = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(input) ? `${input}:00` : input
    return column.valueType === 'DATETIME_TZ' ? `${value}+00:00` : value
  }
  return input
}

function inputOf(inputs: FormInputs, column: string): string {
  return Object.hasOwn(inputs, column) ? inputs[column] : ''
}

/**
 * 送る本文の値を作る（D20）。変えたカラムが無ければ空の組を返す（保存を押せない）。
 *
 * @param columns 詳細のカラム
 * @param mode 作成か更新か
 * @param initial 初めの入力の値
 * @param current 今の入力の値
 * @param truncatedColumns 先頭だけを受けたカラム
 * @param lockedColumns 入力不可の参照の項目（X-5）
 */
export function diffForm(
  columns: readonly ColumnDefinition[],
  mode: FormMode,
  initial: FormInputs,
  current: FormInputs,
  truncatedColumns: readonly string[] = [],
  lockedColumns: ReadonlySet<string> = NO_LOCKED,
): RowValues {
  const entries: [string, CellValue][] = []
  for (const column of columns) {
    if (!isEditable(column, mode, truncatedColumns) || lockedColumns.has(column.column)) {
      continue
    }
    const now = inputOf(current, column.column)
    if (column.masked || mode === 'create') {
      if (now !== '') {
        entries.push([column.column, toWireValue(column, now)])
      }
      continue
    }
    if (now === inputOf(initial, column.column)) {
      continue
    }
    if (now === '') {
      entries.push([column.column, column.valueType === 'STRING' ? '' : null])
    } else {
      entries.push([column.column, toWireValue(column, now)])
    }
  }
  return Object.fromEntries(entries)
}

/** 変更があるか（未保存の確かめに使う。作成でも初めの値と比べる。入力不可の参照の項目は数えない） */
export function hasFormChanges(
  columns: readonly ColumnDefinition[],
  mode: FormMode,
  initial: FormInputs,
  current: FormInputs,
  truncatedColumns: readonly string[] = [],
  lockedColumns: ReadonlySet<string> = NO_LOCKED,
): boolean {
  return columns.some(
    (column) =>
      isEditable(column, mode, truncatedColumns) &&
      !lockedColumns.has(column.column) &&
      inputOf(initial, column.column) !== inputOf(current, column.column),
  )
}
