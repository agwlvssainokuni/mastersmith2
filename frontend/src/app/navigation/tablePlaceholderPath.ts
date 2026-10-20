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
// テーブルの置き場の道を作る・読む純粋な関数（Intent 261004-role-menu の U7 の D20、L6、NFR1.6・NFR1.7）。
// - 道は `/tables?schema=<スキーマ名>&table=<テーブル名>&item=<位置の道>` の形だけ。値は URLSearchParams で
//   エンコードし、先頭は常に `/tables?` にする（名前から外部の URL・`javascript:` の道を作れない）。
// - 置き場の道を作るのはこのファイルの toTablePlaceholderPath の1か所だけ（ほかの場所で文字列の連結で作らない）。
// - 読み戻しは値を読んだまま（正規化しない）。schema・table のどちらかが無い・空なら ref を null にする。
// - Intent 261016-business-data-views の U3（D1・L9）で、業務データの4つの道（一覧 `/tables`・詳細 `/tables/row`・作成
//   `/tables/new`・更新 `/tables/edit`）を作る1か所の関数 toTablePath と、道がテーブルの道かを判定する isTablePath を足した。
//   どの道も問い合わせの部分は schema・table・item だけで、主キー・行の値・検索の値は載せない（D2・D3）。
//   今の toTablePlaceholderPath・readTablePlaceholderParams・TableRef の形は保つ（一覧の道を作る・読む）。

/** テーブルの置き場の画面の道（登録の path）。業務データの一覧の道 */
export const TABLE_PLACEHOLDER_PATH = '/tables'

/** 業務データの画面の道（D1） */
export const TABLE_PATHS = {
  list: TABLE_PLACEHOLDER_PATH,
  row: '/tables/row',
  new: '/tables/new',
  edit: '/tables/edit',
} as const

/** 業務データの画面の種類 */
export type TableScreen = keyof typeof TABLE_PATHS

const TABLE_PATH_SET: ReadonlySet<string> = new Set(Object.values(TABLE_PATHS))

/**
 * 道（pathname）が業務データの4つの道のどれかか（サイドバーの今の項目の判定に使う。AC1.1.6）。
 *
 * @param pathname 道（問い合わせの部分を含まない）
 */
export function isTablePath(pathname: string): boolean {
  return TABLE_PATH_SET.has(pathname)
}

/** スキーマ名とテーブル名の組 */
export interface TableRef {
  schemaName: string
  tableName: string
}

/** 置き場の道から読んだ引数 */
export interface TablePlaceholderParams {
  /** スキーマ名とテーブル名（どちらかが無い・空なら null） */
  ref: TableRef | null
  /** 押したメニューの項目の位置の道（前置きの無い NavNode.id。無い・空なら null） */
  itemId: string | null
}

/**
 * テーブルの置き場の道を作る。
 *
 * @param ref スキーマ名とテーブル名
 * @param itemId 押したメニューの項目の位置の道（無ければ item を付けない）
 * @returns `/tables?` で始まる道
 */
export function toTablePlaceholderPath(ref: TableRef, itemId?: string | null): string {
  return toTablePath('list', ref, itemId)
}

/**
 * 業務データの画面の道を作る（4つの道を作るのはこの関数の1か所だけ。D1）。
 *
 * @param screen 画面の種類
 * @param ref スキーマ名とテーブル名
 * @param itemId 押したメニューの項目の位置の道（無ければ item を付けない）
 * @returns `/tables` か `/tables/row`・`/tables/new`・`/tables/edit` に `?` が続く道
 */
export function toTablePath(screen: TableScreen, ref: TableRef, itemId?: string | null): string {
  const query = new URLSearchParams({ schema: ref.schemaName, table: ref.tableName })
  if (itemId !== undefined && itemId !== null && itemId !== '') {
    query.set('item', itemId)
  }
  return `${TABLE_PATHS[screen]}?${query.toString()}`
}

/**
 * 置き場の道の問い合わせの部分（`?` で始まってもよい）から引数を読む。
 *
 * @param search 問い合わせの部分（location.search）
 * @returns 読んだ引数
 */
export function readTablePlaceholderParams(search: string): TablePlaceholderParams {
  const query = new URLSearchParams(search)
  const schemaName = query.get('schema')
  const tableName = query.get('table')
  const item = query.get('item')
  const ref =
    schemaName !== null && schemaName !== '' && tableName !== null && tableName !== ''
      ? { schemaName, tableName }
      : null
  return { ref, itemId: item !== null && item !== '' ? item : null }
}
