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
// 行を見分ける文字（行のラベル）を作る純粋な関数（Intent 261016-business-data-views の U3 の L6、D9・R-02、
// AC1.4.7・AC4.1.1、U3-NFR5.9・7.10）。
// - 定義の rowLabelColumns の各カラムの値を、valueFormat の書式に通した後の、画面に見えている文字で作る（生の値は使わない）。
// - 各部分は読み上げの名前の計算と同じく、空白の並びを1つにまとめて前後を切る。空白だけの部分は「（空）」にする。
//   そのため、行のリンクの見える文字（最初の部分）は、行のラベル（部分を半角の空白でつないだもの）に必ず含まれる
//   （WCAG 2.5.3。axe-core 4.14 の label-content-name-mismatch）。DOM の文字は切らず、省略は CSS だけで行う（D17）。
// - rowLabelColumns が空のとき（DETAIL が許されない）、または最初のカラムが一覧の列に無いときは、行のリンクを出さない。
import type { ColumnDefinition, RowValues, TableDefinition } from './businessDataTypes'
import { formatCell, type ValueTexts } from './valueFormat'

/** 読み上げの名前と同じ形にまとめる（空白の並びを1つに、前後を切る） */
export function normalizeLabelPart(text: string): string {
  return text.replace(/\s+/gu, ' ').trim()
}

function findColumn(definition: TableDefinition, column: string): ColumnDefinition | undefined {
  return (
    definition.listColumns.find((item) => item.column === column) ??
    definition.detailColumns.find((item) => item.column === column)
  )
}

/**
 * 行のラベルの部分（rowLabelColumns の順に、書式の後の見えている文字）。
 *
 * @param definition テーブルの定義
 * @param values 行の値
 * @param texts 決まった文
 */
export function rowLabelParts(
  definition: TableDefinition,
  values: RowValues,
  texts: ValueTexts,
): readonly string[] {
  return definition.rowLabelColumns.map((column) => {
    const format = findColumn(definition, column) ?? {
      valueType: 'STRING',
      listFormat: null,
      masked: false,
    }
    const value = Object.hasOwn(values, column) ? values[column] : undefined
    const part = normalizeLabelPart(formatCell(format, value, texts).text)
    return part === '' ? texts.empty : part
  })
}

/**
 * 行のラベル（部分を半角の空白でつなぐ。部分が無ければ「（空）」）。
 *
 * @param parts rowLabelParts の結果
 * @param texts 決まった文
 */
export function rowLabelText(parts: readonly string[], texts: ValueTexts): string {
  return parts.length === 0 ? texts.empty : parts.join(' ')
}

/**
 * 行のリンクの見える文字（最初の部分。行のラベルに必ず含まれる）。
 *
 * @param parts rowLabelParts の結果
 * @param texts 決まった文
 */
export function rowLinkText(parts: readonly string[], texts: ValueTexts): string {
  return parts[0] ?? texts.empty
}

/**
 * 行のリンクを置くカラム。DETAIL が許され、rowLabelColumns の最初のカラムが一覧の列にあるときだけ、その名前を返す。
 *
 * @param definition テーブルの定義
 * @returns カラムの名前（リンクを出さないときは null）
 */
export function rowLinkColumn(definition: TableDefinition): string | null {
  const first = definition.rowLabelColumns[0]
  if (first === undefined || !definition.operations.includes('DETAIL')) {
    return null
  }
  return definition.listColumns.some((column) => column.column === first) ? first : null
}
