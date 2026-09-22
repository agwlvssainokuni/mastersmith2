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
import { render, type RenderResult } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, useLocation } from 'react-router'
import { vi } from 'vitest'
import { I18nProvider } from '../i18n/I18nProvider'
import { LoginStateGate } from '../login-state/LoginStateGate'
import { FeatureRegistryProvider } from '../registry/FeatureRegistryContext'
import type { FeatureRegistration, LoginState, LoginStateProvider } from '../registry/types'

export interface RenderOptions {
  /** 最初の画面の URL */
  route?: string
  /** 登録 */
  registrations?: readonly FeatureRegistration[]
  /** ログイン状態の提供元 */
  provider?: LoginStateProvider
  /** ブラウザの希望言語 */
  languages?: readonly string[]
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

/** 骨組みの Provider の中に部品を描画する。 */
export function renderWithProviders(ui: ReactElement, options: RenderOptions = {}): RenderResult {
  const { route = '/', registrations = [], provider, languages = ['ja-JP'] } = options
  stubBrowserLanguages(languages)
  return render(
    <MemoryRouter initialEntries={[route]}>
      <I18nProvider
        featureMessages={registrations.flatMap((r) => (r.messages ? [r.messages] : []))}
      >
        <FeatureRegistryProvider registrations={registrations}>
          <LoginStateGate provider={provider}>
            {ui}
            <LocationProbe />
          </LoginStateGate>
        </FeatureRegistryProvider>
      </I18nProvider>
    </MemoryRouter>,
  )
}
