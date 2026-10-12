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
// 140 の画面の時間の測りの部品（Intent 261004-role-menu の U6、performance-design.md 4.2〜4.4・9節の R-01・R-02）。
// - 時計は画面の中の performance.now() の1つだけ。テストの側の時計と組にしない。聞き手と見張りは page.evaluate で入れ、
//   アプリのコードには入れない。
// - 測り始め: 操作から測る項目は click・change の捕捉の段で、event.target.closest(選択子) が当たったときだけ記録する
//   （pointerdown は使わない）。応答から測る項目は PerformanceObserver の resource の項目（initiatorType が fetch で、
//   道が当たる）の responseEnd。どちらも最初の1回だけを残す（app-frame-ui の guard にそろえる）。
// - 測り終わり: MutationObserver で終わりの条件（項目ごとに書く、すべての条件がそろう）を見つけ、その後の次の
//   requestAnimationFrame の時刻。条件は data-testid と、任意で含む文で書く。
// - 判定は 5 回の中央値（小さい順の3番目）。最大と1回目も記録する。値は名前と時間だけ。
import { expect, type Page } from '@playwright/test'

/** 測り始めの種類 */
export type MeasureStart =
  { kind: 'click' | 'change'; selector: string } | { kind: 'responseEnd'; path: string }

/** 終わりの条件の1つ（data-testid が一致する要素のうち、text を含むものがある） */
export interface EndCondition {
  testId: string
  text?: string
}

/** 測りを仕込む（毎回、画面を読み込み直した後・操作の前に呼ぶ）。 */
export async function armMeasure(
  page: Page,
  start: MeasureStart,
  conditions: readonly EndCondition[],
): Promise<void> {
  await page.evaluate(
    ({ start: begin, conditions: ends }) => {
      type Mark = { start?: number; end?: number }
      const w = window as unknown as { __u6Mark: Mark }
      const mark: Mark = {}
      w.__u6Mark = mark
      if (begin.kind === 'responseEnd') {
        const observer = new PerformanceObserver((list) => {
          for (const entry of list.getEntries() as PerformanceResourceTiming[]) {
            if (
              mark.start === undefined &&
              entry.initiatorType === 'fetch' &&
              new URL(entry.name).pathname === begin.path
            ) {
              mark.start = entry.responseEnd
              observer.disconnect()
            }
          }
        })
        observer.observe({ type: 'resource' })
      } else {
        const listener = (event: Event) => {
          const target = event.target as Element | null
          if (mark.start === undefined && target?.closest(begin.selector)) {
            mark.start = performance.now()
            document.removeEventListener(begin.kind, listener, true)
          }
        }
        document.addEventListener(begin.kind, listener, true)
      }
      const satisfied = () =>
        ends.every((condition) =>
          [...document.querySelectorAll(`[data-testid]`)].some(
            (element) =>
              element.getAttribute('data-testid') === condition.testId &&
              (condition.text === undefined ||
                (element.textContent ?? '').includes(condition.text)),
          ),
        )
      const watcher = new MutationObserver(() => {
        if (mark.end === undefined && satisfied()) {
          watcher.disconnect()
          requestAnimationFrame(() => {
            mark.end = performance.now()
          })
        }
      })
      watcher.observe(document.body, {
        subtree: true,
        childList: true,
        characterData: true,
        attributes: true,
      })
    },
    { start, conditions },
  )
}

/** 記録がそろうのを待ち、終わり − 始め（ミリ秒）を返す。 */
export async function readMeasure(page: Page, timeout = 20_000): Promise<number> {
  await expect
    .poll(
      () =>
        page.evaluate(() => {
          const mark = (window as unknown as { __u6Mark?: { start?: number; end?: number } })
            .__u6Mark
          return mark?.start !== undefined && mark.end !== undefined
        }),
      { timeout },
    )
    .toBe(true)
  return page.evaluate(() => {
    const mark = (window as unknown as { __u6Mark: { start: number; end: number } }).__u6Mark
    return mark.end - mark.start
  })
}

/** ページの JavaScript のヒープの大きさ（使えるブラウザだけ。使えなければ null） */
export async function readHeapBytes(page: Page): Promise<number | null> {
  return page.evaluate(() => {
    const memory = (performance as unknown as { memory?: { usedJSHeapSize: number } }).memory
    return memory === undefined ? null : memory.usedJSHeapSize
  })
}

/** 項目の記録 */
export interface MeasureRecord {
  id: string
  start: MeasureStart['kind']
  clock: 'performance.now() in the page'
  targetMs: number | null
  runs: number[]
  median: number | null
  max: number | null
  first: number | null
  verdict: 'Met' | 'Not Met' | 'Unverified' | 'Recorded'
  note?: string
}

/** 5 回の値から記録を作る（中央値で判定。目標が無い項目は記録だけ）。 */
export function summarizeRuns(
  id: string,
  start: MeasureStart['kind'],
  runs: readonly number[],
  targetMs: number | null,
  expectedRuns: number,
  note?: string,
): MeasureRecord {
  const sorted = [...runs].sort((a, b) => a - b)
  const median = sorted.length === 0 ? null : (sorted[Math.floor((sorted.length - 1) / 2)] ?? null)
  const verdict =
    runs.length < expectedRuns
      ? 'Unverified'
      : targetMs === null
        ? 'Recorded'
        : median !== null && median <= targetMs
          ? 'Met'
          : 'Not Met'
  return {
    id,
    start,
    clock: 'performance.now() in the page',
    targetMs,
    runs: [...runs],
    median,
    max: sorted.length === 0 ? null : (sorted.at(-1) ?? null),
    first: runs[0] ?? null,
    verdict,
    note,
  }
}
