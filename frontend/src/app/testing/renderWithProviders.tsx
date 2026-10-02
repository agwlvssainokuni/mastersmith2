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
// 画面の部品のテストの補助（テストからだけ使う。アプリの画面からは読み込まない）。
// ブラウザの希望言語・画面の URL・登録・ログイン状態の提供元を与えて、部品を骨組みの Provider の中に描画する。
// 並びは App と同じ ThemeProvider → LoginStateGate → DisplaySettingsProvider → I18nProvider → FeatureRegistryProvider →
// AdminForbiddenProvider。
// 見た目の設定は既定で答え済み（当てる値なし）の約束を渡すため、/api/appearance の要求を用意せずに描ける（NFR9.10）。
import { render, type RenderResult } from '@testing-library/react'
import { ThemeProvider } from 'make-you-chic-ui'
import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter, useLocation } from 'react-router'
import { vi } from 'vitest'
import { resetApiClient } from '../../shared/api-client/apiClient'
import { AdminForbiddenProvider } from '../admin-forbidden/AdminForbiddenProvider'
import {
  resetAppearanceLoad,
  resolvedAppearance,
  type AppearanceResult,
} from '../display-settings/appearanceLoad'
import {
  DisplaySettingsProvider,
  useDisplaySettings,
} from '../display-settings/DisplaySettingsProvider'
import { resetDisplaySettings } from '../display-settings/displaySettingsStore'
import { I18nProvider } from '../i18n/I18nProvider'
import { resetLoginHandoff } from '../login-handoff/loginHandoff'
import { LoginStateGate } from '../login-state/LoginStateGate'
import { FeatureRegistryProvider } from '../registry/FeatureRegistryContext'
import type {
  FeatureMessages,
  FeatureRegistration,
  LoginState,
  LoginStateProvider,
} from '../registry/types'

export interface RenderOptions {
  /** 最初の画面の URL */
  route?: string
  /** 登録 */
  registrations?: readonly FeatureRegistration[]
  /** ログイン状態の提供元 */
  provider?: LoginStateProvider
  /** ブラウザの希望言語 */
  languages?: readonly string[]
  /** 見た目の設定の読み取りの約束（既定は答え済み・当てる値なし） */
  appearance?: Promise<AppearanceResult>
}

/**
 * 表示の設定まわりのモジュールの状態とブラウザの保存・<html> の属性を初めに戻す
 * （表示の設定の置き場・受け渡し・見た目の設定の約束・ApiClient の登録）。テストの前後で呼ぶ。
 */
export function resetDisplayTestState(): void {
  resetDisplaySettings()
  resetLoginHandoff()
  resetAppearanceLoad()
  resetApiClient()
  localStorage.clear()
  sessionStorage.clear()
  const html = document.documentElement
  for (const name of ['data-theme', 'data-font-size', 'data-brand', 'data-font-family']) {
    html.removeAttribute(name)
  }
  html.removeAttribute('lang')
}

/** ブラウザの希望言語を差し替える。 */
export function stubBrowserLanguages(languages: readonly string[]): void {
  vi.spyOn(window.navigator, 'languages', 'get').mockReturnValue(languages)
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue(languages[0] ?? '')
}

/** 決まったログイン状態を返す偽の提供元を作る。 */
export function fakeProvider(state: LoginState): LoginStateProvider {
  return { getLoginState: () => state }
}

/** 現在の画面の URL を表示する（画面の移動の確認に使う）。 */
function LocationProbe() {
  const { pathname } = useLocation()
  return (
    <output data-testid="location" hidden>
      {pathname}
    </output>
  )
}

/** 表示の設定の土台が決めた言語で文言を提供する。 */
function LocalizedContent({
  featureMessages,
  children,
}: {
  featureMessages: readonly FeatureMessages[]
  children: ReactNode
}) {
  const { language } = useDisplaySettings()
  return (
    <I18nProvider language={language} featureMessages={featureMessages}>
      {children}
    </I18nProvider>
  )
}

/** 骨組みの Provider の中に部品を描画する。 */
export function renderWithProviders(ui: ReactElement, options: RenderOptions = {}): RenderResult {
  const {
    route = '/',
    registrations = [],
    provider,
    languages = ['ja-JP'],
    appearance = resolvedAppearance(),
  } = options
  stubBrowserLanguages(languages)
  // 前のテストの表示の設定の置き場を持ち越さない（ブラウザの保存の値は、次に読むときに読み直す）。
  resetDisplaySettings()
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ThemeProvider>
        <LoginStateGate provider={provider}>
          <DisplaySettingsProvider appearance={appearance}>
            <LocalizedContent
              featureMessages={registrations.flatMap((r) => (r.messages ? [r.messages] : []))}
            >
              <FeatureRegistryProvider registrations={registrations}>
                <AdminForbiddenProvider>
                  {ui}
                  <LocationProbe />
                </AdminForbiddenProvider>
              </FeatureRegistryProvider>
            </LocalizedContent>
          </DisplaySettingsProvider>
        </LoginStateGate>
      </ThemeProvider>
    </MemoryRouter>,
  )
}
