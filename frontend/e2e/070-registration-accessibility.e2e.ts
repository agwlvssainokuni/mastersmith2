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
// ビルドした WAR で、登録の完了の画面（U6）の表示の設定の組ごとのアクセシビリティと横のはみ出しを確かめる
// （Intent 260925-user-management の U6。NFR7.3・NFR7.5・NFR9.5、NFR 設計の security-design.md 5節）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない（NFR9.11）。
// - 組ごとに1つのテスト（20 組）。ログインしない。登録の完了の画面はログインの前の画面のため、組のテーマと文字の大きさは
//   U4 の切り替え方（初めのスクリプトで U4 の鍵を置く、support/displayCombos.ts の prepareCombo）で当てる。
// - 状態は2つ: ready（確かめの API POST /api/registration/verify だけを見本 VERIFY_SAMPLE の 200 に差し替えて出すフォーム）と、
//   unavailable（差し替えずにフラグメントの無い /register を開いたときの使えないリンクの表示）。
//   完了の要求は送らない。サーバーの状態（内部DB・監査・招待）を変えない。
// - green・orange の組の primary の Button のコントラスト不足は、U4 の既知の制約として、状態ごとの名前の一覧
//   （support/axe.ts の REGISTRATION_KNOWN_VIOLATIONS）で扱う。ほかの違反は失敗にする。
// - CSP の違反・画面の問題は各組で失敗にする。
// - test.step の題・注記・添付に、見本のトークンと見本のメールアドレスの値を入れない。
// - 既存の 010〜060 は変えない。
import { expect, test, type Page } from '@playwright/test'
import {
  missingRequiredRules,
  REGISTRATION_KNOWN_VIOLATIONS,
  runAxe,
  splitKnownViolations,
  type RegistrationAxeState,
} from './support/axe'
import {
  DISPLAY_COMBOS,
  expectComboApplied,
  prepareCombo,
  type DisplayCombo,
} from './support/displayCombos'
import { describeOverflow, measureHorizontalOverflow } from './support/overflow'
import { watchPage } from './support/pageProblems'
import { A11Y_SAMPLE_TOKEN, VERIFY_SAMPLE } from './support/registrationFixtures'

const VERIFY_PATH = '/api/registration/verify'
const COMPLETE_PATH = '/api/registration/complete'

/** 登録の完了の API への要求を数える（差し替えた要求も数える）。 */
function countRegistrationRequests(page: Page): { verify: number; complete: number } {
  const counts = { verify: 0, complete: 0 }
  page.on('request', (request) => {
    const { pathname } = new URL(request.url())
    if (pathname === VERIFY_PATH) {
      counts.verify += 1
    } else if (pathname === COMPLETE_PATH) {
      counts.complete += 1
    }
  })
  return counts
}

/** 確かめの API の POST だけを見本の 200 に差し替える（このページの中だけで効く）。 */
async function routeVerify(page: Page): Promise<void> {
  await page.route(
    (url) => url.pathname === VERIFY_PATH,
    (route) => {
      if (route.request().method() !== 'POST') {
        return route.fallback()
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(VERIFY_SAMPLE),
      })
    },
  )
}

/** 1つの状態の検査の記録を残し、合否を確かめる。 */
async function checkState(
  page: Page,
  combo: DisplayCombo,
  state: RegistrationAxeState,
): Promise<void> {
  const summary = await runAxe(page)
  const { known, unexpected, expectedKnown } = splitKnownViolations(
    summary,
    combo.brandColor,
    REGISTRATION_KNOWN_VIOLATIONS[state],
  )
  const overflow = await measureHorizontalOverflow(page)
  const record = {
    combo: combo.name,
    state,
    violations: unexpected.length,
    violationRules: [...new Set(summary.violations.map((violation) => violation.id))],
    unexpected,
    knownViolations: known,
    incomplete: summary.incomplete,
    overflow,
  }
  test.info().annotations.push({ type: 'axe', description: JSON.stringify(record) })
  await test.info().attach(`axe-${combo.name}-${state}.json`, {
    body: JSON.stringify(record, null, 2),
    contentType: 'application/json',
  })
  expect(missingRequiredRules(summary), `${combo.name} ${state}: 流れなかった規則`).toEqual([])
  expect(unexpected, `${combo.name} ${state}: ${unexpected.join(' / ')}`).toEqual([])
  expect(
    known,
    `${combo.name} ${state}: 既知の違反が一覧と違います（make-you-chic-ui が直したなら REGISTRATION_KNOWN_VIOLATIONS と README を見直す）`,
  ).toEqual(expectedKnown)
  expect(overflow.overflows, describeOverflow(`${combo.name} ${state}`, overflow)).toBe(false)
}

test.describe('070 registration screen accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`registration ${combo.name}`, async ({ page }) => {
      const watch = await watchPage(page)
      const counts = countRegistrationRequests(page)
      await prepareCombo(page, combo)
      await routeVerify(page)

      await test.step('form after the link is verified', async () => {
        await page.goto(`/register#token=${A11Y_SAMPLE_TOKEN}`)
        await expect(page.getByTestId('registration-email-input')).toBeVisible()
        await expect(page.getByTestId('registration-form')).toBeVisible()
        await expectComboApplied(page, combo)
        await checkState(page, combo, 'ready')
      })

      await test.step('unavailable link without the fragment', async () => {
        await page.unroute((url) => url.pathname === VERIFY_PATH)
        await page.goto('/register')
        await expect(page.getByTestId('registration-unavailable')).toBeVisible()
        await expectComboApplied(page, combo)
        await checkState(page, combo, 'unavailable')
      })

      await page.waitForLoadState('networkidle')
      expect(counts.verify, '確かめの要求（ready の1回だけ）').toBe(1)
      expect(counts.complete, '完了の要求').toBe(0)
      expect(watch.cspViolations, `${combo.name}: CSP の違反`).toEqual([])
      expect(watch.problems, `${combo.name}: 画面の問題`).toEqual([])
    })
  }
})
