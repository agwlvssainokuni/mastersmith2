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
// 管理者のログインと、サイドバーの項目で画面を開く手伝い（Intent 260925-user-management の B5 の共用の手伝い。
// U5 の NFR 設計 logical-components.md 5.4、Q3: A）。U5 の 060 が作り、U6・U7 の検査と E2E-1 も使う。
// ログインは画面のフォームから、playwright.config.ts の初期管理者（実行ごとに作るパスワード）で行う（既存の 030・040 と同じ道）。
// 初期管理者の設定は変えず読むだけ。メールアドレス・パスワードを test.step の題・注記・添付に入れない。
import { expect, type Page } from '@playwright/test'
import { adminEmail, adminPassword } from '../../playwright.config'

/** ログインの画面のフォームから初期管理者でログインし、ホームの画面が出るまで待つ。 */
export async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('/')
  await expect(page.getByTestId('login-layout')).toBeVisible()
  await page.getByTestId('login-form-email-input').fill(adminEmail)
  await page.getByTestId('login-form-password-input').fill(adminPassword)
  await page.getByTestId('login-form-submit-button').click()
  await expect(page.getByTestId('home-page')).toBeVisible()
}

/** サイドバーのリンクを名前で押す。 */
export async function openSidebarItem(page: Page, name: string): Promise<void> {
  await page.getByRole('link', { name, exact: true }).click()
}
