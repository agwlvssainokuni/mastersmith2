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

/** テーブルの置き場の画面の道（登録の path） */
export const TABLE_PLACEHOLDER_PATH = '/tables'

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
  const query = new URLSearchParams({ schema: ref.schemaName, table: ref.tableName })
  if (itemId !== undefined && itemId !== null && itemId !== '') {
    query.set('item', itemId)
  }
  return `${TABLE_PLACEHOLDER_PATH}?${query.toString()}`
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
