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
// 画面の入口の data router（main.tsx の createBrowserRouter）への差し替えの見張り（Intent 261004-role-menu の U6、
// L11・NFR6.7、cicd-pipeline.md 3節）。main.tsx と同じ形（すべての道を1つの経路で App に渡す）を
// createMemoryRouter で作り、App の中の画面で useBlocker が働くことと、App の中の振り分けが変わらないことを確かめる。
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect, useRef, useState } from 'react'
import { createMemoryRouter, RouterProvider, useBlocker } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { App } from '../App'
import { resolvedAppearance } from '../display-settings/appearanceLoad'
import type { FeatureRegistration, LoginState } from '../registry/types'
import { resetDisplayTestState, stubBrowserLanguages } from '../testing/renderWithProviders'

/**
 * 未保存の変更を持てる画面（useBlocker を使う）。react-router の useBlocker は、最初の描画の効果で鍵を状態に置き、
 * 次の描画の効果で router.getBlocker に関数を登録する（node_modules/react-router の hooks.js）。そのため、印
 * （guard-ready の data-ready）は2回目の描画の効果で立てる。効果は宣言の順に走るため、useBlocker の効果の後に走る。
 * 印は状態にせず DOM に直接立てる（効果の中で setState しない。oxlint の react(set-state-in-effect)）。
 */
function GuardedScreen() {
  const [dirty, setDirty] = useState(true)
  const readyRef = useRef<HTMLOutputElement>(null)
  const commits = useRef(0)
  const blocker = useBlocker(dirty)
  useEffect(() => {
    commits.current += 1
    if (commits.current >= 2) {
      readyRef.current?.setAttribute('data-ready', 'true')
    }
  })
  return (
    <div>
      <h1 data-testid="guarded-screen">guarded</h1>
      <output ref={readyRef} data-testid="guard-ready" />
      <output data-testid="blocker-state">{blocker.state}</output>
      <button type="button" onClick={() => setDirty(false)}>
        clean
      </button>
      {blocker.state === 'blocked' ? (
        <>
          <button type="button" onClick={() => blocker.reset()}>
            stay
          </button>
          <button type="button" onClick={() => blocker.proceed()}>
            leave
          </button>
        </>
      ) : null}
    </div>
  )
}

function OtherScreen() {
  return <h1 data-testid="other-screen">other</h1>
}

function registration(state: LoginState): FeatureRegistration {
  return {
    featureId: 'probe',
    routes: [
      { path: '/admin/guarded', screen: GuardedScreen, layout: 'SHELL', access: 'ADMIN' },
      { path: '/admin/other', screen: OtherScreen, layout: 'SHELL', access: 'ADMIN' },
    ],
    sidebarItems: [
      {
        id: 'probe-other',
        labelKey: 'probe.other',
        path: '/admin/other',
        order: 900,
        visibleWhen: 'ADMIN',
        section: 'ADMIN',
      },
    ],
    messages: { ja: { 'probe.other': 'ほかの画面' }, en: { 'probe.other': 'Other' } },
    loginStateProvider: { getLoginState: () => state },
  }
}

const ADMIN: LoginState = { loggedIn: true, admin: true, displayName: '管理 太郎' }
const MEMBER: LoginState = { loggedIn: true, admin: false, displayName: '利用 花子' }

/** main.tsx と同じ形の data router で App を描く。 */
function renderDataRouter(entries: string[], state: LoginState = ADMIN) {
  stubBrowserLanguages(['ja-JP'])
  const modules = { 'features/probe/registration.ts': { registration: registration(state) } }
  const router = createMemoryRouter(
    [{ path: '*', element: <App modules={modules} appearance={resolvedAppearance()} /> }],
    { initialEntries: entries, initialIndex: entries.length - 1 },
  )
  render(<RouterProvider router={router} />)
  return router
}

afterEach(() => {
  resetDisplayTestState()
})

describe('data router entry', () => {
  it('blocks a sidebar navigation while the screen has unsaved changes, and stays on reset', async () => {
    const user = userEvent.setup()
    const router = renderDataRouter(['/admin/guarded'])
    await waitFor(() =>
      expect(screen.getByTestId('guard-ready')).toHaveAttribute('data-ready', 'true'),
    )

    await user.click(await screen.findByTestId('sidebar-nav-/admin/other'))
    await waitFor(() => expect(screen.getByTestId('blocker-state')).toHaveTextContent('blocked'))
    expect(router.state.location.pathname).toBe('/admin/guarded')

    await user.click(screen.getByRole('button', { name: 'stay' }))
    await waitFor(() => expect(screen.getByTestId('blocker-state')).toHaveTextContent('unblocked'))
    expect(router.state.location.pathname).toBe('/admin/guarded')
    expect(screen.queryByTestId('other-screen')).not.toBeInTheDocument()
  })

  it('blocks the browser back navigation and moves after proceed', async () => {
    const user = userEvent.setup()
    const router = renderDataRouter(['/admin/other', '/admin/guarded'])
    expect(await screen.findByTestId('guarded-screen')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByTestId('guard-ready')).toHaveAttribute('data-ready', 'true'),
    )

    await act(async () => {
      await router.navigate(-1)
    })
    await waitFor(() => expect(screen.getByTestId('blocker-state')).toHaveTextContent('blocked'))
    expect(router.state.location.pathname).toBe('/admin/guarded')

    await user.click(screen.getByRole('button', { name: 'leave' }))
    expect(await screen.findByTestId('other-screen')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/other')
  })

  it('moves without blocking when there are no unsaved changes', async () => {
    const user = userEvent.setup()
    const router = renderDataRouter(['/admin/guarded'])
    await user.click(await screen.findByRole('button', { name: 'clean' }))
    await user.click(await screen.findByTestId('sidebar-nav-/admin/other'))
    expect(await screen.findByTestId('other-screen')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/other')
  })

  it('keeps the routing inside App and shows home at /', async () => {
    renderDataRouter(['/'])
    expect(await screen.findByTestId('home-page')).toBeInTheDocument()
  })

  it('shows not found for unregistered paths', async () => {
    renderDataRouter(['/no/such/path'])
    expect(await screen.findByTestId('not-found-page')).toBeInTheDocument()
  })

  it('shows the admin forbidden view to non-admins opening an admin path directly', async () => {
    renderDataRouter(['/admin/guarded'], MEMBER)
    expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
    expect(screen.queryByTestId('guarded-screen')).not.toBeInTheDocument()
  })
})
