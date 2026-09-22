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
import { render, screen } from '@testing-library/react'
import { useToast } from 'make-you-chic-ui'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { App } from './App'
import type { FeatureRegistration } from './registry/types'
import { stubBrowserLanguages } from './testing/renderWithProviders'

function renderApp(modules?: Record<string, unknown>, route = '/') {
  stubBrowserLanguages(['ja-JP'])
  return render(
    <MemoryRouter initialEntries={[route]}>
      <App modules={modules} />
    </MemoryRouter>,
  )
}

function ToastProbe() {
  const toast = useToast()
  return <h1 data-testid="toast-probe">{typeof toast.show}</h1>
}

describe('App', () => {
  it('starts with U1 alone and shows only the login layout', () => {
    // U2 以降の登録に左右されずに U1 だけの状態を確かめるため、空の登録を渡す。
    renderApp({})
    expect(screen.getByTestId('login-layout')).toBeInTheDocument()
    expect(screen.queryByTestId('app-shell')).not.toBeInTheDocument()
  })

  it('stops starting and names the problem when registrations are duplicated', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const duplicated: FeatureRegistration = {
      featureId: 'dup',
      routes: [{ path: '/x', screen: () => null, layout: 'SHELL', access: 'LOGGED_IN' }],
    }
    renderApp({
      'features/a/registration.ts': { registration: duplicated },
      'features/b/registration.ts': { registration: { ...duplicated } },
    })
    expect(screen.getByTestId('startup-error-page')).toBeInTheDocument()
    expect(screen.getByTestId('startup-error-problems')).toHaveTextContent(/"\/x"/)
    expect(screen.getByTestId('startup-error-problems')).toHaveTextContent(/featureId が重複/)
    expect(screen.queryByTestId('login-layout')).not.toBeInTheDocument()
  })

  it('stops starting when a registration file has no registration export', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    renderApp({ 'features/broken/registration.ts': {} })
    expect(screen.getByTestId('startup-error-problems')).toHaveTextContent(/features\/broken/)
  })

  it('wires the design system providers and feature registrations', async () => {
    const registration: FeatureRegistration = {
      featureId: 'probe',
      routes: [{ path: '/probe', screen: ToastProbe, layout: 'STANDALONE', access: 'PUBLIC' }],
      messages: { ja: { 'probe.title': '確認' }, en: { 'probe.title': 'Probe' } },
      loginStateProvider: { getLoginState: () => ({ loggedIn: false, admin: false }) },
    }
    renderApp({ 'features/probe/registration.ts': { registration } }, '/probe')
    expect(await screen.findByTestId('toast-probe')).toHaveTextContent('function')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderApp({})
    expect(await axe(container)).toHaveNoViolations()
  })
})
