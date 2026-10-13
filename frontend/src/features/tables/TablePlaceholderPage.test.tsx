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
// テーブルの置き場の画面のテスト（Intent 261004-role-menu の U7 の D20〜D23・W7.1〜W7.3、NFR1.1・NFR1.6・NFR2.9）。
// 置き場の問い合わせは fetch を差し替えて ApiClient を通す。作業ロールと業務のメニューは偽の API の提供元で包む。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ToastProvider } from 'make-you-chic-ui'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { BusinessNavigationProvider } from '../../app/navigation/BusinessNavigationProvider'
import {
  ACCOUNTING,
  fakeNavigationLoad,
  fakeWorkRoleApi,
  SALES,
} from '../../app/testing/frameTestSupport'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../../app/testing/renderWithProviders'
import { WorkRoleProvider } from '../../app/work-role/WorkRoleProvider'
import { WorkRoleSwitcher } from '../../app/work-role/WorkRoleSwitcher'
import type { WorkRoleState } from '../../app/work-role/workRoleApi'
import { registration } from './registration'
import { TablePlaceholderPage } from './TablePlaceholderPage'

let fetchMock: ReturnType<typeof vi.fn>
let answer: () => Response

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const access = (ja: string, en: string) =>
  json(200, { schemaName: 'S', tableName: 'ORDER_T', displayName: { ja, en }, main: 'READ' })

beforeEach(() => {
  answer = () => access('受注', 'Orders')
  fetchMock = vi.fn(() => Promise.resolve(answer()))
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  resetDisplayTestState()
})

const TWO: WorkRoleState = { roles: [SALES, ACCOUNTING], current: SALES }

function renderPage(
  route = '/tables?schema=S&table=ORDER_T&item=0.1',
  options: { roles?: WorkRoleState[]; languages?: string[] } = {},
) {
  const role = fakeWorkRoleApi(options.roles ?? [TWO])
  const nav = fakeNavigationLoad([{ items: [], emptyReason: 'NOT_CONFIGURED' }])
  const view = renderWithProviders(
    <ToastProvider>
      <WorkRoleProvider api={role.api}>
        <BusinessNavigationProvider load={nav.load}>
          <header>
            <WorkRoleSwitcher />
          </header>
          <main>
            <TablePlaceholderPage />
          </main>
        </BusinessNavigationProvider>
      </WorkRoleProvider>
    </ToastProvider>,
    {
      route,
      registrations: [registration],
      provider: fakeProvider({ loggedIn: true, admin: false }),
      languages: options.languages,
    },
  )
  return { view, role: role.calls, nav: nav.calls }
}

const tablePaths = () =>
  fetchMock.mock.calls
    .map(([path]) => String(path))
    .filter((path) => path.startsWith('/api/me/table-access'))

describe('TablePlaceholderPage', () => {
  it('shows the display name and the preparing text, asking without the item', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: '受注' })).toBeInTheDocument()
    expect(
      screen.getByText(/この画面は準備中です。/, { selector: 'p.tables-text' }),
    ).toBeInTheDocument()
    expect(tablePaths()).toEqual(['/api/me/table-access?schema=S&table=ORDER_T'])
  })

  it('uses the English name, then the other language and the table name for empty names', async () => {
    const { view } = renderPage(undefined, { languages: ['en-US'] })
    expect(await screen.findByRole('heading', { level: 1, name: 'Orders' })).toBeInTheDocument()
    view.unmount()

    answer = () => access('', 'Orders')
    const second = renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Orders' })).toBeInTheDocument()
    second.view.unmount()

    answer = () => access('', '')
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'ORDER_T' })).toBeInTheDocument()
  })

  it('shows the same forbidden view without names for 403, 400 and missing parameters', async () => {
    const texts: string[] = []
    for (const [route, status] of [
      ['/tables?schema=S&table=SECRET_T', 403],
      ['/tables?schema=S&table=SECRET_T', 400],
      ['/tables?schema=S', 0],
    ] as const) {
      answer = () => json(status, { status, code: status === 403 ? 'ACCESS_DENIED' : 'X' })
      const { view } = renderPage(route)
      const heading = await screen.findByRole('heading', {
        level: 1,
        name: 'この画面を表示する権限がありません',
      })
      const page = screen.getByTestId('tables-page')
      expect(heading).toBeInTheDocument()
      expect(page).not.toHaveTextContent('SECRET_T')
      expect(page).not.toHaveTextContent('受注')
      await waitFor(() => expect(page).toHaveTextContent('今の作業ロール: 営業'))
      texts.push(page.textContent ?? '')
      view.unmount()
    }
    expect(new Set(texts).size).toBe(1)
    expect(tablePaths()).toHaveLength(2)
  })

  it('reloads the business menu and the work role after a 403', async () => {
    answer = () => json(403, { status: 403, code: 'ACCESS_DENIED' })
    const { nav, role } = renderPage()
    await screen.findByRole('heading', { name: 'この画面を表示する権限がありません' })
    await waitFor(() => expect(nav.count).toBe(2))
    expect(role.get).toBe(2)
  })

  it('asks again after the work role is switched', async () => {
    const user = userEvent.setup()
    renderPage(undefined, { roles: [TWO, { ...TWO, current: ACCOUNTING }] })
    await screen.findByRole('heading', { level: 1, name: '受注' })
    answer = () => json(403, { status: 403, code: 'ACCESS_DENIED' })
    await user.click(screen.getByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    expect(
      await screen.findByRole('heading', { name: 'この画面を表示する権限がありません' }),
    ).toBeInTheDocument()
    expect(tablePaths().length).toBeGreaterThanOrEqual(2)
  })

  it('offers the work role switch only with two or more roles and moves the focus to the trigger', async () => {
    const user = userEvent.setup()
    answer = () => json(403, { status: 403, code: 'ACCESS_DENIED' })
    const { view } = renderPage()
    await user.click(await screen.findByRole('button', { name: '作業ロールを切り替える' }))
    expect(screen.getByRole('button', { name: '作業ロール: 営業' })).toHaveFocus()
    expect(screen.getByRole('link', { name: 'ホームへ' })).toHaveAttribute('href', '/')
    view.unmount()

    renderPage(undefined, { roles: [{ roles: [SALES], current: SALES }] })
    await screen.findByRole('heading', { name: 'この画面を表示する権限がありません' })
    await waitFor(() => expect(screen.getByText('今の作業ロール: 営業')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: '作業ロールを切り替える' })).toBeNull()
    expect(screen.getByRole('link', { name: 'ホームへ' })).toBeInTheDocument()
  })

  it('tells the state through a live region without moving the focus', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1, name: '受注' })
    const live = screen.getByTestId('tables-live')
    expect(live).toHaveAttribute('aria-live', 'polite')
    await waitFor(() => expect(live).toHaveTextContent('この画面は準備中です。'))
    expect(document.body).toHaveFocus()
  })

  it('shows a load error and asks again with Try again', async () => {
    const user = userEvent.setup()
    answer = () => json(500, { status: 500 })
    renderPage()
    const retry = await screen.findByRole('button', { name: 'もう一度' })
    expect(
      screen.getByText('読み込めませんでした。', { selector: 'p.tables-text' }),
    ).toBeInTheDocument()
    answer = () => access('受注', 'Orders')
    await user.click(retry)
    expect(await screen.findByRole('heading', { level: 1, name: '受注' })).toBeInTheDocument()
  })

  it('has no accessibility violations in the available and the forbidden views', async () => {
    const { view } = renderPage()
    await screen.findByRole('heading', { level: 1, name: '受注' })
    expect(await axe(view.container)).toHaveNoViolations()
    view.unmount()
    answer = () => json(403, { status: 403, code: 'ACCESS_DENIED' })
    const second = renderPage()
    await within(second.view.container).findByRole('button', { name: '作業ロールを切り替える' })
    expect(await axe(second.view.container)).toHaveNoViolations()
  })
})
