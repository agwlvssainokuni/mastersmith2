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
// ログアウトの画面のテスト（Intent 261004-role-menu の U7 の D27、L11、security-design.md 5.2・5.3、NFR1.8）。
// StrictMode で包んで描き、効果の二度の実行でも logout が1回になることを確かめる。
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode, useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { NAV_EXPANDED_STORAGE_KEY } from '../../app/navigation/navExpansion'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../../app/testing/renderWithProviders'
import { LogoutPage } from './LogoutPage'
import { consumeLogoutIntent, markLogoutIntent, peekLogoutIntent } from './logoutIntent'
import { registration } from './registration'

const { logoutMock } = vi.hoisted(() => ({ logoutMock: vi.fn(() => Promise.resolve()) }))

vi.mock('./authSession', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./authSession')>()),
  logout: logoutMock,
}))

beforeEach(() => {
  logoutMock.mockClear()
  sessionStorage.setItem(NAV_EXPANDED_STORAGE_KEY, '["biz:0"]')
})

afterEach(() => {
  consumeLogoutIntent()
  resetDisplayTestState()
})

function renderLogout() {
  return renderWithProviders(
    <StrictMode>
      <LogoutPage />
    </StrictMode>,
    {
      route: '/logout',
      registrations: [registration],
      provider: fakeProvider({ loggedIn: true, admin: false }),
    },
  )
}

async function settle() {
  await act(async () => {
    await Promise.resolve()
  })
}

describe('LogoutPage', () => {
  it('logs out once with the intent, clears the intent and the saved expanded ids, and stays', async () => {
    markLogoutIntent()
    renderLogout()
    expect(await screen.findByRole('status')).toHaveTextContent('ログアウトしています')
    await waitFor(() => expect(logoutMock).toHaveBeenCalledTimes(1))
    await settle()
    expect(logoutMock).toHaveBeenCalledTimes(1)
    expect(peekLogoutIntent()).toBe(false)
    expect(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY)).toBeNull()
    expect(screen.getByTestId('location')).toHaveTextContent('/logout')
  })

  it('goes home with replace and does not log out without the intent', async () => {
    renderLogout()
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
    await settle()
    expect(logoutMock).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY)).toBe('["biz:0"]')
  })

  it('does nothing more when it is drawn again', async () => {
    const user = userEvent.setup()
    function Redraw() {
      const [count, setCount] = useState(0)
      return (
        <>
          <button type="button" onClick={() => setCount((value) => value + 1)}>
            {`redraw ${count}`}
          </button>
          <LogoutPage />
        </>
      )
    }
    markLogoutIntent()
    renderWithProviders(
      <StrictMode>
        <Redraw />
      </StrictMode>,
      {
        route: '/logout',
        registrations: [registration],
        provider: fakeProvider({ loggedIn: true, admin: false }),
      },
    )
    await waitFor(() => expect(logoutMock).toHaveBeenCalledTimes(1))
    markLogoutIntent()
    await user.click(screen.getByRole('button', { name: 'redraw 0' }))
    expect(screen.getByRole('button', { name: 'redraw 1' })).toBeInTheDocument()
    await settle()
    expect(logoutMock).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations', async () => {
    markLogoutIntent()
    const { container } = renderLogout()
    await screen.findByRole('status')
    expect(await axe(container)).toHaveNoViolations()
  })
})
