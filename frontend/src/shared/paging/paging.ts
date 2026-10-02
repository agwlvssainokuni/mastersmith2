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
// 管理の一覧（招待の一覧・利用者の一覧）のページ送りの計算（BR2.1〜BR2.5）。純粋な関数で、React・window・API に
// 触れず、どの機能のフォルダーにも依存しない。ページの大きさは 20、ページの数は全件数を 20 で割った切り上げ（全件数が 0 なら
// ページは 0）。画面の文言（訳の鍵）は持たず、使う機能が自分で持つ。

/**
 * 1ページの件数（BR1.1・BR2.1）。サーバーの `cherry.mastersmith.common.paging.Paging.PAGE_SIZE` と同じ値にする
 * （変えるときは両方を同じ変更で直す）。
 */
export const PAGE_SIZE = 20

/** ページ送りのボタンの向き */
export type PagerDirection = 'prev' | 'next'

/** 件数の範囲（何件目から何件目まで。全件数が 0 なら両方 0） */
export interface PageRange {
  from: number
  to: number
}

/** ページの数（全件数が 0 以下なら 0。BR2.1）。 */
export function pageCount(total: number): number {
  return total <= 0 ? 0 : Math.ceil(total / PAGE_SIZE)
}

/**
 * そのページの件数の範囲（BR2.2）。例: 2ページ目・全 43 件なら 21〜40 件目、3ページ目なら 41〜43 件目。
 *
 * @param page ページ（1 から）
 * @param total 全件数
 */
export function pageRange(page: number, total: number): PageRange {
  if (total <= 0) {
    return { from: 0, to: 0 }
  }
  const from = (page - 1) * PAGE_SIZE + 1
  return { from, to: Math.min(page * PAGE_SIZE, total) }
}

/**
 * ページの補正（BR2.3）。応答の行が空で全件数が 1 以上のとき（ほかの操作で今のページが最後を超えた）は、
 * 最後のページを返す。補正しないとき（行がある・全件数が 0・すでに最後のページを読んだ）は undefined。
 * すでに最後のページを読んで行が空だったときに同じページを読み続けないよう、今のページと同じなら補正しない。
 *
 * @param page 読んだページ
 * @param total 応答の全件数
 * @param itemCount 応答の行の数
 */
export function correctedPage(page: number, total: number, itemCount: number): number | undefined {
  if (itemCount > 0 || total <= 0) {
    return undefined
  }
  const last = pageCount(total)
  return last === page ? undefined : last
}

/**
 * ページ送りで押したボタンが、読んだ先のページで押せなくなるか（BR2.4）。押せなくなるときは、フォーカスを一覧の見出しへ
 * 移す。「前へ」は1ページ目、「次へ」は最後のページに着いたとき押せなくなる。
 *
 * @param direction 押したボタン
 * @param page 読んだ先のページ
 * @param total 全件数
 */
export function pagerButtonDisabledAfter(
  direction: PagerDirection,
  page: number,
  total: number,
): boolean {
  return direction === 'prev' ? page <= 1 : page >= pageCount(total)
}
