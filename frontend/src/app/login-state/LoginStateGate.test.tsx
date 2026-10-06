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
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import type { LoginState, LoginStateProvider } from '../registry/types'
import { LoginStateGate, normalizeLoginState, useLoginState, useLogout } from './LoginStateGate'

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

  it('passes the display settings only while logged in', async () => {
    const preferences = { language: 'en', theme: 'dark', fontSize: 'lg' } as const
    function PreferencesProbe() {
      const state = useLoginState()
      return <p data-testid="preferences">{JSON.stringify(state.preferences ?? null)}</p>
    }
    render(
      <LoginStateGate
        provider={{
          getLoginState: () => ({
            loggedIn: true,
            admin: false,
            displayName: '山田 花子',
            preferences,
          }),
        }}
      >
        <PreferencesProbe />
      </LoginStateGate>,
    )
    expect(await screen.findByTestId('preferences')).toHaveTextContent(JSON.stringify(preferences))
  })

  it('drops the display settings and the name when logged out', () => {
    expect(
      normalizeLoginState({
        loggedIn: false,
        admin: true,
        displayName: '山田 花子',
        preferences: { language: 'en' },
      }),
    ).toEqual({ loggedIn: false, admin: false })
  })

  it('keeps a provider without display settings working as before', () => {
    expect(normalizeLoginState({ loggedIn: true, admin: true, displayName: '管理者' })).toEqual({
      loggedIn: true,
      admin: true,
      displayName: '管理者',
    })
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

/** useLogout の関数を押して、その答えを出す */
function LogoutProbe() {
  const logout = useLogout()
  const [answer, setAnswer] = useState('-')
  return (
    <main>
      <button
        type="button"
        data-testid="logout-probe-button"
        onClick={() => {
          void logout().then((called) => setAnswer(String(called)))
        }}
      >
        logout
      </button>
      <p data-testid="logout-probe-answer">{answer}</p>
    </main>
  )
}

async function pressLogout(): Promise<string> {
  const user = userEvent.setup()
  await user.click(await screen.findByTestId('logout-probe-button'))
  await waitFor(() => expect(screen.getByTestId('logout-probe-answer')).not.toHaveTextContent('-'))
  return screen.getByTestId('logout-probe-answer').textContent ?? ''
}

const loggedIn = () => ({ loggedIn: true, admin: false })

describe('useLogout', () => {
  it('calls the logout of the provider and answers true', async () => {
    const logout = vi.fn(() => Promise.resolve())
    render(
      <LoginStateGate provider={{ getLoginState: loggedIn, logout }}>
        <LogoutProbe />
      </LoginStateGate>,
    )

    expect(await pressLogout()).toBe('true')
    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('does nothing and answers false when the provider has no logout', async () => {
    render(
      <LoginStateGate provider={{ getLoginState: loggedIn }}>
        <LogoutProbe />
      </LoginStateGate>,
    )

    expect(await pressLogout()).toBe('false')
  })

  it('keeps a failing logout inside and answers true because it was called', async () => {
    const logout = vi.fn(() => Promise.reject(new Error('network down')))
    render(
      <LoginStateGate provider={{ getLoginState: loggedIn, logout }}>
        <LogoutProbe />
      </LoginStateGate>,
    )

    expect(await pressLogout()).toBe('true')
    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('answers false when no provider is registered', async () => {
    render(
      <LoginStateGate>
        <LogoutProbe />
      </LoginStateGate>,
    )

    expect(await pressLogout()).toBe('false')
  })
})
