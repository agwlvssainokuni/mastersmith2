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
import { act, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import type { LoginState, LoginStateProvider } from '../registry/types'
import { LoginStateGate, useLoginState } from './LoginStateGate'

function Probe() {
  const state = useLoginState()
  return (
    <main>
      <p data-testid="state">{`${state.loggedIn}/${state.admin}/${state.displayName ?? '-'}`}</p>
    </main>
  )
}

describe('LoginStateGate', () => {
  it('treats the user as logged out when no provider is registered', () => {
    render(
      <LoginStateGate>
        <Probe />
      </LoginStateGate>,
    )
    expect(screen.getByTestId('state')).toHaveTextContent('false/false/-')
  })

  it('passes the state of the provider to its children', async () => {
    const provider: LoginStateProvider = {
      getLoginState: () => ({ loggedIn: true, admin: true, displayName: '管理者' }),
    }
    render(
      <LoginStateGate provider={provider}>
        <Probe />
      </LoginStateGate>,
    )
    expect(await screen.findByTestId('state')).toHaveTextContent('true/true/管理者')
  })

  it('never reports admin when the provider says logged out', async () => {
    render(
      <LoginStateGate provider={{ getLoginState: async () => ({ loggedIn: false, admin: true }) }}>
        <Probe />
      </LoginStateGate>,
    )
    expect(await screen.findByTestId('state')).toHaveTextContent('false/false/-')
  })

  it('treats a failing provider as logged out', async () => {
    render(
      <LoginStateGate
        provider={{
          getLoginState: () => {
            throw new Error('問い合わせの失敗')
          },
        }}
      >
        <Probe />
      </LoginStateGate>,
    )
    expect(await screen.findByTestId('state')).toHaveTextContent('false/false/-')
  })

  it('follows changes announced by the provider and stops listening when unmounted', async () => {
    let current: LoginState = { loggedIn: false, admin: false }
    const listeners = new Set<() => void>()
    const provider: LoginStateProvider = {
      getLoginState: () => current,
      subscribe: (listener) => {
        listeners.add(listener)
        return () => listeners.delete(listener)
      },
    }
    const { unmount } = render(
      <LoginStateGate provider={provider}>
        <Probe />
      </LoginStateGate>,
    )
    expect(await screen.findByTestId('state')).toHaveTextContent('false/false/-')

    current = { loggedIn: true, admin: false, displayName: 'user@example.com' }
    await act(async () => {
      listeners.forEach((listener) => listener())
    })
    expect(screen.getByTestId('state')).toHaveTextContent('true/false/user@example.com')

    unmount()
    expect(listeners.size).toBe(0)
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <LoginStateGate>
        <Probe />
      </LoginStateGate>,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
