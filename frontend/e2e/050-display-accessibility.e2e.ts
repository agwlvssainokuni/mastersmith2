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
// - 既知の違反は、名前と対象を指定して扱う（support/axe.ts の KNOWN_VIOLATIONS）。make-you-chic-ui の固定先を 310e1ec に上げて
//   （Intent 260928-quality-followup の FR1）、green・orange の組の primary の Button のコントラスト不足が当たらなくなったため、
//   一覧は空で、どの違反も失敗にする。一覧に載せた既知の違反が消えたときも気づけるよう、一覧どおりにあることを確かめる。
// - Intent 261102-timezone-ui-polish の U6 で、既存の 2 つのテストに書き足した（新しい test() は足さない）: ログイン画面の飾りのアイコン
//   （見える・読み上げに出ない）と、/favicon.ico・/favicon.svg を未認証で画像として配ること・<head> の favicon の <link>。
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { hasSampleShape } from './support/appearanceFixture'
import { missingRequiredRules, runAxe, splitKnownViolations } from './support/axe'
import { DISPLAY_COMBOS, expectComboApplied, prepareCombo } from './support/displayCombos'
import { describeOverflow, measureHorizontalOverflow } from './support/overflow'
import { watchPage } from './support/pageProblems'

const TARGET_MS = 2_000
const RUNS = 5

/**
 * 組み上がった WAR が、ログインなしで favicon の素材を画像として配ること（Intent 261102-timezone-ui-polish の U6、SD4.2 の L4）。
 * 素材が欠けると /favicon.ico は 404 でなく index.html（HTML）を 200 で返すため、種類と本文の先頭まで確かめる。
 * 新しい流れ・新しい test() は足さず、この既存のテストの 1 回目に書き足す（E2E の本数に数えない）。
 */
async function expectFaviconAssets(request: APIRequestContext, page: Page): Promise<void> {
  for (const [path, isSvg] of [
    ['/favicon.ico', false],
    ['/favicon.svg', true],
  ] as const) {
    const response = await request.get(path)
    const type = response.headers()['content-type'] ?? ''
    const body = await response.body()
    expect(response.status(), `${path} の状態`).toBe(200)
    expect(type.startsWith('image/'), `${path} の種類 ${type}`).toBe(true)
    if (isSvg) {
      expect(type, `${path} の種類`).toMatch(/^image\/svg\+xml/)
    } else {
      // ICO の先頭 4 バイト（予約 0・種類 1）
      expect([...body.subarray(0, 4)], `${path} の先頭 4 バイト`).toEqual([0, 0, 1, 0])
    }
    expect(body.subarray(0, 15).toString('latin1').toLowerCase(), `${path} の本文`).not.toContain(
      '<!doctype html',
    )
    expect(response.headers()['x-content-type-options'], `${path} の nosniff`).toBe('nosniff')
  }
  const hrefs = await page
    .locator('head link[rel="icon"]')
    .evaluateAll((links) => links.map((link) => link.getAttribute('href') ?? ''))
  expect(hrefs, 'favicon の <link>').toHaveLength(2)
  for (const href of hrefs) {
    expect(href, `favicon の href ${href}`).toMatch(/^\/[^/]/)
  }
}

test.describe('050 display settings accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`login screen ${combo.name}`, async ({ page }) => {
      await prepareCombo(page, combo)

      await page.goto('/login')
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('ログイン')
      await expect(page.getByTestId('login-language-switch')).toBeVisible()
      await expectComboApplied(page, combo)
      // 飾りのアイコン（U6）。見えて、読み上げには出ない（axe とはみ出しの検査は、アイコンを含めた画面で下のとおり行う）
      const appIcon = page.getByTestId('login-layout-app-icon')
      await expect(appIcon).toBeVisible()
      await expect(appIcon.locator('svg')).toHaveAttribute('aria-hidden', 'true')
      await expect(page.getByRole('img')).toHaveCount(0)

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
        `${combo.name}: 既知の違反が一覧と違います（KNOWN_VIOLATIONS と docs/verification.md を見直す）`,
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
          await expectFaviconAssets(context.request, page)
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
