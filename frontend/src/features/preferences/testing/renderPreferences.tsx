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
// プリファレンスとパスワードの変更の画面のテストの補助（テストからだけ使う。画面からは読み込まない）。
// - renderPreferencesApp: 骨組みの renderWithProviders に preferences と auth の機能の登録を渡し、make-you-chic-ui の
//   ToastProvider で包み、本物の AppRouter（アプリシェル・ユーザーメニュー・遅延読み込み）で最初の URL を描く。
//   ログイン状態は auth の本物の提供元と認証の状態（トークンの更新の答えは偽のサーバーが返す）で作り、U4 の口・ApiClient も
//   本物で動かす（計画の9節の決定 2）。
// - renderPreferencesPart: 画面部品だけを、文言を登録した骨組みの Provider と ToastProvider の中に描く。
// 画面の言語はブラウザの希望言語（languages）で切り替える（既定は ja）。
import type { RenderResult } from '@testing-library/react'
import { ToastProvider } from 'make-you-chic-ui'
import { Suspense, type ReactElement } from 'react'
import type { FeatureRegistration, LoginStateProvider } from '../../../app/registry/types'
import { AppRouter } from '../../../app/routing/AppRouter'
import { renderWithProviders } from '../../../app/testing/renderWithProviders'
import { initializeAuthSession, resetAuthSession } from '../../auth/authSession'
import { loginStateProvider } from '../../auth/loginStateProvider'
import { registration as authRegistration } from '../../auth/registration'
import { preferencesMessages } from '../messages'
import { registration } from '../registration'

/** 文言だけを持つ登録 */
export const preferencesMessagesRegistration: FeatureRegistration = {
  featureId: 'preferences',
  messages: preferencesMessages,
}

/** アプリとしての描画の設定 */
export interface RenderPreferencesAppOptions {
  /** 最初の画面の URL（既定は /me/preferences） */
  route?: string
  /** ブラウザの希望言語（既定は ja） */
  languages?: readonly string[]
  /** ログイン状態の提供元（既定は auth の本物の提供元） */
  provider?: LoginStateProvider
}

/**
 * アプリシェルの中に画面を描く。先に偽のサーバーで POST /api/auth/session/refresh の答え（ログイン中なら
 * sessionResponse）を用意しておく。認証の状態は描く前に初めに戻し、ApiClient へ更新の手段を登録し直す。
 */
export function renderPreferencesApp(options: RenderPreferencesAppOptions = {}): RenderResult {
  const {
    route = '/me/preferences',
    languages = ['ja-JP'],
    provider = loginStateProvider,
  } = options
  resetAuthSession()
  initializeAuthSession()
  return renderWithProviders(
    <ToastProvider>
      <Suspense fallback={<p data-testid="screen-loading">loading</p>}>
        <AppRouter />
      </Suspense>
    </ToastProvider>,
    { route, languages, provider, registrations: [registration, authRegistration] },
  )
}

/** 画面部品だけを描く（languages はブラウザの希望言語）。 */
export function renderPreferencesPart(
  ui: ReactElement,
  languages: readonly string[] = ['ja-JP'],
): RenderResult {
  return renderWithProviders(<ToastProvider>{ui}</ToastProvider>, {
    registrations: [preferencesMessagesRegistration],
    languages,
  })
}
