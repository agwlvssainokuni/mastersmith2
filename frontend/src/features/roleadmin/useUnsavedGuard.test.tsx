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
// 未保存の変更の確かめの口のテスト（Intent 261004-role-menu の U6 の D15、7.2、L6）。data router（createMemoryRouter）で
// 描き、写しがある間だけ道の移動を止める・道が同じ移動は止めない・留まる・移る（写しを捨ててから移る）、beforeunload の
// 張り外しを確かめる。
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useUnsavedGuard } from './useUnsavedGuard'

function Probe({ initialDirty, onDiscard }: { initialDirty: boolean; onDiscard: () => void }) {
  const [dirty, setDirty] = useState(initialDirty)
  const guard = useUnsavedGuard(dirty, () => {
    onDiscard()
    setDirty(false)
  })
  return (
    <div>
      <output data-testid="blocked">{String(guard.blocked)}</output>
      <button type="button" onClick={guard.stay}>
        stay
      </button>
      <button type="button" onClick={guard.leave}>
        leave
      </button>
      <button type="button" onClick={() => setDirty(false)}>
        clean
      </button>
    </div>
  )
}

function renderProbe(initialDirty = true) {
  const onDiscard = vi.fn()
  const element = <Probe initialDirty={initialDirty} onDiscard={onDiscard} />
  const router = createMemoryRouter(
    [
      { path: '/admin/roles/:roleId', element },
      { path: '/admin/roles', element: <p data-testid="list">list</p> },
    ],
    { initialEntries: ['/admin/roles/7'] },
  )
  render(<RouterProvider router={router} />)
  return { router, onDiscard }
}

describe('useUnsavedGuard', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('blocks a navigation to another path while dirty, and stays on reset', async () => {
    const user = userEvent.setup()
    const { router, onDiscard } = renderProbe()
    await act(async () => {
      await router.navigate('/admin/roles')
    })
    expect(screen.getByTestId('blocked')).toHaveTextContent('true')
    await user.click(screen.getByRole('button', { name: 'stay' }))
    expect(screen.getByTestId('blocked')).toHaveTextContent('false')
    expect(router.state.location.pathname).toBe('/admin/roles/7')
    expect(onDiscard).not.toHaveBeenCalled()
  })

  it('discards the changes and proceeds on leave', async () => {
    const user = userEvent.setup()
    const { router, onDiscard } = renderProbe()
    await act(async () => {
      await router.navigate('/admin/roles')
    })
    await user.click(screen.getByRole('button', { name: 'leave' }))
    expect(onDiscard).toHaveBeenCalledTimes(1)
    expect(await screen.findByTestId('list')).toBeInTheDocument()
  })

  it('does not block when clean or when only the query changes', async () => {
    const { router } = renderProbe()
    await act(async () => {
      await router.navigate('/admin/roles/7?x=1')
    })
    expect(screen.getByTestId('blocked')).toHaveTextContent('false')
    expect(router.state.location.search).toBe('?x=1')

    const clean = renderProbe(false)
    await act(async () => {
      await clean.router.navigate('/admin/roles')
    })
    expect(clean.router.state.location.pathname).toBe('/admin/roles')
  })

  it('adds the beforeunload listener only while dirty and removes it when clean', async () => {
    const user = userEvent.setup()
    const add = vi.spyOn(window, 'addEventListener')
    const remove = vi.spyOn(window, 'removeEventListener')
    renderProbe()
    const added = add.mock.calls.filter(([type]) => type === 'beforeunload')
    expect(added).toHaveLength(1)
    const listener = added[0]?.[1]
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    await user.click(screen.getByRole('button', { name: 'clean' }))
    expect(remove).toHaveBeenCalledWith('beforeunload', listener)
  })

  it('does not add the beforeunload listener while clean', () => {
    const add = vi.spyOn(window, 'addEventListener')
    renderProbe(false)
    expect(add.mock.calls.filter(([type]) => type === 'beforeunload')).toHaveLength(0)
  })

  it('ignores stay and leave when nothing is blocked', async () => {
    const user = userEvent.setup()
    const { router, onDiscard } = renderProbe()
    await user.click(screen.getByRole('button', { name: 'leave' }))
    await user.click(screen.getByRole('button', { name: 'stay' }))
    expect(onDiscard).not.toHaveBeenCalled()
    expect(router.state.location.pathname).toBe('/admin/roles/7')
  })
})
