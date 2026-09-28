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
// ビルドした WAR で、招待の管理の画面（U5）の表示の設定の組ごとのアクセシビリティと、一覧と次のページが出るまでの時間を
// 確かめる（Intent 260925-user-management の U5。NFR6.1・NFR6.2・NFR7.3・NFR7.4・NFR9.3・NFR9.9）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない。
// - 組ごとに1つのテスト（20 組）。ログインの画面から初期管理者でログインし、サイドバーの「利用者の招待」から開く。
//   一覧の API（GET /api/admin/invitations?page=…）の答えだけを見本（support/invitationFixtures.ts）に差し替え、
//   一覧（行あり）→ 招待の入力の Modal → 取り消しの確かめの Modal（(a)・(b) は警告も）の状態で検査する。
//   Modal は開くだけで、招待・取り消しの要求を送らない（招待の管理の POST が0件であることを確かめる）。
// - ログインの後は利用者の設定が当たるため、組のテーマと文字の大きさは、ログインと復元の本物の応答の user.theme・
//   user.fontSize の2項目だけを書き換えて当てる（support/loginPreferences.ts。U5 の計画の Step 16 の依頼者の決定 A。
//   サーバーの状態は変えない。NFR 設計の security-design.md 6節の「一覧の API だけ」より差し替えの範囲が広い）。
// - (c) の組では、Tab でフォーカスした行の「取り消す」が表示の幅の中に入ることを確かめる（矢印のキーは使わない。
//   機能設計の承認の場の U5 R-01）。
// - green・orange の組の primary の Button のコントラスト不足は、U4 の既知の制約として、状態ごとの名前の一覧
//   （support/axe.ts の INVITATION_KNOWN_VIOLATIONS）で扱う。ほかの違反は失敗にする。
// - 画面の時間は記録だけで失敗させない（統合の関門にしない）。CSP の違反・画面の問題・本物の応答と見本の形の違いは失敗にする。
// - test.step の題・注記・添付に、アクセストークン・パスワード・初期管理者と測定の招待のメールアドレスを入れない。
// - 既存の 010〜050 は変えない。
import { expect, test, type Page } from '@playwright/test'
import { loginAsAdmin, openSidebarItem } from './support/adminLogin'
import { routeLoginPreferences } from './support/loginPreferences'
import {
  INVITATION_KNOWN_VIOLATIONS,
  missingRequiredRules,
  runAxe,
  splitKnownViolations,
  type InvitationAxeState,
} from './support/axe'
import {
  DISPLAY_COMBOS,
  expectComboApplied,
  prepareCombo,
  type DisplayCombo,
} from './support/displayCombos'
import {
  hasInvitationPageShape,
  sampleInvitationPage,
  unavailableInvitationPage,
} from './support/invitationFixtures'
import {
  newRunTag,
  readInvitationList,
  requestAdminAccessToken,
  seedInvitations,
} from './support/invitationSeed'
import { describeOverflow, measureHorizontalOverflow } from './support/overflow'
import { watchPage } from './support/pageProblems'
import type { InvitationPage } from '../src/features/invitation/api/types'

const LIST_TARGET_MS = 2_000
const NEXT_TARGET_MS = 1_500
const RUNS = 5
const SEED_COUNT = 21
const SIDEBAR_ITEM = '利用者の招待'
const TABLE_NAME = '招待中の人'

/** 招待の管理の API のパス */
function isInvitationsPath(url: URL): boolean {
  return (
    url.pathname === '/api/admin/invitations' || url.pathname.startsWith('/api/admin/invitations/')
  )
}

/**
 * 一覧の GET（問い合わせ page= の付いたもの）だけを見本の答えに差し替える。POST は差し替えない。
 * 返す見本は戻り値の current を入れ替えて変える（警告の状態）。差し替えはこのページの中だけで効く。
 */
async function routeList(page: Page, body: InvitationPage): Promise<{ current: InvitationPage }> {
  const listBody = { current: body }
  await page.route(
    (url) => url.pathname === '/api/admin/invitations' && url.searchParams.has('page'),
    (route) => {
      if (route.request().method() !== 'GET') {
        return route.fallback()
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(listBody.current),
      })
    },
  )
  return listBody
}

/** 招待の管理の POST を数える。 */
function countInvitationPosts(page: Page): { count: number } {
  const posts = { count: 0 }
  page.on('request', (request) => {
    if (request.method() === 'POST' && isInvitationsPath(new URL(request.url()))) {
      posts.count += 1
    }
  })
  return posts
}

/** 1つの状態の検査の記録を残し、合否を確かめる。 */
async function checkState(
  page: Page,
  combo: DisplayCombo,
  state: InvitationAxeState,
): Promise<void> {
  const summary = await runAxe(page)
  const { known, unexpected, expectedKnown } = splitKnownViolations(
    summary,
    combo.brandColor,
    INVITATION_KNOWN_VIOLATIONS[state],
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
    `${combo.name} ${state}: 既知の違反が一覧と違います（make-you-chic-ui が直したなら INVITATION_KNOWN_VIOLATIONS と README を見直す）`,
  ).toEqual(expectedKnown)
  expect(overflow.overflows, describeOverflow(`${combo.name} ${state}`, overflow)).toBe(false)
}

test.describe('060 invitation screen accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`invitations ${combo.name}`, async ({ page }) => {
      const watch = await watchPage(page)
      const posts = countInvitationPosts(page)
      await prepareCombo(page, combo)
      const listBody = await routeList(page, sampleInvitationPage())
      const login = await routeLoginPreferences(page, {
        theme: combo.theme,
        fontSize: combo.fontSize,
      })
      const table = page.getByRole('table', { name: TABLE_NAME })
      const firstRevoke = page.getByTestId('invitation-row-revoke-1001')

      await test.step('open the invitations screen', async () => {
        await loginAsAdmin(page)
        await openSidebarItem(page, SIDEBAR_ITEM)
        await expect(table.locator('tbody tr')).toHaveCount(20)
        await expectComboApplied(page, combo)
        expect(login.rewritten, 'ログインの応答の書き換え').toBe(1)
      })

      await test.step('list with rows', async () => {
        await checkState(page, combo, 'list')
      })

      if (combo.viewport) {
        await test.step('tab to the revoke button of the first row', async () => {
          await page.getByTestId('invitation-row-resend-1001').focus()
          await page.keyboard.press('Tab')
          await expect(firstRevoke).toBeFocused()
          const box = await firstRevoke.boundingBox()
          const width = combo.viewport?.width ?? 0
          expect(box, '取り消すの位置').not.toBeNull()
          expect(box?.x ?? -1, '取り消すの左端').toBeGreaterThanOrEqual(0)
          expect((box?.x ?? 0) + (box?.width ?? 0), '取り消すの右端').toBeLessThanOrEqual(width)
        })
      }

      await test.step('invite dialog', async () => {
        await page.getByTestId('invitation-invite-button').click()
        await expect(page.getByTestId('invitation-invite-dialog')).toBeVisible()
        await checkState(page, combo, 'inviteDialog')
        await page.getByTestId('invitation-invite-cancel').click()
        await expect(page.getByTestId('invitation-invite-dialog')).toBeHidden()
      })

      await test.step('revoke confirmation dialog', async () => {
        await firstRevoke.click()
        await expect(page.getByTestId('invitation-revoke-dialog')).toBeVisible()
        await checkState(page, combo, 'revokeDialog')
        await page.getByTestId('invitation-revoke-cancel').click()
        await expect(page.getByTestId('invitation-revoke-dialog')).toBeHidden()
      })

      if (!combo.viewport) {
        await test.step('warning when invitations are unavailable', async () => {
          listBody.current = unavailableInvitationPage()
          await page.reload()
          await expect(page.getByTestId('invitation-unavailable-alert')).toBeVisible()
          expect(login.rewritten, '復元の応答の書き換え').toBe(2)
          await expect(table.locator('tbody tr')).toHaveCount(20)
          await expectComboApplied(page, combo)
          await checkState(page, combo, 'unavailable')
        })
      }

      await page.waitForLoadState('networkidle')
      expect(posts.count, '招待の管理の POST').toBe(0)
      expect(watch.cspViolations, `${combo.name}: CSP の違反`).toEqual([])
      expect(watch.problems, `${combo.name}: 画面の問題`).toEqual([])
    })
  }

  test('list and next page time with real invitations, without CSP violations', async ({
    browser,
    request,
  }) => {
    const token = await requestAdminAccessToken(request)
    const before = (await readInvitationList(request, token)) as { invitationEnabled?: unknown }
    // 招待を使える設定が無い WAR では測れないため飛ばす（Build and Test で Unverified）。E2E の WAR には SMTP とベース URL が
    // 渡る（playwright.config.ts の webServer.env）ため、念のための備え。ログインと一覧の API は設定が無くても成功する
    // （U3 の InvitationAdminApiIT.notConfigured が確かめる。NFR 設計の承認の場の U5 R-01・R-02）。
    if (before.invitationEnabled !== true) {
      test.info().annotations.push({
        type: 'skip-reason',
        description: '招待を使える設定が無いため測定を飛ばした',
      })
      test.skip(true, '招待を使える設定が無い')
      return
    }
    const seeded = await seedInvitations(request, token, SEED_COUNT, newRunTag())

    const { baseURL, viewport } = test.info().project.use
    const listTimes: number[] = []
    const nextTimes: number[] = []
    for (let run = 1; run <= RUNS; run += 1) {
      const context = await browser.newContext({ baseURL, viewport, locale: 'ja-JP' })
      try {
        const page = await context.newPage()
        const watch = await watchPage(page)
        await loginAsAdmin(page)
        const rows = page.getByRole('table', { name: TABLE_NAME }).locator('tbody tr')
        const listResponse = page.waitForResponse((response) => {
          const url = new URL(response.url())
          return url.pathname === '/api/admin/invitations' && response.request().method() === 'GET'
        })

        const listStarted = Date.now()
        await openSidebarItem(page, SIDEBAR_ITEM)
        await expect(rows.nth(19)).toBeVisible()
        listTimes.push(Date.now() - listStarted)

        if (run === 1) {
          const body: unknown = await (await listResponse).json()
          expect(hasInvitationPageShape(body), '本物の一覧の応答と見本の形').toBe(true)
        }

        const nextStarted = Date.now()
        await page.getByRole('button', { name: '次へ' }).click()
        await expect(page.getByText(/（2 \/ \d+ ページ）/)).toBeVisible()
        await expect(rows.first()).toBeVisible()
        nextTimes.push(Date.now() - nextStarted)

        await page.getByRole('button', { name: '前へ' }).click()
        await expect(rows.nth(19)).toBeVisible()
        await page.waitForLoadState('networkidle')
        expect(watch.cspViolations, `run ${run}: CSP の違反`).toEqual([])
        expect(watch.problems, `run ${run}: 画面の問題`).toEqual([])
      } finally {
        await context.close()
      }
    }
    const record = {
      seeded,
      list: {
        runs: listTimes,
        withinTarget: listTimes.filter((ms) => ms <= LIST_TARGET_MS).length,
        targetMs: LIST_TARGET_MS,
      },
      nextPage: {
        runs: nextTimes,
        withinTarget: nextTimes.filter((ms) => ms <= NEXT_TARGET_MS).length,
        targetMs: NEXT_TARGET_MS,
      },
    }
    test
      .info()
      .annotations.push({ type: 'invitation-screen-ms', description: JSON.stringify(record) })
    await test.info().attach('invitation-screen.json', {
      body: JSON.stringify(record, null, 2),
      contentType: 'application/json',
    })
  })
})
