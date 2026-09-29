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
// ビルドした WAR で、プリファレンスとパスワードの変更の画面（U7）の表示の設定の組ごとのアクセシビリティと、画面を開く・保存・
// パスワードの変更の時間を確かめる（Intent 260925-user-management の U7。NFR6.1〜NFR6.3・NFR7.3・NFR7.4・NFR9.8・NFR9.9）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない。
// - 組ごとに1つのテスト（20 組）。初期管理者でログインし、ユーザーメニューの「プリファレンス」「パスワードの変更」で移る。
//   組のテーマと文字の大きさは、GET /api/me/preferences の答えだけを見本（support/preferencesFixtures.ts）に差し替え、画面の
//   開いた時点のそろえ（D2、本物の applyUserPreferences）で当てる（U7 の計画の9節の決定 1。loginPreferences.ts は使わない）。
//   ブランドカラーは U4 の prepareCombo で /api/appearance を差し替える。PUT・POST は差し替えず、送らない（送れば失敗）。
// - 状態は、プリファレンスの最初・画面の確かめの誤り（氏名を空にして保存）、パスワードの変更の最初・画面の確かめの誤り
//   （3つを空のまま変更）の4つ（NFR 設計の Q3 A）。
// - 既知の違反は、状態ごとの名前の一覧（support/axe.ts の PREFERENCES_KNOWN_VIOLATIONS）で扱う。make-you-chic-ui の固定先を
//   310e1ec に上げて（Intent 260928-quality-followup の FR1）、primary の Button・トップバーのアバター（2語の氏名の頭文字）・
//   dark の組の FormField の誤りの文字のコントラスト不足が当たらなくなったため、一覧は空で、どの違反も失敗にする。
// - 測りのテスト1件は何も差し替えず、招待から作った利用者（support/registeredUser.ts）で、開く（2画面×5回）・保存（5回）・
//   パスワードの変更（5回）の時間を記録する。時間では失敗させない。本物の GET /api/me/preferences の応答と見本の形の違い、
//   CSP の違反・画面の問題は失敗にする。招待を使えない・Mailpit に届かないときは理由を注記に残して飛ばす（念のための道）。
// - test.step の題・注記・添付・失敗の知らせに、初期管理者と作った利用者のメールアドレス・パスワード・アクセストークン・
//   招待のトークンとリンクを入れない。
// - 既存の 010〜070・090 は変えない。測りが置く招待・利用者・監査・Mailpit の1通に、後の 090 は頼らない。
import { expect, test, type Page } from '@playwright/test'
import { loginAsAdmin } from './support/adminLogin'
import {
  missingRequiredRules,
  PREFERENCES_KNOWN_VIOLATIONS,
  runAxe,
  splitKnownViolations,
  type PreferencesAxeState,
} from './support/axe'
import {
  DISPLAY_COMBOS,
  expectComboApplied,
  prepareCombo,
  type DisplayCombo,
} from './support/displayCombos'
import { newRunTag, requestAdminAccessToken } from './support/invitationSeed'
import { describeOverflow, measureHorizontalOverflow } from './support/overflow'
import { watchPage } from './support/pageProblems'
import { hasPreferencesShape, preferencesSample } from './support/preferencesFixtures'
import {
  createRegisteredUser,
  loginWithForm,
  newRunPassword,
  openUserMenuItem,
  registrationPrerequisites,
} from './support/registeredUser'
import type { Preferences } from '../src/features/preferences/preferencesApi'

const OPEN_TARGET_MS = 2_000
const SAVE_TARGET_MS = 1_500
const PASSWORD_TARGET_MS = 2_500
const RUNS = 5
const PREFERENCES_ITEM = 'プリファレンス'
const PASSWORD_ITEM = 'パスワードの変更'
const PREFERENCES_PATH = '/api/me/preferences'

/** GET /api/me/preferences だけを見本の答えに差し替える（PUT は差し替えない）。差し替えた回数を返す。 */
async function routePreferences(page: Page, body: Preferences): Promise<{ served: number }> {
  const counter = { served: 0 }
  await page.route(
    (url) => url.pathname === PREFERENCES_PATH,
    (route) => {
      if (route.request().method() !== 'GET') {
        return route.fallback()
      }
      counter.served += 1
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    },
  )
  return counter
}

/** /api/me/ への書き込み（GET 以外）の要求を数える。 */
function countMeWrites(page: Page): { count: number } {
  const writes = { count: 0 }
  page.on('request', (request) => {
    if (request.method() !== 'GET' && new URL(request.url()).pathname.startsWith('/api/me/')) {
      writes.count += 1
    }
  })
  return writes
}

/** 1つの状態の検査の記録を残し、合否を確かめる。 */
async function checkState(
  page: Page,
  combo: DisplayCombo,
  state: PreferencesAxeState,
): Promise<void> {
  const summary = await runAxe(page)
  const { known, unexpected, expectedKnown } = splitKnownViolations(
    summary,
    combo.brandColor,
    PREFERENCES_KNOWN_VIOLATIONS[state],
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
    `${combo.name} ${state}: 既知の違反が一覧と違います（PREFERENCES_KNOWN_VIOLATIONS と README を見直す）`,
  ).toEqual(expectedKnown)
  expect(overflow.overflows, describeOverflow(`${combo.name} ${state}`, overflow)).toBe(false)
}

/** 場面ごとの記録（ミリ秒と目標以内の回数） */
function summarize(runs: number[], targetMs: number) {
  return { runs, withinTarget: runs.filter((ms) => ms <= targetMs).length, targetMs }
}

test.describe('080 preferences screens accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`preferences ${combo.name}`, async ({ page }) => {
      const watch = await watchPage(page)
      const writes = countMeWrites(page)
      await prepareCombo(page, combo)
      const preferences = await routePreferences(
        page,
        preferencesSample(combo.theme, combo.fontSize),
      )
      const nameInput = page.getByTestId('preferences-display-name-input')
      const saveButton = page.getByTestId('preferences-save-button')
      const submitButton = page.getByTestId('preferences-password-submit-button')

      await test.step('open the preferences screen from the user menu', async () => {
        await loginAsAdmin(page)
        await openUserMenuItem(page, PREFERENCES_ITEM)
        await expect(saveButton).toBeVisible()
        await expectComboApplied(page, combo)
        expect(preferences.served, 'プリファレンスの答えの差し替え').toBe(1)
      })

      await test.step('preferences ready', async () => {
        await checkState(page, combo, 'preferences-ready')
      })

      await test.step('preferences with a check error', async () => {
        await nameInput.fill('')
        await saveButton.click()
        await expect(nameInput).toBeFocused()
        await expect(nameInput).toHaveAttribute('aria-invalid', 'true')
        await checkState(page, combo, 'preferences-invalid')
      })

      await test.step('open the password screen from the user menu', async () => {
        await openUserMenuItem(page, PASSWORD_ITEM)
        await expect(submitButton).toBeVisible()
        await expectComboApplied(page, combo)
      })

      await test.step('password ready', async () => {
        await checkState(page, combo, 'password-ready')
      })

      await test.step('password with check errors', async () => {
        await submitButton.click()
        await expect(page.getByTestId('preferences-password-current-input')).toBeFocused()
        await checkState(page, combo, 'password-invalid')
      })

      await page.waitForLoadState('networkidle')
      expect(writes.count, '/api/me/ への書き込み').toBe(0)
      expect(watch.cspViolations, `${combo.name}: CSP の違反`).toEqual([])
      expect(watch.problems, `${combo.name}: 画面の問題`).toEqual([])
    })
  }

  test('open, save and password change time with a registered user, without CSP violations', async ({
    browser,
    request,
  }) => {
    // 開く 10 回（回ごとに新しいコンテキストでログイン）・保存 5 回・パスワードの変更 5 回で、既定の 30 秒を超える。
    test.setTimeout(240_000)
    const adminToken = await requestAdminAccessToken(request)
    const unavailable = await registrationPrerequisites(request, adminToken)
    if (unavailable !== undefined) {
      // 通常の ./gradlew e2eTest では通らない念のための道（E2E の WAR には SMTP とベース URL が渡り、e2eTest は Mailpit を
      // 確かめる）。Build and Test で Unverified とする。
      test.info().annotations.push({ type: 'skip-reason', description: unavailable })
      test.skip(true, unavailable)
      return
    }
    const user = await createRegisteredUser(request, newRunTag())
    const { baseURL, viewport } = test.info().project.use
    const newContext = () => browser.newContext({ baseURL, viewport, locale: 'ja-JP' })

    const openTimes: Record<'preferences' | 'password', number[]> = {
      preferences: [],
      password: [],
    }
    let shapeChecked = false
    for (const screenName of ['preferences', 'password'] as const) {
      for (let run = 1; run <= RUNS; run += 1) {
        const context = await newContext()
        try {
          const page = await context.newPage()
          const watch = await watchPage(page)
          await loginWithForm(page, user.email, user.password)
          await page.getByTestId('app-shell').getByTestId('dropdown-trigger').click()
          const item = page.getByRole('menuitem', {
            name: screenName === 'preferences' ? PREFERENCES_ITEM : PASSWORD_ITEM,
            exact: true,
          })
          await expect(item).toBeVisible()
          const response =
            screenName === 'preferences' && !shapeChecked
              ? page.waitForResponse(
                  (res) =>
                    new URL(res.url()).pathname === PREFERENCES_PATH &&
                    res.request().method() === 'GET',
                )
              : undefined
          const started = Date.now()
          await item.click()
          await expect(
            page.getByTestId(
              screenName === 'preferences'
                ? 'preferences-save-button'
                : 'preferences-password-submit-button',
            ),
          ).toBeVisible()
          openTimes[screenName].push(Date.now() - started)
          if (response !== undefined) {
            const body: unknown = await (await response).json()
            expect(hasPreferencesShape(body), '本物のプリファレンスの応答と見本の形').toBe(true)
            shapeChecked = true
          }
          await page.waitForLoadState('networkidle')
          expect(watch.cspViolations, `open ${screenName} ${run}: CSP の違反`).toEqual([])
          expect(watch.problems, `open ${screenName} ${run}: 画面の問題`).toEqual([])
        } finally {
          await context.close()
        }
      }
    }

    const saveTimes: number[] = []
    const passwordTimes: number[] = []
    const context = await newContext()
    try {
      const page = await context.newPage()
      const watch = await watchPage(page)
      await loginWithForm(page, user.email, user.password)
      await openUserMenuItem(page, PREFERENCES_ITEM)
      const saveButton = page.getByTestId('preferences-save-button')
      await expect(saveButton).toBeVisible()
      const savedToast = page.getByTestId('toast-container').getByText('保存しました')
      for (let run = 1; run <= RUNS; run += 1) {
        // 前の回の Toast が消えてから次の回を始める（前の Toast を拾わない）
        await expect(savedToast).toHaveCount(0, { timeout: 10_000 })
        await page.getByRole('radio', { name: run % 2 === 1 ? 'ダーク' : 'ライト' }).check()
        const started = Date.now()
        await saveButton.click()
        await expect(savedToast).toBeVisible()
        saveTimes.push(Date.now() - started)
      }

      await openUserMenuItem(page, PASSWORD_ITEM)
      const submitButton = page.getByTestId('preferences-password-submit-button')
      await expect(submitButton).toBeVisible()
      const changedToast = page.getByTestId('toast-container').getByText('パスワードを変更しました')
      let currentPassword = user.password
      for (let run = 1; run <= RUNS; run += 1) {
        await expect(changedToast).toHaveCount(0, { timeout: 10_000 })
        const nextPassword = newRunPassword()
        await page.getByTestId('preferences-password-current-input').fill(currentPassword)
        await page.getByTestId('preferences-password-new-input').fill(nextPassword)
        await page.getByTestId('preferences-password-confirm-input').fill(nextPassword)
        const started = Date.now()
        await submitButton.click()
        await expect(changedToast).toBeVisible()
        passwordTimes.push(Date.now() - started)
        currentPassword = nextPassword
      }
      await page.waitForLoadState('networkidle')
      expect(watch.cspViolations, 'save and password: CSP の違反').toEqual([])
      expect(watch.problems, 'save and password: 画面の問題').toEqual([])
    } finally {
      await context.close()
    }

    const record = {
      openPreferences: summarize(openTimes.preferences, OPEN_TARGET_MS),
      openPassword: summarize(openTimes.password, OPEN_TARGET_MS),
      save: summarize(saveTimes, SAVE_TARGET_MS),
      passwordChange: summarize(passwordTimes, PASSWORD_TARGET_MS),
      realResponseShape: shapeChecked,
    }
    test
      .info()
      .annotations.push({ type: 'preferences-screen-ms', description: JSON.stringify(record) })
    await test.info().attach('preferences-screen.json', {
      body: JSON.stringify(record, null, 2),
      contentType: 'application/json',
    })
  })
})
