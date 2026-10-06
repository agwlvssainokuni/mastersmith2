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
import { act, cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { useEffect, useLayoutEffect } from 'react'
import {
  useDisplaySettings,
  type DisplaySettingsValue,
} from '../display-settings/DisplaySettingsProvider'
import { registerAuthHandlers } from '../../shared/api-client/apiClient'
import { useAdminForbidden } from '../admin-forbidden/AdminForbiddenProvider'
import type { FeatureRegistration, LoginState, LoginStateProvider } from '../registry/types'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../testing/renderWithProviders'
import { ShellLayout } from './ShellLayout'

function registrations(logout: () => void): FeatureRegistration[] {
  return [
    {
      featureId: 'demo',
      routes: [
        { path: '/reports', screen: () => null, layout: 'SHELL', access: 'LOGGED_IN' },
        { path: '/admin', screen: () => null, layout: 'SHELL', access: 'ADMIN' },
      ],
      sidebarItems: [
        {
          id: 'admin',
          labelKey: 'demo.admin',
          path: '/admin',
          order: 20,
          visibleWhen: 'ADMIN',
          section: 'ADMIN',
        },
        {
          id: 'reports',
          labelKey: 'demo.reports',
          path: '/reports',
          order: 10,
          visibleWhen: 'LOGGED_IN',
          section: 'ADMIN',
        },
      ],
      userMenuItems: [{ id: 'logout', labelKey: 'demo.logout', action: logout, order: 1 }],
      messages: {
        ja: { 'demo.admin': '管理', 'demo.reports': '帳票', 'demo.logout': 'ログアウト' },
        en: { 'demo.admin': 'Admin', 'demo.reports': 'Reports', 'demo.logout': 'Sign out' },
      },
    },
  ]
}

const content = <p data-testid="shell-content">中身</p>

describe('ShellLayout', () => {
  it('lists home first and then the visible items in order', async () => {
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: true, displayName: '管理者' }),
    })
    const nav = await screen.findByRole('navigation')
    expect(
      within(nav)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/', '/reports', '/admin'])
    expect(screen.getByTestId('shell-content')).toBeInTheDocument()
  })

  it('hides ADMIN items from non-administrators', async () => {
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: false }),
    })
    const nav = await screen.findByRole('navigation')
    expect(within(nav).queryByTestId('sidebar-nav-/admin')).not.toBeInTheDocument()
    expect(within(nav).getByTestId('sidebar-nav-/reports')).toBeInTheDocument()
  })

  it('moves to the selected screen when a sidebar item is chosen', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: false }),
    })
    await user.click(await screen.findByTestId('sidebar-nav-/reports'))
    expect(screen.getByTestId('location')).toHaveTextContent('/reports')
  })

  it('shows the display name and runs the chosen user menu action', async () => {
    const user = userEvent.setup()
    const logout = vi.fn()
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(logout),
      provider: fakeProvider({ loggedIn: true, admin: false, displayName: 'user@example.com' }),
    })
    await user.click(await screen.findByRole('button', { name: /user@example.com/ }))
    await user.click(await screen.findByText('ログアウト'))
    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('uses English labels for an English browser', async () => {
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: false }),
      languages: ['en-US'],
    })
    expect(await screen.findByText('Reports')).toBeInTheDocument()
    expect(screen.getByText('Home')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: true, displayName: '管理者' }),
    })
    await screen.findByTestId('shell-content')
    expect(await axe(container)).toHaveNoViolations()
  })

  it('shows the name of the user, not the email address, in the user menu', async () => {
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({
        loggedIn: true,
        admin: false,
        displayName: '山田 花子',
        preferences: { language: 'ja', theme: 'light', fontSize: 'md' },
      }),
    })
    expect(await screen.findByRole('button', { name: /山田 花子/ })).toBeInTheDocument()
    expect(screen.queryByText(/hanako@example\.com/)).not.toBeInTheDocument()
  })

  it('shows the new name right after the preferences are saved', async () => {
    const captured: { value?: DisplaySettingsValue; effectsFlushed?: boolean } = {}
    function Capture() {
      const settings = useDisplaySettings()
      useLayoutEffect(() => {
        captured.value = settings
      })
      // 描画の後の効果（useEffect）が流れたことの印。
      useEffect(() => {
        captured.effectsFlushed = true
      })
      return null
    }
    renderWithProviders(
      <ShellLayout>
        <Capture />
        {content}
      </ShellLayout>,
      {
        registrations: registrations(() => {}),
        provider: fakeProvider({
          loggedIn: true,
          admin: false,
          displayName: '山田 花子',
          preferences: { language: 'ja', theme: 'light', fontSize: 'md' },
        }),
      },
    )
    await screen.findByRole('button', { name: /山田 花子/ })
    // 保存の前に、氏名が出た描画の後の効果が流れ終わるのを待つ。LoginStateGate は提供元の答えを待ってから中身を描くため、
    // DisplaySettingsProvider は氏名と同じ描画で作られ、表示の設定の保存先の購読（useSyncExternalStore）を描画の後の効果で
    // 始める。負荷が高いと氏名が見えた直後にはまだ購読が始まっておらず、そこで同期の act で保存しても描き直されない
    // （CI で1回落ちた。DisplaySettingsProvider.test.tsx の waitForEffects と同じ理由と待ち方）。Capture の useEffect は
    // 同じ描画の効果としてまとめて流れるため、これが流れたことで購読が始まったとみなす。
    await waitFor(() => expect(captured.effectsFlushed).toBe(true))

    act(() =>
      captured.value?.applyUserPreferences({
        displayName: '佐藤 花子',
        language: 'ja',
        theme: 'light',
        fontSize: 'md',
      }),
    )

    expect(screen.getByRole('button', { name: /佐藤 花子/ })).toBeInTheDocument()
    resetDisplayTestState()
  })

  it('has no accessibility violations while showing the name', async () => {
    const { container } = renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({
        loggedIn: true,
        admin: false,
        displayName: '山田 花子',
        preferences: { language: 'en', theme: 'dark', fontSize: 'lg' },
      }),
    })
    await screen.findByRole('button', { name: /山田 花子/ })
    expect(await axe(container)).toHaveNoViolations()
    resetDisplayTestState()
  })

  describe('user menu items with a path (U7)', () => {
    function pathRegistrations(logout: () => void): FeatureRegistration[] {
      return [
        {
          featureId: 'me',
          routes: [
            { path: '/me/preferences', screen: () => null, layout: 'SHELL', access: 'LOGGED_IN' },
          ],
          userMenuItems: [
            { id: 'prefs', labelKey: 'me.prefs', path: '/me/preferences', order: 80 },
            { id: 'logout', labelKey: 'me.logout', action: logout, order: 100 },
          ],
          messages: {
            ja: { 'me.prefs': 'プリファレンス', 'me.logout': 'ログアウト' },
            en: { 'me.prefs': 'Preferences', 'me.logout': 'Log out' },
          },
        },
      ]
    }

    async function openMenu(logout: () => void = vi.fn()) {
      const user = userEvent.setup()
      renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
        registrations: pathRegistrations(logout),
        provider: fakeProvider({ loggedIn: true, admin: false, displayName: '山田 花子' }),
      })
      await user.click(await screen.findByRole('button', { name: /山田 花子/ }))
      return user
    }

    it('renders a path item as a link menu item and moves without reloading on a click', async () => {
      const user = await openMenu()
      const item = await screen.findByRole('menuitem', { name: 'プリファレンス' })
      expect(item.tagName).toBe('A')
      expect(item).toHaveAttribute('href', '/me/preferences')
      const clicks: boolean[] = []
      document.addEventListener('click', (event) => clicks.push(event.defaultPrevented), {
        once: true,
      })
      await user.click(item)
      expect(clicks).toEqual([true])
      expect(screen.getByTestId('location')).toHaveTextContent('/me/preferences')
      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })

    it('moves with Enter and with Space from the keyboard', async () => {
      for (const key of ['{Enter}', ' ']) {
        const user = await openMenu()
        const item = await screen.findByRole('menuitem', { name: 'プリファレンス' })
        item.focus()
        await user.keyboard(key)
        expect(screen.getByTestId('location')).toHaveTextContent('/me/preferences')
        cleanup()
      }
    })

    it('keeps the logout item as a button that runs its action', async () => {
      const logout = vi.fn()
      const user = await openMenu(logout)
      const item = await screen.findByRole('menuitem', { name: 'ログアウト' })
      expect(item.tagName).toBe('BUTTON')
      expect(item).not.toHaveAttribute('href')
      await user.click(item)
      expect(logout).toHaveBeenCalledTimes(1)
      expect(screen.getByTestId('location')).toHaveTextContent('/')
    })

    it('has no accessibility violations with the menu open', async () => {
      await openMenu()
      await screen.findByRole('menuitem', { name: 'プリファレンス' })
      // メニューは make-you-chic-ui が body の直下に描く（ランドマークの外）ため、best-practice の region の規則に当たる。
      // 実際のブラウザの検査（080）と同じ WCAG 2.0・2.1 の A・AA の規則で確かめる。
      // タグの指定は vitest.setup.ts で止めた CSS の要る規則を有効に戻すため、ここでも止める。
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

  describe('admin forbidden (U4)', () => {
    /** 押すと管理の API の 403 を骨組みに渡す子 */
    function Reporter() {
      const report = useAdminForbidden()
      return (
        <button
          type="button"
          data-testid="report-403"
          onClick={() =>
            report({ kind: 'response', status: 403, code: 'ACCESS_DENIED' }, '/api/admin/check')
          }
        >
          中身
        </button>
      )
    }

    /** 状態を差し替えて知らせる偽物の提供元 */
    function switchableProvider(initial: LoginState): {
      provider: LoginStateProvider
      set: (next: LoginState) => void
    } {
      let state = initial
      const listeners = new Set<() => void>()
      return {
        provider: {
          getLoginState: () => state,
          subscribe: (listener) => {
            listeners.add(listener)
            return () => {
              listeners.delete(listener)
            }
          },
        },
        set: (next) => {
          state = next
          for (const listener of listeners) {
            listener()
          }
        },
      }
    }

    const ADMIN: LoginState = { loggedIn: true, admin: true, displayName: '管理者' }
    const MEMBER: LoginState = { loggedIn: true, admin: false, displayName: '管理者' }

    async function renderReporter(provider: LoginStateProvider) {
      const user = userEvent.setup()
      renderWithProviders(
        <ShellLayout>
          <Reporter />
        </ShellLayout>,
        { route: '/admin', registrations: registrations(() => {}), provider },
      )
      expect(await screen.findByTestId('sidebar-nav-/admin')).toBeInTheDocument()
      return user
    }

    it('replaces the content with the forbidden view and keeps the sidebar and the top bar', async () => {
      const user = await renderReporter(fakeProvider(ADMIN))
      await user.click(screen.getByTestId('report-403'))

      expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
      expect(screen.queryByTestId('report-403')).not.toBeInTheDocument()
      expect(screen.getByRole('navigation')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /管理者/ })).toBeInTheDocument()
      resetDisplayTestState()
    })

    it('hides the admin menu after the reread that follows a 403', async () => {
      const login = switchableProvider(ADMIN)
      const refresh = vi.fn(() => {
        login.set(MEMBER)
        return Promise.resolve(true)
      })
      registerAuthHandlers({ getAccessToken: () => null, refresh, onUnauthenticated: () => {} })
      const user = await renderReporter(login.provider)

      await user.click(screen.getByTestId('report-403'))

      await waitFor(() =>
        expect(screen.queryByTestId('sidebar-nav-/admin')).not.toBeInTheDocument(),
      )
      expect(refresh).toHaveBeenCalledTimes(1)
      expect(screen.getByTestId('admin-forbidden-view')).toBeInTheDocument()
      resetDisplayTestState()
    })

    it('hides the admin menu when a token refresh answers that the user is no longer an admin', async () => {
      const login = switchableProvider(ADMIN)
      await renderReporter(login.provider)

      act(() => login.set(MEMBER))

      await waitFor(() =>
        expect(screen.queryByTestId('sidebar-nav-/admin')).not.toBeInTheDocument(),
      )
      expect(screen.getByTestId('sidebar-nav-/reports')).toBeInTheDocument()
      resetDisplayTestState()
    })

    it('hides the admin menu when the user signs in again without the admin flag', async () => {
      const login = switchableProvider(ADMIN)
      await renderReporter(login.provider)

      act(() => login.set({ loggedIn: false, admin: false }))
      await waitFor(() =>
        expect(screen.queryByTestId('sidebar-nav-/reports')).not.toBeInTheDocument(),
      )
      act(() => login.set(MEMBER))

      expect(await screen.findByTestId('sidebar-nav-/reports')).toBeInTheDocument()
      expect(screen.queryByTestId('sidebar-nav-/admin')).not.toBeInTheDocument()
      resetDisplayTestState()
    })

    it('leaves the forbidden view by moving to a screen outside the admin area', async () => {
      const user = await renderReporter(fakeProvider(ADMIN))
      await user.click(screen.getByTestId('report-403'))
      await screen.findByTestId('admin-forbidden-view')

      await user.click(screen.getByTestId('sidebar-nav-/reports'))

      expect(screen.getByTestId('location')).toHaveTextContent('/reports')
      expect(screen.queryByTestId('admin-forbidden-view')).not.toBeInTheDocument()
      expect(screen.getByTestId('report-403')).toBeInTheDocument()
      resetDisplayTestState()
    })
  })
})
