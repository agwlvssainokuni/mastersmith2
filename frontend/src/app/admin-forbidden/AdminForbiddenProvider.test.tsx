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
// 権限が無い URL の Provider と口のテスト（U4 の D2〜D8、FC 3.3、R-02、NFR3.1・NFR9.1・NFR9.2）。
// 読み直しは ApiClient に登録した偽物の更新（答えを外から決められる約束）の呼ばれた回数で確かめる。
// ShellLayout の代わりに、useIsAdminForbiddenHere の値と描画ごとの値の記録を出す部品で確かめる。
import { act, render, screen, waitFor } from '@testing-library/react'
import { useEffect, useLayoutEffect } from 'react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers } from '../../shared/api-client/apiClient'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../testing/renderWithProviders'
import { useAdminForbidden, useIsAdminForbiddenHere } from './AdminForbiddenProvider'

const DETAIL_MARKER = 'admin-forbidden-detail-marker'

/** 管理の API の 403・ACCESS_DENIED（problem の detail に目印を持つ） */
function forbiddenError(): unknown {
  const error = { kind: 'response', status: 403, code: 'ACCESS_DENIED' }
  Object.defineProperty(error, 'problem', {
    value: { status: 403, code: 'ACCESS_DENIED', detail: DETAIL_MARKER },
    enumerable: false,
  })
  return error
}

type Handle = (error: unknown, apiPath: string) => boolean

/** 部品が描いたときの口の関数（最後の描画の値） */
let handle: Handle = () => false
/** 描画ごとの「パス:権限が無い URL か」の記録 */
let renders: string[] = []
/** 口の関数を依存にした useEffect が動いた回数 */
let effectRuns = 0
/** 外から呼ぶ画面の移動 */
let navigateTo: (to: string | number) => void = () => {}

function Probe() {
  const report = useAdminForbidden()
  const forbiddenHere = useIsAdminForbiddenHere()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  renders.push(`${pathname}:${String(forbiddenHere)}`)
  useLayoutEffect(() => {
    handle = report
    navigateTo = (to) => {
      void (typeof to === 'number' ? navigate(to) : navigate(to))
    }
  })
  useEffect(() => {
    effectRuns += 1
  }, [report])
  return (
    <p data-testid="probe">
      {pathname} {forbiddenHere ? 'forbidden' : 'content'}
    </p>
  )
}

const refresh = vi.fn<() => Promise<boolean>>()

/** 答えを外から決められる更新を登録する。 */
function pendingRefresh(): { resolve: (value: boolean) => void } {
  const control = { resolve: (_value: boolean) => {} }
  refresh.mockImplementation(
    () =>
      new Promise<boolean>((resolve) => {
        control.resolve = resolve
      }),
  )
  return control
}

function renderProbe(route = '/admin/a') {
  return renderWithProviders(<Probe />, {
    route,
    provider: fakeProvider({ loggedIn: true, admin: true }),
  })
}

async function report(error: unknown, apiPath: string, fn: Handle = handle): Promise<boolean> {
  let result = false
  await act(async () => {
    result = fn(error, apiPath)
    await Promise.resolve()
  })
  return result
}

function probeText(): string {
  return screen.getByTestId('probe').textContent ?? ''
}

beforeEach(() => {
  resetDisplayTestState()
  refresh.mockReset()
  refresh.mockResolvedValue(true)
  registerAuthHandlers({ getAccessToken: () => null, refresh, onUnauthenticated: () => {} })
  handle = () => false
  renders = []
  effectRuns = 0
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
  resetDisplayTestState()
})

describe('AdminForbiddenProvider', () => {
  it('turns the current URL forbidden and rereads the login state once', async () => {
    renderProbe()
    await screen.findByTestId('probe')

    expect(await report(forbiddenError(), '/api/admin/check')).toBe(true)

    expect(probeText()).toContain('forbidden')
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('does not reread again for 403s repeated on the same URL', async () => {
    renderProbe()
    await screen.findByTestId('probe')

    await report(forbiddenError(), '/api/admin/dsl')
    await report(forbiddenError(), '/api/admin/dsl')
    await report(forbiddenError(), '/api/admin/dsl')

    expect(probeText()).toContain('forbidden')
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('changes nothing for failures that are not an admin forbidden', async () => {
    renderProbe()
    await screen.findByTestId('probe')

    expect(await report({ kind: 'response', status: 403 }, '/api/admin/dsl')).toBe(false)
    expect(await report(forbiddenError(), '/api/me/preferences')).toBe(false)
    expect(await report({ kind: 'network' }, '/api/admin/dsl')).toBe(false)
    expect(
      await report({ kind: 'response', status: 401, code: 'ACCESS_DENIED' }, '/api/admin/x'),
    ).toBe(false)

    expect(probeText()).toContain('content')
    expect(refresh).not.toHaveBeenCalled()
  })

  it('drops the forbidden URL when the URL changes', async () => {
    renderProbe()
    await screen.findByTestId('probe')
    await report(forbiddenError(), '/api/admin/dsl')
    expect(probeText()).toContain('forbidden')

    act(() => navigateTo('/home'))

    expect(probeText()).toBe('/home content')
  })

  it('throws when the hooks are used outside the provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    function OnlyReport() {
      useAdminForbidden()
      return null
    }
    function OnlyHere() {
      useIsAdminForbiddenHere()
      return null
    }
    expect(() =>
      render(
        <MemoryRouter>
          <OnlyReport />
        </MemoryRouter>,
      ),
    ).toThrow(/AdminForbiddenProvider/)
    expect(() =>
      render(
        <MemoryRouter>
          <OnlyHere />
        </MemoryRouter>,
      ),
    ).toThrow(/AdminForbiddenProvider/)
  })

  it('does not show the old forbidden state after A to B and back to A', async () => {
    renderProbe('/admin/a')
    await screen.findByTestId('probe')
    await report(forbiddenError(), '/api/admin/dsl')
    expect(probeText()).toBe('/admin/a forbidden')

    renders = []
    act(() => navigateTo('/admin/b'))
    expect(renders.length).toBeGreaterThan(0)
    expect(renders.every((entry) => entry === '/admin/b:false')).toBe(true)

    renders = []
    act(() => navigateTo(-1))
    expect(probeText()).toBe('/admin/a content')
    expect(renders.every((entry) => entry === '/admin/a:false')).toBe(true)
  })

  it('does not make B forbidden for a 403 handed by A after leaving A, but still rereads', async () => {
    renderProbe('/admin/a')
    await screen.findByTestId('probe')
    const handleOfA = handle

    act(() => navigateTo('/admin/b'))
    expect(await report(forbiddenError(), '/api/admin/dsl', handleOfA)).toBe(true)

    expect(probeText()).toBe('/admin/b content')
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('keeps the same function before and after the URL becomes forbidden', async () => {
    renderProbe()
    await screen.findByTestId('probe')
    const before = handle
    const runsBefore = effectRuns

    await report(forbiddenError(), '/api/admin/dsl')
    expect(probeText()).toContain('forbidden')

    expect(handle).toBe(before)
    expect(effectRuns).toBe(runsBefore)
  })

  it('calls a single reread while it is unfinished even for unbound 403s', async () => {
    const control = pendingRefresh()
    renderProbe('/admin/a')
    await screen.findByTestId('probe')
    const handleOfA = handle

    await report(forbiddenError(), '/api/admin/dsl')
    act(() => navigateTo('/admin/b'))
    await report(forbiddenError(), '/api/admin/dsl', handleOfA)
    await report(forbiddenError(), '/api/admin/dsl', handleOfA)
    expect(refresh).toHaveBeenCalledTimes(1)

    await act(async () => {
      control.resolve(true)
      await Promise.resolve()
    })
    await report(forbiddenError(), '/api/admin/dsl', handleOfA)
    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2))
  })

  it('shows the forbidden state right after the report while the reread is pending', async () => {
    pendingRefresh()
    renderProbe()
    await screen.findByTestId('probe')

    await report(forbiddenError(), '/api/admin/dsl')

    expect(probeText()).toContain('forbidden')
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('never rereads on a timer', async () => {
    renderProbe()
    await screen.findByTestId('probe')
    vi.useFakeTimers()

    await report(forbiddenError(), '/api/admin/dsl')
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10 * 60 * 1000)
    })

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(probeText()).toContain('forbidden')
  })

  it('shows no detail of the failure and writes nothing to the console', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((name) =>
      vi.spyOn(console, name),
    )
    renderProbe()
    await screen.findByTestId('probe')

    await report(forbiddenError(), '/api/admin/dsl')

    expect(document.body.textContent).not.toContain(DETAIL_MARKER)
    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled()
    }
  })
})
