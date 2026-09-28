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
import type { Page } from '@playwright/test'

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
