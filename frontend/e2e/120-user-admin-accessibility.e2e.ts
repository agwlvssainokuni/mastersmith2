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
// ビルドした WAR で、利用者の管理の画面（S1〜S5）を表示の設定の組ごとに実際のブラウザで確かめ、画面の時間を測って記録する
// （Intent 260930-user-admin の U5。NFR7.3・NFR3.5・NFR5.1・NFR5.2、security-design.md 4節・5節、performance-design.md 5節、
// 計画の Step 17 の表）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない（流れに数えるのは 110 だけ）。
// - 組ごとに1つのテスト（support/displayCombos.ts の 20 組）で、11 の状態を順に作る（押せない項目はホバーとキーボードの
//   フォーカスに分けるため、検査は 12 回）。検査ごとに axe-core の違反 0 件・REQUIRED_RULES が流れた・横のはみ出しが無い
//   ことを確かめる。誤りの状態と、2語の氏名・長いメールアドレスの見本を含める。
// - 管理の API は差し替えの口（support/adminApiRoute.ts）で受ける。検査では一覧の GET・操作の POST を見本
//   （support/userAdminFixtures.ts）で返し、見本の無い GET は決まった失敗で返し、GET 以外で見本の無いものは打ち切って記録する。
//   各テストの終わりに、口が受けた件数 1 以上と打ち切りの記録 0 件を確かめる。口・見張りは page.goto とログインより前に張る。
//   120 は request の口で /api/admin/ の下へ要求を送らない。内部DB・Mailpit に書かない。初期管理者の状態は変えない。
// - 差し替えた失敗（409・通信の失敗）に Chrome が出す「Failed to load resource:」の表示は、位置の URL が管理の API の道の
//   ものだけを数えて引く（この中だけの扱い。共有の support/pageProblems.ts は変えない。130 と同じ扱い）。
// - 画面の時間の測りは既定の1組（light・md・blue・既定の幅）だけで1件。値は記録するだけで成否にしない（本番での判定は
//   Unverified。基盤の設計の R-01）。nextPage は見本の応答での描画の時間で API の時間を含まず、list は差し替えの口の
//   上乗せを含む。
// - test.step の題・注記・添付に、氏名・メールアドレス・パスワード・トークンを入れない（組と状態の名前と件数だけ）。
//   trace は e2eTraceMode()（既定 off）。失敗のときだけ、値を伏せた手がかりを添付 user-admin-diagnostics に残す。
// - 既存の 010〜100・130 と support/ の既存のファイルは変えない。
// - Intent 261003-user-admin-followup: 表示を閉じた後に行の「操作」へフォーカスが戻ること（FR1.3）と、開いたメニューの矩形が
//   表示の中に収まること（FR2.2。support/overflow.ts に測る関数を足した）を確かめる。G2 の観察として、言語の選択肢で Enter を
//   押して送信したときのフォーカスの行き先を注記 edit-enter-focus に記録する（送信の後に行の「操作」へ戻ることだけを確かめる）。
import { expect, test, type ConsoleMessage, type Page } from '@playwright/test'
import { loginAsAdmin, openSidebarItem } from './support/adminLogin'
import {
  expectNoBlockedWrites,
  isAdminApiPath,
  normalizeAdminPath,
  routeAdminApi,
  type AdminApiRoute,
} from './support/adminApiRoute'
import { missingRequiredRules, runAxe } from './support/axe'
import {
  DISPLAY_COMBOS,
  expectComboApplied,
  prepareCombo,
  type DisplayCombo,
} from './support/displayCombos'
import { routeLoginPreferences } from './support/loginPreferences'
import {
  describeContainment,
  describeOverflow,
  measureHorizontalOverflow,
  measureViewportContainment,
} from './support/overflow'
import { watchPage } from './support/pageProblems'
import { e2eTraceMode } from './support/traceMode'
import {
  startUserAdminDiagnostics,
  type UserAdminDiagnostics,
} from './support/userAdminDiagnostics'
import {
  conflictProblem,
  emptyListPage,
  inspectionListPage,
  measurementListPage,
  SELF_ROW_ID,
  TARGET_ROW_ID,
  USER_ADMIN_DETAIL_MARKER,
} from './support/userAdminFixtures'

test.use({ trace: e2eTraceMode() })

/** サイドバーの項目 */
const SIDEBAR_ITEM = '利用者の管理'
/** サイドバーのホームの項目 */
const HOME_ITEM = 'ホーム'
/** 一覧の API の道 */
const LIST_PATH = '/api/admin/users'
/** 止める操作の API の道の型 */
const SUSPEND_PATH = '/api/admin/users/{id}/suspend'
/** 検索に当たらない検索の文字 */
const NO_MATCH_SEARCH = 'no-such-user'
/** 検索の上限（254 コードポイント）を超える検索の文字 */
const TOO_LONG_SEARCH = 'あ'.repeat(255)
/** 測る回数 */
const RUNS = 5
/** 目標（NFR5.1・NFR5.2、記録のみ） */
const LIST_TARGET_MS = 2000
const NEXT_PAGE_TARGET_MS = 1500

/** 検査の状態の名前 */
type InspectionState =
  | '01-list'
  | '02-self-menu-open'
  | '03-confirm-dialog'
  | '04-edit-dialog-invalid'
  | '05-business-failure'
  | '06-search-too-long'
  | '07-edit-dialog'
  | '08-success-toast'
  | '09-load-failure'
  | '10-empty-search'
  | '11-disabled-item-hover'
  | '11-disabled-item-focus'

/** 一覧の表の行 */
function tableRows(page: Page) {
  return page.getByTestId('useradmin-table').locator('tbody tr')
}

/** 行の「操作」 */
function rowActionsButton(page: Page, userId: number) {
  return page.getByTestId(`useradmin-row-actions-${userId}`).getByRole('button')
}

/** 差し替えた失敗に出る読み込みの失敗の表示を集める（watchPage の後に張る）。 */
function watchExcludedConsole(page: Page): string[] {
  const excludedTexts: string[] = []
  page.on('console', (message: ConsoleMessage) => {
    if (message.type() !== 'error' || !message.text().startsWith('Failed to load resource:')) {
      return
    }
    let pathname = ''
    try {
      pathname = new URL(message.location().url).pathname
    } catch {
      return
    }
    if (isAdminApiPath(normalizeAdminPath(pathname))) {
      excludedTexts.push(`console: ${message.text()}`)
    }
  })
  return excludedTexts
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

/** 1つの状態を検査し、注記に件数と規則の名前だけを残す。 */
async function inspect(page: Page, combo: DisplayCombo, state: InspectionState): Promise<void> {
  const summary = await runAxe(page)
  const overflow = await measureHorizontalOverflow(page)
  const record = {
    combo: combo.name,
    state,
    violations: summary.violations.length,
    violationRules: [...new Set(summary.violations.map((violation) => violation.id))],
    incomplete: summary.incomplete,
    overflow,
  }
  test.info().annotations.push({ type: 'axe', description: JSON.stringify(record) })
  const label = `${combo.name} ${state}`
  expect(missingRequiredRules(summary), `${label}: 流れなかった規則`).toEqual([])
  expect(record.violationRules, `${label}: 違反`).toEqual([])
  expect(overflow.overflows, describeOverflow(label, overflow)).toBe(false)
}

/** 行の「操作」を開いて項目を選ぶ。 */
async function chooseRowAction(page: Page, userId: number, item: string): Promise<void> {
  await rowActionsButton(page, userId).click()
  await page.getByRole('menuitem', { name: item, exact: true }).click()
}

/**
 * 表示（Modal）を閉じた後、開いた元の行の「操作」へフォーカスが戻ることを確かめる（FR1.3。body に落ちる不具合の回帰の
 * 確かめ）。フォーカスが戻るのは背景の inert が外れた後のため、inert が外れるのを待つだけの形をやめ、この確かめで次の操作の
 * 前の待ちを兼ねる。
 */
async function expectFocusReturnedToRow(page: Page, userId: number): Promise<void> {
  await expect(rowActionsButton(page, userId)).toBeFocused()
}

/**
 * 開いたメニューの矩形が表示の中に収まることを確かめる（FR2.2）。メニューは位置を固定で置くため、文書の横の大きさの
 * 判定（inspect の measureHorizontalOverflow）では拾えない。
 */
async function expectMenuInViewport(
  page: Page,
  combo: DisplayCombo,
  state: InspectionState,
): Promise<void> {
  const menu = page.getByRole('menu')
  await expect(menu).toBeVisible()
  const measured = await measureViewportContainment(menu)
  test.info().annotations.push({
    type: 'menu-rect',
    description: JSON.stringify({ combo: combo.name, state, ...measured }),
  })
  expect(measured.contained, describeContainment(`${combo.name} ${state}`, measured)).toBe(true)
}

/** 一覧が見本の行で出るまで待つ。 */
async function expectInspectionList(page: Page): Promise<void> {
  await expect(tableRows(page)).toHaveCount(inspectionListPage().items.length)
}

/** 測った値の要約（数だけ） */
function summarize(
  times: readonly number[],
  targetMs: number,
  apiTimeIncluded: boolean,
  note: string,
) {
  const [first = 0, ...rest] = times
  return {
    first,
    rest,
    max: Math.max(...times),
    withinTarget: times.every((time) => time <= targetMs),
    targetMs,
    apiTimeIncluded,
    note,
  }
}

test.describe('120 user admin accessibility and screen time on the built WAR', () => {
  let diagnostics: UserAdminDiagnostics

  test.beforeEach(() => {
    diagnostics = startUserAdminDiagnostics()
  })

  // Playwright は1つ目の引数を分割代入の形で求めるため、軽い働き手の値（browserName）を受けて使わない。
  test.afterEach(async ({ browserName: _browserName }, testInfo) => {
    await diagnostics.attachOnFailure(testInfo)
  })

  for (const combo of DISPLAY_COMBOS) {
    test(`user admin ${combo.name}`, async ({ page }) => {
      // 11 の状態ごとに axe を流すため、既定の 30 秒を超える。
      test.setTimeout(120_000)
      const { baseURL } = test.info().project.use
      const watch = await watchPage(page)
      const excludedTexts = watchExcludedConsole(page)
      diagnostics.watch('admin', page, watch)
      const route: AdminApiRoute = await routeAdminApi(page, baseURL ?? '', 'inspect')
      const list = { failing: false }
      route.setMock('GET', LIST_PATH, (url) => {
        if (list.failing) {
          return { abort: true }
        }
        return url.searchParams.has('q')
          ? { status: 200, json: emptyListPage() }
          : { status: 200, json: inspectionListPage() }
      })
      await prepareCombo(page, combo)
      await routeLoginPreferences(page, { theme: combo.theme, fontSize: combo.fontSize })

      await diagnostics.step('log in and open the user admin screen', async () => {
        await loginAsAdmin(page)
        await expectComboApplied(page, combo)
        await openSidebarItem(page, SIDEBAR_ITEM)
        await expectInspectionList(page)
        await expectComboApplied(page, combo)
      })

      await diagnostics.step('check the list', async () => {
        await inspect(page, combo, '01-list')
      })

      await diagnostics.step('check the own row menu and its disabled items', async () => {
        await rowActionsButton(page, SELF_ROW_ID).click()
        const disabled = page.getByRole('menuitem', { name: '管理者の印を外す', exact: true })
        await expect(disabled).toBeVisible()
        await expect(disabled).toHaveAttribute('aria-disabled', 'true')
        await inspect(page, combo, '02-self-menu-open')
        await expectMenuInViewport(page, combo, '02-self-menu-open')
        await disabled.hover()
        await inspect(page, combo, '11-disabled-item-hover')
        await expectMenuInViewport(page, combo, '11-disabled-item-hover')
        await page.keyboard.press('Escape')
        await expect(disabled).toBeHidden()
        await page.mouse.move(0, 0)
        await rowActionsButton(page, SELF_ROW_ID).focus()
        await page.keyboard.press('Enter')
        await expect(disabled).toBeVisible()
        const items = page.getByRole('menuitem')
        const count = await items.count()
        for (let step = 0; step < count; step += 1) {
          const focusedDisabled = await page.evaluate(
            () => document.activeElement?.getAttribute('aria-disabled') === 'true',
          )
          if (focusedDisabled) {
            break
          }
          await page.keyboard.press('ArrowDown')
        }
        expect(
          await page.evaluate(() => document.activeElement?.getAttribute('aria-disabled')),
          `${combo.name}: フォーカスが押せない項目にある`,
        ).toBe('true')
        await inspect(page, combo, '11-disabled-item-focus')
        await expectMenuInViewport(page, combo, '11-disabled-item-focus')
        await page.keyboard.press('Escape')
        await expect(disabled).toBeHidden()
      })

      await diagnostics.step('check the confirm dialog', async () => {
        await chooseRowAction(page, TARGET_ROW_ID, '利用を止める')
        const dialog = page.getByRole('alertdialog', { name: '利用を止めますか？' })
        await expect(dialog).toBeVisible()
        await expect(dialog.getByTestId('useradmin-confirm-cancel')).toBeFocused()
        await inspect(page, combo, '03-confirm-dialog')
        await dialog.getByTestId('useradmin-confirm-cancel').click()
        await expect(dialog).toBeHidden()
        await expectFocusReturnedToRow(page, TARGET_ROW_ID)
        // Escape と閉じるボタンで閉じた場合も、行の「操作」へ戻る（FR1.3）。
        await chooseRowAction(page, TARGET_ROW_ID, '利用を止める')
        await expect(dialog.getByTestId('useradmin-confirm-cancel')).toBeFocused()
        await page.keyboard.press('Escape')
        await expect(dialog).toBeHidden()
        await expectFocusReturnedToRow(page, TARGET_ROW_ID)
        await chooseRowAction(page, TARGET_ROW_ID, '利用を止める')
        await dialog.getByTestId('modal-close-button').click()
        await expect(dialog).toBeHidden()
        await expectFocusReturnedToRow(page, TARGET_ROW_ID)
        // 「管理者の印を付ける」の確かめの表示（状態を変えない。開いて Escape で閉じるだけ。AC2.1.8）。
        await chooseRowAction(page, TARGET_ROW_ID, '管理者の印を付ける')
        const grant = page.getByRole('alertdialog', { name: '管理者の印を付けますか？' })
        await expect(grant.getByTestId('useradmin-confirm-cancel')).toBeFocused()
        await page.keyboard.press('Escape')
        await expect(grant).toBeHidden()
        await expectFocusReturnedToRow(page, TARGET_ROW_ID)
      })

      await diagnostics.step('check the business failure notice', async () => {
        route.setMock('POST', SUSPEND_PATH, { status: 409, json: conflictProblem() })
        await chooseRowAction(page, TARGET_ROW_ID, '利用を止める')
        const dialog = page.getByRole('alertdialog', { name: '利用を止めますか？' })
        await dialog.getByTestId('useradmin-confirm-submit').click()
        await expect(page.getByTestId('useradmin-failure-alert')).toBeVisible()
        await expect(dialog).toBeHidden()
        await expectFocusReturnedToRow(page, TARGET_ROW_ID)
        await expectInspectionList(page)
        expect(
          await page.locator('body').textContent(),
          `${combo.name}: detail の目印`,
        ).not.toContain(USER_ADMIN_DETAIL_MARKER)
        await inspect(page, combo, '05-business-failure')
        await page
          .getByTestId('useradmin-failure-alert')
          .getByRole('button', { name: '知らせを閉じる' })
          .click()
        await expect(page.getByTestId('useradmin-failure-alert')).toBeHidden()
      })

      await diagnostics.step('check the success toast', async () => {
        route.setMock('POST', SUSPEND_PATH, { status: 204 })
        await chooseRowAction(page, TARGET_ROW_ID, '利用を止める')
        const dialog = page.getByRole('alertdialog', { name: '利用を止めますか？' })
        await dialog.getByTestId('useradmin-confirm-submit').click()
        const toast = page.getByTestId('toast-container').getByText('さんの利用を止めました')
        await expect(toast).toBeVisible()
        await expect(dialog).toBeHidden()
        await expectFocusReturnedToRow(page, TARGET_ROW_ID)
        await inspect(page, combo, '08-success-toast')
        route.setMock('POST', SUSPEND_PATH, undefined)
        await expect(toast).toHaveCount(0, { timeout: 10_000 })
        await expectInspectionList(page)
      })

      await diagnostics.step('check the edit dialog', async () => {
        await chooseRowAction(page, TARGET_ROW_ID, '氏名・言語を直す')
        const dialog = page.getByTestId('useradmin-edit-dialog')
        await expect(dialog).toBeVisible()
        await inspect(page, combo, '07-edit-dialog')
        await dialog.getByTestId('useradmin-edit-name').fill('')
        await dialog.getByTestId('useradmin-edit-save').click()
        await expect(dialog.getByText('氏名を入れてください。')).toBeVisible()
        await inspect(page, combo, '04-edit-dialog-invalid')
        await dialog.getByTestId('useradmin-edit-cancel').click()
        await expect(dialog).toBeHidden()
        await expectFocusReturnedToRow(page, TARGET_ROW_ID)
        // Escape と閉じるボタンで閉じた場合も、行の「操作」へ戻る（FR1.3）。
        await chooseRowAction(page, TARGET_ROW_ID, '氏名・言語を直す')
        await expect(dialog.getByTestId('useradmin-edit-name')).toBeFocused()
        await page.keyboard.press('Escape')
        await expect(dialog).toBeHidden()
        await expectFocusReturnedToRow(page, TARGET_ROW_ID)
        await chooseRowAction(page, TARGET_ROW_ID, '氏名・言語を直す')
        await expect(dialog).toBeVisible()
        await page
          .getByRole('dialog', { name: '氏名と言語を直す' })
          .getByTestId('modal-close-button')
          .click()
        await expect(dialog).toBeHidden()
        await expectFocusReturnedToRow(page, TARGET_ROW_ID)
      })

      await diagnostics.step('check the search errors and the empty search', async () => {
        await page.getByTestId('useradmin-search-input').fill(TOO_LONG_SEARCH)
        await page.getByTestId('useradmin-search-submit').click()
        await expect(page.getByTestId('useradmin-search-error')).toBeVisible()
        await inspect(page, combo, '06-search-too-long')
        await page.getByTestId('useradmin-search-input').fill(NO_MATCH_SEARCH)
        await page.getByTestId('useradmin-search-submit').click()
        await expect(page.getByTestId('useradmin-empty-search')).toBeVisible()
        await inspect(page, combo, '10-empty-search')
      })

      await diagnostics.step('check the load failure', async () => {
        list.failing = true
        await page.getByTestId('useradmin-search-clear').click()
        await expect(page.getByTestId('useradmin-load-error')).toBeVisible()
        await inspect(page, combo, '09-load-failure')
        list.failing = false
        // 検索の文字は 200 の応答のときだけ置き換わる（機能設計の R-04）ため、もう一度読み込むと前の検索のまま読む。
        await page.getByTestId('useradmin-retry').click()
        await expect(page.getByTestId('useradmin-empty-search')).toBeVisible()
        await page.getByTestId('useradmin-search-clear').click()
        await expectInspectionList(page)
      })

      await page.waitForLoadState('networkidle')
      const remaining = withoutExcluded(watch.problems, excludedTexts)
      test.info().annotations.push({
        type: 'user-admin-problems',
        description: JSON.stringify({
          combo: combo.name,
          routeSeen: route.seen,
          blocked: route.blocked.length,
          failedReplies: route.failedReplies,
          excludedConsole: excludedTexts.length,
          remainingProblems: remaining.length,
          cspViolations: watch.cspViolations.length,
        }),
      })
      expectNoBlockedWrites(route, combo.name)
      expect(excludedTexts.length, `${combo.name}: 除いた表示の件数`).toBeLessThanOrEqual(
        route.failedReplies,
      )
      expect(watch.cspViolations, `${combo.name}: CSP の違反`).toEqual([])
      expect(remaining, `${combo.name}: 画面の問題`).toEqual([])
    })
  }

  test('edit dialog submitted with Enter on the language radio (G2 observation)', async ({
    page,
  }) => {
    // Intent 261003-user-admin-followup の G2: 言語の選択肢で Enter を押して送信したとき、送信中と送信の後にフォーカスが
    // どこにあるかを本物のブラウザで見て、注記 edit-enter-focus に記録する（コードは変えない）。
    const combo = DISPLAY_COMBOS.find(
      (candidate) =>
        candidate.theme === 'light' &&
        candidate.fontSize === 'md' &&
        candidate.brandColor === 'blue' &&
        candidate.viewport === undefined,
    )
    expect(combo, '既定の組（light・md・blue・既定の幅）').toBeDefined()
    if (combo === undefined) {
      return
    }
    const { baseURL } = test.info().project.use
    const watch = await watchPage(page)
    diagnostics.watch('admin', page, watch)
    const route = await routeAdminApi(page, baseURL ?? '', 'inspect')
    route.setMock('GET', LIST_PATH, { status: 200, json: inspectionListPage() })
    // 氏名・言語の保存（PUT）は、送信中の様子を見るため、こちらが離すまで答えを止める（差し替えの口より後に張るため優先される）。
    let release: () => void = () => {}
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    let profileRequests = 0
    await page.route(`**/api/admin/users/${TARGET_ROW_ID}/profile`, async (profileRoute) => {
      profileRequests += 1
      await held
      await profileRoute.fulfill({ status: 204 })
    })
    await prepareCombo(page, combo)
    await routeLoginPreferences(page, { theme: combo.theme, fontSize: combo.fontSize })
    await loginAsAdmin(page)
    await openSidebarItem(page, SIDEBAR_ITEM)
    await expectInspectionList(page)

    /** 今フォーカスのある要素の形（値は出さず、要素の種類と印だけ） */
    const describeFocus = () =>
      page.evaluate(() => {
        const active = document.activeElement
        if (active === null || active === document.body) {
          return { element: 'body' }
        }
        return {
          element: active.tagName.toLowerCase(),
          type: active.getAttribute('type'),
          testId: active.getAttribute('data-testid'),
          disabled: active instanceof HTMLInputElement ? active.disabled : null,
          inDialog: active.closest('[role="dialog"]') !== null,
          inRowActions: active.closest(`[data-testid^="useradmin-row-actions-"]`) !== null,
        }
      })

    await chooseRowAction(page, TARGET_ROW_ID, '氏名・言語を直す')
    const dialog = page.getByTestId('useradmin-edit-dialog')
    await expect(dialog).toBeVisible()
    // はじめのフォーカス（氏名）が当たった後に言語の選択肢へ移す（先に移すと、はじめのフォーカスで氏名へ戻されるため）。
    await expect(dialog.getByTestId('useradmin-edit-name')).toBeFocused()
    const english = page.getByRole('radio', { name: 'English' })
    await english.focus()
    await expect(english).toBeFocused()
    await page.keyboard.press('Enter')
    await expect.poll(() => profileRequests).toBe(1)
    await expect(english).toBeDisabled()
    const whileSubmitting = await describeFocus()
    // ブラウザが押せなくなった要素からフォーカスを外すか（描画の更新の時点で外す）を、少し待って見る。
    await page.evaluate(
      () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
    )
    const afterFrames = await describeFocus()
    release()
    await expect(dialog).toBeHidden()
    await expectFocusReturnedToRow(page, TARGET_ROW_ID)
    const afterSubmit = await describeFocus()
    test.info().annotations.push({
      type: 'edit-enter-focus',
      description: JSON.stringify({ whileSubmitting, afterFrames, afterSubmit }),
    })
    expectNoBlockedWrites(route, combo.name)
    expect(watch.cspViolations, `${combo.name}: CSP の違反`).toEqual([])
  })

  test('user admin screen time with the default combo', async ({ page }) => {
    // 一覧を開く 5 回と次のページ 5 回で、既定の 30 秒を超えうる。
    test.setTimeout(120_000)
    const combo = DISPLAY_COMBOS.find(
      (candidate) =>
        candidate.theme === 'light' &&
        candidate.fontSize === 'md' &&
        candidate.brandColor === 'blue' &&
        candidate.viewport === undefined,
    )
    expect(combo, '既定の組（light・md・blue・既定の幅）').toBeDefined()
    if (combo === undefined) {
      return
    }
    const { baseURL } = test.info().project.use
    const watch = await watchPage(page)
    diagnostics.watch('admin', page, watch)
    const route = await routeAdminApi(page, baseURL ?? '', 'measure-first')
    await prepareCombo(page, combo)
    await routeLoginPreferences(page, { theme: combo.theme, fontSize: combo.fontSize })
    const listTimes: number[] = []
    const nextPageTimes: number[] = []

    await diagnostics.step('log in with the default combo', async () => {
      await loginAsAdmin(page)
      await expectComboApplied(page, combo)
    })

    await diagnostics.step('measure opening the list', async () => {
      for (let run = 1; run <= RUNS; run += 1) {
        const started = Date.now()
        await openSidebarItem(page, SIDEBAR_ITEM)
        await expect(tableRows(page).first()).toBeVisible()
        listTimes.push(Date.now() - started)
        await openSidebarItem(page, HOME_ITEM)
        await expect(page.getByTestId('home-page')).toBeVisible()
      }
    })

    await diagnostics.step('measure the next page with two sample pages', async () => {
      route.setMode('measure-second')
      route.setMock('GET', LIST_PATH, (url) => ({
        status: 200,
        json: measurementListPage(url.searchParams.get('page') === '2' ? 2 : 1),
      }))
      await openSidebarItem(page, SIDEBAR_ITEM)
      await expect(page.getByText('測定 01', { exact: true })).toBeVisible()
      for (let run = 1; run <= RUNS; run += 1) {
        const started = Date.now()
        await page.getByRole('button', { name: '次へ', exact: true }).click()
        await expect(page.getByText('測定 21', { exact: true })).toBeVisible()
        nextPageTimes.push(Date.now() - started)
        await page.getByRole('button', { name: '前へ', exact: true }).click()
        await expect(page.getByText('測定 01', { exact: true })).toBeVisible()
      }
    })

    const record = {
      list: summarize(
        listTimes,
        LIST_TARGET_MS,
        true,
        '本物の一覧の API を差し替えの口を通して読む',
      ),
      nextPage: summarize(
        nextPageTimes,
        NEXT_PAGE_TARGET_MS,
        false,
        '見本の応答での描画の時間。API の時間を含まない',
      ),
    }
    test
      .info()
      .annotations.push({ type: 'user-admin-screen-ms', description: JSON.stringify(record) })
    await test.info().attach('user-admin-screen-ms', {
      body: JSON.stringify(record, null, 2),
      contentType: 'application/json',
    })
    expectNoBlockedWrites(route, combo.name)
    expect(watch.cspViolations, `${combo.name}: CSP の違反`).toEqual([])
    expect(watch.problems, `${combo.name}: 画面の問題`).toEqual([])
  })
})
