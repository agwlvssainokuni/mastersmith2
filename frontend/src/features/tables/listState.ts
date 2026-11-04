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
// 一覧の状態の既定と、定義が変わったときの直し（Intent 261016-business-data-views の U3 の L6、D3・D10・D11、F1 の 3・F6 の 2）。
// - 既定: 検索は search.default の値、並べ替えは送らない（サーバーの既定の並び）、1ページ目、20 件。検索の領域は、すべての
//   項目が折りたたみの欄にあるときだけ閉じて始める（AC1.2.7）。
// - 定義が変わったとき: 今の定義に無い検索の入力と、並べ替えに使えなくなった並べ替えを外し、1ページ目から読み直す。
import type { ListState, SearchInputs, SortKey, TableDefinition } from './businessDataTypes'
import { DEFAULT_PAGE_SIZE } from './listPaging'
import { defaultSearchInputs } from './searchConditions'

/**
 * 一覧の既定の状態。時点を表す型の検索の既定の値は、表示のタイムゾーンの壁時計の文字にする（D28。Intent
 * 261102-timezone-ui-polish の U4）。表示のタイムゾーンがまだ無ければ、その項目は空にする（記憶に置く前の既定の状態は
 * 描くたびに作り直すため、ready になった後の描画で入る）。
 *
 * @param definition テーブルの定義
 * @param timeZone 表示のタイムゾーン（まだ無い・読み込めないときは null）
 */
export function defaultListState(definition: TableDefinition, timeZone: string | null): ListState {
  const fields = definition.searchFields
  return {
    search: defaultSearchInputs(fields, timeZone),
    sort: null,
    page: 1,
    size: DEFAULT_PAGE_SIZE,
    searchExpanded: !(fields.length > 0 && fields.every((field) => field.collapsed)),
  }
}

/**
 * 定義が変わった後も使える状態に直す（F6 の 2）。直すところが無ければ同じ物を返す。
 *
 * @param state 今の一覧の状態
 * @param definition 新しい定義
 */
export function sanitizeListState(state: ListState, definition: TableDefinition): ListState {
  const columns = new Set(definition.searchFields.map((field) => field.column))
  const entries = Object.entries(state.search)
  const kept = entries.filter(([column]) => columns.has(column))
  const sortable = new Set(definition.sortableColumns)
  const sort: SortKey | null =
    state.sort !== null && sortable.has(state.sort.column) ? state.sort : null
  if (kept.length === entries.length && sort === state.sort) {
    return state
  }
  const search: SearchInputs = Object.fromEntries(kept)
  return { ...state, search, sort, page: 1 }
}
