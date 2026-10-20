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
// 業務データの画面のテストの描き方（テストからだけ使う。unit-test-instructions.md 6節）。
// - 一覧と詳細の画面を道の切り替え（Routes）の中に描き、骨組みの Provider・作業ロール・業務のメニューで包む。
// - API は fetch を差し替えて ApiClient を通す。答えは道と本文で決める関数で返し、呼ばれた道と本文を記録する。
// - 「戻る」のボタンで、ブラウザの戻る操作（navigate(-1)）をまねる。
import { ToastProvider } from 'make-you-chic-ui'
import { Route, Routes, useNavigate } from 'react-router'
import { vi } from 'vitest'
import type { WorkRoleState } from '../../../app/work-role/workRoleApi'
import { WorkRoleSwitcher } from '../../../app/work-role/WorkRoleSwitcher'
import { fakeProvider, renderWithProviders } from '../../../app/testing/renderWithProviders'
import { BusinessDataDetailPage } from '../BusinessDataDetailPage'
import { BusinessDataListPage } from '../BusinessDataListPage'
import { registration } from '../registration'
import { jsonResponse } from './businessDataSamples'
import { ONE_ROLE, tableFrame } from './tableProviders'

/** 記録した要求 */
export interface RecordedCall {
  path: string
  body: Record<string, unknown>
}

/** 答えを決める関数（status と本文を返す） */
export type Responder = (
  path: string,
  body: Record<string, unknown>,
) => { status: number; body: unknown }

function BackButton() {
  const navigate = useNavigate()
  return (
    <button type="button" onClick={() => void navigate(-1)}>
      戻る
    </button>
  )
}

/** 業務データの画面を描く */
export function renderTablesScreen(
  route: string,
  responder: Responder,
  options: { roles?: WorkRoleState[]; languages?: string[] } = {},
) {
  const calls: RecordedCall[] = []
  const fetchMock = vi.fn((path: string, init?: RequestInit) => {
    const body = (init?.body === undefined ? {} : JSON.parse(String(init.body))) as Record<
      string,
      unknown
    >
    calls.push({ path, body })
    const answer = responder(path, body)
    return Promise.resolve(jsonResponse(answer.status, answer.body))
  })
  vi.stubGlobal('fetch', fetchMock)
  const frame = tableFrame(options.roles ?? [ONE_ROLE])
  const view = renderWithProviders(
    <ToastProvider>
      <frame.Frame>
        <header>
          <WorkRoleSwitcher />
          <BackButton />
        </header>
        <main>
          <Routes>
            <Route path="/tables" element={<BusinessDataListPage />} />
            <Route path="/tables/row" element={<BusinessDataDetailPage />} />
            <Route path="*" element={<p>other</p>} />
          </Routes>
        </main>
      </frame.Frame>
    </ToastProvider>,
    {
      route,
      registrations: [registration],
      provider: fakeProvider({ loggedIn: true, admin: false }),
      languages: options.languages,
    },
  )
  return { view, calls, frame, fetchMock }
}

/** 道ごとの呼ばれた回数 */
export function countCalls(calls: readonly RecordedCall[], path: string): number {
  return calls.filter((call) => call.path === path).length
}
