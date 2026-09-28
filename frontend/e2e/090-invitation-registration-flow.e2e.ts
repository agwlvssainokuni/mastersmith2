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
// この Intent の代表の流れ E2E-1（Intent 260925-user-management の U6、functional-spec.md の W13、NFR1.2・NFR1.3・NFR1.5・
// NFR2.1・NFR6.1・NFR9.5・NFR9.10）: 管理者でログイン → 招待の画面（U5）で招待 → 手元の受け手（Mailpit）からリンクを取り出す
// → 登録の完了 → ログインの画面の案内とメールアドレスの持ち越し → 新しい利用者でログイン → 管理画面に入れない → ログアウト。
// - 前のテストが作った状態に頼らない。管理者のログインと招待を自分で行い、宛先は実行ごとに重ならない値にする（最後の番号）。
// - 何も差し替えない（本物の WAR・Mailpit・一時の内部DB）。招待を1件置き、Mailpit に1通届き、新しい利用者と監査の記録が残る。
//   Mailpit のメールは消さず、Mailpit の API に書き込まない（基盤の設計の Q1 C）。
// - リンクを開いてからフォーム（メールアドレスの欄）が見えるまでの時間を、キャッシュが空の新しいコンテキストで5回測る
//   （NFR6.1、performance-design.md 2節）。時間では失敗させず、値と目標（2,000 ミリ秒）以内の回数を注記と添付に残す。
//   同じ5回のアドレス欄の #token=・CSP の違反・ブラウザの保存のトークンの確かめは失敗の条件にする。
// - 登録は、招待の後に管理者のコンテキストを閉じ、新しいコンテキスト（招待された人のブラウザ）で行う（U6 の計画の9節の決定 1）。
//   本物の確かめの応答の項目の名前と型が見本（support/registrationFixtures.ts の VERIFY_SAMPLE）と同じことを毎回確かめる。
// - test.step の題・注記・添付・失敗の知らせに、リンク・トークン・宛先・パスワード・初期管理者のメールアドレスを入れない
//   （基盤の設計の N9・Q2 A）。リンクは page.goto のまま開く（HTML の報告と失敗のときのトレースに載ることは受け入れた）。
// - 既存の 010〜070 は変えない。
import { randomBytes } from 'node:crypto'
import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'
import { loginAsAdmin, openSidebarItem } from './support/adminLogin'
import { findInvitationLink } from './support/mailpit'
import { watchPage, type PageWatch } from './support/pageProblems'
import { shapeOf, VERIFY_SAMPLE } from './support/registrationFixtures'

const RUNS = 5
const FORM_TARGET_MS = 2_000
const VERIFY_PATH = '/api/registration/verify'
const INVITEE_NAME = '招待 花子'
const DISPLAY_SETTINGS_KEY = 'mastersmith.display-settings'

/** 実行ごとに重ならない宛先とパスワード（リポジトリに値を置かない） */
function newInvitee(): { email: string; password: string } {
  const tag = `${Date.now()}-${randomBytes(4).toString('hex')}`
  return {
    email: `e2e-invitee-${tag}@example.com`,
    password: `e2e-invitee-pw-${randomBytes(8).toString('hex')}`,
  }
}

/** キャッシュが空の新しいコンテキスト（テストの設定の baseURL・表示の幅・ja-JP） */
async function newContext(browser: Browser): Promise<BrowserContext> {
  const { baseURL, viewport } = test.info().project.use
  return browser.newContext({ baseURL, viewport, locale: 'ja-JP' })
}

/** ブラウザの保存（localStorage・sessionStorage）のすべての鍵と値を1つの文字列にする。 */
async function storedValues(page: Page): Promise<string> {
  return page.evaluate(() => {
    const values: string[] = []
    for (const storage of [window.localStorage, window.sessionStorage]) {
      for (let i = 0; i < storage.length; i += 1) {
        const key = storage.key(i)
        if (key !== null) {
          values.push(`${key}=${storage.getItem(key) ?? ''}`)
        }
      }
    }
    return values.join('\n')
  })
}

/**
 * 招待のリンクを開く。開けないときの Playwright の失敗の知らせは URL（トークン）を含むため、種類だけの失敗に置き換える。
 */
async function openLink(page: Page, link: string): Promise<void> {
  try {
    await page.goto(link)
  } catch {
    throw new Error('招待のリンクを開けませんでした（WAR の起動を確かめる）')
  }
}

/** 画面の問題と CSP の違反が無いこと（件数だけを失敗の知らせに出す） */
function expectQuietPage(watch: PageWatch, label: string): void {
  expect(watch.cspViolations.length, `${label}: CSP の違反の件数`).toBe(0)
  expect(watch.problems.length, `${label}: 画面の問題の件数`).toBe(0)
}

test.describe('090 invitation to registration flow (E2E-1) on the built WAR', () => {
  test('an administrator invites, the invitee registers from the mail link and logs in without admin access', async ({
    browser,
  }) => {
    const invitee = newInvitee()
    let link = ''

    await test.step('administrator invites a user from the invitations screen', async () => {
      const context = await newContext(browser)
      try {
        const page = await context.newPage()
        const watch = await watchPage(page)
        await loginAsAdmin(page)
        await openSidebarItem(page, '利用者の招待')
        await expect(page.getByTestId('invitation-admin-page')).toBeVisible()
        await page.getByTestId('invitation-invite-button').click()
        const dialog = page.getByTestId('invitation-invite-dialog')
        await expect(dialog).toBeVisible()
        await page.getByTestId('invitation-invite-email-input').fill(invitee.email)
        await dialog.getByRole('radio', { name: '日本語' }).check()
        const created = page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === '/api/admin/invitations' &&
            response.request().method() === 'POST',
        )
        await page.getByTestId('invitation-invite-submit').click()
        expect((await created).status(), '招待の応答の状態コード').toBe(201)
        await expect(dialog).toBeHidden()
        // 送信の結果が SENT なら失敗の知らせは出ない
        await expect(page.getByTestId('invitation-failure-alert')).toHaveCount(0)
        expectQuietPage(watch, 'administrator')
      } finally {
        // 管理者のコンテキストはここで閉じる（招待された人は自分のブラウザで開く）
        await context.close()
      }
    })

    await test.step('take the registration link from the local mail receiver', async () => {
      link = await findInvitationLink(invitee.email)
      const { baseURL } = test.info().project.use
      expect(link.startsWith(`${baseURL ?? ''}/register#token=`), 'ベース URL で始まるリンク').toBe(
        true,
      )
    })
    const token = decodeURIComponent(new URL(link).hash.replace(/^#token=/, ''))
    expect(token.length > 0, 'リンクのトークン').toBe(true)

    await test.step('measure the time from opening the link to the form five times', async () => {
      const runs: number[] = []
      for (let run = 1; run <= RUNS; run += 1) {
        const context = await newContext(browser)
        try {
          const page = await context.newPage()
          const watch = await watchPage(page)
          const started = Date.now()
          await openLink(page, link)
          await expect(page.getByTestId('registration-email-input')).toBeVisible()
          runs.push(Date.now() - started)
          expect(page.url().includes('#token='), `run ${run}: アドレス欄のフラグメント`).toBe(false)
          expect((await storedValues(page)).includes(token), `run ${run}: 保存のトークン`).toBe(
            false,
          )
          await page.waitForLoadState('networkidle')
          expectQuietPage(watch, `run ${run}`)
        } finally {
          await context.close()
        }
      }
      const record = {
        runs,
        withinTarget: runs.filter((ms) => ms <= FORM_TARGET_MS).length,
        targetMs: FORM_TARGET_MS,
      }
      test
        .info()
        .annotations.push({ type: 'registration-form-ms', description: JSON.stringify(record) })
      await test.info().attach('registration-form.json', {
        body: JSON.stringify(record, null, 2),
        contentType: 'application/json',
      })
    })

    const context = await newContext(browser)
    try {
      const page = await context.newPage()
      const watch = await watchPage(page)

      await test.step('invitee opens the link and completes the registration', async () => {
        const verified = page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === VERIFY_PATH &&
            response.request().method() === 'POST',
        )
        await openLink(page, link)
        const response = await verified
        expect(response.status(), '確かめの状態コード').toBe(200)
        const body: unknown = await response.json()
        // 値は比べず、項目の名前と型だけを見本と比べる（NFR 設計の Q1 A）
        expect(shapeOf(body), '本物の確かめの応答と見本の形').toEqual(shapeOf(VERIFY_SAMPLE))
        const email = page.getByTestId('registration-email-input')
        await expect(email).toBeVisible()
        expect(page.url().includes('#token='), 'アドレス欄のフラグメント').toBe(false)
        expect((await email.inputValue()) === invitee.email, 'メールアドレスの欄が宛先').toBe(true)
        await page.getByTestId('registration-display-name-input').fill(INVITEE_NAME)
        await page.getByTestId('registration-password-input').fill(invitee.password)
        await page.getByTestId('registration-password-confirmation-input').fill(invitee.password)
        await page.getByTestId('registration-submit-button').click()
      })

      await test.step('login page shows the notice and keeps the email address', async () => {
        await expect(page.getByTestId('login-layout')).toBeVisible()
        await expect(page.getByTestId('login-form-registered-alert')).toBeVisible()
        const email = await page.getByTestId('login-form-email-input').inputValue()
        expect(email === invitee.email, 'ログインの画面のメールアドレスの欄').toBe(true)
        const url = page.url()
        expect(url.includes('@') || url.includes('%40'), 'URL のメールアドレス').toBe(false)
        const stored = await page.evaluate(
          (key) => window.localStorage.getItem(key),
          DISPLAY_SETTINGS_KEY,
        )
        expect(JSON.parse(stored ?? '{}'), '保存した表示の設定').toEqual({
          language: 'ja',
          theme: 'system',
          fontSize: 'md',
        })
        const values = await storedValues(page)
        expect(values.includes(token), '保存のトークン').toBe(false)
        expect(values.includes(invitee.password), '保存のパスワード').toBe(false)
        expect(values.includes(invitee.email), '保存の宛先').toBe(false)
      })

      await test.step('new user logs in and cannot use the administration screens', async () => {
        await page.getByTestId('login-form-password-input').fill(invitee.password)
        await page.getByTestId('login-form-submit-button').click()
        await expect(page.getByTestId('home-page')).toBeVisible()
        for (const name of ['管理', '利用者の招待', 'DSL']) {
          await expect(
            page.getByRole('link', { name, exact: true }),
            `${name} のリンク`,
          ).toHaveCount(0)
        }
        for (const path of ['/admin/invitations', '/admin']) {
          await page.goto(path)
          await expect(
            page.getByTestId('not-found-page'),
            `${path} は見つからない表示`,
          ).toBeVisible()
        }
      })

      await test.step('new user logs out', async () => {
        await page.getByRole('button', { name: INVITEE_NAME }).click()
        await page.getByRole('menuitem', { name: 'ログアウト' }).click()
        await expect(page.getByTestId('login-layout')).toBeVisible()
      })

      await page.waitForLoadState('networkidle')
      expectQuietPage(watch, 'invitee')
    } finally {
      await context.close()
    }
  })
})
