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
// 業務データの値の書式（Intent 261016-business-data-views の U3 の L6、D15・D16、AC1.5.2〜AC1.5.8、U3-NFR4.20）。
// - 純粋な関数。値は文字のまま扱い、数に直さない（BIGINT・DECIMAL の桁を失わない）。日英で同じ形にする。
// - 表のセル・詳細・行のラベル（rowLabel）は、すべてこの関数の結果を使う。
// - 返すのは文字だけで、HTML として解釈しない（呼ぶ側は React の文字として描く）。
// - password（masked）は値の有無によらず `••••••`、BINARY は「（バイナリ）」、NULL は「（値なし）」。空の文字列は空のまま
//   （NULL と見分ける）。サーバーは password と BINARY のカラムの値を返さない（項目が無い）。
import type { CellValue, ColumnDefinition } from './businessDataTypes'

/** 伏せた値の表示（password。D15） */
export const MASKED_TEXT = '••••••'

/** 書式に使う決まった文（画面の言語の文を呼ぶ側が渡す） */
export interface ValueTexts {
  /** NULL（（値なし）） */
  null: string
  /** BINARY（（バイナリ）） */
  binary: string
  /** 行のラベルで空白だけの値（（空）） */
  empty: string
  yes: string
  no: string
  on: string
  off: string
}

/** 書式の後の値の種類 */
export type FormattedKind = 'text' | 'null' | 'masked' | 'binary'

/** 書式の後の値 */
export interface FormattedValue {
  kind: FormattedKind
  /** 画面に見える文字 */
  text: string
}

/** 書式に使うカラムの項目 */
export type FormatColumn = Pick<ColumnDefinition, 'valueType' | 'listFormat' | 'masked'>

const NUMBER_TEXT = /^([+-]?)(\d+)(\.\d+)?$/

/** 整数の部分を3桁ごとに区切る（文字のまま。数に直さない） */
export function groupDigits(value: string): string {
  const match = NUMBER_TEXT.exec(value)
  if (match === null) {
    return value
  }
  const [, sign, integer, fraction = ''] = match
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return `${sign}${grouped}${fraction}`
}

/** 日時の区切りを整える（`T` を空白に、末尾の `Z` を `+00:00` に） */
function formatTemporal(value: string, withOffset: boolean): string {
  let text = value.replace(/^(\d{4}-\d{2}-\d{2})T/, '$1 ')
  if (withOffset && /[Zz]$/.test(text)) {
    text = `${text.slice(0, -1)}+00:00`
  }
  return text
}

/**
 * セルの値を書式にする。
 *
 * @param column カラムの型と書式
 * @param value 値（無い・null は NULL）
 * @param texts 決まった文
 */
export function formatCell(
  column: FormatColumn,
  value: CellValue | undefined,
  texts: ValueTexts,
): FormattedValue {
  if (column.masked) {
    return { kind: 'masked', text: MASKED_TEXT }
  }
  if (column.valueType === 'BINARY') {
    return { kind: 'binary', text: texts.binary }
  }
  if (value === null || value === undefined) {
    return { kind: 'null', text: texts.null }
  }
  if (value === '') {
    return { kind: 'text', text: '' }
  }
  const format = column.listFormat
  if (
    column.valueType === 'BOOLEAN' ||
    format === 'BOOLEAN_YES_NO' ||
    format === 'BOOLEAN_ON_OFF'
  ) {
    const onOff = format === 'BOOLEAN_ON_OFF'
    if (value === 'true') {
      return { kind: 'text', text: onOff ? texts.on : texts.yes }
    }
    if (value === 'false') {
      return { kind: 'text', text: onOff ? texts.off : texts.no }
    }
    return { kind: 'text', text: value }
  }
  if (format === 'NUMBER_GROUPED') {
    return { kind: 'text', text: groupDigits(value) }
  }
  switch (column.valueType) {
    case 'DATETIME':
    case 'DATE':
    case 'TIME':
      return { kind: 'text', text: formatTemporal(value, false) }
    case 'DATETIME_TZ':
    case 'TIME_TZ':
      return { kind: 'text', text: formatTemporal(value, true) }
    default:
      return { kind: 'text', text: value }
  }
}
