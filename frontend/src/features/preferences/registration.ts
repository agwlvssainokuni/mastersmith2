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
// プリファレンスとパスワードの変更の画面を画面の骨組みに登録する（frontend-components.md の 6節、functional-spec.md の W1）。
// 画面はどちらも SHELL・LOGGED_IN で、遅延読み込み（入口の JavaScript に入れない。NFR6.6）。入り口はユーザーメニューの
// 2つの項目（path で読み込み直しなしで移る、ログアウトの 100 より前）だけで、サイドバーの項目は無い。
// 画面で隠すことはサーバー側の判定の代わりにしない（/api/me/ の 401 はサーバーが返す）。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { preferencesMessages } from './messages'

const PreferencesPage = lazy(() =>
  import('./PreferencesPage').then((module) => ({ default: module.PreferencesPage })),
)

const PasswordChangePage = lazy(() =>
  import('./PasswordChangePage').then((module) => ({ default: module.PasswordChangePage })),
)

/** プリファレンスの画面の URL */
export const PREFERENCES_PATH = '/me/preferences'

/** パスワードの変更の画面の URL */
export const PASSWORD_CHANGE_PATH = '/me/password'

/** プリファレンスとパスワードの変更の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'preferences',
  routes: [
    { path: PREFERENCES_PATH, screen: PreferencesPage, layout: 'SHELL', access: 'LOGGED_IN' },
    {
      path: PASSWORD_CHANGE_PATH,
      screen: PasswordChangePage,
      layout: 'SHELL',
      access: 'LOGGED_IN',
    },
  ],
  userMenuItems: [
    {
      id: 'preferences-open',
      labelKey: 'preferences.menu.preferences',
      path: PREFERENCES_PATH,
      order: 80,
    },
    {
      id: 'preferences-password',
      labelKey: 'preferences.menu.password',
      path: PASSWORD_CHANGE_PATH,
      order: 90,
    },
  ],
  messages: preferencesMessages,
}
