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
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { StandaloneLayout } from '../layout/StandaloneLayout'
import { BusinessNavigationProvider } from '../navigation/BusinessNavigationProvider'
import type { NavigationResponse } from '../navigation/navigationApi'
import type { FeatureRegistration } from '../registry/types'
import { deferred, fakeNavigationLoad, fakeWorkRoleApi, SALES } from '../testing/frameTestSupport'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../testing/renderWithProviders'
import { WorkRoleProvider } from '../work-role/WorkRoleProvider'
import type { WorkRoleState } from '../work-role/workRoleApi'
import { HomePage } from './HomePage'

describe('HomePage', () => {
  it('shows the heading and a short description in Japanese', () => {
    renderWithProviders(<HomePage />)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('ホーム')
    expect(screen.getByText(/MasterSmith へようこそ/)).toBeInTheDocument()
  })

  it('shows English text for an English browser', () => {
    renderWithProviders(<HomePage />, { languages: ['en'] })
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Home')
    expect(screen.getByText(/Welcome to MasterSmith/)).toBeInTheDocument()
  })

  it('has exactly one top-level heading', () => {
    renderWithProviders(<HomePage />)
    expect(screen.getAllByRole('heading')).toHaveLength(1)
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(
      <StandaloneLayout>
        <HomePage />
      </StandaloneLayout>,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('HomePage notices (Intent 261004-role-menu U7 D19)', () => {
  afterEach(() => {
    resetDisplayTestState()
  })

  const myPermissions: FeatureRegistration = {
    featureId: 'mypermissions',
    routes: [{ path: '/me/permissions', screen: () => null, layout: 'SHELL', access: 'LOGGED_IN' }],
  }
  const WITH_ROLE: WorkRoleState = { roles: [SALES], current: SALES }
  const NO_ROLE: WorkRoleState = { roles: [], current: null }
  const empty = (emptyReason: 'NOT_CONFIGURED' | 'NOTHING_VISIBLE'): NavigationResponse => ({
    items: [],
    emptyReason,
  })

  function renderHome(
    navAnswers: Parameters<typeof fakeNavigationLoad>[0],
    role: WorkRoleState = WITH_ROLE,
    registrations: FeatureRegistration[] = [],
  ) {
    const roleApi = fakeWorkRoleApi([role])
    const nav = fakeNavigationLoad(navAnswers)
    const view = renderWithProviders(
      <WorkRoleProvider api={roleApi.api}>
        <BusinessNavigationProvider load={nav.load}>
          <main>
            <HomePage />
          </main>
        </BusinessNavigationProvider>
      </WorkRoleProvider>,
      { provider: fakeProvider({ loggedIn: true, admin: false }), registrations },
    )
    return { view, nav: nav.calls, role: roleApi.calls }
  }

  it('shows no notice while loading or when there is a menu', async () => {
    const pending = deferred<NavigationResponse>()
    renderHome([pending])
    await screen.findByTestId('home-page')
    expect(screen.queryByTestId('home-notice')).toBeNull()
  })

  it('tells that no business menu is set up', async () => {
    renderHome([empty('NOT_CONFIGURED')])
    expect(await screen.findByRole('status')).toHaveTextContent(
      '業務のメニューが設定されていません。',
    )
  })

  it('tells that no role is assigned when there is no work role', async () => {
    renderHome([empty('NOTHING_VISIBLE')], NO_ROLE)
    await waitFor(() =>
      expect(screen.getByTestId('home-notice')).toHaveTextContent(
        'ロールが割り当てられていません。管理者に依頼してください。',
      ),
    )
  })

  it('tells that nothing is visible for the work role', async () => {
    renderHome([empty('NOTHING_VISIBLE')])
    await waitFor(() =>
      expect(screen.getByTestId('home-notice')).toHaveTextContent(
        '表示できる業務のメニューがありません。作業ロールに見てよいテーブルがありません。',
      ),
    )
  })

  it('tells a load failure and reloads the menu and the work role with Try again', async () => {
    const user = userEvent.setup()
    const { nav, role } = renderHome([new Error('down'), empty('NOT_CONFIGURED')])
    expect(await screen.findByTestId('home-notice')).toHaveTextContent(
      '業務のメニューを読み込めませんでした。',
    )
    await user.click(screen.getByRole('button', { name: 'もう一度' }))
    await waitFor(() =>
      expect(screen.getByTestId('home-notice')).toHaveTextContent(
        '業務のメニューが設定されていません。',
      ),
    )
    expect(nav.count).toBe(2)
    expect(role.get).toBe(2)
  })

  it('adds the link to my permissions only when that screen is registered', async () => {
    const { view } = renderHome([empty('NOT_CONFIGURED')])
    await screen.findByTestId('home-notice')
    expect(screen.queryByRole('link', { name: '自分の権限を見る' })).toBeNull()
    view.unmount()

    renderHome([empty('NOT_CONFIGURED')], WITH_ROLE, [myPermissions])
    expect(await screen.findByRole('link', { name: '自分の権限を見る' })).toHaveAttribute(
      'href',
      '/me/permissions',
    )
  })

  it('has no accessibility violations with the failure notice', async () => {
    const { view } = renderHome([new Error('down')], WITH_ROLE, [myPermissions])
    await screen.findByRole('button', { name: 'もう一度' })
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
