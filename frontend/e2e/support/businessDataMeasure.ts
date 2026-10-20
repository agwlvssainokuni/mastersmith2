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
// 業務データの画面の時間の測りの部品（Intent 261016-business-data-views の U3 の L10、performance-design.md の 4節、
// U3-NFR1.7・1.8、計画 4.3 の R-11・4.8 の P-3）。
// - 時計は画面の中の performance.now() の1つだけ。聞き手と見張りは page.evaluate で入れ、アプリのコードには入れない。
// - 測り始め: document の click と change の捕捉の段で、event.target.closest(選択子) が当たった最初の1回（1ページの件数の
//   選択はネイティブの select の change で起きるため、u7Measure.ts の click だけの形を広げた）。
// - 測り終わり（判定の値）: MutationObserver と focusin で、名前で選んだ判定が真になった後の次の requestAnimationFrame の時刻
//   （NFR1.8 の物差しのまま）。あわせて、その次の requestAnimationFrame の時刻（「塗りまで」）も記録する。「塗りまで」は
//   判定に使わず、参考として並べる（F1: A）。
// - 判定は listShown100・listShown20・detailFocused・formShown100・listShown100Back の5つ（P-3・R-11）。テストは関数の
//   本体ではなく名前で選ぶ。記録に残すのは名前と時間だけで、行の値を残さない。
import { expect, type Page } from '@playwright/test'
import { summarizeRuns, type MeasureRecord } from './u6Measure'

/** 判定の名前 */
export type BusinessDataDoneName =
  'listShown100' | 'listShown20' | 'detailFocused' | 'formShown100' | 'listShown100Back'

/** 判定ごとの目標（ミリ秒。U3-NFR1.7） */
export const BUSINESS_DATA_TARGETS: Readonly<Record<BusinessDataDoneName, number>> = {
  listShown100: 1000,
  listShown20: 500,
  detailFocused: 500,
  formShown100: 500,
  listShown100Back: 1000,
}

/** 判定に使う値（列の鍵と、詳細・フォームの項目の数だけ） */
export interface BusinessDataDoneParams {
  /** 一覧の表の最後の列の鍵（Table の列の鍵。行の操作の列があれば actions、無ければ c<n>） */
  lastColumnKey: string
  /** 詳細の項目の数 */
  detailItems: number
}

/** 1回の測り（判定の値と「塗りまで」） */
export interface PaintRun {
  value: number
  toPaint: number
}

/** 判定の表を画面に入れる（画面を読み込み直した後、armBusinessDataMeasure より前に呼ぶ）。 */
export async function installBusinessDataDone(
  page: Page,
  params: BusinessDataDoneParams,
): Promise<void> {
  await page.evaluate((values) => {
    const byTestId = (id: string): Element | null => document.querySelector(`[data-testid="${id}"]`)
    const notBusy = (): boolean => byTestId('tables-page')?.getAttribute('aria-busy') === 'false'
    const listShown = (rows: number) => (): boolean =>
      notBusy() &&
      document.querySelectorAll('[data-testid^="table-row-r"]').length === rows &&
      byTestId(`table-cell-r${rows - 1}-${values.lastColumnKey}`) !== null
    const done: Record<string, () => boolean> = {
      listShown100: listShown(100),
      listShown100Back: listShown(100),
      listShown20: listShown(20),
      detailFocused: () =>
        document.activeElement?.getAttribute('data-testid') === 'tables-detail-heading' &&
        document.querySelectorAll('[data-testid^="tables-detail-c"]').length === values.detailItems,
      formShown100: () =>
        document.querySelectorAll('[data-testid^="tables-form-c"]').length === values.detailItems,
    }
    ;(window as unknown as { __bizDone: Record<string, () => boolean> }).__bizDone = done
  }, params)
}

/** 測りを仕込む（測る操作の直前に呼ぶ）。 */
export async function armBusinessDataMeasure(
  page: Page,
  startSelector: string,
  done: BusinessDataDoneName,
): Promise<void> {
  await page.evaluate(
    ({ selector, name }) => {
      type Mark = { start?: number; end?: number; paint?: number }
      const w = window as unknown as { __bizMark: Mark; __bizDone: Record<string, () => boolean> }
      const mark: Mark = {}
      w.__bizMark = mark
      const listener = (event: Event) => {
        const target = event.target as Element | null
        if (mark.start === undefined && target?.closest(selector)) {
          mark.start = performance.now()
          document.removeEventListener('click', listener, true)
          document.removeEventListener('change', listener, true)
        }
      }
      document.addEventListener('click', listener, true)
      document.addEventListener('change', listener, true)
      const check = () => {
        if (mark.start === undefined || mark.end !== undefined || !w.__bizDone[name]?.()) {
          return
        }
        observer.disconnect()
        document.removeEventListener('focusin', check, true)
        requestAnimationFrame(() => {
          mark.end = performance.now()
          requestAnimationFrame(() => {
            mark.paint = performance.now()
          })
        })
      }
      // フォーカスの移動は DOM の変化ではないため、focusin でも判定を見直す（detailFocused）。
      const observer = new MutationObserver(check)
      observer.observe(document.body, {
        subtree: true,
        childList: true,
        characterData: true,
        attributes: true,
      })
      document.addEventListener('focusin', check, true)
    },
    { selector: startSelector, name: done },
  )
}

/** 記録がそろうのを待ち、判定の値と「塗りまで」（ミリ秒）を返す。 */
export async function readBusinessDataMeasure(page: Page, timeout = 30_000): Promise<PaintRun> {
  await expect
    .poll(
      () =>
        page.evaluate(() => {
          const mark = (window as unknown as { __bizMark?: { start?: number; paint?: number } })
            .__bizMark
          return mark?.start !== undefined && mark.paint !== undefined
        }),
      { timeout },
    )
    .toBe(true)
  return page.evaluate(() => {
    const mark = (window as unknown as { __bizMark: { start: number; end: number; paint: number } })
      .__bizMark
    return { value: mark.end - mark.start, toPaint: mark.paint - mark.start }
  })
}

/** 判定の記録と「塗りまで」の参考の記録 */
export interface PaintRecord extends MeasureRecord {
  paint: { runs: number[]; median: number | null; max: number | null; first: number | null }
}

/**
 * 5 回の値から記録を作る（判定は判定の値の中央値。「塗りまで」は参考として並べるだけ）。
 *
 * @param id 判定の名前（記録の名前）
 * @param runs 測った値
 * @param targetMs 目標
 * @param expectedRuns 測るはずだった回数
 * @param note 測れなかった回の知らせ
 */
export function summarizePaintRuns(
  id: string,
  runs: readonly PaintRun[],
  targetMs: number | null,
  expectedRuns: number,
  note?: string,
): PaintRecord {
  const base = summarizeRuns(
    id,
    'click',
    runs.map((run) => run.value),
    targetMs,
    expectedRuns,
    note,
  )
  const paint = summarizeRuns(
    `${id} paint`,
    'click',
    runs.map((run) => run.toPaint),
    null,
    expectedRuns,
  )
  return {
    ...base,
    paint: { runs: paint.runs, median: paint.median, max: paint.max, first: paint.first },
  }
}
