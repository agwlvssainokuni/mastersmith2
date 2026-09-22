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
// 画面の起動（WF5）。登録の読み込みと検査、表示言語、ログイン状態、URL ごとの振り分けをつなぐ。
// React Router のルーター（BrowserRouter など）は、呼び出し側（main.tsx・テスト）が外側に置く。
import { ModalStackProvider, ThemeProvider, ToastProvider } from 'make-you-chic-ui'
import { useMemo } from 'react'
import { baseMessageKeys } from './i18n/i18n'
import { I18nProvider } from './i18n/I18nProvider'
import { StandaloneLayout } from './layout/StandaloneLayout'
import { LoginStateGate } from './login-state/LoginStateGate'
import { StartupErrorPage } from './pages/StartupErrorPage'
import { FeatureRegistryProvider } from './registry/FeatureRegistryContext'
import { loadRegistrations } from './registry/loadRegistrations'
import { registrationModules } from './registry/registrationModules'
import { RegistrationError, type FeatureRegistration } from './registry/types'
import { validateRegistrations } from './registry/validateRegistrations'
import { AppRouter } from './routing/AppRouter'

export interface AppProps {
  /** 登録用ファイルの読み込みの結果（既定は src/features/<featureId>/registration.ts のすべて） */
  modules?: Readonly<Record<string, unknown>>
}

type StartupResult =
  | { ok: true; registrations: readonly FeatureRegistration[] }
  | { ok: false; problems: readonly string[] }

function startup(modules: Readonly<Record<string, unknown>>): StartupResult {
  try {
    const registrations = loadRegistrations(modules)
    validateRegistrations(registrations, baseMessageKeys)
    return { ok: true, registrations }
  } catch (error) {
    if (error instanceof RegistrationError) {
      console.error(error.message)
      return { ok: false, problems: error.problems }
    }
    throw error
  }
}

/** アプリの部品をつなぐ。登録に問題があれば、画面の起動を止めて問題の登録を示す（BR7.2）。 */
export function App({ modules = registrationModules }: AppProps) {
  const result = useMemo(() => startup(modules), [modules])
  const featureMessages = useMemo(
    () =>
      result.ok
        ? result.registrations.flatMap((registration) =>
            registration.messages ? [registration.messages] : [],
          )
        : [],
    [result],
  )

  return (
    <ThemeProvider>
      <ToastProvider>
        <ModalStackProvider>
          <I18nProvider featureMessages={featureMessages}>
            {result.ok ? (
              <FeatureRegistryProvider registrations={result.registrations}>
                <LoginStateGate
                  provider={
                    result.registrations.find((r) => r.loginStateProvider)?.loginStateProvider
                  }
                >
                  <AppRouter />
                </LoginStateGate>
              </FeatureRegistryProvider>
            ) : (
              <StandaloneLayout>
                <StartupErrorPage problems={result.problems} />
              </StandaloneLayout>
            )}
          </I18nProvider>
        </ModalStackProvider>
      </ToastProvider>
    </ThemeProvider>
  )
}
