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
// 参照ピッカーの検索の条件と、ページの表示を決める純粋な関数（Intent 261029-reference-lookup-cleanup の U3、D22〜D26、
// frontend-components.md の 4.4）。判定はサーバーを正とし、画面の確かめは要求を減らすためのもの。
// - 入力の部品は valueType と演算子から、既存の作成・更新の画面（FieldInput）と同じ type・inputMode・step・説明文で選ぶ。
//   BOOLEAN は「（指定しない）」・真・偽の Select、CONTAINS は常に文字（D22）。
// - 送る値の書き方は一覧の検索（searchConditions.ts）と同じで、入力をそのまま送る。値が空の条件は送らない
//   （空白だけは空とみなさない。D23）。
// - 長さはコードポイントで数える（1,000 まで）。EQUALS は isReadableAs で型として読めるかを確かめる（D23）。
// - 日時の一致の条件（Intent 261102-timezone-ui-polish の U4、計画 Step 11、D34）: 時点を表す型（DATETIME_TZ）は表示の
//   タイムゾーンの壁時計を文字の入力で受け、resolveDateTimeInput（用途 equals）で時点（UTC の時差つき）に直して送る。
//   夏時間で存在しない時刻は誤り、2回ある時刻は2択の選択が要る（D26・D27）。時差を持たない日時（DATETIME）は変換せず
//   区切りを T にして送る。「UTC で入れる」の案内は出さない（文言は Step 12 で消した）。変換は「検索」の押下1回につき条件1つあたり1回
//   （ページ送りは作った条件を持ち回る。useLookupSearch）。
import { countCodePoints } from '../../../shared/validation/codePoints'
import { MAX_SEARCH_VALUE_LENGTH } from '../businessDataLimits'
import { PICKER_PAGE_SIZE } from '../businessDataParse'
import type { PickerSearchField, ReferenceCondition } from '../businessDataTypes'
import type { FormTime } from '../formDiff'
import {
  dateTimeErrorReason,
  dateTimeKindOf,
  resolveDateTimeInput,
  type DateTimeErrorReason,
  type DateTimeKind,
  type OverlapOffsets,
} from '../resolveDateTimeInput'
import { isReadableAs } from '../searchConditions'

/** 条件の入力の部品（D22） */
export type SearchInputSpec =
  | { kind: 'boolean' }
  | {
      kind: 'text'
      type: 'text' | 'number' | 'date'
      inputMode?: 'decimal'
      step?: number
      /** 説明文の文言の鍵（既存の作成・更新の画面と同じ鍵） */
      hintKeys: readonly string[]
      /** 日時の条件の種類（時点を表す型・時差を持たない日時。日時でなければ項目なし） */
      dateTime?: DateTimeKind
    }

/** ピッカーの条件の日時の受け渡し（表示のタイムゾーンと、条件ごとの2択の選択。フォームと同じ形） */
export type PickerTime = FormTime

/**
 * 条件の入力の部品を選ぶ（D22）。
 *
 * @param field 検索の条件
 */
export function searchInputFor(field: PickerSearchField): SearchInputSpec {
  if (field.operator === 'CONTAINS') {
    return { kind: 'text', type: 'text', hintKeys: [] }
  }
  switch (field.valueType) {
    case 'INTEGER':
      return { kind: 'text', type: 'number', hintKeys: [] }
    case 'BIG_INTEGER':
    case 'DECIMAL':
      return {
        kind: 'text',
        type: 'text',
        inputMode: 'decimal',
        hintKeys: ['tables.form.numberHint'],
      }
    case 'DATE':
      return { kind: 'text', type: 'date', hintKeys: ['tables.form.dateHint'] }
    case 'DATETIME':
      return { kind: 'text', type: 'text', hintKeys: [], dateTime: 'local' }
    case 'DATETIME_TZ':
      return { kind: 'text', type: 'text', hintKeys: [], dateTime: 'instant' }
    case 'BOOLEAN':
      return { kind: 'boolean' }
    default:
      return { kind: 'text', type: 'text', hintKeys: [] }
  }
}

/** 条件の入力（カラムの名前ごと） */
export type PickerInputs = Readonly<Record<string, string>>

/** 条件ごとの誤り（日時の条件は画面だけの理由。D19・D26・D27・D11） */
export type PickerInputError = 'too-long' | 'invalid-value' | DateTimeErrorReason

function inputOf(inputs: PickerInputs, column: string): string {
  return Object.hasOwn(inputs, column) ? inputs[column] : ''
}

/**
 * 送る条件を作る（値が空の条件は送らない）。
 *
 * @param fields 検索の条件
 * @param inputs 入力
 */
export function buildPickerConditions(
  fields: readonly PickerSearchField[],
  inputs: PickerInputs,
): ReferenceCondition[] {
  const conditions: ReferenceCondition[] = []
  for (const field of fields) {
    const value = inputOf(inputs, field.column)
    if (value !== '') {
      conditions.push({ column: field.column, value })
    }
  }
  return conditions
}

/**
 * 「検索」を押したときの画面の確かめの結果（D23）。誤りのうち夏時間で2回ある時刻（DATE_TIME_OVERLAP）は、2択に出す2つの
 * 時差を overlaps に持つ（2回ある時刻が無ければ項目なし。同じ押下の変換の結果を使い、変換を増やさない）。
 */
export type PickerValidation =
  | { kind: 'empty' }
  | {
      kind: 'errors'
      errors: Readonly<Record<string, PickerInputError>>
      overlaps?: Readonly<Record<string, OverlapOffsets>>
    }
  | { kind: 'ok'; conditions: readonly ReferenceCondition[] }

/**
 * 「検索」を押したときに確かめる（D23）。すべて空なら送らない、1,000 コードポイントを超える・EQUALS で型として読めない
 * 値は条件の誤りにして送らない。日時の一致の条件は表示のタイムゾーンの壁時計を時点に直して送る（D34）。
 *
 * @param fields 検索の条件
 * @param inputs 入力
 * @param time 表示のタイムゾーンと条件ごとの2択の選択
 */
export function validatePickerInputs(
  fields: readonly PickerSearchField[],
  inputs: PickerInputs,
  time: PickerTime,
): PickerValidation {
  if (buildPickerConditions(fields, inputs).length === 0) {
    return { kind: 'empty' }
  }
  const conditions: ReferenceCondition[] = []
  const errors: [string, PickerInputError][] = []
  const overlaps: [string, OverlapOffsets][] = []
  for (const field of fields) {
    const value = inputOf(inputs, field.column)
    if (value === '') {
      continue
    }
    if (countCodePoints(value) > MAX_SEARCH_VALUE_LENGTH) {
      errors.push([field.column, 'too-long'])
      continue
    }
    const kind = field.operator === 'EQUALS' ? dateTimeKindOf(field.valueType) : null
    if (kind !== null) {
      const choice = Object.hasOwn(time.overlapChoices, field.column)
        ? time.overlapChoices[field.column]
        : null
      const resolved = resolveDateTimeInput({
        text: value,
        kind,
        timeZone: time.timeZone,
        purpose: 'equals',
        overlapChoice: choice ?? null,
      })
      if (resolved.kind === 'ok') {
        conditions.push({ column: field.column, value: resolved.wire })
      } else {
        errors.push([field.column, dateTimeErrorReason(resolved) ?? 'DATE_TIME_FORMAT'])
        if (resolved.kind === 'overlap-pending') {
          overlaps.push([field.column, resolved.overlap])
        }
      }
    } else if (field.operator === 'EQUALS' && !isReadableAs(field.valueType, value)) {
      errors.push([field.column, 'invalid-value'])
    } else {
      conditions.push({ column: field.column, value })
    }
  }
  if (errors.length === 0) {
    return { kind: 'ok', conditions }
  }
  return overlaps.length === 0
    ? { kind: 'errors', errors: Object.fromEntries(errors) }
    : { kind: 'errors', errors: Object.fromEntries(errors), overlaps: Object.fromEntries(overlaps) }
}

/** ページの表示（D25・D26） */
export type PickerPaging =
  /** 行が1件以上: Table に渡す件数と、件数を言わない状態の文の鍵 */
  | {
      kind: 'table'
      totalCount: number
      statusKey: 'tables.picker.pageStatusMore' | 'tables.picker.pageStatusLast'
      rangeKey: 'tables.picker.rangeMore' | 'tables.picker.rangeLast'
      from: number
      to: number
    }
  /** 行が 0 件: Table を描かない。backTo は前のページ（1 ページ目なら null） */
  | { kind: 'empty'; backTo: number | null }

/**
 * ページの表示を決める（D25・D26）。Table の総ページ max(1, ceil(totalCount / 20)) が、続きがあれば page＋1、無ければ page に
 * なる件数を渡す（U1 は件数を返さない）。
 *
 * @param page 今のページ（1 から）
 * @param rowCount 今のページの行の数
 * @param hasNext 続きがあるか
 */
export function pickerPaging(page: number, rowCount: number, hasNext: boolean): PickerPaging {
  if (rowCount === 0) {
    return { kind: 'empty', backTo: page >= 2 ? page - 1 : null }
  }
  const from = (page - 1) * PICKER_PAGE_SIZE + 1
  const to = (page - 1) * PICKER_PAGE_SIZE + rowCount
  return {
    kind: 'table',
    totalCount: to + (hasNext ? 1 : 0),
    statusKey: hasNext ? 'tables.picker.pageStatusMore' : 'tables.picker.pageStatusLast',
    rangeKey: hasNext ? 'tables.picker.rangeMore' : 'tables.picker.rangeLast',
    from,
    to,
  }
}
