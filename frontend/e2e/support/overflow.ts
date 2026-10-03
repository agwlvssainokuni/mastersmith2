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
// 横のはみ出しの判定（U4 の NFR 設計 logical-components.md 5.4）。文書の横の大きさが表示の幅を超えないこと。
// 中で横に動く領域（表など）は包む要素の中だけで動くため、文書の横の大きさに出ない。
import type { Locator, Page } from '@playwright/test'

/** 横の大きさの測定値 */
export interface HorizontalOverflow {
  scrollWidth: number
  innerWidth: number
  overflows: boolean
}

/** 文書の横の大きさと表示の幅を測る。 */
export async function measureHorizontalOverflow(page: Page): Promise<HorizontalOverflow> {
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }))
  return { scrollWidth, innerWidth, overflows: scrollWidth > innerWidth }
}

/** はみ出したときの失敗の説明 */
export function describeOverflow(comboName: string, measured: HorizontalOverflow): string {
  return `${comboName}: 横にはみ出しています（scrollWidth ${measured.scrollWidth}px > innerWidth ${measured.innerWidth}px）`
}

/** 画面に固定で置く部品（開いたメニューなど）の矩形と表示の大きさ */
export interface ViewportContainment {
  left: number
  right: number
  bottom: number
  innerWidth: number
  innerHeight: number
  contained: boolean
}

/**
 * 渡した要素（開いたメニューなど）の矩形が表示の中に収まるかを測る（FR2.2）。
 * 位置を固定で置く部品は文書の横の大きさ（scrollWidth）に出ないため、measureHorizontalOverflow では拾えない。
 * 左端が 0 以上、右端が innerWidth 以下、下端が innerHeight 以下のときに収まるとみなす。
 */
export async function measureViewportContainment(locator: Locator): Promise<ViewportContainment> {
  return locator.evaluate((element) => {
    const rect = element.getBoundingClientRect()
    const innerWidth = window.innerWidth
    const innerHeight = window.innerHeight
    return {
      left: rect.left,
      right: rect.right,
      bottom: rect.bottom,
      innerWidth,
      innerHeight,
      contained: rect.left >= 0 && rect.right <= innerWidth && rect.bottom <= innerHeight,
    }
  })
}

/** 収まらなかったときの失敗の説明 */
export function describeContainment(name: string, measured: ViewportContainment): string {
  return `${name}: 表示の外へはみ出しています（left ${measured.left}px・right ${measured.right}px / innerWidth ${measured.innerWidth}px・bottom ${measured.bottom}px / innerHeight ${measured.innerHeight}px）`
}
