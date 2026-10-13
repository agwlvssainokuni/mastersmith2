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
// ユーザーメニューの開き口（Intent 261004-role-menu の U7 の L12、計画 12節 D-9）。
// トップバーに作業ロールの切り替え（`work-role-switcher` の入れ物の中の Dropdown）が並ぶため、`dropdown-trigger` は2つになる。
// ユーザーメニューのトリガーは、アプリシェルのトップバー（header）の中で、`work-role-switcher` の子孫でない
// `dropdown-trigger` の1件に絞る。表の行の「操作」の Dropdown（main の中）とも重ならない。
// 開き口は氏名で探さない（氏名を選択子・失敗の知らせに出さないため）。
import { expect, type Locator, type Page } from '@playwright/test'

/** トップバーの中の、作業ロールの入れ物の外の Dropdown のトリガー */
const USER_MENU_TRIGGER =
  'xpath=.//*[@data-testid="dropdown-trigger"][not(ancestor::*[@data-testid="work-role-switcher"])]'

/** ユーザーメニューのトリガー（1件に絞れることを確かめる） */
export function userMenuTrigger(page: Page): Locator {
  return page.getByTestId('app-shell').locator('header').locator(USER_MENU_TRIGGER)
}

/** ユーザーメニューを開く。 */
export async function openUserMenu(page: Page): Promise<void> {
  const trigger = userMenuTrigger(page)
  await expect(trigger, 'ユーザーメニューのトリガーが1件').toHaveCount(1)
  await trigger.click()
}

/** ユーザーメニューを開き、項目を名前で選ぶ。 */
export async function openUserMenuItem(page: Page, name: string): Promise<void> {
  await openUserMenu(page)
  await page.getByRole('menuitem', { name, exact: true }).click()
}
