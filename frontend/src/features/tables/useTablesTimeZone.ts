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
// 業務データの画面が使う表示のタイムゾーンの読み口（Intent 261102-timezone-ui-polish の U4、計画 Step 12、D9〜D12、
// frontend-components.md の 1節・4節、logical-components.md の useTablesTimeZone）。
// - U2 の useDisplayTimeZone だけから読む（ブラウザの既定のタイムゾーンを暗に使わない）。
// - 画面の定義に時点を表す型（DATETIME_TZ）が要るとき（カラム・検索の項目・参照のラベルの列・ピッカーの列と条件）だけ、
//   読み込み中（loading）を待つ（D10）。要らない画面は待たない。
// - 書式（formatCell）に渡す状態（CellTimeZone）と、日時の欄に渡す状態（FieldTimeZone）を作る。loading の間は書式に
//   時点の値を渡さない（画面が待つ）。
import { useMemo } from 'react'
import {
  useDisplayTimeZone,
  type DisplayTimeZoneState,
} from '../../app/display-settings/DisplayTimeZoneProvider'
import type { ColumnDefinition, SearchField, ValueType } from './businessDataTypes'
import type { CellTimeZone } from './valueFormat'

/** 日時の欄に渡す表示のタイムゾーンの状態（loading・error では時点の欄を使えない。D11） */
export type FieldTimeZone =
  | { readonly status: 'ready'; readonly timeZone: string }
  | { readonly status: 'loading' }
  | { readonly status: 'error' }

/** useTablesTimeZone が返す値 */
export interface TablesTimeZone {
  /** U2 の状態そのもの（案内の1行の source に使う） */
  readonly state: DisplayTimeZoneState
  /** 画面が待つか（時点を表す型が要り、まだ読み込み中） */
  readonly waiting: boolean
  /** 書式に渡す状態（loading は error として渡す。待つ画面では loading の間に書式を呼ばない） */
  readonly cell: CellTimeZone
  /** 日時の欄に渡す状態 */
  readonly field: FieldTimeZone
  /** ready のときの名前（ほかは null） */
  readonly timeZone: string | null
}

const INSTANT: ValueType = 'DATETIME_TZ'

/**
 * 案内の1行のリンクの行き先（プリファレンスの画面。features/preferences の PREFERENCES_PATH と同じ値）。
 * 機能どうしは直接 import しない決まりのため、ここに同じ値を置き、テスト（useTablesTimeZone.test.ts）で一致を確かめる。
 */
export const PREFERENCES_LINK_PATH = '/me/preferences'

/**
 * カラムの並びに、表示のタイムゾーンが要る値（時点を表す型の値・参照のラベル・ピッカーの列と条件）があるか。
 *
 * @param columns カラムの定義
 */
export function columnsNeedTimeZone(columns: readonly ColumnDefinition[]): boolean {
  return columns.some((column) => {
    if (column.valueType === INSTANT) {
      return true
    }
    const reference = column.reference ?? null
    if (reference === null) {
      return false
    }
    if (reference.labelValueType === INSTANT) {
      return true
    }
    const picker = reference.picker
    return (
      picker !== null &&
      (picker.listColumns.some((item) => item.valueType === INSTANT) ||
        picker.searchFields.some((item) => item.valueType === INSTANT))
    )
  })
}

/**
 * 検索の項目に時点を表す型の条件があるか。
 *
 * @param fields 検索の項目
 */
export function searchFieldsNeedTimeZone(fields: readonly SearchField[]): boolean {
  return fields.some((field) => field.valueType === INSTANT)
}

/**
 * U2 の状態から、書式に渡す状態を作る（ready は名前、それ以外は error）。
 *
 * @param state U2 の状態
 */
export function toCellTimeZone(state: DisplayTimeZoneState): CellTimeZone {
  return state.status === 'ready'
    ? { status: 'ready', timeZone: state.timeZone }
    : { status: 'error' }
}

/**
 * U2 の状態から、日時の欄に渡す状態を作る。
 *
 * @param state U2 の状態
 */
export function toFieldTimeZone(state: DisplayTimeZoneState): FieldTimeZone {
  return state.status === 'ready' ? { status: 'ready', timeZone: state.timeZone } : state
}

/**
 * 業務データの画面の表示のタイムゾーンを返す。
 *
 * @param needed 画面の定義に時点を表す型が要るか（定義を読む前は false）
 */
export function useTablesTimeZone(needed: boolean): TablesTimeZone {
  const state = useDisplayTimeZone()
  // 同じ状態の間は同じ物を返す（呼ぶ側の useMemo・useCallback の依存を揺らさない）
  return useMemo(
    () => ({
      state,
      waiting: needed && state.status === 'loading',
      cell: toCellTimeZone(state),
      field: toFieldTimeZone(state),
      timeZone: state.status === 'ready' ? state.timeZone : null,
    }),
    [state, needed],
  )
}
