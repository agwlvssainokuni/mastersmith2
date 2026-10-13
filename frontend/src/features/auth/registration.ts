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
// 認証の機能を U1 の画面の骨組みに登録する（BR8.1、BR8.7）。U1 のファイルは書き換えない。
// 画面の部品は遅延読み込みにし、起動時にはログイン状態の提供元と文言だけを渡す。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { initializeAuthSession } from './authSession'
import { loginStateProvider } from './loginStateProvider'
import { markLogoutIntent } from './logoutIntent'

const LoginPage = lazy(() =>
  import('./LoginPage').then((module) => ({ default: module.LoginPage })),
)

// ログアウトは道 /logout への移動にする（Intent 261004-role-menu の U7 の D27。S4 の未保存の確かめを当てるため）。
// 画面は遅延読み込みで、入口の JavaScript に入れない（計画 11節）。
const LogoutPage = lazy(() =>
  import('./LogoutPage').then((module) => ({ default: module.LogoutPage })),
)

/** ログアウトの画面の道 */
export const LOGOUT_PATH = '/logout'

initializeAuthSession()

/** 認証の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'auth',
  routes: [
    {
      path: '/login',
      screen: LoginPage,
      layout: 'STANDALONE',
      access: 'PUBLIC',
      role: 'LOGIN',
    },
    { path: LOGOUT_PATH, screen: LogoutPage, layout: 'STANDALONE', access: 'LOGGED_IN' },
  ],
  userMenuItems: [
    {
      id: 'auth-logout',
      labelKey: 'auth.menu.logout',
      path: LOGOUT_PATH,
      beforeNavigate: markLogoutIntent,
      order: 100,
    },
  ],
  loginStateProvider,
  messages: {
    ja: {
      'auth.login.email': 'メールアドレス',
      'auth.login.password': 'パスワード',
      'auth.login.submit': 'ログイン',
      'auth.login.emailRequired': 'メールアドレスを入力してください',
      'auth.login.passwordRequired': 'パスワードを入力してください',
      'auth.login.failed': 'メールアドレスまたはパスワードが正しくありません',
      'auth.login.error': 'ログインできませんでした。しばらくしてから、もう一度お試しください',
      'auth.menu.logout': 'ログアウト',
      'auth.logout.inProgress': 'ログアウトしています',
      'auth.login.registered': '登録が完了しました。設定したパスワードでログインしてください。',
      'auth.language.label': '表示の言語',
    },
    en: {
      'auth.login.email': 'Email address',
      'auth.login.password': 'Password',
      'auth.login.submit': 'Log in',
      'auth.login.emailRequired': 'Enter your email address',
      'auth.login.passwordRequired': 'Enter your password',
      'auth.login.failed': 'The email address or the password is not correct',
      'auth.login.error': 'The login failed. Please try again later',
      'auth.menu.logout': 'Log out',
      'auth.logout.inProgress': 'Logging out',
      'auth.login.registered': 'Registration is complete. Log in with the password you set.',
      'auth.language.label': 'Display language',
    },
  },
}
