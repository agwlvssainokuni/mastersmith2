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
import { initializeAuthSession, logout } from './authSession'
import { loginStateProvider } from './loginStateProvider'

const LoginPage = lazy(() =>
  import('./LoginPage').then((module) => ({ default: module.LoginPage })),
)

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
  ],
  userMenuItems: [
    {
      id: 'auth-logout',
      labelKey: 'auth.menu.logout',
      action: () => {
        void logout()
      },
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
    },
  },
}
