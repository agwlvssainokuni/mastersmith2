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
// 業務データの画面を data router（createMemoryRouter＋RouterProvider）で描くテストの補助（テストからだけ使う。B6）。
// 作成・更新の画面は未保存の確かめに useBlocker を使い、data router が要るため、renderTablesScreen（MemoryRouter）とは別に置く。
// - 一覧・詳細・作成・更新の4つの道と、トップバーの作業ロールの切り替えを、骨組みの Provider・作業ロール・業務のメニューで包む。
// - API は fetch を差し替えて ApiClient を通す。答えは道・メソッド・本文で決める関数で返し、呼ばれた要求を記録する。
// - 1つの答えを Response そのもので返すこともできる（content-type の違う 2xx・本文の無い 5xx の確かめ）。
// - before で、履歴で最初の道の前に置く道を渡せる（戻る操作の確かめ。最初の道が今の位置になる）。
import { render } from '@testing-library/react'
import { ModalStackProvider, ThemeProvider, ToastProvider } from 'make-you-chic-ui'
import type { ReactNode } from 'react'
import { createMemoryRouter, Outlet, RouterProvider, useLocation } from 'react-router'
import { vi } from 'vitest'
import { AdminForbiddenProvider } from '../../../app/admin-forbidden/AdminForbiddenProvider'
import { resolvedAppearance } from '../../../app/display-settings/appearanceLoad'
import {
  DisplaySettingsProvider,
  useDisplaySettings,
} from '../../../app/display-settings/DisplaySettingsProvider'
import { resetDisplaySettings } from '../../../app/display-settings/displaySettingsStore'
import { I18nProvider } from '../../../app/i18n/I18nProvider'
import { LoginStateGate } from '../../../app/login-state/LoginStateGate'
import { FeatureRegistryProvider } from '../../../app/registry/FeatureRegistryContext'
import { fakeProvider, stubBrowserLanguages } from '../../../app/testing/renderWithProviders'
import type { WorkRoleState } from '../../../app/work-role/workRoleApi'
import { WorkRoleSwitcher } from '../../../app/work-role/WorkRoleSwitcher'
import { BusinessDataDetailPage } from '../BusinessDataDetailPage'
import { BusinessDataFormPage } from '../BusinessDataFormPage'
import { BusinessDataListPage } from '../BusinessDataListPage'
import { registration } from '../registration'
import { jsonResponse } from './businessDataSamples'
import { ONE_ROLE, tableFrame } from './tableProviders'

/** 記録した要求 */
export interface RecordedRequest {
  path: string
  method: string
  body: Record<string, unknown>
}

/** 答えを決める関数（status と本文、または Response をそのまま返す） */
export type DataRouterResponder = (
  request: RecordedRequest,
) => { status: number; body: unknown } | Response | Promise<Response>

function LocalizedContent({ children }: { children: ReactNode }) {
  const { language } = useDisplaySettings()
  return (
    <I18nProvider
      language={language}
      featureMessages={registration.messages ? [registration.messages] : []}
    >
      {children}
    </I18nProvider>
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

/** 業務データの画面を data router で描く */
export function renderTablesDataRouter(
  route: string,
  responder: DataRouterResponder,
  options: {
    roles?: WorkRoleState[]
    languages?: string[]
    state?: unknown
    /** 履歴で route の前に置く道（ブラウザの戻る操作の確かめ） */
    before?: string[]
  } = {},
) {
  const calls: RecordedRequest[] = []
  const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
    const body = (init?.body === undefined ? {} : JSON.parse(String(init.body))) as Record<
      string,
      unknown
    >
    const request = { path, method: init?.method ?? 'GET', body }
    calls.push(request)
    const answer = await responder(request)
    return answer instanceof Response ? answer : jsonResponse(answer.status, answer.body)
  })
  vi.stubGlobal('fetch', fetchMock)
  stubBrowserLanguages(options.languages ?? ['ja-JP'])
  resetDisplaySettings()
  const frame = tableFrame(options.roles ?? [ONE_ROLE])
  const shell = (
    <ThemeProvider>
      <LoginStateGate provider={fakeProvider({ loggedIn: true, admin: false })}>
        <DisplaySettingsProvider appearance={resolvedAppearance()}>
          <LocalizedContent>
            <FeatureRegistryProvider registrations={[registration]}>
              <AdminForbiddenProvider>
                <ToastProvider>
                  <ModalStackProvider>
                    <frame.Frame>
                      <header>
                        <WorkRoleSwitcher />
                      </header>
                      <main>
                        <Outlet />
                      </main>
                      <LocationProbe />
                    </frame.Frame>
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
        children: [
          { path: '/tables', element: <BusinessDataListPage /> },
          { path: '/tables/row', element: <BusinessDataDetailPage /> },
          { path: '/tables/new', element: <BusinessDataFormPage /> },
          { path: '/tables/edit', element: <BusinessDataFormPage /> },
          { path: '*', element: <p>other</p> },
        ],
      },
    ],
    {
      initialEntries: [
        ...(options.before ?? []).map((entry) => ({
          pathname: entry.split('?')[0],
          search: entry.includes('?') ? `?${entry.split('?')[1]}` : '',
        })),
        {
          pathname: route.split('?')[0],
          search: route.includes('?') ? `?${route.split('?')[1]}` : '',
          state: options.state,
        },
      ],
    },
  )
  const view = render(<RouterProvider router={router} />)
  return { view, router, calls, frame, fetchMock }
}
