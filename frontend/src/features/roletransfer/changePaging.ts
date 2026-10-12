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
// 確かめの結果の開いた行の、変わる点の 100 件ずつのページ送りの計算（Intent 261004-role-menu の U6 の D24、NFR2.8・
// NFR6.2）。純粋な関数。ページは 1 から。全件数が 0 ならページは 0 で範囲は空。

/** 開いた行の1ページの件数 */
export const CHANGE_PAGE_SIZE = 100

/** 1ページの範囲（start は含み、end は含まない添字） */
export interface ChangePageRange {
  page: number
  pageCount: number
  start: number
  end: number
}

/** ページの数 */
export function changePageCount(total: number): number {
  return total <= 0 ? 0 : Math.ceil(total / CHANGE_PAGE_SIZE)
}

/** そのページの範囲。ページが範囲の外なら最初か最後のページに寄せる。 */
export function changePageRange(page: number, total: number): ChangePageRange {
  const count = changePageCount(total)
  if (count === 0) {
    return { page: 0, pageCount: 0, start: 0, end: 0 }
  }
  const current = Math.min(Math.max(Math.trunc(page), 1), count)
  const start = (current - 1) * CHANGE_PAGE_SIZE
  return { page: current, pageCount: count, start, end: Math.min(start + CHANGE_PAGE_SIZE, total) }
}
