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
// ビルドした WAR で、チームの代表の流れ「ログイン → 管理画面に入れるか → ログアウト」を確かめる
// （FR2.2、FR8.1、BR4.1、BR5.1〜BR5.3。team.md Testing Posture）。
// 管理者でない利用者の 403 は、E2E には初期管理者しかいないためサーバー側の結合テストで確かめる（U3 計画の C8）。
// 文中の U1〜U3 と「計画」は、前の Intent（260922-auth-audit-base）の単位の番号とその Code Generation の計画を指す。
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

test.describe('030 admin access on the built WAR', () => {
  test('logs in, opens the administration area from the sidebar and logs out', async ({ page }) => {
    const problems = await collectProblems(page)

    await page.goto('/')
    await expect(page.getByTestId('login-layout')).toBeVisible()

    await page.getByTestId('login-form-email-input').fill(adminEmail)
    await page.getByTestId('login-form-password-input').fill(adminPassword)
    await page.getByTestId('login-form-submit-button').click()

    await expect(page.getByTestId('home-page')).toBeVisible()

    const adminLink = page.getByRole('link', { name: '管理', exact: true })
    await expect(adminLink).toBeVisible()
    await adminLink.click()

    await expect(page.getByTestId('admin-area-page')).toHaveAttribute('aria-busy', 'false')
    await expect(page.getByTestId('admin-placeholder')).toBeVisible()
    await expect(page.getByRole('heading', { level: 1, name: '管理' })).toBeVisible()
    await expect(page.getByTestId('admin-placeholder')).toContainText('今後の管理機能')

    await page.getByRole('button', { name: adminEmail }).click()
    await page.getByRole('menuitem', { name: 'ログアウト' }).click()

    await expect(page.getByTestId('login-layout')).toBeVisible()

    await page.waitForLoadState('networkidle')
    expect(problems).toEqual([])
  })
})
