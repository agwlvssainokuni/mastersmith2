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
import { act, cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { useEffect, useLayoutEffect, type ReactNode } from 'react'
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
import { ToastProvider } from 'make-you-chic-ui'
import { BusinessNavigationProvider } from '../navigation/BusinessNavigationProvider'
import { NAV_EXPANDED_STORAGE_KEY } from '../navigation/navExpansion'
import type { NavigationResponse, NavNode } from '../navigation/navigationApi'
import {
  ACCOUNTING,
  deferred,
  fakeNavigationLoad,
  fakeWorkRoleApi,
  NavigateButton,
  SALES,
} from '../testing/frameTestSupport'
import { WorkRoleProvider } from '../work-role/WorkRoleProvider'
import type { WorkRoleState } from '../work-role/workRoleApi'
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
          visibleWhen: 'ADMIN',
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
  it('lists home first and then the admin items in order', async () => {
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

  it('hides the admin section from non-administrators', async () => {
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: false }),
    })
    const nav = await screen.findByRole('navigation')
    expect(within(nav).queryByTestId('sidebar-nav-adm:admin')).not.toBeInTheDocument()
    expect(within(nav).queryByTestId('sidebar-nav-adm:reports')).not.toBeInTheDocument()
    expect(within(nav).getByTestId('sidebar-nav-home')).toBeInTheDocument()
  })

  it('moves to the selected screen when a sidebar item is chosen', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: true }),
    })
    await user.click(await screen.findByTestId('sidebar-nav-adm:reports'))
    expect(screen.getByTestId('location')).toHaveTextContent('/reports')
  })

  it('leaves a sidebar link clicked with Ctrl, Cmd, Shift or the middle button to the browser', async () => {
    renderWithProviders(<ShellLayout>{content}</ShellLayout>, {
      registrations: registrations(() => {}),
      provider: fakeProvider({ loggedIn: true, admin: true }),
    })
    const link = await screen.findByTestId('sidebar-nav-adm:reports')
    const prevented: boolean[] = []
    // React の処理の後に既定が止められたかを記録し、jsdom の移動（未実装）を起こさないよう最後に止める。
    const record = (event: Event) => {
      prevented.push(event.defaultPrevented)
      event.preventDefault()
    }
    window.addEventListener('click', record)
    try {
      fireEvent.click(link, { ctrlKey: true })
      fireEvent.click(link, { metaKey: true })
      fireEvent.click(link, { shiftKey: true })
      fireEvent.click(link, { button: 1 })
      expect(prevented).toEqual([false, false, false, false])
      expect(screen.getByTestId('location')).not.toHaveTextContent('/reports')
      fireEvent.click(link)
      expect(prevented[4]).toBe(true)
      expect(screen.getByTestId('location')).toHaveTextContent('/reports')
    } finally {
      window.removeEventListener('click', record)
    }
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
      provider: fakeProvider({ loggedIn: true, admin: true }),
      languages: ['en-US'],
    })
    expect(await screen.findByText('Reports')).toBeInTheDocument()
    expect(screen.getByText('Home')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Main menu' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Administration' })).toBeInTheDocument()
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
      expect(await screen.findByTestId('sidebar-nav-adm:admin')).toBeInTheDocument()
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
        expect(screen.queryByTestId('sidebar-nav-adm:admin')).not.toBeInTheDocument(),
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
        expect(screen.queryByTestId('sidebar-nav-adm:admin')).not.toBeInTheDocument(),
      )
      expect(screen.queryByTestId('sidebar-nav-adm:reports')).not.toBeInTheDocument()
      expect(screen.getByTestId('sidebar-nav-home')).toBeInTheDocument()
      resetDisplayTestState()
    })

    it('hides the admin menu when the user signs in again without the admin flag', async () => {
      const login = switchableProvider(ADMIN)
      await renderReporter(login.provider)

      act(() => login.set({ loggedIn: false, admin: false }))
      await waitFor(() => expect(screen.queryByTestId('sidebar-nav-home')).not.toBeInTheDocument())
      act(() => login.set(MEMBER))

      expect(await screen.findByTestId('sidebar-nav-home')).toBeInTheDocument()
      expect(screen.queryByTestId('sidebar-nav-adm:admin')).not.toBeInTheDocument()
      resetDisplayTestState()
    })

    it('leaves the forbidden view by moving to a screen outside the admin area', async () => {
      const user = await renderReporter(fakeProvider(ADMIN))
      await user.click(screen.getByTestId('report-403'))
      await screen.findByTestId('admin-forbidden-view')

      await user.click(screen.getByTestId('sidebar-nav-adm:reports'))

      expect(screen.getByTestId('location')).toHaveTextContent('/reports')
      expect(screen.queryByTestId('admin-forbidden-view')).not.toBeInTheDocument()
      expect(screen.getByTestId('report-403')).toBeInTheDocument()
      resetDisplayTestState()
    })
  })
})

describe('ShellLayout with the business menu (Intent 261004-role-menu U7)', () => {
  const LONG = (prefix: string) => `${prefix}${'長'.repeat(40 - prefix.length)}`

  /** 5 段の入れ子（末端が T1）と、記号を含む名前の T2 */
  function deepTree(): NavNode[] {
    const leaf: NavNode = {
      id: '0.0.0.0.0',
      label: { ja: LONG('段5'), en: 'Level 5' },
      icon: 'list',
      table: { schemaName: 'S', tableName: 'T1' },
      navigable: true,
      items: [],
    }
    let node = leaf
    for (let depth = 4; depth >= 1; depth -= 1) {
      node = {
        id: ['0', '0', '0', '0', '0'].slice(0, depth).join('.'),
        label: { ja: LONG(`段${depth}`), en: `Level ${depth}` },
        icon: depth === 1 ? 'skull' : 'list',
        table: null,
        navigable: false,
        items: [node],
      }
    }
    return [
      node,
      {
        id: '1',
        label: { ja: '<script>alert(1)</script><img src=x onerror=alert(2)>&"\'', en: 'Two' },
        icon: 'list',
        table: { schemaName: 'S', tableName: 'T2' },
        navigable: true,
        items: [],
      },
    ]
  }

  const menu = (items: NavNode[]): NavigationResponse => ({ items, emptyReason: null })
  const TWO: WorkRoleState = { roles: [SALES, ACCOUNTING], current: SALES }

  function Frame({ children }: { children?: ReactNode }) {
    return (
      <ShellLayout>
        <NavigateButton to="/somewhere" label="move-away" />
        <NavigateButton to="/tables?schema=S&table=T1" label="open-t1" />
        {children}
      </ShellLayout>
    )
  }

  function renderShell(
    options: {
      route?: string
      navAnswers?: Parameters<typeof fakeNavigationLoad>[0]
      roleAnswers?: Parameters<typeof fakeWorkRoleApi>[0]
      registrations?: FeatureRegistration[]
      languages?: string[]
      admin?: boolean
    } = {},
  ) {
    const role = fakeWorkRoleApi(options.roleAnswers ?? [TWO])
    const nav = fakeNavigationLoad(options.navAnswers ?? [menu(deepTree())])
    const view = renderWithProviders(
      <ToastProvider>
        <WorkRoleProvider api={role.api}>
          <BusinessNavigationProvider load={nav.load}>
            <Frame />
          </BusinessNavigationProvider>
        </WorkRoleProvider>
      </ToastProvider>,
      {
        route: options.route ?? '/',
        registrations: options.registrations ?? registrations(() => {}),
        provider: fakeProvider({
          loggedIn: true,
          admin: options.admin ?? true,
          displayName: '管理者',
        }),
        languages: options.languages,
      },
    )
    return { view, role: role.calls, nav: nav.calls }
  }

  const toggle = (id: string) => screen.getByTestId(`sidebar-toggle-biz:${id}`)
  const LEVEL_IDS = ['0', '0.0', '0.0.0', '0.0.0.0']

  afterEach(() => {
    resetDisplayTestState()
  })

  it('orders the home, business and admin sections and shows no admin section to non-admins', async () => {
    const { view } = renderShell()
    const nav = await screen.findByRole('navigation', { name: 'メインメニュー' })
    await waitFor(() => expect(screen.getByTestId('sidebar-toggle-biz:0')).toBeInTheDocument())
    expect(
      within(nav)
        .getAllByRole('heading')
        .map((h) => h.textContent),
    ).toEqual(['業務', '管理'])
    expect(within(nav).getByTestId('sidebar-nav-home')).toBeInTheDocument()
    view.unmount()

    renderShell({ admin: false })
    await waitFor(() => expect(screen.getByTestId('sidebar-toggle-biz:0')).toBeInTheDocument())
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual(['業務'])
  })

  it('shows no business heading while loading or for an empty menu', async () => {
    const pending = deferred<NavigationResponse>()
    const { view } = renderShell({ navAnswers: [pending] })
    await screen.findByRole('navigation')
    expect(screen.queryByRole('heading', { name: '業務' })).toBeNull()
    act(() => pending.resolve({ items: [], emptyReason: 'NOTHING_VISIBLE' }))
    await waitFor(() => expect(screen.getByTestId('sidebar-nav-home')).toBeInTheDocument())
    expect(screen.queryByRole('heading', { name: '業務' })).toBeNull()
    view.unmount()
  })

  it('names the toggle buttons with aria-expanded in Japanese and in English', async () => {
    const user = userEvent.setup()
    const { view } = renderShell()
    await waitFor(() => expect(toggle('0')).toBeInTheDocument())
    expect(toggle('0')).toHaveAttribute('aria-expanded', 'false')
    expect(toggle('0')).toHaveAccessibleName(`${LONG('段1')}を開く`)
    await user.click(toggle('0'))
    expect(toggle('0')).toHaveAttribute('aria-expanded', 'true')
    expect(toggle('0')).toHaveAccessibleName(`${LONG('段1')}を閉じる`)
    view.unmount()
    sessionStorage.clear()

    renderShell({ languages: ['en-US'] })
    await waitFor(() => expect(toggle('0')).toHaveAccessibleName('Expand Level 1'))
    expect(screen.getByRole('navigation', { name: 'Main menu' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Business' })).toBeInTheDocument()
  })

  it('opens all five levels and moves to the leaf with the keyboard only', async () => {
    const user = userEvent.setup()
    renderShell()
    await waitFor(() => expect(toggle('0')).toBeInTheDocument())
    async function tabTo(element: HTMLElement) {
      for (let i = 0; i < 40 && document.activeElement !== element; i += 1) {
        await user.tab()
      }
      expect(element).toHaveFocus()
    }
    for (const [index, id] of LEVEL_IDS.entries()) {
      await tabTo(toggle(id))
      await user.keyboard(index % 2 === 0 ? '{Enter}' : ' ')
      expect(toggle(id)).toHaveAttribute('aria-expanded', 'true')
    }
    const leaf = screen.getByRole('link', { name: LONG('段5') })
    await tabTo(leaf)
    await user.keyboard('{Enter}')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/tables'))
    await waitFor(() => expect(leaf).toHaveAttribute('aria-current', 'page'))
  })

  it('renders every level of a tree exactly five levels deep and marks one current item', async () => {
    renderShell({ route: '/tables?schema=S&table=T1&item=0.0.0.0.0' })
    const leaf = await screen.findByRole('link', { name: LONG('段5') })
    for (const id of LEVEL_IDS) {
      expect(toggle(id)).toHaveAttribute('aria-expanded', 'true')
    }
    for (const depth of [1, 2, 3, 4]) {
      expect(screen.getByTitle(LONG(`段${depth}`))).toBeInTheDocument()
    }
    expect(leaf).toHaveAttribute('aria-current', 'page')
    expect(document.querySelectorAll('[aria-current="page"]')).toHaveLength(1)
  })

  it('renders untrusted labels as text and replaces an unknown icon with list', async () => {
    renderShell()
    const item = await screen.findByTestId('sidebar-nav-biz:1')
    expect(item).toHaveTextContent('<script>alert(1)</script><img src=x onerror=alert(2)>&"\'')
    expect(document.querySelector('nav script, nav img')).toBeNull()
    expect(
      within(screen.getByTestId('sidebar-nav-biz:0')).getByTestId('icon-list'),
    ).toBeInTheDocument()
    expect(within(screen.getByTestId('sidebar-nav-biz:0')).queryByTestId('icon-skull')).toBeNull()
  })

  it('keeps the expanded groups across moves and stores only the ids', async () => {
    const user = userEvent.setup()
    renderShell()
    await waitFor(() => expect(toggle('0')).toBeInTheDocument())
    await user.click(toggle('0'))
    await user.click(screen.getByRole('button', { name: 'move-away' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/somewhere'))
    expect(toggle('0')).toHaveAttribute('aria-expanded', 'true')
    expect(JSON.parse(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY) ?? '[]')).toEqual(['biz:0'])
  })

  it('opens the ancestors of a directly opened path even when the tree arrives later', async () => {
    const later = deferred<NavigationResponse>()
    renderShell({ route: '/tables?schema=S&table=T1', navAnswers: [later] })
    await screen.findByRole('navigation')
    act(() => later.resolve(menu(deepTree())))
    const leaf = await screen.findByRole('link', { name: LONG('段5') })
    expect(leaf).toHaveAttribute('aria-current', 'page')
    expect(toggle('0.0.0.0')).toHaveAttribute('aria-expanded', 'true')
  })

  it('does not reopen a branch closed on the same path', async () => {
    const user = userEvent.setup()
    renderShell({ route: '/tables?schema=S&table=T1' })
    await screen.findByRole('link', { name: LONG('段5') })
    await user.click(toggle('0'))
    expect(toggle('0')).toHaveAttribute('aria-expanded', 'false')
    await user.click(screen.getByRole('button', { name: 'move-away' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/somewhere'))
    expect(toggle('0')).toHaveAttribute('aria-expanded', 'false')
    await user.click(screen.getByRole('button', { name: 'open-t1' }))
    await waitFor(() => expect(toggle('0')).toHaveAttribute('aria-expanded', 'true'))
  })

  it('keeps the saved expanded ids while the tree is loading or failed, and drops unknown ones once loaded', async () => {
    sessionStorage.setItem(NAV_EXPANDED_STORAGE_KEY, JSON.stringify(['biz:0', 'biz:gone']))
    const pending = deferred<NavigationResponse>()
    const { view } = renderShell({ navAnswers: [pending] })
    await screen.findByRole('navigation')
    expect(JSON.parse(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY) ?? '[]')).toEqual([
      'biz:0',
      'biz:gone',
    ])
    act(() => pending.reject(new Error('down')))
    expect(await screen.findByTestId('sidebar-nav-biz-load-error')).toHaveTextContent(
      '業務のメニューを読み込めませんでした',
    )
    expect(JSON.parse(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY) ?? '[]')).toHaveLength(2)
    view.unmount()

    renderShell()
    await waitFor(() => expect(toggle('0')).toHaveAttribute('aria-expanded', 'true'))
    await waitFor(() =>
      expect(JSON.parse(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY) ?? '[]')).toEqual([
        'biz:0',
      ]),
    )
  })

  it('replaces the business section after a work role switch', async () => {
    const user = userEvent.setup()
    const other: NavNode = {
      id: '7',
      label: { ja: '請求', en: 'Bills' },
      icon: 'list',
      table: { schemaName: 'S', tableName: 'BILL' },
      navigable: true,
      items: [],
    }
    renderShell({
      navAnswers: [menu(deepTree()), menu([other])],
      roleAnswers: [TWO, { ...TWO, current: ACCOUNTING }],
    })
    await user.click(await screen.findByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    expect(await screen.findByTestId('sidebar-nav-biz:7')).toBeInTheDocument()
    expect(screen.queryByTestId('sidebar-toggle-biz:0')).toBeNull()
  })

  it('shows the load failure item instead of the previous tree when the reload after a switch fails', async () => {
    const user = userEvent.setup()
    renderShell({
      navAnswers: [menu(deepTree()), new Error('down')],
      roleAnswers: [TWO, { ...TWO, current: ACCOUNTING }],
    })
    await user.click(await screen.findByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    expect(await screen.findByTestId('sidebar-nav-biz-load-error')).toHaveTextContent(
      '業務のメニューを読み込めませんでした',
    )
    expect(screen.queryByTestId('sidebar-toggle-biz:0')).toBeNull()
  })

  it('calls beforeNavigate of a path item once before moving', async () => {
    const user = userEvent.setup()
    const order: string[] = []
    function Probe() {
      return <output data-testid="probe" />
    }
    const feature: FeatureRegistration = {
      featureId: 'me',
      routes: [{ path: '/logout', screen: Probe, layout: 'STANDALONE', access: 'LOGGED_IN' }],
      userMenuItems: [
        {
          id: 'logout',
          labelKey: 'me.logout',
          path: '/logout',
          beforeNavigate: () => order.push(screen.getByTestId('location').textContent ?? ''),
          order: 100,
        },
      ],
      messages: { ja: { 'me.logout': 'ログアウト' }, en: { 'me.logout': 'Log out' } },
    }
    renderShell({ registrations: [feature] })
    await user.click(await screen.findByRole('button', { name: /管理者/ }))
    await user.click(screen.getByRole('menuitem', { name: 'ログアウト' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/logout'))
    expect(order).toEqual(['/'])
  })

  it('has no accessibility violations with five open levels, long names and the current leaf', async () => {
    const { view } = renderShell({ route: '/tables?schema=S&table=T1' })
    await screen.findByRole('link', { name: LONG('段5') })
    expect(await axe(view.container)).toHaveNoViolations()
  })

  describe('work role switcher placement by width (G2: A)', () => {
    /** (width < 768px) の答えを決め、変化を知らせられる偽の matchMedia */
    function installWidth(narrow: boolean) {
      let matches = narrow
      const listeners = new Set<() => void>()
      vi.stubGlobal('matchMedia', (query: string) => ({
        media: query,
        get matches() {
          return query === '(width < 768px)' && matches
        },
        addEventListener: (_type: string, listener: () => void) => listeners.add(listener),
        removeEventListener: (_type: string, listener: () => void) => listeners.delete(listener),
      }))
      return {
        set(next: boolean) {
          matches = next
          for (const listener of listeners) {
            listener()
          }
        },
      }
    }

    afterEach(() => {
      vi.unstubAllGlobals()
    })

    const header = () => screen.getByTestId('app-shell').querySelector('header')
    const content = () => screen.getByTestId('app-shell-content')

    it('puts the switcher in the topbar on a wide screen', async () => {
      installWidth(false)
      renderShell()
      const switcher = await screen.findByTestId('work-role-switcher')
      expect(header()?.contains(switcher)).toBe(true)
      expect(screen.queryByTestId('shell-work-role-bar')).toBeNull()
    })

    it('moves the switcher to the top of the content area below 768px and keeps its name', async () => {
      const user = userEvent.setup()
      installWidth(true)
      renderShell()
      const bar = await screen.findByTestId('shell-work-role-bar')
      expect(content().firstElementChild).toBe(bar)
      const switcher = within(bar).getByTestId('work-role-switcher')
      expect(header()?.querySelector('[data-testid="work-role-switcher"]')).toBeNull()
      expect(screen.getAllByTestId('work-role-switcher')).toHaveLength(1)
      // ユーザーメニューのトリガーはトップバーに残る。
      expect(within(header() as HTMLElement).getByRole('button', { name: /管理者/ })).toBeVisible()
      // 作業ロールのボタンは作業ロールの読み込みの答えの後に出る（帯より後に届きうる）ため、出るのを待つ（Build and Test で
      // verify の中で2回落ちた。team.md の waitFor の決まり）。
      await user.click(await within(switcher).findByRole('button', { name: '作業ロール: 営業' }))
      expect(screen.getByRole('menuitem', { name: '経理' })).toBeInTheDocument()
    })

    it('follows a change of the width', async () => {
      const width = installWidth(false)
      renderShell()
      await screen.findByTestId('work-role-switcher')
      act(() => width.set(true))
      expect(await screen.findByTestId('shell-work-role-bar')).toBeInTheDocument()
      act(() => width.set(false))
      await waitFor(() => expect(screen.queryByTestId('shell-work-role-bar')).toBeNull())
      expect(header()?.querySelector('[data-testid="work-role-switcher"]')).not.toBeNull()
    })

    it('has no accessibility violations with the switcher in the content area', async () => {
      installWidth(true)
      const { view } = renderShell()
      await screen.findByRole('button', { name: '作業ロール: 営業' })
      expect(await axe(view.container)).toHaveNoViolations()
    })
  })
})
