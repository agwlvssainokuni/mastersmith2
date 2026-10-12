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
// S4・S5 の枠のテスト（Intent 261004-role-menu の U6 の D32・D15、NFR4.7、計画 5節 (e)）。
// - 道の roleId が正の整数でないとき・ROLE_NOT_FOUND のときは「このロールはありません。」で、ほかを読まない（D32）。
// - main.tsx と同じ形の data router で App を描き、S4 → S5 のタブの切り替えの後もロールの読み込みが1回で、フォーカスが
//   タブに残る（作り直されない）ことを確かめる（計画 5節 (e)）。本物の登録（registration.ts）を使い、API は差し替える。
// - 未保存の写しがあるときのタブの切り替えは確かめが出て、留まると道が変わらずフォーカスは選んでいるタブに戻る。
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { App } from '../../app/App'
import { resolvedAppearance } from '../../app/display-settings/appearanceLoad'
import type { FeatureRegistration } from '../../app/registry/types'
import { resetDisplayTestState, stubBrowserLanguages } from '../../app/testing/renderWithProviders'
import type { RoleAdminApi } from './api/roleAdminApi'
import { registration } from './registration'
import { RoleDetailPage } from './RoleDetailPage'
import {
  apiError,
  assignmentsOf,
  fakeRoleAdminApi,
  LONG_ROLE_NAME,
  nodeOf,
  roleOf,
  type FakeRoleAdminApi,
} from './testing/fixtures'
import { renderRoleAdminDataRouter } from './testing/renderRoleAdmin'

// 登録の画面は既定の roleAdminApi を使うため、App を通すテストでは差し替えた API へ委ねる。
const holder = vi.hoisted(() => ({ api: null as unknown }))
vi.mock('./api/roleAdminApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/roleAdminApi')>()
  return {
    ...actual,
    roleAdminApi: new Proxy(
      {},
      {
        get: (_target, key: string) => (holder.api as Record<string, unknown>)[key],
      },
    ),
  }
})

function readyApi(): FakeRoleAdminApi {
  const api = fakeRoleAdminApi()
  api.getRole.mockResolvedValue(roleOf())
  api.listSchemas.mockResolvedValue([nodeOf()])
  api.listTables.mockResolvedValue([])
  api.getAssignments.mockResolvedValue(assignmentsOf())
  return api
}

/** main.tsx と同じ形の data router で App を描く（本物の roleadmin の登録と管理者のログイン状態）。 */
function renderApp(route: string) {
  stubBrowserLanguages(['ja-JP'])
  const admin: FeatureRegistration = {
    featureId: 'probe',
    loginStateProvider: {
      getLoginState: () => ({ loggedIn: true, admin: true, displayName: '管理 一郎' }),
    },
  }
  const modules = {
    'features/roleadmin/registration.ts': { registration },
    'features/probe/registration.ts': { registration: admin },
  }
  const router = createMemoryRouter(
    [{ path: '*', element: <App modules={modules} appearance={resolvedAppearance()} /> }],
    { initialEntries: [route] },
  )
  render(<RouterProvider router={router} />)
  return router
}

function renderDetail(api: RoleAdminApi, route = '/admin/roles/7') {
  const element = <RoleDetailPage api={api} />
  return renderRoleAdminDataRouter(
    [
      { path: '/admin/roles', element: <p data-testid="list-screen">list</p> },
      { path: '/admin/roles/:roleId', element },
      { path: '/admin/roles/:roleId/assignments', element },
    ],
    { route },
  )
}

describe('RoleDetailPage', () => {
  beforeEach(() => {
    holder.api = readyApi()
  })

  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('shows the role heading, the back link and the permissions tab on /admin/roles/:roleId', async () => {
    const api = readyApi()
    renderDetail(api)
    expect(await screen.findByRole('heading', { name: 'ロール: 営業' })).toBeInTheDocument()
    expect(screen.getByTestId('roleadmin-back')).toHaveAttribute('href', '/admin/roles')
    expect(screen.getByRole('tab', { name: '権限の設定' })).toHaveAttribute('aria-selected', 'true')
    expect(api.getRole).toHaveBeenCalledWith(7)
  })

  it.each(['abc', '0', '-1', '1.5', '99999999999999999999'])(
    'shows the missing message and reads nothing for the path id %s',
    async (id) => {
      const api = readyApi()
      renderDetail(api, `/admin/roles/${id}`)
      expect(await screen.findByTestId('roleadmin-detail-missing')).toHaveTextContent(
        'このロールはありません。',
      )
      expect(screen.getByRole('link', { name: 'ロールの一覧へ' })).toHaveAttribute(
        'href',
        '/admin/roles',
      )
      expect(api.getRole).not.toHaveBeenCalled()
      expect(api.listSchemas).not.toHaveBeenCalled()
    },
  )

  it('shows the missing message on ROLE_NOT_FOUND and does not read the permissions', async () => {
    const api = readyApi()
    api.getRole.mockRejectedValue(apiError(404, 'ROLE_NOT_FOUND'))
    renderDetail(api)
    expect(await screen.findByTestId('roleadmin-detail-missing')).toBeInTheDocument()
    expect(api.listSchemas).not.toHaveBeenCalled()
    expect(api.getAssignments).not.toHaveBeenCalled()
  })

  it('shows the load failure with retry', async () => {
    const user = userEvent.setup()
    const api = readyApi()
    api.getRole.mockRejectedValueOnce(apiError(500))
    renderDetail(api)
    expect(await screen.findByTestId('roleadmin-detail-error')).toHaveTextContent(
      'ロールを読み込めませんでした。',
    )
    await user.click(screen.getByRole('button', { name: 'もう一度' }))
    expect(await screen.findByRole('heading', { name: 'ロール: 営業' })).toBeInTheDocument()
  })

  it('selects the assignments tab from the path', async () => {
    const api = readyApi()
    renderDetail(api, '/admin/roles/7/assignments')
    expect(await screen.findByRole('tab', { name: '割り当て' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(await screen.findByTestId('roleadmin-assign')).toBeInTheDocument()
    expect(api.listSchemas).not.toHaveBeenCalled()
  })

  it('keeps the page (one role read, focus on the tab) when switching S4 to S5 through App', async () => {
    const user = userEvent.setup()
    const api = holder.api as FakeRoleAdminApi
    const router = renderApp('/admin/roles/7')
    const tab = await screen.findByRole('tab', { name: '割り当て' })
    await screen.findByTestId('roleadmin-perm')
    await user.click(tab)
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/roles/7/assignments'))
    expect(await screen.findByTestId('roleadmin-assign')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '割り当て' })).toHaveFocus()
    expect(api.getRole).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('tab', { name: '権限の設定' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/roles/7'))
    expect(await screen.findByTestId('roleadmin-perm')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '権限の設定' })).toHaveFocus()
    expect(api.getRole).toHaveBeenCalledTimes(1)
  })

  it('asks before switching tabs with unsaved changes; staying keeps the path and focus on the selected tab', async () => {
    const user = userEvent.setup()
    const api = readyApi()
    const { router } = renderDetail(api)
    const select = await screen.findByRole('combobox', { name: '販売DBの主権限' })
    await user.selectOptions(select, 'READ')
    const assignmentsTab = screen.getByRole('tab', { name: '割り当て' })
    await user.click(assignmentsTab)
    const dialog = await screen.findByRole('alertdialog', { name: '保存していない変更' })
    await waitFor(() =>
      expect(within(dialog).getByRole('button', { name: '留まる' })).toHaveFocus(),
    )
    await user.click(within(dialog).getByRole('button', { name: '留まる' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
    expect(router.state.location.pathname).toBe('/admin/roles/7')
    await waitFor(() => expect(screen.getByRole('tab', { name: '権限の設定' })).toHaveFocus())
    expect(screen.getByRole('combobox', { name: '販売DBの主権限' })).toHaveValue('READ')
  })

  it('leaves and discards the changes when the user chooses to leave', async () => {
    const user = userEvent.setup()
    const api = readyApi()
    const { router } = renderDetail(api)
    await user.selectOptions(
      await screen.findByRole('combobox', { name: '販売DBの主権限' }),
      'FULL',
    )
    await user.click(screen.getByTestId('roleadmin-back'))
    const dialog = await screen.findByRole('alertdialog', { name: '保存していない変更' })
    await user.click(within(dialog).getByRole('button', { name: '移る' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/roles'))
    expect(await screen.findByTestId('list-screen')).toBeInTheDocument()
    expect(api.savePermissions).not.toHaveBeenCalled()
  })

  it('has no accessibility violations with a 64 character role name', async () => {
    const api = readyApi()
    api.getRole.mockResolvedValue(roleOf({ name: LONG_ROLE_NAME }))
    const view = renderDetail(api)
    await screen.findByTestId('roleadmin-perm')
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
