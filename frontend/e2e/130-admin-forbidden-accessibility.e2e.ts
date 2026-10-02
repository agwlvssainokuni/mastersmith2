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
// ビルドした WAR で、管理の画面の「権限が無い」の表示（S6）を表示の設定の組ごとに実際のブラウザで確かめる
// （Intent 260930-user-admin の U4。NFR7.3・NFR3.2・NFR9.5・NFR9.10、NFR 設計の logical-components.md 6節）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない（NFR9.10）。
// - 組ごとに1つのテスト（support/displayCombos.ts の 20 組）。初期管理者でログインし、サイドバーの「管理」から管理の入口を
//   開く。管理の API は GET /api/admin/check の1本だけを 403・ACCESS_DENIED の見本（この中の定数。共有の support/ には
//   置かない）に差し替える。ほかの管理の API と GET 以外の要求は差し替えず、送らないことを確かめる。
// - 組のテーマ・文字の大きさと、上の帯の Avatar の頭文字が2文字になる架空の2語の氏名は、ログインと復元の本物の応答を
//   1つの差し替え（support/loginPreferences.ts の displayName）で書き換えて当てる。サーバーの状態（初期管理者の印・氏名・
//   設定）は変えない。アクセストークンはテストのコードで取り出さない。
// - S6 が出た後のログインの状態の読み直し（POST /api/auth/session/refresh）の応答を待つ約束は、管理の入口を開く操作より
//   前に作る（NFR 設計の承認の場の R-01）。E2E のサーバーでは初期管理者は管理者のままのため、読み直しの後も S6 のまま
//   続く（残る危険 R1 の形）。
// - 管理者でない利用者が URL を開いたときの S6（ForbiddenByRoute）は、ここでは確かめない。E2E には初期管理者しか
//   いないため、部品のテスト（AppRouter.test.tsx・decideRoute.test.ts）で確かめる。
// - 差し替えた 403 に Chrome が出す「Failed to load resource:」の表示は、位置の URL が /api/admin/check のものだけを
//   数えて引く（この中だけの扱い。共有の support/pageProblems.ts は変えない。logical-components.md 6.3）。
// - 画面の時間は測らず、記録もしない（NFR9.3）。test.step の題・注記・添付に、氏名・メールアドレス・パスワード・
//   トークンを入れない。注記に残すのは組の名前と件数・規則の名前・はみ出し・Avatar の文字の数だけ。
// - 既存の 010〜100 は変えない。
import { expect, test, type Page } from '@playwright/test'
import { loginAsAdmin, openSidebarItem } from './support/adminLogin'
import { missingRequiredRules, runAxe } from './support/axe'
import { DISPLAY_COMBOS, expectComboApplied, prepareCombo } from './support/displayCombos'
import { routeLoginPreferences } from './support/loginPreferences'
import { describeOverflow, measureHorizontalOverflow } from './support/overflow'
import { watchPage } from './support/pageProblems'

/** 管理の入口の確かめの API */
const ADMIN_CHECK_PATH = '/api/admin/check'
/** 管理の API の根 */
const ADMIN_API_PREFIX = '/api/admin/'
/** ログインの状態の読み直しの API */
const REFRESH_PATH = '/api/auth/session/refresh'
/** サイドバーの管理の入口の項目 */
const SIDEBAR_ITEM = '管理'
/** 上の帯の Avatar の頭文字が2文字になる架空の2語の氏名（値は注記・添付・標準出力に出さない） */
const TWO_WORD_NAME = 'Probe Sample'
/** 403 の本文の detail の目印（個人に関する値でも秘密でもない固定の文字。画面に出ないことを確かめる） */
const DETAIL_MARKER = 'admin-forbidden-detail-marker'
/** 管理の入口の確かめの API の 403・ACCESS_DENIED の見本 */
const FORBIDDEN_PROBLEM = {
  type: 'about:blank',
  title: 'Forbidden',
  status: 403,
  detail: DETAIL_MARKER,
  code: 'ACCESS_DENIED',
} as const

/** 解けない URL は空のパスとする。 */
function pathOf(url: string): string {
  try {
    return new URL(url).pathname
  } catch {
    return ''
  }
}

/** GET /api/admin/check だけを 403 の見本に差し替え、返した回数を数える。GET 以外は差し替えない。 */
async function routeAdminCheck(page: Page): Promise<{ answered: number }> {
  const counter = { answered: 0 }
  await page.route(
    (url) => url.pathname === ADMIN_CHECK_PATH,
    (route) => {
      if (route.request().method() !== 'GET') {
        return route.fallback()
      }
      counter.answered += 1
      return route.fulfill({
        status: 403,
        contentType: 'application/problem+json',
        body: JSON.stringify(FORBIDDEN_PROBLEM),
      })
    },
  )
  return counter
}

/** 差し替えた 403 に出る読み込みの失敗の表示を集める（watchPage の後に張る）。 */
function watchExcludedConsole(page: Page): string[] {
  const excludedTexts: string[] = []
  page.on('console', (message) => {
    if (message.type() !== 'error') {
      return
    }
    if (pathOf(message.location().url) !== ADMIN_CHECK_PATH) {
      return
    }
    if (!message.text().startsWith('Failed to load resource:')) {
      return
    }
    excludedTexts.push(`console: ${message.text()}`)
  })
  return excludedTexts
}

/** 管理の API への GET 以外の要求を数える。 */
function countAdminNonGet(page: Page): { count: number } {
  const counter = { count: 0 }
  page.on('request', (request) => {
    if (request.method() !== 'GET' && pathOf(request.url()).startsWith(ADMIN_API_PREFIX)) {
      counter.count += 1
    }
  })
  return counter
}

/** 問題の一覧の写しから、除く表示を1件ずつ取り除いた残りを返す。 */
function withoutExcluded(problems: readonly string[], excluded: readonly string[]): string[] {
  const rest = [...problems]
  for (const text of excluded) {
    const index = rest.indexOf(text)
    if (index >= 0) {
      rest.splice(index, 1)
    }
  }
  return rest
}

test.describe('130 admin forbidden view accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`admin forbidden ${combo.name}`, async ({ page }) => {
      const watch = await watchPage(page)
      const excludedTexts = watchExcludedConsole(page)
      const nonGet = countAdminNonGet(page)
      await prepareCombo(page, combo)
      const login = await routeLoginPreferences(page, {
        theme: combo.theme,
        fontSize: combo.fontSize,
        displayName: TWO_WORD_NAME,
      })
      const check = await routeAdminCheck(page)
      const view = page.getByTestId('admin-forbidden-view')
      const heading = page.getByTestId('admin-forbidden-heading')

      await test.step('log in with the combo applied', async () => {
        await loginAsAdmin(page)
        await expectComboApplied(page, combo)
      })

      await test.step('open the admin area and wait for the reread of the login state', async () => {
        // 読み直しの応答を待つ約束は、管理の入口を開く操作より前に作る（NFR 設計の承認の場の R-01）。
        const reread = page.waitForResponse(
          (response) =>
            pathOf(response.url()) === REFRESH_PATH &&
            response.request().method() === 'POST' &&
            response.status() === 200,
        )
        await openSidebarItem(page, SIDEBAR_ITEM)
        await expect(view).toBeVisible()
        await reread
        await expect(view).toBeVisible()
        await expectComboApplied(page, combo)
      })

      await test.step('check the forbidden view', async () => {
        const summary = await runAxe(page)
        const overflow = await measureHorizontalOverflow(page)
        const avatarLength = ((await page.locator('.mycui-avatar').first().textContent()) ?? '')
          .length
        const record = {
          combo: combo.name,
          violations: summary.violations.length,
          violationRules: [...new Set(summary.violations.map((violation) => violation.id))],
          incomplete: summary.incomplete,
          overflow,
          avatarLength,
          loginRewritten: login.rewritten,
          forbiddenAnswered: check.answered,
          excludedConsole: excludedTexts.length,
        }
        test.info().annotations.push({ type: 'axe', description: JSON.stringify(record) })
        expect(missingRequiredRules(summary), `${combo.name}: 流れなかった規則`).toEqual([])
        expect(record.violationRules, `${combo.name}: 違反`).toEqual([])
        expect(overflow.overflows, describeOverflow(combo.name, overflow)).toBe(false)
        await expect(view.getByTestId('alert')).toHaveAttribute('role', 'status')
        await expect(heading).toBeFocused()
        expect(avatarLength, `${combo.name}: Avatar の文字の数`).toBe(2)
        expect(
          await page.locator('body').textContent(),
          `${combo.name}: detail の目印`,
        ).not.toContain(DETAIL_MARKER)
        expect(
          login.rewritten,
          `${combo.name}: ログインと復元の応答の書き換え`,
        ).toBeGreaterThanOrEqual(1)
      })

      await test.step('go back home from the forbidden view', async () => {
        await page.getByTestId('admin-forbidden-home-link').click()
        await expect(page.getByTestId('home-page')).toBeVisible()
        await expect(view).toBeHidden()
      })

      await page.waitForLoadState('networkidle')
      const remaining = withoutExcluded(watch.problems, excludedTexts)
      test.info().annotations.push({
        type: 'admin-forbidden-problems',
        description: JSON.stringify({
          combo: combo.name,
          loginRewritten: login.rewritten,
          forbiddenAnswered: check.answered,
          excludedConsole: excludedTexts.length,
          remainingProblems: remaining.length,
          cspViolations: watch.cspViolations.length,
          adminNonGetRequests: nonGet.count,
        }),
      })
      expect(excludedTexts.length, `${combo.name}: 除いた表示の件数`).toBeLessThanOrEqual(
        check.answered,
      )
      expect(watch.cspViolations, `${combo.name}: CSP の違反`).toEqual([])
      expect(remaining, `${combo.name}: 画面の問題`).toEqual([])
      expect(nonGet.count, `${combo.name}: 管理の API への GET 以外の要求`).toBe(0)
    })
  }
})
