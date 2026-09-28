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
// 招待から登録を完了した利用者を作り、画面のフォームでログインする手伝い（Intent 260925-user-management の B5 の共用の手伝い。
// U7 の計画の9節の決定 3、U7 の NFR 設計 performance-design.md 4.2）。U7 の 080 の測りが使う。
// - 招待と初期管理者のアクセストークンは U5 の invitationSeed.ts、招待メールのリンクの取り出しは U6 の mailpit.ts を使う
//   （既存の手伝いは変えない）。登録の完了は画面ではなく API で行う（画面の流れは E2E-1 の 090 が確かめる）。
// - 宛先は実行ごとに重ならない `u7-perf-<印>@example.com`、パスワードは実行ごとに randomBytes から作る
//   `e2e-u7-pw-<16進>`（リポジトリに置かない）。
// - トークン・パスワード・アクセストークン・宛先・リンクは戻り値とテストの変数だけに持ち、失敗の知らせ・console・注記・添付・
//   標準出力に出さない（件数と種類だけ）。Mailpit の API は GET だけ（書き込まず、メールを消さない）。
import { randomBytes } from 'node:crypto'
import { expect, type APIRequestContext, type Page } from '@playwright/test'
import type { CompleteRegistrationRequest } from '../../src/features/registration/registrationApi'
import { readRegistrationToken } from '../../src/features/registration/registrationToken'
import { readInvitationList, requestAdminAccessToken } from './invitationSeed'
import { findInvitationLink, MAILPIT_API_URL } from './mailpit'

/** 作った利用者の氏名（固定のテストの値） */
export const REGISTERED_DISPLAY_NAME = '計測 花子'

/** 作った利用者（値は戻り値とテストの変数だけに持つ） */
export interface RegisteredUser {
  email: string
  password: string
}

/** 測りを行えない理由の種類 */
export type RegistrationUnavailable = 'invitation-disabled' | 'mailpit-unreachable'

/** パスワードの変更の測りで交互に使うパスワードを作る（12 コードポイント以上、72 バイト以下）。 */
export function newRunPassword(): string {
  return `e2e-u7-pw-${randomBytes(12).toString('hex')}`
}

/**
 * 招待から利用者を作れるかを確かめる。招待を使える設定（invitationEnabled）と、Mailpit の API に届くか。
 * 使えないときは理由の種類だけを返す。
 */
export async function registrationPrerequisites(
  request: APIRequestContext,
  adminToken: string,
): Promise<RegistrationUnavailable | undefined> {
  const list = (await readInvitationList(request, adminToken)) as { invitationEnabled?: unknown }
  if (list.invitationEnabled !== true) {
    return 'invitation-disabled'
  }
  try {
    const response = await fetch(`${MAILPIT_API_URL}/api/v1/info`, { method: 'GET' })
    return response.ok ? undefined : 'mailpit-unreachable'
  } catch {
    return 'mailpit-unreachable'
  }
}

/**
 * 招待の API で1件を置き（言語 ja）、Mailpit のメールからリンクを取り出し、登録の完了の API で利用者を作る
 * （テーマ light・文字の大きさ md・言語 ja）。宛先とパスワードを返す。
 */
export async function createRegisteredUser(
  request: APIRequestContext,
  runTag: string,
): Promise<RegisteredUser> {
  const adminToken = await requestAdminAccessToken(request)
  const email = `u7-perf-${runTag}@example.com`
  const invited = await request.post('/api/admin/invitations', {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { email, language: 'ja' },
  })
  expect(invited.status(), '招待の API の状態コード').toBe(201)
  const body = (await invited.json()) as { sendResult?: unknown }
  expect(body.sendResult === 'SENT', '招待メールの送信の結果が SENT').toBe(true)

  const link = await findInvitationLink(email)
  const token = readRegistrationToken(new URL(link).hash)
  expect(token !== undefined, '招待のリンクのトークンを読めた').toBe(true)

  const password = newRunPassword()
  const complete: CompleteRegistrationRequest = {
    token: token ?? '',
    displayName: REGISTERED_DISPLAY_NAME,
    password,
    passwordConfirmation: password,
    language: 'ja',
    theme: 'light',
    fontSize: 'md',
  }
  const completed = await request.post('/api/registration/complete', { data: complete })
  expect(completed.status(), '登録の完了の API の状態コード').toBe(204)
  return { email, password }
}

/** ログインの画面のフォームでログインし、ホームの画面が出るまで待つ（adminLogin.ts の loginAsAdmin と同じ道）。 */
export async function loginWithForm(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/')
  await expect(page.getByTestId('login-layout')).toBeVisible()
  await page.getByTestId('login-form-email-input').fill(email)
  await page.getByTestId('login-form-password-input').fill(password)
  await page.getByTestId('login-form-submit-button').click()
  await expect(page.getByTestId('home-page')).toBeVisible()
}

/** アプリシェルのユーザーメニューを開き、項目を名前で選ぶ（開き口は氏名で探さない）。 */
export async function openUserMenuItem(page: Page, name: string): Promise<void> {
  await page.getByTestId('app-shell').getByTestId('dropdown-trigger').click()
  await page.getByRole('menuitem', { name, exact: true }).click()
}
