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
// 実際のブラウザの検査の組（U4 の NFR 設計 logical-components.md 5.2・5.3、NFR7.3・NFR7.5）。
// (a) テーマ × 文字の大きさ（blue、既定の幅）、(b) ブランドカラー × テーマ（md、既定の幅）、(c) (a) と同じ6組を
// 375px × 812px で、合わせて 20 組。B5 の画面も同じ組と切り替え方を使う。
// 切り替えは本物の道を通す: テーマ・文字の大きさは読み込みの前に U4 の鍵へ置き（写しの鍵の書き直し）、
// ブランドカラーは GET /api/appearance の答えを見本の形で差し替える。<html> の属性を直接書き換えない。
import { expect, type Page } from '@playwright/test'
import type { BrandColor, FontSize } from '../../src/app/display-settings/displaySettingsTypes'
import { appearanceWith } from './appearanceFixture'

/** 検査の組 */
export interface DisplayCombo {
  /** 組の名前（テストの題と報告に使う） */
  name: string
  theme: 'light' | 'dark'
  fontSize: FontSize
  brandColor: BrandColor
  /** 表示の幅（無ければ既定の Desktop Chrome の幅） */
  viewport?: { width: number; height: number }
}

const THEMES = ['light', 'dark'] as const
const SIZES = ['sm', 'md', 'lg'] as const
const BRANDS = ['blue', 'green', 'purple', 'orange'] as const
const NARROW = { width: 375, height: 812 }

/** 20 組の一覧 */
export const DISPLAY_COMBOS: readonly DisplayCombo[] = [
  ...THEMES.flatMap((theme) =>
    SIZES.map((fontSize) => ({
      name: `(a) ${theme} ${fontSize} blue`,
      theme,
      fontSize,
      brandColor: 'blue' as const,
    })),
  ),
  ...BRANDS.flatMap((brandColor) =>
    THEMES.map((theme) => ({
      name: `(b) ${brandColor} ${theme} md`,
      theme,
      fontSize: 'md' as const,
      brandColor,
    })),
  ),
  ...THEMES.flatMap((theme) =>
    SIZES.map((fontSize) => ({
      name: `(c) ${theme} ${fontSize} blue 375px`,
      theme,
      fontSize,
      brandColor: 'blue' as const,
      viewport: NARROW,
    })),
  ),
]

/**
 * 組の表示の設定をページに仕込む（ページは Playwright がテストごとに作る新しいコンテキストのもの）。
 * 表示の幅、読み込みの前の U4 の鍵、見た目の設定の答えの差し替え（このページの中だけで効く）を置く。
 */
export async function prepareCombo(page: Page, combo: DisplayCombo): Promise<void> {
  if (combo.viewport) {
    await page.setViewportSize(combo.viewport)
  }
  await page.addInitScript(
    ({ key, value }) => {
      window.localStorage.setItem(key, value)
    },
    {
      key: 'mastersmith.display-settings',
      value: JSON.stringify({ theme: combo.theme, fontSize: combo.fontSize }),
    },
  )
  await page.route('**/api/appearance', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(appearanceWith(combo.brandColor)),
    }),
  )
}

/** 組の切り替えが効いたことを <html> の属性で確かめる（効いていなければ検査の結果を使わずに失敗させる）。 */
export async function expectComboApplied(page: Page, combo: DisplayCombo): Promise<void> {
  const html = page.locator('html')
  await expect(html).toHaveAttribute('data-brand', combo.brandColor)
  await expect(html).toHaveAttribute('data-font-size', combo.fontSize)
  await expect
    .poll(() => html.getAttribute('data-theme'))
    .toBe(combo.theme === 'light' ? null : combo.theme)
}
