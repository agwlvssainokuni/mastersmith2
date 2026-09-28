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
// ビルドした WAR で、表示の設定の組ごとのアクセシビリティと、最初の画面が出るまでの時間を確かめる
// （Intent 260925-user-management の U4。NFR6.1・NFR6.4・NFR7.3・NFR7.5・NFR9.4・NFR9.11）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない。
// - どのテストもログインしない（内部DB・監査の記録・ロックの回数を変えない）。既存の 010〜040 は変えない。
// - 組ごとに1つのテストで、ページは Playwright がテストごとに作る新しいコンテキストのもの（状態を持ち越さない）。
// - 最初の画面の時間は記録だけで失敗させない（統合の関門にしない）。CSP の違反と、sans のときの Noto Serif JP の読み込みは
//   不安定ではないため失敗させる。
// - green・orange の組の primary の Button のコントラスト不足は、依頼者が受け入れた既知の違反として、名前と対象を指定して扱う
//   （support/axe.ts の KNOWN_VIOLATIONS）。既知の違反が消えたときも気づけるよう、あることを確かめる。
import { expect, test } from '@playwright/test'
import { hasSampleShape } from './support/appearanceFixture'
import { missingRequiredRules, runAxe, splitKnownViolations } from './support/axe'
import { DISPLAY_COMBOS, expectComboApplied, prepareCombo } from './support/displayCombos'
import { describeOverflow, measureHorizontalOverflow } from './support/overflow'
import { watchPage } from './support/pageProblems'

const TARGET_MS = 2_000
const RUNS = 5

test.describe('050 display settings accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`login screen ${combo.name}`, async ({ page }) => {
      await prepareCombo(page, combo)

      await page.goto('/login')
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('ログイン')
      await expect(page.getByTestId('login-language-switch')).toBeVisible()
      await expectComboApplied(page, combo)

      const summary = await runAxe(page)
      const { known, unexpected, expectedKnown } = splitKnownViolations(summary, combo.brandColor)
      const overflow = await measureHorizontalOverflow(page)
      const record = {
        combo: combo.name,
        violations: unexpected.length,
        violationRules: [...new Set(summary.violations.map((violation) => violation.id))],
        unexpected,
        knownViolations: known,
        incomplete: summary.incomplete,
        overflow,
      }
      test.info().annotations.push({ type: 'axe', description: JSON.stringify(record) })
      await test.info().attach(`axe-${combo.name}.json`, {
        body: JSON.stringify(record, null, 2),
        contentType: 'application/json',
      })

      expect(missingRequiredRules(summary), `${combo.name}: 流れなかった規則`).toEqual([])
      expect(unexpected, `${combo.name}: ${unexpected.join(' / ')}`).toEqual([])
      expect(
        known,
        `${combo.name}: 既知の違反が一覧と違います（make-you-chic-ui が直したなら KNOWN_VIOLATIONS と README を見直す）`,
      ).toEqual(expectedKnown)
      expect(overflow.overflows, describeOverflow(combo.name, overflow)).toBe(false)
    })
  }

  test('first screen time with the real appearance, without CSP violations or serif fonts', async ({
    browser,
  }) => {
    const { baseURL, viewport } = test.info().project.use
    const timings: number[] = []
    for (let run = 1; run <= RUNS; run += 1) {
      const context = await browser.newContext({ baseURL, viewport, locale: 'ja-JP' })
      try {
        const page = await context.newPage()
        const watch = await watchPage(page)
        const appearanceResponse = page.waitForResponse(
          (response) => new URL(response.url()).pathname === '/api/appearance',
        )

        const started = Date.now()
        await page.goto('/', { waitUntil: 'commit' })
        await expect(page.getByRole('heading', { level: 1, name: 'ログイン' })).toBeVisible()
        timings.push(Date.now() - started)

        const response = await appearanceResponse
        expect(response.status()).toBe(200)
        if (run === 1) {
          const body: unknown = await response.json()
          expect(hasSampleShape(body), `本物の応答 ${JSON.stringify(body)} と見本の形`).toBe(true)
        }
        await page.waitForLoadState('networkidle')
        expect(watch.cspViolations, `run ${run}: CSP の違反`).toEqual([])
        expect(watch.problems, `run ${run}: 画面の問題`).toEqual([])
        expect(
          watch.requests.filter((url) => url.includes('noto-serif-jp')),
          `run ${run}: sans のときの Noto Serif JP の要求`,
        ).toEqual([])
      } finally {
        await context.close()
      }
    }
    const record = {
      runs: timings,
      withinTarget: timings.filter((ms) => ms <= TARGET_MS).length,
      targetMs: TARGET_MS,
    }
    test.info().annotations.push({ type: 'first-screen-ms', description: JSON.stringify(record) })
    await test.info().attach('first-screen.json', {
      body: JSON.stringify(record, null, 2),
      contentType: 'application/json',
    })
  })
})
