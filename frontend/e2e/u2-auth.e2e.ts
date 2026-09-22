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
// ビルドした WAR で、初期管理者のログインからログアウトまでを確かめる（FR2.1、FR2.4、FR5.3、FR6.1、BR8.1〜BR8.8）。
// 管理画面に入れるかの確認は U3 で足す（U2 計画の C9）。
import { expect, test, type Page } from '@playwright/test'
import { adminEmail, adminPassword } from '../playwright.config'

/** 画面で起きた CSP 違反とスクリプトのエラーを集める（認証の API の 401 の表示は除く）。 */
async function collectProblems(page: Page): Promise<string[]> {
  const problems: string[] = []
  page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    if (message.type() !== 'error') {
      return
    }
    // 起動時のトークンの更新（未ログイン）と、わざと誤ったパスワードで送るログインは 401 になる。
    // ブラウザはこれを「資源の読み込みの失敗」として表示するため、認証の API の分だけを除く。
    if (message.location().url.includes('/api/auth/') || message.text().includes('/api/auth/')) {
      return
    }
    problems.push(`console: ${message.text()}`)
  })
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      console.error(`CSP violation: ${event.violatedDirective} ${event.blockedURI}`)
    })
  })
  return problems
}

async function fillLogin(page: Page, email: string, password: string): Promise<void> {
  await page.getByTestId('login-form-email-input').fill(email)
  await page.getByTestId('login-form-password-input').fill(password)
  await page.getByTestId('login-form-submit-button').click()
}

test.describe('U2 authentication on the built WAR', () => {
  test('logs in as the initial administrator, keeps the session over a reload and logs out', async ({
    page,
  }) => {
    const problems = await collectProblems(page)

    await page.goto('/')
    await expect(page.getByTestId('login-layout')).toBeVisible()

    await fillLogin(page, adminEmail, adminPassword)

    await expect(page.getByTestId('home-page')).toBeVisible()

    await page.reload()
    await expect(page.getByTestId('home-page')).toBeVisible()

    await page.getByRole('button', { name: adminEmail }).click()
    await page.getByRole('menuitem', { name: 'ログアウト' }).click()

    await expect(page.getByTestId('login-layout')).toBeVisible()

    await page.reload()
    await expect(page.getByTestId('login-layout')).toBeVisible()

    await page.waitForLoadState('networkidle')
    expect(problems).toEqual([])
  })

  test('shows one message for a wrong password and stays on the login screen', async ({ page }) => {
    const problems = await collectProblems(page)

    await page.goto('/')
    await fillLogin(page, adminEmail, 'まちがったパスワード-9999')

    await expect(page.getByTestId('login-form-error-text')).toHaveText(
      'メールアドレスまたはパスワードが正しくありません',
    )
    await expect(page.getByTestId('login-form-password-input')).toHaveValue('')
    await expect(page.getByTestId('login-layout')).toBeVisible()

    await page.waitForLoadState('networkidle')
    expect(problems).toEqual([])
  })
})
