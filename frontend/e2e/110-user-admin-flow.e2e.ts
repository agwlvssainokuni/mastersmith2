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
// ビルドした WAR で、利用者の管理の代表の流れを確かめる（Intent 260930-user-admin の U5、E2E-M9、functional-spec.md の 9節、
// 計画の Step 17 の順1〜10）。検索 → ロック → 失敗回数を戻す → 409・400 の照らし合わせ → 止める → 入れない → 停止を解く →
// 入れる → 画面と本物のサーバーを通した 403（U4 のレビューの R-02、計画 9節の Q-B の決定 A）。
// - 流れの E2E の本数（team.md の「機能の Intent ごとに代表の流れを1本まで」）に数えるのは、この Intent では 110 だけ。
// - 前のテストが作った状態に頼らず、利用者 U を createRegisteredUser（既存の手伝い、変えない）で作る。状態を変えるのは U だけで、
//   初期管理者の印・停止・ロックは変えない。U は消さない（利用者を消す仕組みは無く、宛先は実行ごとに重ならない）。
// - 何も差し替えない（本物の WAR・Mailpit・一時の内部DB）。例外は順9 だけで、管理者でない U の新しいページのログインの応答
//   （POST /api/auth/login）の user.admin を真に書き換える差し替えを1つ置く（復元・更新の応答は書き換えない。サーバーの
//   状態は変えない。計画 8節の D-14）。
// - U の宛先・パスワード・氏名と runTag は、作った直後に recordSecretValues で値のファイルに書き、報告の部品が探す。
//   test.step の題・注記・添付・expect の説明文・locator の名前と期待値に値を入れない。読み上げの名前に値が入っていることは、
//   比べた真偽だけを expect に渡す（security-design.md 3.3）。
// - trace は e2eTraceMode()（既定 off）。失敗のときだけ、値を伏せた手がかりを注記と添付 user-admin-diagnostics に残す。
// - 利用者の管理の画面の上では openUserMenuItem を使わない（行の「操作」の開き口と重なる）。U の画面（ホーム）では使ってよい。
// - ロックのしきい値は既定の 5 回（playwright.config.ts の webServer はしきい値の設定 MASTERSMITH_AUTH_LOCK_THRESHOLD を
//   渡していない）。解除の予定の時刻の文言の形には固定しない（機能設計の R-08）。
// - 既存の 010〜100・130 と support/ の既存のファイルは変えない。
import { randomBytes } from 'node:crypto'
import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'
import { loginAsAdmin, openSidebarItem } from './support/adminLogin'
import { requestAdminAccessToken } from './support/invitationSeed'
import { watchPage } from './support/pageProblems'
import {
  createRegisteredUser,
  loginWithForm,
  openUserMenuItem,
  REGISTERED_DISPLAY_NAME,
  registrationPrerequisites,
} from './support/registeredUser'
import { recordSecretValues } from './support/secretValues'
import { e2eTraceMode } from './support/traceMode'
import { startUserAdminDiagnostics } from './support/userAdminDiagnostics'
import {
  conflictProblem,
  inspectionListPage,
  searchValidationProblem,
  shapeDifferences,
  USER_ADMIN_CONFLICT_CODES,
} from './support/userAdminFixtures'
import { newUserAdminRunTag } from './support/userAdminRun'

test.use({ trace: e2eTraceMode() })

/** サイドバーの項目 */
const SIDEBAR_ITEM = '利用者の管理'
/** 利用者の管理の API の根 */
const USER_ADMIN_API = '/api/admin/users'
/** ログインの API */
const LOGIN_API = '/api/auth/login'
/** ログインの状態の読み直しの API */
const REFRESH_API = '/api/auth/session/refresh'
/** プリファレンスの API */
const PREFERENCES_API = '/api/me/preferences'
/** ロックのしきい値（既定。webServer はしきい値の設定を渡していない） */
const LOCK_THRESHOLD = 5
/** 検索の上限（254 コードポイント）を超える検索の文字 */
const TOO_LONG_SEARCH = 'あ'.repeat(255)
/** パスワードを誤ったときと同じ文言 */
const LOGIN_FAILED_TEXT = 'メールアドレスまたはパスワードが正しくありません'

/** 解けない URL は空のパスとする。 */
function pathOf(url: string): string {
  try {
    return new URL(url).pathname
  } catch {
    return ''
  }
}

/** キャッシュが空の新しいコンテキスト（テストの設定の baseURL・表示の幅・ja-JP） */
async function newContext(browser: Browser): Promise<BrowserContext> {
  const { baseURL, viewport } = test.info().project.use
  return browser.newContext({ baseURL, viewport, locale: 'ja-JP' })
}

/** 一覧の表の行（見出しの行を除く） */
function tableRows(page: Page) {
  return page.getByTestId('useradmin-table').locator('tbody tr')
}

/** 検索の欄に文字を入れて [検索] を押し、一覧の本物の応答を待って返す。 */
async function searchUsers(page: Page, text: string) {
  // 画面を開いたときの全体の一覧の応答を取り違えないよう、検索の文字（q）が入れた文字と同じ応答だけを待つ。
  const response = page.waitForResponse(
    (candidate) =>
      pathOf(candidate.url()) === USER_ADMIN_API &&
      candidate.request().method() === 'GET' &&
      new URL(candidate.url()).searchParams.get('q') === text,
  )
  await page.getByTestId('useradmin-search-input').fill(text)
  await page.getByTestId('useradmin-search-submit').click()
  return response
}

/**
 * U の行の「操作」を開き、項目を名前で選ぶ。押す前に、一覧がちょうど1行で、その行が流れの中で作った U の行（userId）で
 * あることを確かめる（初期管理者の行を操作しないため。値は出さず真偽だけを expect に渡す）。
 */
async function chooseRowAction(page: Page, userId: number, item: string): Promise<void> {
  await expect(tableRows(page)).toHaveCount(1)
  const row = tableRows(page).first()
  const isUserRow = (await row.getByTestId(`useradmin-row-actions-${userId}`).count()) === 1
  expect(isUserRow, '操作の対象が流れの中で作った利用者の行').toBe(true)
  await row.getByRole('button', { name: /の操作$/ }).click()
  await page.getByRole('menuitem', { name: item, exact: true }).click()
}

/** 確かめの表示でフォーカスが「やめる」にあることを確かめ、実行のボタンを押す。 */
async function confirmAction(page: Page, title: string): Promise<void> {
  const dialog = page.getByRole('alertdialog', { name: title })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByTestId('useradmin-confirm-cancel')).toBeFocused()
  await dialog.getByTestId('useradmin-confirm-submit').click()
  await expect(dialog).toBeHidden()
  // make-you-chic-ui の ModalStack は閉じた後の描画で背景の inert を外すため、外れるまで待ってから次の操作をする。
  await expect(page.locator('body > [inert]')).toHaveCount(0)
}

/** 成功の Toast（氏名に続く決まった文で探す） */
function successToast(page: Page, text: string) {
  return page.getByTestId('toast-container').getByText(text)
}

test.describe('110 user admin representative flow on the built WAR', () => {
  const diagnostics = startUserAdminDiagnostics()

  // Playwright は1つ目の引数を分割代入の形で求めるため、軽い働き手の値（browserName）を受けて使わない。
  test.afterEach(async ({ browserName: _browserName }, testInfo) => {
    await diagnostics.attachOnFailure(testInfo)
  })

  test('search, lock, reset, suspend and resume a user created in the flow', async ({
    browser,
    page,
    request,
  }) => {
    // U を作る（招待とメール）・ロックの5回・2つのブラウザでのログインを含み、既定の 30 秒を超える。
    test.setTimeout(180_000)
    const runTag = newUserAdminRunTag()
    const email = `u7-perf-${runTag}@example.com`
    // 作る前に宛先・氏名・runTag を値のファイルに書く（作る途中で失敗しても報告の部品が探せるように）。
    recordSecretValues([
      { kind: 'runTag', value: runTag },
      { kind: 'email', value: email },
      { kind: 'displayName', value: REGISTERED_DISPLAY_NAME },
    ])
    expect([...email].length <= 254, 'U の宛先が検索の上限（254）に収まる').toBe(true)

    const contexts: BrowserContext[] = []
    try {
      const user = await diagnostics.step('prepare a registered user', async () => {
        const adminToken = await requestAdminAccessToken(request)
        const unavailable = await registrationPrerequisites(request, adminToken)
        if (unavailable !== undefined) {
          // 通常の ./gradlew e2eTest では通らない念のための道（e2eTest は Mailpit を確かめる）。理由の種類だけを注記する。
          test.info().annotations.push({ type: 'skip-reason', description: unavailable })
          return undefined
        }
        const created = await createRegisteredUser(request, runTag)
        recordSecretValues([{ kind: 'password', value: created.password }])
        expect(created.email === email, 'U の宛先が runTag から作った形').toBe(true)
        return created
      })
      if (user === undefined) {
        test.skip(true, 'registration prerequisites are not available')
        return
      }

      const adminWatch = await watchPage(page)
      diagnostics.watch('admin', page, adminWatch)
      let userId = 0

      await diagnostics.step('find the user by the run tag', async () => {
        await loginAsAdmin(page)
        await openSidebarItem(page, SIDEBAR_ITEM)
        await expect(page.getByTestId('useradmin-page')).toBeVisible()
        const response = await searchUsers(page, runTag)
        expect(response.status(), '一覧の API の状態コード').toBe(200)
        const body = (await response.json()) as { items?: { userId?: unknown }[] }
        await expect(tableRows(page)).toHaveCount(1)
        const row = tableRows(page).first()
        await expect(row.getByRole('cell').nth(3)).toHaveText('有効')
        await expect(row.getByRole('cell').nth(4)).toContainText('—')
        await expect(row.getByText('ロック中')).toHaveCount(0)
        const name =
          (await row.getByRole('button', { name: /の操作$/ }).getAttribute('aria-label')) ?? ''
        expect(
          name.includes(email) && name.includes(REGISTERED_DISPLAY_NAME),
          '操作の名前に対象の氏名と宛先がある',
        ).toBe(true)

        // 一覧の本物の応答と見本の項目の名前と型が一致する（NFR9.9。違いは項目の名前だけ）。
        expect(shapeDifferences(body, inspectionListPage()), '一覧の応答と見本の違い').toEqual([])
        const found = body.items?.[0]?.userId
        expect(typeof found, '行の userId の型').toBe('number')
        userId = found as number
      })

      await diagnostics.step('lock the user with wrong passwords', async () => {
        const wrongPassword = `wrong-${randomBytes(8).toString('hex')}`
        for (let attempt = 1; attempt <= LOCK_THRESHOLD; attempt += 1) {
          const response = await request.post(LOGIN_API, {
            data: { email, password: wrongPassword },
          })
          expect(response.status(), `誤ったパスワードのログイン ${attempt} 回目の状態コード`).toBe(
            401,
          )
          const problem = (await response.json()) as { code?: unknown }
          expect(problem.code, `誤ったパスワードのログイン ${attempt} 回目の code`).toBe(
            'AUTHENTICATION_FAILED',
          )
        }
        const response = await searchUsers(page, runTag)
        expect(response.status(), '一覧の API の状態コード').toBe(200)
        const lockCell = tableRows(page).first().getByRole('cell').nth(4)
        await expect(lockCell).toContainText('ロック中')
        await expect(lockCell).toContainText('まで')
      })

      const userContext = await newContext(browser)
      contexts.push(userContext)
      const userPage = await userContext.newPage()
      diagnostics.watch('user', userPage, await watchPage(userPage))

      await diagnostics.step('reset the login failures and let the user sign in', async () => {
        await chooseRowAction(page, userId, 'ロックを解除（失敗回数を戻す）')
        await confirmAction(page, 'ロックを解除しますか？')
        await expect(successToast(page, 'さんのロックを解除しました')).toBeVisible()
        const row = tableRows(page).first()
        await expect(row.getByText('ロック中')).toHaveCount(0)
        await row.getByRole('button', { name: /の操作$/ }).click()
        await expect(
          page.getByRole('menuitem', { name: '利用を止める', exact: true }),
        ).toBeVisible()
        await expect(
          page.getByRole('menuitem', { name: 'ロックを解除（失敗回数を戻す）', exact: true }),
        ).toHaveCount(0)
        await page.keyboard.press('Escape')
        await loginWithForm(userPage, user.email, user.password)
      })

      await diagnostics.step('compare the real 409 and 400 with the samples', async () => {
        const adminToken = await requestAdminAccessToken(request)
        const headers = { Authorization: `Bearer ${adminToken}` }
        const conflict = await request.post(
          `${USER_ADMIN_API}/${encodeURIComponent(String(userId))}/reset-login-failures`,
          { headers },
        )
        expect(conflict.status(), 'もう一度失敗回数を戻す API の状態コード').toBe(409)
        const conflictBody = (await conflict.json()) as { code?: unknown }
        expect(conflictBody.code, '409 の code').toBe('USER_ADMIN_NO_CHANGE')
        expect(shapeDifferences(conflictBody, conflictProblem()), '409 の応答と見本の違い').toEqual(
          [],
        )
        const sampleCode = conflictProblem().code
        expect(
          (USER_ADMIN_CONFLICT_CODES as readonly unknown[]).includes(sampleCode),
          '409 の見本の code が契約 C3 の5つの code のどれか',
        ).toBe(true)

        const invalid = await request.get(
          `${USER_ADMIN_API}?page=1&q=${encodeURIComponent(TOO_LONG_SEARCH)}`,
          { headers },
        )
        expect(invalid.status(), '上限を超える検索の状態コード').toBe(400)
        const invalidBody = (await invalid.json()) as { code?: unknown }
        expect(invalidBody.code, '400 の code').toBe('VALIDATION_FAILED')
        expect(
          shapeDifferences(invalidBody, searchValidationProblem()),
          '400 の応答と見本の違い',
        ).toEqual([])
      })

      await diagnostics.step('suspend the user and the user cannot continue', async () => {
        await chooseRowAction(page, userId, '利用を止める')
        await confirmAction(page, '利用を止めますか？')
        await expect(successToast(page, 'さんの利用を止めました')).toBeVisible()
        const row = tableRows(page).first()
        await expect(row.getByRole('cell').nth(3)).toHaveText('利用停止')
        await row.getByRole('button', { name: /の操作$/ }).click()
        await expect(page.getByRole('menuitem', { name: '停止を解く', exact: true })).toBeVisible()
        await page.keyboard.press('Escape')

        const preferences401 = userPage.waitForResponse(
          (response) => pathOf(response.url()) === PREFERENCES_API && response.status() === 401,
        )
        const refresh401 = userPage.waitForResponse(
          (response) =>
            pathOf(response.url()) === REFRESH_API &&
            response.request().method() === 'POST' &&
            response.status() === 401,
        )
        await openUserMenuItem(userPage, 'プリファレンス')
        await preferences401
        await refresh401
        await expect(userPage.getByTestId('login-layout')).toBeVisible()
        await userPage.getByTestId('login-form-email-input').fill(user.email)
        await userPage.getByTestId('login-form-password-input').fill(user.password)
        await userPage.getByTestId('login-form-submit-button').click()
        await expect(userPage.getByTestId('login-form-error-text')).toHaveText(LOGIN_FAILED_TEXT)
      })

      await diagnostics.step('resume the user and the user can sign in again', async () => {
        await chooseRowAction(page, userId, '停止を解く')
        await confirmAction(page, '停止を解きますか？')
        await expect(successToast(page, 'さんの停止を解きました')).toBeVisible()
        await expect(tableRows(page).first().getByRole('cell').nth(3)).toHaveText('有効')
        await loginWithForm(userPage, user.email, user.password)
      })

      await diagnostics.step(
        'a non-admin user sees the forbidden view from the real API',
        async () => {
          // 計画 9節の Q-B の決定 A: U の新しいページのログインの応答の user.admin だけを真に書き換える（このページの中だけ）。
          const forbiddenContext = await newContext(browser)
          contexts.push(forbiddenContext)
          const forbiddenPage = await forbiddenContext.newPage()
          diagnostics.watch('non-admin', forbiddenPage, await watchPage(forbiddenPage))
          const rewrite = { count: 0 }
          await forbiddenPage.route(
            (url) => url.pathname === LOGIN_API,
            async (route) => {
              if (route.request().method() !== 'POST') {
                return route.fallback()
              }
              const response = await route.fetch()
              if (!response.ok()) {
                return route.fulfill({ response })
              }
              const body = (await response.json()) as { user?: Record<string, unknown> }
              if (typeof body.user !== 'object' || body.user === null) {
                return route.fulfill({ response })
              }
              rewrite.count += 1
              return route.fulfill({
                response,
                json: { ...body, user: { ...body.user, admin: true } },
              })
            },
          )
          await loginWithForm(forbiddenPage, user.email, user.password)
          expect(rewrite.count, 'ログインの応答の書き換え').toBe(1)
          const forbidden = forbiddenPage.waitForResponse(
            (response) =>
              pathOf(response.url()) === USER_ADMIN_API && response.request().method() === 'GET',
          )
          const reread = forbiddenPage.waitForResponse(
            (response) =>
              pathOf(response.url()) === REFRESH_API &&
              response.request().method() === 'POST' &&
              response.status() === 200,
          )
          await openSidebarItem(forbiddenPage, SIDEBAR_ITEM)
          const forbiddenResponse = await forbidden
          expect(forbiddenResponse.status(), '管理でない利用者の一覧の API の状態コード').toBe(403)
          const problem = (await forbiddenResponse.json()) as { code?: unknown }
          expect(problem.code, '403 の code').toBe('ACCESS_DENIED')
          await expect(forbiddenPage.getByTestId('admin-forbidden-view')).toBeVisible()
          await reread
          await expect(
            forbiddenPage.getByRole('link', { name: SIDEBAR_ITEM, exact: true }),
          ).toHaveCount(0)
          await expect(forbiddenPage.getByTestId('admin-forbidden-view')).toBeVisible()
          expect(rewrite.count, '復元・更新の応答は書き換えない').toBe(1)
        },
      )

      test.info().annotations.push({
        type: 'user-admin-flow',
        description: JSON.stringify({
          lockAttempts: LOCK_THRESHOLD,
          adminProblems: adminWatch.problems.length,
          adminCspViolations: adminWatch.cspViolations.length,
        }),
      })
      expect(adminWatch.cspViolations, '管理者の画面の CSP の違反').toEqual([])
    } finally {
      for (const context of contexts) {
        await context.close()
      }
    }
  })
})
