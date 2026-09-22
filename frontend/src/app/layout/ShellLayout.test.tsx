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
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import type { FeatureRegistration } from '../registry/types'
import { fakeProvider, renderWithProviders } from '../testing/renderWithProviders'
import { ShellLayout } from './ShellLayout'

function registrations(logout: () => void): FeatureRegistration[] {
  return [
    {
      featureId: 'demo',
      routes: [
        { path: '/reports', screen: () => null, layout: 'SHELL', access: 'LOGGED_IN' },
        { path: '/admin', screen: () => null, layout: 'SHELL', access: 'ADMIN' },
      ],
      sidebarItems: [
        { id: 'admin', labelKey: 'demo.admin', path: '/admin', order: 20, visibleWhen: 'ADMIN' },
        {
          id: 'reports',
          labelKey: 'demo.reports',
          path: '/reports',
          order: 10,
          visibleWhen: 'LOGGED_IN',
        },
      ],
      userMenuItems: [{ id: 'logout', labelKey: 'demo.logout', action: logout, order: 1 }],
      messages: {
        ja: { 'demo.admin': '管理', 'demo.reports': '帳票', 'demo.logout': 'ログアウト' },
        en: { 'demo.admin': 'Admin', 'demo.reports': 'Reports', 'demo.logout': 'Sign out' },
      },
    },
  ]
}

const content = <p data-testid="shell-content">中身</p>

describe('ShellLayout', () => {
  it('lists home first and then the visible items in order', async () => {
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: true, displayName: '管理者' }),
    })
    const nav = await screen.findByRole('navigation')
    expect(
      within(nav)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/', '/reports', '/admin'])
    expect(screen.getByTestId('shell-content')).toBeInTheDocument()
  })

  it('hides ADMIN items from non-administrators', async () => {
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: false }),
    })
    const nav = await screen.findByRole('navigation')
    expect(within(nav).queryByTestId('sidebar-nav-/admin')).not.toBeInTheDocument()
    expect(within(nav).getByTestId('sidebar-nav-/reports')).toBeInTheDocument()
  })

  it('moves to the selected screen when a sidebar item is chosen', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: false }),
    })
    await user.click(await screen.findByTestId('sidebar-nav-/reports'))
    expect(screen.getByTestId('location')).toHaveTextContent('/reports')
  })

  it('shows the display name and runs the chosen user menu action', async () => {
    const user = userEvent.setup()
    const logout = vi.fn()
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(logout),
      provider: fakeProvider({ loggedIn: true, admin: false, displayName: 'user@example.com' }),
    })
    await user.click(await screen.findByRole('button', { name: /user@example.com/ }))
    await user.click(await screen.findByText('ログアウト'))
    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('uses English labels for an English browser', async () => {
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: false }),
      languages: ['en-US'],
    })
    expect(await screen.findByText('Reports')).toBeInTheDocument()
    expect(screen.getByText('Home')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: true, displayName: '管理者' }),
    })
    await screen.findByTestId('shell-content')
    expect(await axe(container)).toHaveNoViolations()
  })
})
