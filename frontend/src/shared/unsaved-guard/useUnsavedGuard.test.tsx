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
// 張り外しを確かめる。Intent 261016-business-data-views の U3 で src/shared/ へ移し、markClean と入力の版の数の確かめを足した
// （D25、計画 4.3 の R-08、U3-NFR7.11）。
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { shouldBlockNavigation, useUnsavedGuard } from './useUnsavedGuard'

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

/** 入力の版の数と markClean を使う画面（業務データの作成・更新のフォームと同じ使い方） */
function FormProbe({ onNavigate }: { onNavigate: (to: string) => void }) {
  const [text, setText] = useState('')
  const [version, setVersion] = useState(0)
  const guard = useUnsavedGuard(text !== '', () => setText(''), { version })
  const [skipping, setSkipping] = useState<boolean | null>(null)
  return (
    <div>
      <output data-testid="blocked">{String(guard.blocked)}</output>
      <button
        type="button"
        onClick={() => {
          setText((current) => `${current}x`)
          setVersion((current) => current + 1)
        }}
      >
        edit
      </button>
      <button type="button" onClick={guard.markClean}>
        markClean
      </button>
      <button
        type="button"
        onClick={() => {
          guard.markClean()
          onNavigate('/done')
        }}
      >
        saveAndGo
      </button>
      <button type="button" onClick={guard.stay}>
        stay
      </button>
      <button type="button" onClick={() => setSkipping(guard.skipsNextNavigation())}>
        readSkip
      </button>
      <output data-testid="skipping">{String(skipping)}</output>
    </div>
  )
}

function renderFormProbe() {
  let navigateTo: (to: string) => void = () => {}
  const router = createMemoryRouter(
    [
      { path: '/form', element: <FormProbe onNavigate={(to) => navigateTo(to)} /> },
      { path: '/done', element: <p data-testid="done">done</p> },
      { path: '/other', element: <p data-testid="other">other</p> },
    ],
    { initialEntries: ['/form'] },
  )
  navigateTo = (to) => {
    void router.navigate(to)
  }
  render(<RouterProvider router={router} />)
  return { router }
}

describe('useUnsavedGuard markClean', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('does not block the navigation right after markClean', async () => {
    const user = userEvent.setup()
    const { router } = renderFormProbe()
    await user.click(screen.getByRole('button', { name: 'edit' }))
    await user.click(screen.getByRole('button', { name: 'saveAndGo' }))
    expect(await screen.findByTestId('done')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/done')
  })

  it('blocks again when an input changes after markClean', async () => {
    const user = userEvent.setup()
    const { router } = renderFormProbe()
    await user.click(screen.getByRole('button', { name: 'edit' }))
    await user.click(screen.getByRole('button', { name: 'markClean' }))
    await user.click(screen.getByRole('button', { name: 'edit' }))
    await act(async () => {
      await router.navigate('/other')
    })
    expect(screen.getByTestId('blocked')).toHaveTextContent('true')
    expect(router.state.location.pathname).toBe('/form')
  })

  it('still blocks without markClean', async () => {
    const user = userEvent.setup()
    const { router } = renderFormProbe()
    await user.click(screen.getByRole('button', { name: 'edit' }))
    await act(async () => {
      await router.navigate('/other')
    })
    expect(screen.getByTestId('blocked')).toHaveTextContent('true')
  })

  it('skips only one navigation even when the blocker function is called many times', async () => {
    const user = userEvent.setup()
    const { router } = renderFormProbe()
    await user.click(screen.getByRole('button', { name: 'edit' }))
    await user.click(screen.getByRole('button', { name: 'markClean' }))
    // 道の変わらない移動が成り立つと、飛ばす旗は下りる。その後に変更を作ると、次の移動は止まる。
    await act(async () => {
      await router.navigate('/form?x=1')
    })
    await act(async () => {
      await router.navigate('/form?x=2')
    })
    await user.click(screen.getByRole('button', { name: 'edit' }))
    await act(async () => {
      await router.navigate('/other')
    })
    expect(screen.getByTestId('blocked')).toHaveTextContent('true')
    await user.click(screen.getByRole('button', { name: 'stay' }))
    expect(router.state.location.pathname).toBe('/form')
  })

  it('removes beforeunload on markClean and adds it again after the next change', async () => {
    const user = userEvent.setup()
    const add = vi.spyOn(window, 'addEventListener')
    const remove = vi.spyOn(window, 'removeEventListener')
    renderFormProbe()
    await user.click(screen.getByRole('button', { name: 'edit' }))
    const addedBefore = add.mock.calls.filter(([type]) => type === 'beforeunload').length
    expect(addedBefore).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: 'markClean' }))
    expect(remove.mock.calls.some(([type]) => type === 'beforeunload')).toBe(true)
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)
    await user.click(screen.getByRole('button', { name: 'edit' }))
    await waitFor(() =>
      expect(add.mock.calls.filter(([type]) => type === 'beforeunload').length).toBeGreaterThan(
        addedBefore,
      ),
    )
    const again = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(again)
    expect(again.defaultPrevented).toBe(true)
  })

  it('drops the skip flag in the next task when no navigation starts after markClean (R-05)', async () => {
    const user = userEvent.setup()
    renderFormProbe()
    await user.click(screen.getByRole('button', { name: 'edit' }))
    // markClean と旗の読み取りを同じ手順で行うと、旗は立っている
    await act(async () => {
      screen.getByRole('button', { name: 'markClean' }).click()
      screen.getByRole('button', { name: 'readSkip' }).click()
    })
    expect(screen.getByTestId('skipping')).toHaveTextContent('true')
    // 移動が始まらないまま次のタスクになると、旗は下りる
    await act(async () => {
      await new Promise((settle) => setTimeout(settle, 0))
    })
    await user.click(screen.getByRole('button', { name: 'readSkip' }))
    expect(screen.getByTestId('skipping')).toHaveTextContent('false')
  })

  it('decides without side effects', () => {
    expect(shouldBlockNavigation(true, false, '/a', '/b')).toBe(true)
    expect(shouldBlockNavigation(true, true, '/a', '/b')).toBe(false)
    expect(shouldBlockNavigation(true, true, '/a', '/b')).toBe(false)
    expect(shouldBlockNavigation(true, false, '/a', '/a')).toBe(false)
    expect(shouldBlockNavigation(false, false, '/a', '/b')).toBe(false)
  })
})
