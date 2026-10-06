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
import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { useParams } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { apiRequest } from '../../shared/api-client/apiClient'
import { useAdminForbidden } from '../admin-forbidden/AdminForbiddenProvider'
import type { FeatureRegistration, LoginState, LoginStateProvider } from '../registry/types'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../testing/renderWithProviders'
import { AppRouter } from './AppRouter'

function ItemScreen() {
  const { id } = useParams()
  return <h1 data-testid="item-screen">{`item ${id}`}</h1>
}
function AdminScreen() {
  return <h1 data-testid="admin-screen">admin</h1>
}
function LoginScreen() {
  return <h1 data-testid="login-screen">login</h1>
}

const screens: FeatureRegistration = {
  featureId: 'screens',
  routes: [
    { path: '/items/:id', screen: ItemScreen, layout: 'SHELL', access: 'LOGGED_IN' },
    { path: '/admin', screen: AdminScreen, layout: 'SHELL', access: 'ADMIN' },
  ],
}
const auth: FeatureRegistration = {
  featureId: 'auth',
  routes: [
    { path: '/login', screen: LoginScreen, layout: 'STANDALONE', access: 'PUBLIC', role: 'LOGIN' },
  ],
}

const member = fakeProvider({ loggedIn: true, admin: false, displayName: 'user@example.com' })
const admin = fakeProvider({ loggedIn: true, admin: true, displayName: 'admin@example.com' })

describe('AppRouter', () => {
  it('shows only the login layout at / with U1 alone', () => {
    renderWithProviders(<AppRouter />)
    expect(screen.getByTestId('login-layout')).toBeInTheDocument()
    expect(screen.queryByTestId('app-shell')).not.toBeInTheDocument()
  })

  it('sends logged-out users opening LOGGED_IN or ADMIN screens to the login screen', () => {
    renderWithProviders(<AppRouter />, { route: '/items/1', registrations: [screens, auth] })
    expect(screen.getByTestId('login-screen')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
    expect(screen.getByTestId('standalone-layout')).toBeInTheDocument()
  })

  it('does not show ADMIN screens to logged-in non-admins', async () => {
    renderWithProviders(<AppRouter />, {
      route: '/admin',
      registrations: [screens],
      provider: member,
    })
    expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
    expect(screen.getByTestId('app-shell')).toBeInTheDocument()
    expect(screen.queryByTestId('admin-screen')).not.toBeInTheDocument()
    expect(screen.queryByTestId('not-found-page')).not.toBeInTheDocument()
  })

  it('shows ADMIN screens to administrators inside the app shell', async () => {
    renderWithProviders(<AppRouter />, {
      route: '/admin',
      registrations: [screens],
      provider: admin,
    })
    expect(await screen.findByTestId('admin-screen')).toBeInTheDocument()
    expect(screen.getByTestId('app-shell')).toBeInTheDocument()
  })

  it('passes URL parameters to SHELL screens', async () => {
    renderWithProviders(<AppRouter />, {
      route: '/items/42',
      registrations: [screens],
      provider: member,
    })
    expect(await screen.findByTestId('item-screen')).toHaveTextContent('item 42')
  })

  it('shows not found inside the shell for unregistered URLs when logged in', async () => {
    renderWithProviders(<AppRouter />, { route: '/no/such', provider: member })
    expect(await screen.findByTestId('not-found-page')).toBeInTheDocument()
    expect(screen.getByTestId('app-shell')).toBeInTheDocument()
  })

  it('shows home at / when logged in', async () => {
    renderWithProviders(<AppRouter />, { provider: member })
    expect(await screen.findByTestId('home-page')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(<AppRouter />)
    expect(await axe(container)).toHaveNoViolations()
  })

  describe('admin forbidden (U4)', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
      resetDisplayTestState()
    })

    /** 開いたときに管理の API を呼ぶ管理の画面 */
    function CallingAdminScreen() {
      useEffect(() => {
        apiRequest('/api/admin/check').catch(() => {})
      }, [])
      return <h1 data-testid="admin-screen">admin</h1>
    }

    /** 押すと管理の API の 403 を骨組みに渡す管理の画面 */
    function ReportingAdminScreen() {
      const report = useAdminForbidden()
      return (
        <button
          type="button"
          data-testid="report-403"
          onClick={() =>
            report({ kind: 'response', status: 403, code: 'ACCESS_DENIED' }, '/api/admin/check')
          }
        >
          admin
        </button>
      )
    }

    function adminFeature(screen: () => React.JSX.Element): FeatureRegistration {
      return {
        featureId: 'manage',
        routes: [{ path: '/admin', screen, layout: 'SHELL', access: 'ADMIN' }],
        sidebarItems: [
          {
            id: 'admin',
            labelKey: 'manage.nav',
            path: '/admin',
            order: 90,
            visibleWhen: 'ADMIN',
            section: 'ADMIN',
          },
        ],
        messages: { ja: { 'manage.nav': '管理の入口' }, en: { 'manage.nav': 'Admin area' } },
      }
    }

    /** 状態を差し替えて知らせる偽物の提供元 */
    function switchableProvider(initial: LoginState): {
      provider: LoginStateProvider
      set: (next: LoginState) => void
    } {
      let state = initial
      const listeners = new Set<() => void>()
      return {
        provider: {
          getLoginState: () => state,
          subscribe: (listener) => {
            listeners.add(listener)
            return () => {
              listeners.delete(listener)
            }
          },
        },
        set: (next) => {
          state = next
          for (const listener of listeners) {
            listener()
          }
        },
      }
    }

    it('never sends a request to the admin API for non-admins opening an admin screen', async () => {
      const fetchMock = vi.fn(() => Promise.resolve(new Response('{}', { status: 200 })))
      vi.stubGlobal('fetch', fetchMock)
      renderWithProviders(<AppRouter />, {
        route: '/admin',
        registrations: [adminFeature(CallingAdminScreen)],
        provider: member,
      })
      await screen.findByTestId('admin-forbidden-view')
      await act(async () => {
        await Promise.resolve()
      })

      expect(fetchMock).not.toHaveBeenCalled()
      expect(screen.queryByTestId('admin-screen')).not.toBeInTheDocument()
    })

    it('hides the admin menu and shows the forbidden view when the login state at start is no longer admin', async () => {
      renderWithProviders(<AppRouter />, {
        route: '/admin',
        registrations: [adminFeature(AdminScreen)],
        provider: member,
      })
      expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
      expect(screen.getByTestId('admin-forbidden-heading')).toHaveTextContent('管理の入口')
      expect(screen.queryByTestId('sidebar-nav-/admin')).not.toBeInTheDocument()
    })

    it('keeps the same shell and view from forbidden by the API to forbidden by the route', async () => {
      const user = userEvent.setup()
      const login = switchableProvider({ loggedIn: true, admin: true, displayName: '管理者' })
      renderWithProviders(<AppRouter />, {
        route: '/admin',
        registrations: [adminFeature(ReportingAdminScreen)],
        provider: login.provider,
      })
      expect(await screen.findByTestId('sidebar-nav-/admin')).toBeInTheDocument()
      await user.click(screen.getByTestId('report-403'))

      const heading = await screen.findByTestId('admin-forbidden-heading')
      await waitFor(() => expect(heading).toHaveFocus())
      const shell = screen.getByTestId('app-shell')
      fireEvent.click(screen.getByTestId('sidebar-toggle'))
      expect(shell).toHaveClass('collapsed')
      expect(heading).toHaveFocus()

      act(() => login.set({ loggedIn: true, admin: false, displayName: '管理者' }))
      await waitFor(() =>
        expect(screen.queryByTestId('sidebar-nav-/admin')).not.toBeInTheDocument(),
      )

      expect(screen.getByTestId('admin-forbidden-heading')).toBe(heading)
      expect(screen.getByTestId('app-shell')).toBe(shell)
      expect(heading).toHaveFocus()
      expect(shell).toHaveClass('collapsed')
      expect(screen.queryByTestId('report-403')).not.toBeInTheDocument()
    })
  })
})
