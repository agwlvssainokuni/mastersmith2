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
// 読み直しのきっかけのテスト（Intent 261004-role-menu の U7 の D4、L4、security-design.md 5.2）。
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { NavigateButton, switchableProvider } from '../testing/frameTestSupport'
import { renderWithProviders, resetDisplayTestState } from '../testing/renderWithProviders'
import { useReloadTriggers } from './useReloadTriggers'

afterEach(() => {
  resetDisplayTestState()
})

function Probe({ onTrigger }: { onTrigger: () => void }) {
  const [extra, setExtra] = useState(0)
  const navigate = useNavigate()
  useReloadTriggers(onTrigger, extra)
  return (
    <div>
      <NavigateButton to="/a" label="path" />
      <NavigateButton to="/a?q=1" label="search" />
      <NavigateButton to="/logout" label="logout" />
      <button type="button" onClick={() => setExtra((value) => value + 1)}>
        extra
      </button>
      <button
        type="button"
        onClick={() => {
          setExtra((value) => value + 1)
          setExtra((value) => value + 1)
        }}
      >
        extra-twice
      </button>
      <button type="button" onClick={() => void navigate('/b?q=2')}>
        both
      </button>
    </div>
  )
}

async function settle() {
  await act(async () => {
    await Promise.resolve()
  })
}

describe('useReloadTriggers', () => {
  it('fires once at login and again for a login notice, a path, a query and the extra key', async () => {
    const user = userEvent.setup()
    const onTrigger = vi.fn()
    const login = switchableProvider({ loggedIn: true, admin: false })
    renderWithProviders(<Probe onTrigger={onTrigger} />, { provider: login.provider })
    await waitFor(() => expect(onTrigger).toHaveBeenCalledTimes(1))

    act(() => login.notify())
    await waitFor(() => expect(onTrigger).toHaveBeenCalledTimes(2))
    await user.click(screen.getByRole('button', { name: 'path' }))
    await waitFor(() => expect(onTrigger).toHaveBeenCalledTimes(3))
    await user.click(screen.getByRole('button', { name: 'search' }))
    await waitFor(() => expect(onTrigger).toHaveBeenCalledTimes(4))
    await user.click(screen.getByRole('button', { name: 'extra' }))
    await waitFor(() => expect(onTrigger).toHaveBeenCalledTimes(5))
  })

  it('merges a path and query change, and two extra changes of one render, into one call each', async () => {
    const user = userEvent.setup()
    const onTrigger = vi.fn()
    renderWithProviders(<Probe onTrigger={onTrigger} />, {
      provider: { getLoginState: () => ({ loggedIn: true, admin: false }) },
    })
    await waitFor(() => expect(onTrigger).toHaveBeenCalledTimes(1))
    await user.click(screen.getByRole('button', { name: 'both' }))
    await settle()
    expect(onTrigger).toHaveBeenCalledTimes(2)
    await user.click(screen.getByRole('button', { name: 'extra-twice' }))
    await settle()
    expect(onTrigger).toHaveBeenCalledTimes(3)
  })

  it('does not fire on /logout or while logged out', async () => {
    const user = userEvent.setup()
    const onTrigger = vi.fn()
    const login = switchableProvider({ loggedIn: true, admin: false })
    renderWithProviders(<Probe onTrigger={onTrigger} />, { provider: login.provider })
    await waitFor(() => expect(onTrigger).toHaveBeenCalledTimes(1))
    await user.click(screen.getByRole('button', { name: 'logout' }))
    await settle()
    act(() => login.set({ loggedIn: false, admin: false }))
    await settle()
    await user.click(screen.getByRole('button', { name: 'path' }))
    await settle()
    expect(onTrigger).toHaveBeenCalledTimes(1)
  })
})
