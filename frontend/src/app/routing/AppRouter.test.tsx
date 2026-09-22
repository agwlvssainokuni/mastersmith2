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
import { screen } from '@testing-library/react'
import { useParams } from 'react-router'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import type { FeatureRegistration } from '../registry/types'
import { fakeProvider, renderWithProviders } from '../testing/renderWithProviders'
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
    expect(await screen.findByTestId('not-found-page')).toBeInTheDocument()
    expect(screen.queryByTestId('admin-screen')).not.toBeInTheDocument()
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
})
