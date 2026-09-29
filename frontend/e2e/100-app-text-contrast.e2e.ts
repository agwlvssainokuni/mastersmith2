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
// ビルドした WAR で、アプリ独自の CSS の文字（make-you-chic-ui の部品の外）のコントラストを、表示の設定の 20 組ごとに実際の
// ブラウザ（axe）で確かめる（Intent 260928-quality-followup の FR2・NFR1。計画の Step 12、Q3: A・Q4: A）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない。
// - 対象は3つの状態: 見つからない画面の「ホームへ」のリンク（.page-link）、DSL の管理の画面の投入の欄の JSON Schema の
//   リンク（.dsl-link）、プリファレンスの画面の選択のまとまりの誤り（.preferences-choice-error）。対象の要素が出ていることを
//   確かめてから検査する。どの状態も、axe の違反（color-contrast を含む WCAG 2.0・2.1 の A・AA）が1件でもあれば失敗にする。
// - 組の切り替えは 080 と同じ（テーマと文字の大きさは GET /api/me/preferences の答えを見本に差し替え、プリファレンスの画面を
//   開いた時点のそろえで当てる。ブランドカラーは prepareCombo で /api/appearance を差し替える）。ログインの状態は利用者の
//   保存した設定を持ち、読み込み直すとそれに戻るため、先にプリファレンスの画面を開き、その後はページを読み込み直さずに
//   画面の中で移る（見つからない画面へは履歴の書き換えで移る）。
// - make-you-chic-ui の部品の既知の違反（310e1ec の直しの外。このリポジトリから直せない。依頼者の決定 G2: C で既知の制約として
//   受け入れた）は、その組・その状態・その要素だけを既知の違反として扱う（STATE_KNOWN_VIOLATIONS）。当たらなくなったら失敗にして
//   気づけるようにする。make-you-chic-ui が直したら外す。
//   - DSL の管理の画面のタブ（Tabs の選ばれたタブ、文字は --color-primary）。
//   - primary の Button にマウスを重ねた状態（hover の背景 --color-primary-hover に文字 --color-primary-text）。プリファレンスの
//     画面の「保存する」で代表させる。
// - 選択のまとまりの誤りは、PUT /api/me/preferences だけを 400 VALIDATION_FAILED の見本（support/preferencesFixtures.ts）に
//   差し替えて出す。サーバーの状態を変える要求（保存・投入）は送らない（ログインの要求だけは送る）。
// - 初期管理者でログインする。test.step の題・注記・添付・失敗の知らせに、メールアドレス・パスワード・トークンを入れない。
import { expect, test, type Page } from '@playwright/test'
import { loginAsAdmin, openSidebarItem } from './support/adminLogin'
import { missingRequiredRules, runAxe, splitKnownViolations } from './support/axe'
import {
  DISPLAY_COMBOS,
  expectComboApplied,
  prepareCombo,
  type DisplayCombo,
} from './support/displayCombos'
import { preferencesSample, preferencesValidationProblem } from './support/preferencesFixtures'
import { openUserMenuItem } from './support/registeredUser'

const PREFERENCES_PATH = '/api/me/preferences'
const NOT_FOUND_PATH = '/e2e-app-text-contrast-not-found'
const PREFERENCES_ITEM = 'プリファレンス'

/** 表示の設定のテーマ */
type Theme = 'light' | 'dark'

/** make-you-chic-ui の部品の、状態ごとの既知の違反（依頼者の決定 G2: C で受け入れた制約） */
interface StateKnownViolation {
  /** 当たる組（ブランドカラーごとのテーマ） */
  combos: Readonly<Record<string, readonly Theme[]>>
  /** 当たる状態 */
  state: ContrastState
  /** 違反の名前（規則と data-testid または選択子） */
  label: string
}

/**
 * make-you-chic-ui の部品の既知の違反。当たる組は 100 の実測で確かめて書いた。
 * - Tabs の選ばれたタブ（DSL の管理の画面の「投入」のタブ）: 文字は --color-primary（brand-500）で、計算で light は背景 #fafafa に
 *   対して green 3.16:1・orange 3.41:1、dark は背景 #0b0f19 に対して blue 3.71:1・purple 3.56:1（ほかの組は届く）。
 * - primary の Button の hover（プリファレンスの画面の「保存する」）: 背景 --color-primary-hover（brand-600）に文字 gray-900 で、
 *   計算で green 3.54:1・orange 3.43:1（テーマに依らない。blue・purple は白の文字で届く）。
 */
const STATE_KNOWN_VIOLATIONS: readonly StateKnownViolation[] = [
  {
    combos: { green: ['light'], orange: ['light'], blue: ['dark'], purple: ['dark'] },
    state: 'dsl-schema-link',
    label: 'color-contrast tab-1',
  },
  {
    combos: { green: ['light', 'dark'], orange: ['light', 'dark'] },
    state: 'primary-button-hover',
    label: 'color-contrast preferences-save-button',
  },
]

/** 検査の状態（アプリ独自の CSS の文字が出る場面） */
type ContrastState =
  'not-found-link' | 'dsl-schema-link' | 'preferences-choice-error' | 'primary-button-hover'

/**
 * GET /api/me/preferences を組の見本に、PUT を 400 の見本に差し替える（ほかの要求は差し替えない）。
 * 差し替えた回数を返す。
 */
async function routePreferences(
  page: Page,
  combo: DisplayCombo,
): Promise<{ gets: number; puts: number }> {
  const counter = { gets: 0, puts: 0 }
  await page.route(
    (url) => url.pathname === PREFERENCES_PATH,
    (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        counter.gets += 1
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(preferencesSample(combo.theme, combo.fontSize)),
        })
      }
      if (method === 'PUT') {
        counter.puts += 1
        return route.fulfill({
          status: 400,
          contentType: 'application/problem+json',
          body: JSON.stringify(preferencesValidationProblem('theme', 'INVALID_VALUE')),
        })
      }
      return route.fallback()
    },
  )
  return counter
}

/** ログイン以外の書き込み（GET・HEAD 以外）の要求の数を数える（差し替えた PUT も数える）。 */
function countWrites(page: Page): { count: number } {
  const writes = { count: 0 }
  page.on('request', (request) => {
    const method = request.method()
    const { pathname } = new URL(request.url())
    if (method !== 'GET' && method !== 'HEAD' && !pathname.startsWith('/api/auth/')) {
      writes.count += 1
    }
  })
  return writes
}

/** その組とその状態で当たるはずの、make-you-chic-ui の部品の既知の違反の名前 */
function expectedStateViolations(combo: DisplayCombo, state: ContrastState): string[] {
  return STATE_KNOWN_VIOLATIONS.filter(
    (known) =>
      known.state === state && (known.combos[combo.brandColor] ?? []).includes(combo.theme),
  ).map((known) => known.label)
}

/** 1つの状態を axe で検査し、記録を残して、既知の違反のほかに違反が無いことを確かめる。 */
async function checkState(page: Page, combo: DisplayCombo, state: ContrastState): Promise<void> {
  const summary = await runAxe(page)
  const split = splitKnownViolations(summary, combo.brandColor)
  const stateKnown = expectedStateViolations(combo, state)
  const known = [...split.known, ...split.unexpected.filter((label) => stateKnown.includes(label))]
  const unexpected = split.unexpected.filter((label) => !stateKnown.includes(label))
  const expectedKnown = [...split.expectedKnown, ...stateKnown]
  const record = {
    combo: combo.name,
    state,
    violations: unexpected.length,
    violationRules: [...new Set(summary.violations.map((violation) => violation.id))],
    unexpected,
    knownViolations: known,
    incomplete: summary.incomplete,
  }
  test.info().annotations.push({ type: 'axe', description: JSON.stringify(record) })
  await test.info().attach(`axe-${combo.name}-${state}.json`, {
    body: JSON.stringify(record, null, 2),
    contentType: 'application/json',
  })
  expect(missingRequiredRules(summary), `${combo.name} ${state}: 流れなかった規則`).toEqual([])
  expect(unexpected, `${combo.name} ${state}: ${unexpected.join(' / ')}`).toEqual([])
  expect(
    known.sort(),
    `${combo.name} ${state}: 既知の違反が一覧と違います（STATE_KNOWN_VIOLATIONS と README を見直す）`,
  ).toEqual(expectedKnown.sort())
}

/**
 * 押した後にマウスを要素の外へ移す（hover の見た目のままで検査しない。検査は止まっている状態の色を見る）。
 * primary の Button の hover の文字のコントラストは、別の状態（primary-button-hover）で確かめる。
 */
async function moveMouseAway(page: Page): Promise<void> {
  await page.mouse.move(0, 0)
}

/** ページを読み込み直さずに、画面の中の道（履歴）を移る（ログインの状態と当てた組を保つ）。 */
async function navigateInApp(page: Page, path: string): Promise<void> {
  await page.evaluate((to) => {
    window.history.pushState({}, '', to)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, path)
}

test.describe('100 app text contrast on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`app text ${combo.name}`, async ({ page }) => {
      const writes = countWrites(page)
      await prepareCombo(page, combo)
      const preferences = await routePreferences(page, combo)

      await test.step('log in and open the preferences screen', async () => {
        await loginAsAdmin(page)
        await openUserMenuItem(page, PREFERENCES_ITEM)
        await expect(page.getByTestId('preferences-save-button')).toBeVisible()
        await expectComboApplied(page, combo)
        expect(preferences.gets, 'プリファレンスの答えの差し替え').toBe(1)
      })

      await test.step('preferences choice error', async () => {
        await page.getByTestId('preferences-save-button').click()
        await moveMouseAway(page)
        const choiceError = page
          .getByTestId('preferences-theme')
          .locator('.preferences-choice-error')
        await expect(choiceError).toBeVisible()
        expect(preferences.puts, '保存の要求の差し替え').toBe(1)
        await expectComboApplied(page, combo)
        await checkState(page, combo, 'preferences-choice-error')
      })

      await test.step('primary button hover', async () => {
        const saveButton = page.getByTestId('preferences-save-button')
        const background = () =>
          saveButton.evaluate((element) => getComputedStyle(element).backgroundColor)
        const restingBackground = await background()
        await saveButton.hover()
        // hover の背景（--color-primary-hover）に変わったことを確かめてから検査する（遷移は無い）
        await expect.poll(background, 'hover の背景').not.toBe(restingBackground)
        await expectComboApplied(page, combo)
        await checkState(page, combo, 'primary-button-hover')
        await moveMouseAway(page)
        await expect.poll(background, 'hover を外した背景').toBe(restingBackground)
      })

      await test.step('dsl submit schema link', async () => {
        await openSidebarItem(page, 'DSL')
        await expect(page.getByTestId('dsl-admin-page')).toBeVisible()
        await page.getByRole('tab', { name: '投入' }).click()
        await moveMouseAway(page)
        await expect(page.getByTestId('dsl-submit-form')).toBeVisible()
        const schemaLink = page.getByTestId('dsl-submit-schema-link')
        await expect(schemaLink).toBeVisible()
        await expect(schemaLink).toHaveClass(/\bdsl-link\b/)
        await expectComboApplied(page, combo)
        await checkState(page, combo, 'dsl-schema-link')
      })

      await test.step('not found screen link', async () => {
        await navigateInApp(page, NOT_FOUND_PATH)
        await expect(page.getByTestId('not-found-page')).toBeVisible()
        const homeLink = page.getByTestId('not-found-home-link')
        await expect(homeLink).toBeVisible()
        await expect(homeLink).toHaveClass(/\bpage-link\b/)
        await expectComboApplied(page, combo)
        await checkState(page, combo, 'not-found-link')
      })

      await page.waitForLoadState('networkidle')
      expect(preferences.puts, '保存の要求の差し替え').toBe(1)
      // 書き込みの要求は、差し替えた保存の1件だけ（サーバーへは届かない）
      expect(writes.count, 'ログイン以外の書き込みの要求').toBe(1)
    })
  }
})
