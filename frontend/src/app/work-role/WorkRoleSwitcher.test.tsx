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
// トップバーの作業ロールの切り替えのテスト（Intent 261004-role-menu の U7 の D5〜D9、NFR2.11・NFR4.1〜NFR4.3、
// 機能設計の承認の場の直し R-01・R-03、計画 12節 D-4・D-6）。make-you-chic-ui の Dropdown は差し替えない。
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ToastProvider } from 'make-you-chic-ui'
import { afterEach, describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import type { FeatureRegistration } from '../registry/types'
import { ACCOUNTING, deferred, fakeWorkRoleApi, SALES } from '../testing/frameTestSupport'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../testing/renderWithProviders'
import { WorkRoleProvider } from './WorkRoleProvider'
import { WorkRoleSwitcher } from './WorkRoleSwitcher'
import type { WorkRoleState } from './workRoleApi'

afterEach(() => {
  resetDisplayTestState()
})

const LONG = 'あ'.repeat(40)
const TWO: WorkRoleState = { roles: [SALES, ACCOUNTING], current: SALES }

const myPermissions: FeatureRegistration = {
  featureId: 'mypermissions',
  routes: [{ path: '/me/permissions', screen: () => null, layout: 'SHELL', access: 'LOGGED_IN' }],
}

function renderSwitcher(
  api: ReturnType<typeof fakeWorkRoleApi>['api'],
  options: { registrations?: FeatureRegistration[]; languages?: string[] } = {},
) {
  return renderWithProviders(
    <ToastProvider>
      <WorkRoleProvider api={api}>
        <WorkRoleSwitcher />
      </WorkRoleProvider>
    </ToastProvider>,
    { provider: fakeProvider({ loggedIn: true, admin: false }), ...options },
  )
}

const trigger = () => within(screen.getByTestId('work-role-switcher')).getByRole('button')

describe('WorkRoleSwitcher', () => {
  it('shows text without a button while loading and after a failed first load', async () => {
    const answer = deferred<WorkRoleState>()
    const { api, calls } = fakeWorkRoleApi([answer])
    renderSwitcher(api)
    expect(await screen.findByText('作業ロール: 読み込んでいます')).toBeInTheDocument()
    expect(within(screen.getByTestId('work-role-switcher')).queryByRole('button')).toBeNull()
    // 「読み込んでいます」はログインした直後、読み込みを始める前（効果の前）にも出るため、読み込みの要求が出て拒否を
    // 受け取る側が付いたのを待ってから拒否する。先に拒否すると、受け取る側の無い拒否（Unhandled Rejection）になる。
    await waitFor(() => expect(calls.get).toBeGreaterThan(0))
    act(() => answer.reject(new Error('down')))
    expect(await screen.findByText('作業ロール: 読み込めませんでした')).toBeInTheDocument()
  })

  it('names the trigger like the visible text and lists the roles with the one in use marked', async () => {
    const user = userEvent.setup()
    const { api } = fakeWorkRoleApi([TWO])
    renderSwitcher(api)
    await waitFor(() => expect(trigger()).toHaveAccessibleName('作業ロール: 営業'))
    expect(screen.getByTestId('work-role-switcher')).toContainElement(
      screen.getByTestId('dropdown-trigger'),
    )
    await user.click(trigger())
    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
      '営業（使用中）',
      '経理',
    ])
  })

  it('sends nothing when the role in use is chosen', async () => {
    const user = userEvent.setup()
    const { api, calls } = fakeWorkRoleApi([TWO])
    renderSwitcher(api)
    await user.click(await screen.findByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '営業（使用中）' }))
    expect(calls.put).toEqual([])
    expect(screen.getByTestId('toast-container')).toBeEmptyDOMElement()
  })

  it('switches with the keyboard, tells the result by a toast and returns the focus to the trigger', async () => {
    const user = userEvent.setup()
    const { api, calls } = fakeWorkRoleApi([TWO, { ...TWO, current: ACCOUNTING }])
    renderSwitcher(api)
    const button = await screen.findByRole('button', { name: '作業ロール: 営業' })
    button.focus()
    await user.keyboard('{Enter}')
    await user.keyboard('{ArrowDown}{Enter}')
    expect(await screen.findByText('作業ロールを経理に切り替えました')).toBeInTheDocument()
    await waitFor(() => expect(trigger()).toHaveAccessibleName('作業ロール: 経理'))
    await waitFor(() => expect(trigger()).toHaveFocus())
    expect(calls.put).toEqual([2])
  })

  it('tells a rejection and reloads the work role', async () => {
    const user = userEvent.setup()
    const { api, calls } = fakeWorkRoleApi([TWO], () =>
      Promise.reject({ kind: 'response', status: 409, code: 'ROLE_BUSY' }),
    )
    renderSwitcher(api)
    await user.click(await screen.findByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    expect(
      await screen.findByText(
        '作業ロールを切り替えられませんでした。割り当てが変わった可能性があります。',
      ),
    ).toBeInTheDocument()
    await waitFor(() => expect(calls.get).toBe(2))
    await waitFor(() => expect(trigger()).toHaveFocus())
  })

  it('cannot be pressed while the switch is being sent', async () => {
    const user = userEvent.setup()
    const sending = deferred<void>()
    const { api } = fakeWorkRoleApi([TWO], () => sending.promise)
    renderSwitcher(api)
    await user.click(await screen.findByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    await waitFor(() => expect(trigger()).toBeDisabled())
    act(() => sending.resolve())
    await waitFor(() => expect(trigger()).toBeEnabled())
  })

  it('shows a disabled item with a reason for one role and for no role, reachable by arrow keys', async () => {
    const user = userEvent.setup()
    const single = fakeWorkRoleApi([{ roles: [SALES], current: SALES }])
    const { unmount } = renderSwitcher(single.api, { registrations: [myPermissions] })
    await user.click(await screen.findByRole('button', { name: '作業ロール: 営業' }))
    const item = screen.getByRole('menuitem', { name: '切り替える先はありません' })
    expect(item).toHaveAttribute('aria-disabled', 'true')
    expect(item).toHaveAccessibleDescription(
      '割り当てられたロールは営業だけのため、切り替える先はありません',
    )
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: '自分の権限を見る' })).toHaveFocus()
    await user.click(item)
    expect(single.calls.put).toEqual([])
    unmount()

    const none = fakeWorkRoleApi([{ roles: [], current: null }])
    renderSwitcher(none.api)
    await user.click(await screen.findByRole('button', { name: '作業ロール: なし' }))
    expect(screen.getAllByRole('menuitem')).toHaveLength(1)
    expect(
      screen.getByRole('menuitem', { name: '作業ロールを選べません' }),
    ).toHaveAccessibleDescription('割り当てられたロールがありません。管理者に依頼してください。')
  })

  it('adds the link to my permissions only when that screen is registered', async () => {
    const user = userEvent.setup()
    const { api } = fakeWorkRoleApi([TWO])
    renderSwitcher(api, { registrations: [myPermissions] })
    await user.click(await screen.findByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '自分の権限を見る' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/me/permissions'))
  })

  it('shows English text', async () => {
    const user = userEvent.setup()
    const { api } = fakeWorkRoleApi([{ roles: [SALES], current: SALES }])
    renderSwitcher(api, { languages: ['en-US'] })
    await user.click(await screen.findByRole('button', { name: 'Work role: 営業' }))
    expect(screen.getByRole('menuitem', { name: 'Nothing to switch to' })).toBeInTheDocument()
  })

  it('has no accessibility violations with an open list and a 40-character role name', async () => {
    const user = userEvent.setup()
    const { api } = fakeWorkRoleApi([
      { roles: [SALES, ACCOUNTING, { roleId: 3, name: LONG }], current: { roleId: 3, name: LONG } },
    ])
    const { container } = renderSwitcher(api)
    await user.click(await screen.findByRole('button', { name: `作業ロール: ${LONG}` }))
    expect(await axe(container)).toHaveNoViolations()
    // メニューは make-you-chic-ui が body の直下に描く（ランドマークの外）ため、既存の UserRowActions・ShellLayout の
    // テストと同じく、WCAG 2.0・2.1 の A・AA の規則で確かめる（文字のコントラストは実際のブラウザの 160 で確かめる）。
    expect(
      await axe(document.body, {
        runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
        rules: {
          'color-contrast': { enabled: false },
          'link-in-text-block': { enabled: false },
        },
      }),
    ).toHaveNoViolations()
  })
})
