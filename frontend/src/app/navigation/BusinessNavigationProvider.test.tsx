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
// 業務のメニューの状態のテスト（Intent 261004-role-menu の U7 の L4、D4・D16・D18・4.2、NFR2.2・NFR2.9、
// security-design.md 3節、計画 12節 D-7・D-8）。
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import {
  ACCOUNTING,
  deferred,
  fakeNavigationLoad,
  fakeWorkRoleApi,
  NavigateButton,
  SALES,
  switchableProvider,
} from '../testing/frameTestSupport'
import { renderWithProviders, resetDisplayTestState } from '../testing/renderWithProviders'
import { useWorkRole, WorkRoleProvider } from '../work-role/WorkRoleProvider'
import { BusinessNavigationProvider, useBusinessNavigation } from './BusinessNavigationProvider'
import { NAV_EXPANDED_STORAGE_KEY } from './navExpansion'
import type { NavigationResponse } from './navigationApi'

afterEach(() => {
  resetDisplayTestState()
})

function menu(name: string): NavigationResponse {
  return {
    items: [
      {
        id: '0',
        label: { ja: name, en: name },
        icon: 'list',
        table: { schemaName: 'S', tableName: name },
        navigable: true,
        items: [],
      },
    ],
    emptyReason: null,
  }
}

function Probe() {
  const business = useBusinessNavigation()
  const role = useWorkRole()
  return (
    <div>
      <output data-testid="status">{business.status}</output>
      <output data-testid="menu">{business.response?.items[0]?.label.ja ?? 'none'}</output>
      <button type="button" onClick={() => business.reload()}>
        reload
      </button>
      <button type="button" onClick={() => void role.switchTo(ACCOUNTING.roleId)}>
        switch
      </button>
      <NavigateButton to="/somewhere" label="move" />
    </div>
  )
}

const TWO = { roles: [SALES, ACCOUNTING], current: SALES }

function renderNav(
  answers: Parameters<typeof fakeNavigationLoad>[0],
  provider = switchableProvider({ loggedIn: true, admin: false }).provider,
) {
  const role = fakeWorkRoleApi([TWO])
  const nav = fakeNavigationLoad(answers)
  renderWithProviders(
    <WorkRoleProvider api={role.api}>
      <BusinessNavigationProvider load={nav.load}>
        <Probe />
      </BusinessNavigationProvider>
    </WorkRoleProvider>,
    { provider },
  )
  return { role: role.calls, nav: nav.calls }
}

const status = () => screen.getByTestId('status')

describe('BusinessNavigationProvider', () => {
  it('returns idle values without requests when no provider is placed', () => {
    function Bare() {
      const business = useBusinessNavigation()
      business.reload()
      return <output>{business.status}</output>
    }
    render(<Bare />)
    expect(screen.getByText('idle')).toBeInTheDocument()
  })

  it('reads after login and sends two requests per path change', async () => {
    const user = userEvent.setup()
    const answer = deferred<NavigationResponse>()
    const calls = renderNav([answer, menu('受注')])
    await waitFor(() => expect(status()).toHaveTextContent('loading'))
    act(() => answer.resolve(menu('受注')))
    await waitFor(() => expect(status()).toHaveTextContent('loaded'))
    expect(calls).toEqual({ role: { get: 1, put: [] }, nav: { count: 1 } })

    await user.click(screen.getByRole('button', { name: 'move' }))
    await waitFor(() => expect(calls.nav.count).toBe(2))
    expect(calls.role.get).toBe(2)
  })

  it('reloads once more after a switch: one PUT and two reloads', async () => {
    const user = userEvent.setup()
    const calls = renderNav([menu('受注'), menu('請求')])
    await waitFor(() => expect(status()).toHaveTextContent('loaded'))
    await user.click(screen.getByRole('button', { name: 'switch' }))
    await waitFor(() => expect(screen.getByTestId('menu')).toHaveTextContent('請求'))
    expect(calls.role.put).toEqual([ACCOUNTING.roleId])
    expect(calls.role.get).toBe(2)
    expect(calls.nav.count).toBe(2)
  })

  it('keeps the previous tree while reloading and when a reload fails', async () => {
    const user = userEvent.setup()
    const second = deferred<NavigationResponse>()
    renderNav([menu('受注'), second])
    await waitFor(() => expect(status()).toHaveTextContent('loaded'))
    await user.click(screen.getByRole('button', { name: 'reload' }))
    await waitFor(() => expect(status()).toHaveTextContent('reloading'))
    expect(screen.getByTestId('menu')).toHaveTextContent('受注')
    act(() => second.reject(new Error('down')))
    await waitFor(() => expect(status()).toHaveTextContent('loaded'))
    expect(screen.getByTestId('menu')).toHaveTextContent('受注')
  })

  it('drops the tree of the previous role and shows load-error when the reload after a switch fails', async () => {
    const user = userEvent.setup()
    const calls = renderNav([menu('受注'), new Error('down'), menu('請求')])
    await waitFor(() => expect(status()).toHaveTextContent('loaded'))
    await user.click(screen.getByRole('button', { name: 'switch' }))
    await waitFor(() => expect(status()).toHaveTextContent('load-error'))
    expect(screen.getByTestId('menu')).toHaveTextContent('none')
    expect(calls.role.put).toEqual([ACCOUNTING.roleId])
    expect(calls.nav.count).toBe(2)

    await user.click(screen.getByRole('button', { name: 'reload' }))
    await waitFor(() => expect(screen.getByTestId('menu')).toHaveTextContent('請求'))
    expect(status()).toHaveTextContent('loaded')
  })

  it('keeps the tree when a later reload with the same work role fails after a switch', async () => {
    const user = userEvent.setup()
    renderNav([menu('受注'), menu('請求'), new Error('down')])
    await waitFor(() => expect(status()).toHaveTextContent('loaded'))
    await user.click(screen.getByRole('button', { name: 'switch' }))
    await waitFor(() => expect(screen.getByTestId('menu')).toHaveTextContent('請求'))
    await user.click(screen.getByRole('button', { name: 'reload' }))
    await waitFor(() => expect(status()).toHaveTextContent('loaded'))
    expect(screen.getByTestId('menu')).toHaveTextContent('請求')
  })

  it('shows load-error for a failed first load and recovers with reload', async () => {
    const user = userEvent.setup()
    renderNav([new Error('down'), menu('受注')])
    await waitFor(() => expect(status()).toHaveTextContent('load-error'))
    await user.click(screen.getByRole('button', { name: 'reload' }))
    await waitFor(() => expect(status()).toHaveTextContent('loaded'))
  })

  it('drops an older answer that arrives after a newer one', async () => {
    const user = userEvent.setup()
    const first = deferred<NavigationResponse>()
    const second = deferred<NavigationResponse>()
    renderNav([first, second])
    await waitFor(() => expect(status()).toHaveTextContent('loading'))
    await user.click(screen.getByRole('button', { name: 'reload' }))
    act(() => second.resolve(menu('新しい')))
    await waitFor(() => expect(screen.getByTestId('menu')).toHaveTextContent('新しい'))
    act(() => first.resolve(menu('古い')))
    await act(async () => {
      await Promise.resolve()
    })
    expect(screen.getByTestId('menu')).toHaveTextContent('新しい')
  })

  it('drops the tree and the saved expanded ids when logged out', async () => {
    const login = switchableProvider({ loggedIn: true, admin: false })
    renderNav([menu('受注')], login.provider)
    await waitFor(() => expect(status()).toHaveTextContent('loaded'))
    sessionStorage.setItem(NAV_EXPANDED_STORAGE_KEY, '["biz:0"]')
    act(() => login.set({ loggedIn: false, admin: false }))
    await waitFor(() => expect(status()).toHaveTextContent('idle'))
    expect(screen.getByTestId('menu')).toHaveTextContent('none')
    expect(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY)).toBeNull()
  })
})
