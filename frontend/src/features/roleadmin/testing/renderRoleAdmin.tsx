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
// ロールの管理の画面のテストの補助（テストからだけ使う。画面からは読み込まない。計画 unit-test-instructions.md の 6節）。
// - renderRoleAdmin: 文言を登録した骨組みの Provider と make-you-chic-ui の Modal の Provider の中に部品を描く
//   （既存の renderWithProviders、MemoryRouter）。useBlocker を使わない部品（S3・S5 の部品）に使う。
// - renderRoleAdminDataRouter: useBlocker を使う部品（RoleDetailPage・PermissionPanel・src/shared/unsaved-guard の useUnsavedGuard）を、
//   data router（createMemoryRouter＋RouterProvider）で描く。renderWithProviders は変えない（計画 5節 (d)）。
import { render, type RenderResult } from '@testing-library/react'
import { ModalStackProvider, ThemeProvider, ToastProvider } from 'make-you-chic-ui'
import type { ReactElement, ReactNode } from 'react'
import { createMemoryRouter, Outlet, RouterProvider, useLocation } from 'react-router'
import { AdminForbiddenProvider } from '../../../app/admin-forbidden/AdminForbiddenProvider'
import { resolvedAppearance } from '../../../app/display-settings/appearanceLoad'
import {
  DisplaySettingsProvider,
  useDisplaySettings,
} from '../../../app/display-settings/DisplaySettingsProvider'
import { resetDisplaySettings } from '../../../app/display-settings/displaySettingsStore'
import { I18nProvider } from '../../../app/i18n/I18nProvider'
import { ShellLayout } from '../../../app/layout/ShellLayout'
import { LoginStateGate } from '../../../app/login-state/LoginStateGate'
import { FeatureRegistryProvider } from '../../../app/registry/FeatureRegistryContext'
import type { FeatureRegistration, LoginStateProvider } from '../../../app/registry/types'
import {
  fakeProvider,
  renderWithProviders,
  stubBrowserLanguages,
} from '../../../app/testing/renderWithProviders'
import { roleAdminMessages } from '../messages'
import { ROLE_LIST_PATH } from '../paths'

/** 文言だけを持つ登録 */
export const roleAdminMessagesRegistration: FeatureRegistration = {
  featureId: 'roleadmin',
  messages: roleAdminMessages,
}

/** 管理者のログイン状態の提供元 */
export function adminProvider(): LoginStateProvider {
  return fakeProvider({ loggedIn: true, admin: true, displayName: '管理 一郎' })
}

/** 描き方の選択 */
export interface RenderRoleAdminOptions {
  /** 最初の画面の URL */
  route?: string
  /** ブラウザの希望言語（既定は ja） */
  languages?: readonly string[]
  /** 登録（既定は文言だけの登録） */
  registrations?: readonly FeatureRegistration[]
  /** ログイン状態の提供元（既定は管理者） */
  provider?: LoginStateProvider
  /** 部品を骨組みの ShellLayout の中に描く（403 の表示の確かめ） */
  withShell?: boolean
}

/** 部品を描く（MemoryRouter）。 */
export function renderRoleAdmin(
  ui: ReactElement,
  options: RenderRoleAdminOptions = {},
): RenderResult {
  const {
    route = ROLE_LIST_PATH,
    languages = ['ja-JP'],
    registrations = [roleAdminMessagesRegistration],
    provider = adminProvider(),
    withShell = false,
  } = options
  const content = withShell ? <ShellLayout>{ui}</ShellLayout> : ui
  return renderWithProviders(
    <ToastProvider>
      <ModalStackProvider>{content}</ModalStackProvider>
    </ToastProvider>,
    { route, registrations, languages, provider },
  )
}

/** 今の画面の URL（道の移動の確かめ） */
function LocationProbe() {
  const { pathname } = useLocation()
  return (
    <output data-testid="location" hidden>
      {pathname}
    </output>
  )
}

/** 表示の設定の土台が決めた言語で文言を出す。 */
function LocalizedContent({
  registrations,
  children,
}: {
  registrations: readonly FeatureRegistration[]
  children: ReactNode
}) {
  const { language } = useDisplaySettings()
  return (
    <I18nProvider
      language={language}
      featureMessages={registrations.flatMap((r) => (r.messages ? [r.messages] : []))}
    >
      {children}
    </I18nProvider>
  )
}

/** 道の登録（data router の1つの道） */
export interface DataRoute {
  path: string
  element: ReactElement
}

/** data router で描いた結果 */
export interface DataRouterRender extends RenderResult {
  router: ReturnType<typeof createMemoryRouter>
}

/**
 * useBlocker を使う部品を data router（createMemoryRouter＋RouterProvider）で描く。骨組みの Provider は、道ごとに
 * 作り直さないよう、すべての道の親の1つの道（Outlet）に置く。
 */
export function renderRoleAdminDataRouter(
  routes: readonly DataRoute[],
  options: RenderRoleAdminOptions = {},
): DataRouterRender {
  const {
    route = ROLE_LIST_PATH,
    languages = ['ja-JP'],
    registrations = [roleAdminMessagesRegistration],
    provider = adminProvider(),
  } = options
  stubBrowserLanguages(languages)
  resetDisplaySettings()
  const shell = (
    <ThemeProvider>
      <LoginStateGate provider={provider}>
        <DisplaySettingsProvider appearance={resolvedAppearance()}>
          <LocalizedContent registrations={registrations}>
            <FeatureRegistryProvider registrations={registrations}>
              <AdminForbiddenProvider>
                <ToastProvider>
                  <ModalStackProvider>
                    <Outlet />
                    <LocationProbe />
                  </ModalStackProvider>
                </ToastProvider>
              </AdminForbiddenProvider>
            </FeatureRegistryProvider>
          </LocalizedContent>
        </DisplaySettingsProvider>
      </LoginStateGate>
    </ThemeProvider>
  )
  const router = createMemoryRouter(
    [
      {
        element: shell,
        children: routes.map((item) => ({ path: item.path, element: item.element })),
      },
    ],
    { initialEntries: [route] },
  )
  const result = render(<RouterProvider router={router} />)
  return { ...result, router }
}
