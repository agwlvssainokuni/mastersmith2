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
// 160 の画面の時間の測りの部品（Intent 261004-role-menu の U7、performance-design.md 4.2〜4.4、NFR 設計の承認の場の R-03）。
// - 時計は画面の中の performance.now() の1つだけ。聞き手と見張りは page.evaluate で入れ、アプリのコードには入れない。
// - 測り始め: document の click の捕捉の段で、event.target.closest(選択子) が当たった最初の1回。
// - 測り終わり: MutationObserver で、決まった判定の表 __u7Done の名前で選んだ判定が真になった後の、次の
//   requestAnimationFrame の時刻。判定は見張りの中に用意した3つ（workRoleSwitched・groupOpened・placeholderShown）だけで、
//   テストは関数の本体ではなく名前で選ぶ。
// - 判定と記録は u6Measure.ts の summarizeRuns（5 回の中央値・最大・1回目）を使う。値は名前と時間だけ。
import { expect, type Page } from '@playwright/test'

/** 判定の名前 */
export type U7DoneName = 'workRoleSwitched' | 'groupOpened' | 'placeholderShown'

/** 判定に使う値（見本の名前と id だけ） */
export interface U7DoneParams {
  /** 切り替えの後のロールの名前（Toast の文に含まれる） */
  switchedRoleName: string
  /** 切り替えの後の木にだけある印の項目の data-testid（sidebar-nav-biz:<id>） */
  markerTestId: string
  /** 開くまとまりの開閉のボタンの data-testid（sidebar-toggle-biz:<id>） */
  groupToggleTestId: string
  /** 置き場の見出しに出る表示名 */
  placeholderHeading: string
}

/** 判定の表を画面に入れる（画面を読み込み直した後、armU7Measure より前に呼ぶ）。 */
export async function installU7Done(page: Page, params: U7DoneParams): Promise<void> {
  await page.evaluate((values) => {
    const byTestId = (id: string): Element | null =>
      [...document.querySelectorAll('[data-testid]')].find(
        (element) => element.getAttribute('data-testid') === id,
      ) ?? null
    const done: Record<string, () => boolean> = {
      workRoleSwitched: () =>
        (byTestId('toast-container')?.textContent ?? '').includes(values.switchedRoleName) &&
        byTestId(values.markerTestId) !== null,
      groupOpened: () => {
        const toggle = byTestId(values.groupToggleTestId)
        const listId = toggle?.getAttribute('aria-controls')
        const list = listId ? document.getElementById(listId) : null
        return list !== null && list.querySelector('li') !== null
      },
      placeholderShown: () =>
        (byTestId('tables-heading')?.textContent ?? '').trim() === values.placeholderHeading,
    }
    ;(window as unknown as { __u7Done: Record<string, () => boolean> }).__u7Done = done
  }, params)
}

/** 測りを仕込む（測る操作の直前に呼ぶ）。 */
export async function armU7Measure(
  page: Page,
  startSelector: string,
  done: U7DoneName,
): Promise<void> {
  await page.evaluate(
    ({ selector, name }) => {
      type Mark = { start?: number; end?: number }
      const w = window as unknown as { __u7Mark: Mark; __u7Done: Record<string, () => boolean> }
      const mark: Mark = {}
      w.__u7Mark = mark
      const listener = (event: Event) => {
        const target = event.target as Element | null
        if (mark.start === undefined && target?.closest(selector)) {
          mark.start = performance.now()
          document.removeEventListener('click', listener, true)
        }
      }
      document.addEventListener('click', listener, true)
      const observer = new MutationObserver(() => {
        if (mark.start === undefined || mark.end !== undefined || !w.__u7Done[name]?.()) {
          return
        }
        observer.disconnect()
        requestAnimationFrame(() => {
          mark.end = performance.now()
        })
      })
      observer.observe(document.body, {
        subtree: true,
        childList: true,
        characterData: true,
        attributes: true,
      })
    },
    { selector: startSelector, name: done },
  )
}

/** 記録がそろうのを待ち、終わり − 始め（ミリ秒）を返す。 */
export async function readU7Measure(page: Page, timeout = 20_000): Promise<number> {
  await expect
    .poll(
      () =>
        page.evaluate(() => {
          const mark = (window as unknown as { __u7Mark?: { start?: number; end?: number } })
            .__u7Mark
          return mark?.start !== undefined && mark.end !== undefined
        }),
      { timeout },
    )
    .toBe(true)
  return page.evaluate(() => {
    const mark = (window as unknown as { __u7Mark: { start: number; end: number } }).__u7Mark
    return mark.end - mark.start
  })
}
