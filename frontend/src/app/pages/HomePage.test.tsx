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
import { screen, waitFor, within } from '@testing-library/react'
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

describe('HomePage entrance cards (Intent 261102-timezone-ui-polish U5 D14〜D21)', () => {
  afterEach(() => {
    resetDisplayTestState()
  })

  const WITH_ROLE: WorkRoleState = { roles: [SALES], current: SALES }
  const NO_ROLE: WorkRoleState = { roles: [], current: null }

  const adminFeature: FeatureRegistration = {
    featureId: 'adm',
    sidebarItems: [
      {
        id: 'dsl',
        labelKey: 'dsl.nav.label',
        path: '/admin/dsl',
        order: 210,
        visibleWhen: 'ADMIN',
        section: 'ADMIN',
        icon: 'edit',
      },
    ],
    messages: { ja: { 'dsl.nav.label': 'DSL の管理' }, en: { 'dsl.nav.label': 'DSL' } },
  }

  const menu = (...items: NavigationResponse['items']): NavigationResponse => ({
    items,
    emptyReason: null,
  })
  const tableNode = (id: string, name: string, kind: 'TABLE' | 'VIEW' | null, label = name) => ({
    id,
    label: { ja: label, en: `${label}-en` },
    icon: 'list',
    table: { schemaName: 'S', tableName: name },
    navigable: true,
    items: [],
    tableKind: kind,
  })

  function renderHome(options: {
    nav: Parameters<typeof fakeNavigationLoad>[0]
    role?: WorkRoleState
    admin?: boolean
    languages?: string[]
    roleApi?: ReturnType<typeof fakeWorkRoleApi>
  }) {
    const roleApi = options.roleApi ?? fakeWorkRoleApi([options.role ?? WITH_ROLE])
    const nav = fakeNavigationLoad(options.nav)
    const view = renderWithProviders(
      <WorkRoleProvider api={roleApi.api}>
        <BusinessNavigationProvider load={nav.load}>
          <main>
            <HomePage />
          </main>
        </BusinessNavigationProvider>
      </WorkRoleProvider>,
      {
        provider: fakeProvider({ loggedIn: true, admin: options.admin ?? false }),
        registrations: [adminFeature],
        languages: options.languages,
      },
    )
    return { view, nav: nav.calls, role: roleApi.calls }
  }

  it('shows the work role line, the business cards and the admin cards with headings', async () => {
    renderHome({
      nav: [menu(tableNode('0', 'ORDER', 'TABLE', '受注'), tableNode('1', 'V', 'VIEW', '集計'))],
      admin: true,
    })

    expect(await screen.findByTestId('home-work-role')).toHaveTextContent(
      `作業ロール: ${SALES.name}`,
    )
    const business = await screen.findByTestId('home-business-section')
    expect(within(business).getByRole('heading', { level: 2 })).toHaveTextContent('業務')
    expect(
      within(business)
        .getAllByRole('link')
        .map((l) => l.textContent),
    ).toEqual(['受注', '集計'])
    expect(within(business).getByTestId('icon-table')).toBeInTheDocument()
    expect(within(business).getByTestId('icon-view')).toBeInTheDocument()
    const admin = screen.getByTestId('home-admin-section')
    expect(within(admin).getByRole('heading', { level: 2 })).toHaveTextContent('管理')
    expect(within(admin).getByRole('link', { name: 'DSL の管理' })).toHaveAttribute(
      'href',
      '/admin/dsl',
    )
    expect(within(admin).getByTestId('icon-edit')).toBeInTheDocument()
  })

  it('shows English text and uses the English labels for an English browser', async () => {
    renderHome({
      nav: [menu(tableNode('0', 'ORDER', 'TABLE', '受注'))],
      admin: true,
      languages: ['en'],
    })

    expect(await screen.findByTestId('home-work-role')).toHaveTextContent(
      `Work role: ${SALES.name}`,
    )
    expect(await screen.findByRole('link', { name: '受注-en' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Business' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Administration' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'DSL' })).toBeInTheDocument()
  })

  it('shows no admin heading or card to a non-administrator', async () => {
    renderHome({ nav: [menu(tableNode('0', 'ORDER', 'TABLE'))] })

    await screen.findByTestId('home-business-section')
    expect(screen.queryByTestId('home-admin-section')).toBeNull()
    expect(screen.queryByRole('link', { name: 'DSL の管理' })).toBeNull()
  })

  it('shows none for the work role when no role is selected', async () => {
    renderHome({ nav: [menu(tableNode('0', 'ORDER', 'TABLE'))], role: NO_ROLE })

    expect(await screen.findByTestId('home-work-role')).toHaveTextContent('作業ロール: なし')
  })

  it('shows neither the business heading nor the work role line while loading', async () => {
    const pending = deferred<NavigationResponse>()
    renderHome({ nav: [pending], admin: true })

    await screen.findByTestId('home-page')
    expect(screen.queryByTestId('home-business-section')).toBeNull()
    expect(screen.queryByTestId('home-notice')).toBeNull()
    // 管理のカードは業務のメニューの状態に関わらず出る
    expect(screen.getByTestId('home-admin-section')).toBeInTheDocument()
  })

  it('shows the notice instead of the business heading when there is nothing to show, keeping the admin cards', async () => {
    renderHome({ nav: [{ items: [], emptyReason: 'NOT_CONFIGURED' }], admin: true })

    expect(await screen.findByTestId('home-notice')).toHaveTextContent(
      '業務のメニューが設定されていません。',
    )
    expect(screen.queryByTestId('home-business-section')).toBeNull()
    expect(screen.getByTestId('home-admin-section')).toBeInTheDocument()
  })

  it('shows the failure notice without a work role line, and keeps the admin cards', async () => {
    renderHome({ nav: [new Error('down')], admin: true })

    expect(await screen.findByTestId('home-notice')).toHaveTextContent(
      '業務のメニューを読み込めませんでした。',
    )
    expect(screen.getByTestId('home-admin-section')).toBeInTheDocument()
  })

  it('renders hostile labels as text and does not call any new API', async () => {
    const label = '<script>alert(1)</script><img src=x onerror=alert(2)>'
    const { nav, role, view } = renderHome({
      nav: [menu(tableNode('0', 'ORDER', 'TABLE', label))],
    })

    expect(await screen.findByRole('link', { name: label })).toBeInTheDocument()
    expect(view.container.querySelector('script, img')).toBeNull()
    expect(nav.count).toBe(1)
    expect(role.get).toBe(1)
  })

  it('has no accessibility violations with the cards', async () => {
    const { view } = renderHome({
      nav: [menu(tableNode('0', 'ORDER', 'TABLE', '受注'), tableNode('1', 'V', 'VIEW', '集計'))],
      admin: true,
    })
    await screen.findByTestId('home-business-section')

    expect(await axe(view.container)).toHaveNoViolations()
  })
})
