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
// ビルドした WAR で、画面が CSP 違反なしに表示されることを確かめる（NFR3.10、security-design 9章）。
// 文中の U1〜U3 と「計画」は、前の Intent（260922-auth-audit-base）の単位の番号とその Code Generation の計画を指す。
import { expect, test, type Page } from '@playwright/test'

/** 画面で起きた CSP 違反とスクリプトのエラーを集める。 */
async function collectProblems(page: Page): Promise<string[]> {
  const problems: string[] = []
  page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    if (message.type() !== 'error') {
      return
    }
    // 未ログインで画面を開くと、U2 のログイン状態の復元がトークンの更新を1回試みて 401 になる（BR8.4）。
    // ブラウザはこれを「資源の読み込みの失敗」として表示するため、その1件だけを除く（U2 計画の D1）。
    if (
      message.location().url.includes('/api/auth/session/refresh') ||
      message.text().includes('/api/auth/session/refresh')
    ) {
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

test.describe('010 skeleton on the built WAR', () => {
  test('shows the login layout at / with a CSP header and without violations or script errors', async ({
    page,
  }) => {
    const problems = await collectProblems(page)

    const response = await page.goto('/')

    expect(response?.status()).toBe(200)
    expect(response?.headers()['content-security-policy']).toContain("script-src 'self'")
    await expect(page.getByTestId('login-layout')).toBeVisible()
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('ログイン')
    await expect(page.locator('html')).toHaveAttribute('lang', 'ja')
    await page.waitForLoadState('networkidle')
    expect(problems).toEqual([])
  })

  test('opening a screen URL directly also shows the login layout', async ({ page }) => {
    const problems = await collectProblems(page)

    const response = await page.goto('/admin/users')

    expect(response?.status()).toBe(200)
    await expect(page.getByTestId('login-layout')).toBeVisible()
    await page.waitForLoadState('networkidle')
    expect(problems).toEqual([])
  })
})
