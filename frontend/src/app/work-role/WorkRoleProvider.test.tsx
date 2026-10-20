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
// 作業ロールの状態のテスト（Intent 261004-role-menu の U7 の L3、D4・D7・D8・4.1、NFR2.2・NFR2.9・NFR2.11、計画
// 12節 D-4・D-8）。API は偽物に差し替え、遅れた応答は解決を手で決める約束で作る。
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect, useRef, useState } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../testing/renderWithProviders'
import {
  ACCOUNTING,
  deferred,
  type Deferred,
  fakeWorkRoleApi,
  NavigateButton,
  SALES,
  switchableProvider,
} from '../testing/frameTestSupport'
import {
  useWorkRole,
  WorkRoleProvider,
  type SwitchResult,
  type WorkRoleApi,
} from './WorkRoleProvider'
import type { WorkRoleState } from './workRoleApi'

afterEach(() => {
  resetDisplayTestState()
})

const TWO: WorkRoleState = { roles: [SALES, ACCOUNTING], current: SALES }
const AFTER_SWITCH: WorkRoleState = { roles: [SALES, ACCOUNTING], current: ACCOUNTING }

function Probe() {
  const role = useWorkRole()
  const [result, setResult] = useState<SwitchResult | '-'>('-')
  return (
    <div>
      <output data-testid="status">{role.status}</output>
      <output data-testid="current">{role.current?.name ?? 'none'}</output>
      <output data-testid="roles">{role.roles.map((r) => r.name).join(',')}</output>
      <output data-testid="revision">{role.revision}</output>
      <output data-testid="result">{result}</output>
      <button type="button" onClick={() => void role.switchTo(2).then(setResult)}>
        to-accounting
      </button>
      <button type="button" onClick={() => void role.switchTo(1).then(setResult)}>
        to-sales
      </button>
      <button type="button" onClick={() => role.reload()}>
        reload
      </button>
      <NavigateButton to="/other?x=1" label="move" />
      <NavigateButton to="/logout" label="logout-path" />
    </div>
  )
}

function renderWorkRole(
  api: WorkRoleApi,
  provider = fakeProvider({ loggedIn: true, admin: false }),
) {
  return renderWithProviders(
    <WorkRoleProvider api={api}>
      <Probe />
    </WorkRoleProvider>,
    { provider },
  )
}

const status = () => screen.getByTestId('status')

describe('WorkRoleProvider', () => {
  it('returns idle values without requests when no provider is placed', async () => {
    let answer: SwitchResult | undefined
    function Bare() {
      const role = useWorkRole()
      return (
        <button type="button" onClick={() => void role.switchTo(1).then((r) => (answer = r))}>
          {role.status}
        </button>
      )
    }
    render(<Bare />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'idle' }))
    await waitFor(() => expect(answer).toBe('ignored'))
  })

  it('reads the work role after login and shows loading until the answer', async () => {
    const answer = deferred<WorkRoleState>()
    const { api, calls } = fakeWorkRoleApi([answer])
    renderWorkRole(api)
    await waitFor(() => expect(status()).toHaveTextContent('loading'))
    act(() => answer.resolve(TWO))
    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    expect(screen.getByTestId('current')).toHaveTextContent('営業')
    expect(screen.getByTestId('roles')).toHaveTextContent('営業,経理')
    expect(calls.get).toBe(1)
  })

  it('reloads on a login state notice and on a path change but not on /logout, keeping the values meanwhile', async () => {
    const user = userEvent.setup()
    const second = deferred<WorkRoleState>()
    const { api, calls } = fakeWorkRoleApi([TWO, second, TWO])
    const login = switchableProvider({ loggedIn: true, admin: false })
    renderWorkRole(api, login.provider)
    await waitFor(() => expect(status()).toHaveTextContent('ready'))

    act(() => login.notify())
    await waitFor(() => expect(status()).toHaveTextContent('reloading'))
    expect(screen.getByTestId('current')).toHaveTextContent('営業')
    act(() => second.resolve(AFTER_SWITCH))
    await waitFor(() => expect(screen.getByTestId('current')).toHaveTextContent('経理'))

    await user.click(screen.getByRole('button', { name: 'move' }))
    await waitFor(() => expect(calls.get).toBe(3))
    await user.click(screen.getByRole('button', { name: 'logout-path' }))
    await act(async () => {
      await Promise.resolve()
    })
    expect(calls.get).toBe(3)
  })

  it('drops an older answer that arrives after a newer one', async () => {
    const user = userEvent.setup()
    const first = deferred<WorkRoleState>()
    const second = deferred<WorkRoleState>()
    const { api } = fakeWorkRoleApi([first, second])
    renderWorkRole(api)
    await waitFor(() => expect(status()).toHaveTextContent('loading'))
    await user.click(screen.getByRole('button', { name: 'reload' }))
    act(() => second.resolve(AFTER_SWITCH))
    await waitFor(() => expect(screen.getByTestId('current')).toHaveTextContent('経理'))
    act(() => first.resolve(TWO))
    await act(async () => {
      await Promise.resolve()
    })
    expect(screen.getByTestId('current')).toHaveTextContent('経理')
  })

  it('shows load-error only for the first load and keeps the values when a reload fails', async () => {
    const user = userEvent.setup()
    const { api } = fakeWorkRoleApi([new Error('down'), TWO, new Error('down')])
    renderWorkRole(api)
    await waitFor(() => expect(status()).toHaveTextContent('load-error'))
    await user.click(screen.getByRole('button', { name: 'reload' }))
    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    await user.click(screen.getByRole('button', { name: 'reload' }))
    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    expect(screen.getByTestId('current')).toHaveTextContent('営業')
  })

  it('sends nothing for the current role, and switches with one PUT, a reload and a revision', async () => {
    const user = userEvent.setup()
    const { api, calls } = fakeWorkRoleApi([TWO, AFTER_SWITCH])
    renderWorkRole(api)
    await waitFor(() => expect(status()).toHaveTextContent('ready'))

    await user.click(screen.getByRole('button', { name: 'to-sales' }))
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('unchanged'))
    expect(calls.put).toEqual([])

    await user.click(screen.getByRole('button', { name: 'to-accounting' }))
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('switched'))
    await waitFor(() => expect(screen.getByTestId('current')).toHaveTextContent('経理'))
    expect(calls.put).toEqual([2])
    expect(calls.get).toBe(2)
    expect(screen.getByTestId('revision')).toHaveTextContent('1')
  })

  it('treats ROLE_NOT_ASSIGNED, ROLE_BUSY and VALIDATION_FAILED as rejected and other failures as failed', async () => {
    const user = userEvent.setup()
    const errors: unknown[] = [
      { kind: 'response', status: 409, code: 'ROLE_NOT_ASSIGNED' },
      { kind: 'response', status: 409, code: 'ROLE_BUSY' },
      { kind: 'response', status: 400, code: 'VALIDATION_FAILED' },
      { kind: 'network' },
    ]
    let index = 0
    const { api, calls } = fakeWorkRoleApi([TWO], () => Promise.reject(errors[index++]))
    renderWorkRole(api)
    await waitFor(() => expect(status()).toHaveTextContent('ready'))

    const expected = ['rejected', 'rejected', 'rejected', 'failed']
    for (const [i, answer] of expected.entries()) {
      await user.click(screen.getByRole('button', { name: 'to-accounting' }))
      await waitFor(() => expect(calls.get).toBe(i + 2))
      await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent(answer))
      await waitFor(() => expect(status()).toHaveTextContent('ready'))
    }
    expect(screen.getByTestId('revision')).toHaveTextContent('3')
  })

  it('ignores a second switch while the first is being sent', async () => {
    const user = userEvent.setup()
    const sending = deferred<void>()
    const { api, calls } = fakeWorkRoleApi([TWO, AFTER_SWITCH], () => sending.promise)
    renderWorkRole(api)
    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    await user.click(screen.getByRole('button', { name: 'to-accounting' }))
    await waitFor(() => expect(status()).toHaveTextContent('switching'))
    await user.click(screen.getByRole('button', { name: 'to-accounting' }))
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('ignored'))
    act(() => sending.resolve())
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('switched'))
    expect(calls.put).toEqual([2])
  })

  it('drops the values when logged out', async () => {
    const login = switchableProvider({ loggedIn: true, admin: false })
    const { api } = fakeWorkRoleApi([TWO])
    renderWorkRole(api, login.provider)
    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    act(() => login.set({ loggedIn: false, admin: false }))
    await waitFor(() => expect(status()).toHaveTextContent('idle'))
    expect(screen.getByTestId('current')).toHaveTextContent('none')
    expect(screen.getByTestId('roles')).toBeEmptyDOMElement()
  })
})

/** 切り替えの前の確かめを登録する画面（業務データの作成・更新の画面と同じ使い方。D25a） */
function GuardProbe({ answers }: { answers: Deferred<boolean>[] }) {
  const role = useWorkRole()
  const [registered, setRegistered] = useState(false)
  const unregister = useRef<() => void>(() => {})
  const index = useRef(0)
  return (
    <div>
      <output data-testid="registered">{String(registered)}</output>
      <button
        type="button"
        onClick={() => {
          unregister.current = role.registerSwitchGuard(() => {
            const answer = answers[Math.min(index.current, answers.length - 1)]
            index.current += 1
            return answer.promise
          })
          setRegistered(true)
        }}
      >
        register
      </button>
      <button
        type="button"
        onClick={() => {
          unregister.current()
          setRegistered(false)
        }}
      >
        unregister
      </button>
    </div>
  )
}

function renderWithGuard(api: WorkRoleApi, answers: Deferred<boolean>[]) {
  return renderWithProviders(
    <WorkRoleProvider api={api}>
      <Probe />
      <GuardProbe answers={answers} />
    </WorkRoleProvider>,
    { provider: fakeProvider({ loggedIn: true, admin: false }) },
  )
}

describe('WorkRoleProvider switch guard (D25a)', () => {
  it('asks the registered guard first and does not switch when it stays', async () => {
    const user = userEvent.setup()
    const stay = deferred<boolean>()
    const { api, calls } = fakeWorkRoleApi([TWO, AFTER_SWITCH])
    renderWithGuard(api, [stay])
    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    await user.click(screen.getByRole('button', { name: 'register' }))
    await user.click(screen.getByRole('button', { name: 'to-accounting' }))
    // 確かめの最中の2回目は捨てる。
    await user.click(screen.getByRole('button', { name: 'to-accounting' }))
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('ignored'))
    act(() => stay.resolve(false))
    await act(async () => {
      await Promise.resolve()
    })
    expect(calls.put).toEqual([])
    expect(screen.getByTestId('current')).toHaveTextContent('営業')
    expect(screen.getByTestId('revision')).toHaveTextContent('0')
  })

  it('switches when the guard leaves', async () => {
    const user = userEvent.setup()
    const leave = deferred<boolean>()
    const { api, calls } = fakeWorkRoleApi([TWO, AFTER_SWITCH])
    renderWithGuard(api, [leave])
    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    await user.click(screen.getByRole('button', { name: 'register' }))
    await user.click(screen.getByRole('button', { name: 'to-accounting' }))
    act(() => leave.resolve(true))
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('switched'))
    expect(calls.put).toEqual([2])
  })

  it('switches without asking after the guard is removed, and asks nothing for the current role', async () => {
    const user = userEvent.setup()
    const never = deferred<boolean>()
    const { api, calls } = fakeWorkRoleApi([TWO, AFTER_SWITCH])
    renderWithGuard(api, [never])
    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    await user.click(screen.getByRole('button', { name: 'register' }))
    await user.click(screen.getByRole('button', { name: 'to-sales' }))
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('unchanged'))
    await user.click(screen.getByRole('button', { name: 'unregister' }))
    await user.click(screen.getByRole('button', { name: 'to-accounting' }))
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('switched'))
    expect(calls.put).toEqual([2])
  })

  it('returns a removal function from the default value without a provider', async () => {
    const removals: (() => void)[] = []
    function Bare() {
      const role = useWorkRole()
      useEffect(() => {
        removals.push(role.registerSwitchGuard(() => Promise.resolve(false)))
      }, [role])
      return null
    }
    render(<Bare />)
    await waitFor(() => expect(removals).toHaveLength(1))
    expect(typeof removals[0]).toBe('function')
  })
})
