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
// 業務データの一覧のページの計算（Intent 261016-business-data-views の U3 の L6、D11・D12、AC1.3.1・AC1.3.2・AC1.3.14）。
// 共通の shared/paging（20 件に固定）は使い回さず、1ページの件数 20・50・100 を受ける。ページの番号は 1 から U1 の上限
// （1,000,000）まで。
import { MAX_PAGE } from './businessDataParse'
import { PAGE_SIZES, type PageSize } from './businessDataTypes'

/** 既定の1ページの件数 */
export const DEFAULT_PAGE_SIZE: PageSize = 20

/** 1ページの件数として受けられる値か */
export function isPageSize(value: unknown): value is PageSize {
  return (PAGE_SIZES as readonly unknown[]).includes(value)
}

/** 全体のページの数（少なくとも 1。上限を超えない） */
export function totalPages(total: number, size: PageSize): number {
  return Math.min(MAX_PAGE, Math.max(1, Math.ceil(total / size)))
}

/** ページの番号を 1〜上限に収める */
export function clampPage(page: number): number {
  if (!Number.isFinite(page)) {
    return 1
  }
  return Math.min(MAX_PAGE, Math.max(1, Math.trunc(page)))
}

/** 表示の範囲（何件目から何件目まで。行が無ければ null） */
export function displayRange(
  page: number,
  size: PageSize,
  itemCount: number,
): { from: number; to: number } | null {
  if (itemCount <= 0) {
    return null
  }
  const from = (page - 1) * size + 1
  return { from, to: from + itemCount - 1 }
}

/** 今のページに行が無いが、全体には行がある（ほかの人の削除で減った。AC1.3.14） */
export function isPageOverflow(itemCount: number, total: number): boolean {
  return itemCount === 0 && total > 0
}

/** ページを超えたときに移る前のページ（最後のページを超えない） */
export function previousPageFor(page: number, total: number, size: PageSize): number {
  return clampPage(Math.min(page - 1, totalPages(total, size)))
}
