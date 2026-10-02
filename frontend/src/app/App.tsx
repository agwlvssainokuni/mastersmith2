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
// 画面の起動（WF5）。登録の読み込みと検査、ログイン状態、表示の設定、表示言語、URL ごとの振り分けをつなぐ。
// ログイン状態を受けてから表示の設定を決め、その言語で文言を引くため、並びは
// ThemeProvider → LoginStateGate → DisplaySettingsProvider → I18nProvider → FeatureRegistryProvider →
// AdminForbiddenProvider（管理の画面の 403 の共通の扱い。Intent 260930-user-admin の U4）とする（U4 の部品 1節）。
// React Router のルーター（BrowserRouter など）は、呼び出し側（main.tsx・テスト）が外側に置く。
import { ModalStackProvider, ThemeProvider, ToastProvider } from 'make-you-chic-ui'
import { useMemo, type ReactNode } from 'react'
import { AdminForbiddenProvider } from './admin-forbidden/AdminForbiddenProvider'
import { startAppearanceLoad, type AppearanceResult } from './display-settings/appearanceLoad'
import {
  DisplaySettingsProvider,
  useDisplaySettings,
} from './display-settings/DisplaySettingsProvider'
import { baseMessageKeys } from './i18n/i18n'
import { I18nProvider } from './i18n/I18nProvider'
import { StandaloneLayout } from './layout/StandaloneLayout'
import { LoginStateGate } from './login-state/LoginStateGate'
import { StartupErrorPage } from './pages/StartupErrorPage'
import { FeatureRegistryProvider } from './registry/FeatureRegistryContext'
import { loadRegistrations } from './registry/loadRegistrations'
import { registrationModules } from './registry/registrationModules'
import { RegistrationError, type FeatureMessages, type FeatureRegistration } from './registry/types'
import { validateRegistrations } from './registry/validateRegistrations'
import { AppRouter } from './routing/AppRouter'

export interface AppProps {
  /** 登録用ファイルの読み込みの結果（既定は src/features/<featureId>/registration.ts のすべて） */
  modules?: Readonly<Record<string, unknown>>
  /** 見た目の設定の読み取りの約束（既定は描画の外で1回だけ作る startAppearanceLoad() の約束） */
  appearance?: Promise<AppearanceResult>
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

/** アプリの部品をつなぐ。登録に問題があれば、画面の起動を止めて問題の登録を示す（BR7.2）。 */
export function App({
  modules = registrationModules,
  appearance = startAppearanceLoad(),
}: AppProps) {
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
          <LoginStateGate
            provider={
              result.ok
                ? result.registrations.find((r) => r.loginStateProvider)?.loginStateProvider
                : undefined
            }
          >
            <DisplaySettingsProvider appearance={appearance}>
              <LocalizedContent featureMessages={featureMessages}>
                {result.ok ? (
                  <FeatureRegistryProvider registrations={result.registrations}>
                    <AdminForbiddenProvider>
                      <AppRouter />
                    </AdminForbiddenProvider>
                  </FeatureRegistryProvider>
                ) : (
                  <StandaloneLayout>
                    <StartupErrorPage problems={result.problems} />
                  </StandaloneLayout>
                )}
              </LocalizedContent>
            </DisplaySettingsProvider>
          </LoginStateGate>
        </ModalStackProvider>
      </ToastProvider>
    </ThemeProvider>
  )
}
