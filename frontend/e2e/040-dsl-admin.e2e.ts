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
// ビルドした WAR で、DSL の管理画面の代表の流れを確かめる（Build and Test の依頼者の決定 Q5: B）。
// ログイン → サイドバーから DSL の管理（/admin/dsl）→ 書式の版 1 を貼り付けると版の誤りの文が出る → 書式の版 2 を貼り付けで投入
// → プレビュー（要約・違いの表）→ 適用（確かめる表示で確定）→ 今の状態が「適用中」になる → ログアウト。
// 書式の版 2 は Intent 261004-role-menu の U2 dsl-v2（スキーマの階層・メニューの組）。版 1 の確かめは計画の D-11 で1か所足した。
// E2E の環境には対象DB の接続先が無いため、照合は「対象DB の接続先が設定されていません」の警告になる前提で書く。
// 内部DB は実行ごとの一時ディレクトリで、ファイル名の順に1本ずつ実行するため、この確かめの始めは適用もプレビューも無い。
import { expect, test, type Page } from '@playwright/test'
import { adminEmail, adminPassword } from '../playwright.config'

/** 投入する小さな正しい DSL（書式の版 2。スキーマ1つ・テーブル1つ・カラム2つ・メニュー1つ。JSON Schema の必須の項目だけ） */
const DSL_YAML = `version: 2
menus:
  - label: { ja: 部署, en: Departments }
    table: { schema: e2e, name: e2e_dept }
schemas:
  e2e:
    label: { ja: E2E, en: E2E }
    tables:
      e2e_dept:
        label: { ja: 部署, en: Departments }
        view: false
        primaryKey: [dept_code]
        foreignKeys: []
        columns:
          dept_code:
            label: { ja: 部署コード, en: Department code }
            dbType: { name: VARCHAR, length: 10, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: true, operator: EQUALS, collapsed: false }
            list: { visible: true, order: 1, sortable: true }
            detail: { visible: true }
            validations: []
          dept_name:
            label: { ja: 部署名, en: Department name }
            dbType: { name: VARCHAR, length: 50, precision: null, scale: null, nullable: true }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 2, sortable: true }
            detail: { visible: true }
            validations: []
`

/** 書式の版 1 の DSL（版の誤りの 422 になる） */
const VERSION_ONE_YAML = 'version: 1\nmenus: []\ntables: {}\n'

/** 画面で起きた CSP 違反とスクリプトのエラーを集める（想定どおりの 401・404 の表示は除く）。 */
async function collectProblems(page: Page): Promise<string[]> {
  const problems: string[] = []
  page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    if (message.type() !== 'error') {
      return
    }
    const url = message.location().url
    // 起動時のトークンの更新（未ログイン）は 401 になる（020・030 と同じ）。
    if (url.includes('/api/auth/') || message.text().includes('/api/auth/')) {
      return
    }
    // プレビューが無いときの読み込み（GET /api/admin/dsl/preview）は 404 DSL_PREVIEW_NOT_FOUND になる。
    // 画面は「プレビューはありません」として扱うが、ブラウザは資源の読み込みの失敗として表示するため、その分だけを除く。
    if (url.endsWith('/api/admin/dsl/preview') && message.text().includes('404')) {
      return
    }
    // 書式の版 1 の投入（POST /api/admin/dsl/preview）は 422 DSL_INVALID になる。画面は誤りの一覧として扱う。
    let pathname = ''
    try {
      pathname = new URL(url).pathname
    } catch {
      pathname = ''
    }
    if (pathname === '/api/admin/dsl/preview' && message.text().includes('422')) {
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

test.describe('040 DSL administration on the built WAR', () => {
  test('logs in, submits a pasted DSL, applies it after confirmation and logs out', async ({
    page,
  }) => {
    const problems = await collectProblems(page)

    await page.goto('/')
    await expect(page.getByTestId('login-layout')).toBeVisible()
    await page.getByTestId('login-form-email-input').fill(adminEmail)
    await page.getByTestId('login-form-password-input').fill(adminPassword)
    await page.getByTestId('login-form-submit-button').click()
    await expect(page.getByTestId('home-page')).toBeVisible()

    // サイドバーの「DSL」から DSL の管理画面を開く。
    const dslLink = page.getByRole('link', { name: 'DSL', exact: true })
    await expect(dslLink).toBeVisible()
    await dslLink.click()
    await expect(page).toHaveURL(/\/admin\/dsl$/)
    await expect(page.getByTestId('dsl-admin-page')).toBeVisible()
    await expect(page.getByRole('heading', { level: 1, name: 'DSL' })).toBeVisible()
    await expect(page.getByTestId('dsl-status-applied')).toContainText('まだ適用していません')
    await expect(page.getByTestId('dsl-preview-empty')).toBeVisible()

    // 投入のタブで「貼り付ける」を選ぶ。書式の版 1 を投入すると、版の誤りの文が誤りの一覧に出て、プレビューは置かれない。
    await page.getByRole('tab', { name: '投入' }).click()
    await expect(page.getByTestId('dsl-submit-form')).toBeVisible()
    await expect(page.getByTestId('dsl-submit-schema-link')).toHaveAttribute(
      'href',
      '/dsl/dsl-schema-v2.json',
    )
    await page.getByRole('radio', { name: '貼り付ける' }).check()
    await page.getByLabel('DSL（YAML、10MB まで）').fill(VERSION_ONE_YAML)
    await page.getByTestId('dsl-submit-button').click()
    await expect(page.getByTestId('dsl-submit-errors')).toContainText(
      '書式の版 1 は使えません。書式の版 2（スキーマの階層あり）で書いてください。',
    )

    // 書式の版 2 を貼り付けて投入する（プレビューが無いため置き換えの確認は出ない）。
    await page.getByLabel('DSL（YAML、10MB まで）').fill(DSL_YAML)
    await page.getByTestId('dsl-submit-button').click()

    // プレビューのタブに切り替わり、検証を通ったこと・照合できなかった警告・要約・違いの表が示される。
    const preview = page.getByTestId('dsl-preview-panel')
    await expect(preview).toBeVisible()
    await expect(page.getByTestId('dsl-preview-valid')).toHaveText('検証を通りました')
    await expect(page.getByTestId('dsl-preview-not-compared')).toBeVisible()
    await expect(page.getByTestId('dsl-warning-group-TARGET_UNCONFIGURED')).toContainText(
      '対象DB の接続先が設定されていません',
    )
    await expect(page.getByTestId('dsl-preview-schema-count')).toHaveText('スキーマ 1')
    await expect(page.getByTestId('dsl-preview-table-count')).toHaveText(
      'テーブル 1（うちビュー 0）',
    )
    await expect(page.getByTestId('dsl-preview-column-count')).toHaveText('カラム 2')
    await expect(page.getByTestId('dsl-diff-no-applied')).toBeVisible()
    await expect(page.getByTestId('dsl-diff-schema-heading-e2e')).toContainText(
      'スキーマ e2e（E2E）',
    )
    await expect(page.getByTestId('dsl-diff-row-e2e/e2e_dept')).toContainText('増えた')
    await expect(page.getByTestId('dsl-menu-node-0')).toContainText('部署（e2e.e2e_dept）')
    await expect(page.getByTestId('dsl-status-preview')).toContainText('貼り付け')
    await expect(page.getByTestId('dsl-status-preview')).toContainText(adminEmail)

    // 確かめる表示を一度「やめる」で閉じ、開いた元の「適用する」へフォーカスが戻ることを確かめる
    // （Intent 261003-user-admin-followup の FR1.4）。
    const applyButton = page.getByTestId('dsl-preview-apply')
    const confirm = page.getByTestId('dsl-confirm-apply')
    await applyButton.click()
    await expect(confirm).toBeVisible()
    await expect(page.getByTestId('dsl-confirm-cancel')).toBeFocused()
    await page.getByTestId('dsl-confirm-cancel').click()
    await expect(confirm).toBeHidden()
    await expect(applyButton).toBeFocused()

    // 適用する。確かめる表示の「適用する」で確定する。
    await applyButton.click()
    await expect(confirm).toBeVisible()
    await expect(page.getByTestId('dsl-confirm-schema-counts')).toHaveText(
      'スキーマ 増えた 1・減った 0・変わった 0',
    )
    await expect(page.getByTestId('dsl-confirm-diff')).toContainText(
      'テーブル 増えた 1・減った 0・変わった 0',
    )
    await expect(page.getByTestId('dsl-confirm-cancel')).toBeFocused()
    await page.getByTestId('dsl-confirm-ok').click()
    await expect(confirm).toBeHidden()

    // 今の状態が「適用中」になり、プレビューは空になる。
    const applied = page.getByTestId('dsl-status-applied')
    await expect(applied).not.toContainText('まだ適用していません')
    await expect(applied).toContainText('適用中')
    await expect(applied).toContainText(adminEmail)
    await expect(page.getByTestId('dsl-status-preview')).toContainText('プレビューはありません')
    await expect(page.getByTestId('dsl-preview-empty')).toBeVisible()
    await expect(page.getByTestId('dsl-admin-page')).toHaveAttribute('aria-busy', 'false')

    await page.getByRole('button', { name: adminEmail }).click()
    await page.getByRole('menuitem', { name: 'ログアウト' }).click()
    await expect(page.getByTestId('login-layout')).toBeVisible()

    await page.waitForLoadState('networkidle')
    expect(problems).toEqual([])
  })
})
