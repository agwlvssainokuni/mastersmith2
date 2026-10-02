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
// 表示の設定の土台のテスト（部品 7節、W2〜W12、D2〜D8・D11・D14、AC3.2.18・AC4.1.2〜AC4.1.6・AC4.1.8・AC4.1.9、
// NFR2.2・NFR6.2・NFR9.3・NFR9.10）。make-you-chic-ui は差し替えず、<html> の属性と保存の鍵で確かめる。
// U4 の自分の氏名と言語の反映（useApplyOwnProfile、D13・D14、R-01、SD 4.2・4.3、NFR1.5・NFR3.1・NFR8.2）も確かめる。
import { act, render, screen, waitFor } from '@testing-library/react'
import { StrictMode, useEffect, useLayoutEffect } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../shared/api-client/apiClient'
import { App } from '../App'
import { useMessages } from '../i18n/I18nProvider'
import { useLoginState } from '../login-state/LoginStateGate'
import type { LoginState, LoginStateProvider } from '../registry/types'
import {
  renderWithProviders,
  resetDisplayTestState,
  stubBrowserLanguages,
} from '../testing/renderWithProviders'
import { resetAuthSession } from '../../features/auth/authSession'
import {
  NO_APPEARANCE,
  resetAppearanceLoad,
  startAppearanceLoad,
  type AppearanceResult,
} from './appearanceLoad'
import { DISPLAY_SETTINGS_KEY } from './browserStorage'
import { useDisplaySettings, type DisplaySettingsValue } from './DisplaySettingsProvider'
import { saveBrowserDisplaySettings, saveBrowserLanguage } from './displaySettingsStore'
import { useApplyOwnProfile, type ApplyOwnProfileInput } from './useApplyOwnProfile'
import { installFakeColorScheme } from './testing/fakeColorScheme'

const EMAIL = 'display-leak-check@example.com'
const NAME = '表示確認用の氏名'
const TOKEN = 'test-access-token-for-display'

const captured: { value?: DisplaySettingsValue; effectsFlushed?: boolean } = {}

function Probe() {
  const settings = useDisplaySettings()
  const t = useMessages()
  useLayoutEffect(() => {
    captured.value = settings
  })
  // 描画の後の効果（useEffect）が流れたことの印（waitForEffects を参照）。
  useEffect(() => {
    captured.effectsFlushed = true
  })
  return (
    <main>
      <h1 data-testid="probe-text">{t('home.heading')}</h1>
      <p data-testid="probe">
        {`${settings.language}/${settings.theme}/${settings.fontSize}/${settings.resolvedTheme}/${settings.displayName ?? '-'}`}
      </p>
    </main>
  )
}

/**
 * 画面が出た後に、その描画の後の効果（useEffect）が流れ終わるのを待つ。
 * ログイン状態の提供元を渡すと、LoginStateGate は答えを待ってから中身を描くため、DisplaySettingsProvider は Probe と同じ
 * 描画で作られ、表示の設定の保存先の購読（useSyncExternalStore）を描画の後の効果で始める。Probe が見えた直後は、負荷が
 * 高いとその効果がまだ流れておらず、そこで同期の act で設定を変えても描き直されない（Intent 260928-quality-followup の
 * G5 で、負荷をかけて再現し、保存先の値は変わっていて画面が遅れて追い付くことを確かめた）。Probe の useEffect は同じ描画の
 * 効果としてまとめて流れるため、これが流れたことで購読が始まったとみなす。
 */
async function waitForEffects(): Promise<void> {
  await waitFor(() => expect(captured.effectsFlushed).toBe(true))
}

/** ログイン状態を後から変えられる偽の提供元 */
function controllableProvider(initial: LoginState) {
  let current = initial
  const listeners = new Set<() => void>()
  const provider: LoginStateProvider = {
    getLoginState: () => current,
    subscribe: (listener) => {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
  }
  return {
    provider,
    set: async (next: LoginState) => {
      current = next
      await act(async () => {
        listeners.forEach((listener) => listener())
      })
    },
  }
}

function deferred<T>() {
  let resolve: (value: T) => void = () => undefined
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

function user(preferences: LoginState['preferences'], displayName = NAME): LoginState {
  return { loggedIn: true, admin: false, displayName, preferences }
}

function htmlTheme(): string | null {
  return document.documentElement.getAttribute('data-theme')
}

function storedJson(): Record<string, unknown> {
  return JSON.parse(localStorage.getItem(DISPLAY_SETTINGS_KEY) ?? '{}') as Record<string, unknown>
}

function allStoredValues(): string {
  const values: string[] = []
  for (const storage of [localStorage, sessionStorage]) {
    for (let i = 0; i < storage.length; i += 1) {
      const key = storage.key(i)
      if (key !== null) values.push(`${key}=${storage.getItem(key) ?? ''}`)
    }
  }
  return values.join('\n')
}

beforeEach(() => {
  resetDisplayTestState()
  captured.value = undefined
  captured.effectsFlushed = undefined
})

afterEach(() => {
  vi.unstubAllGlobals()
  resetDisplayTestState()
})

describe('DisplaySettingsProvider gate and appearance', () => {
  it('draws nothing until both the appearance and the restore have answered, in either order', async () => {
    for (const appearanceFirst of [true, false]) {
      resetDisplayTestState()
      const appearance = deferred<AppearanceResult>()
      const restore = deferred<LoginState>()
      const { unmount } = renderWithProviders(<Probe />, {
        appearance: appearance.promise,
        provider: { getLoginState: () => restore.promise },
      })
      await act(async () => {
        if (appearanceFirst) appearance.resolve(NO_APPEARANCE)
        else restore.resolve({ loggedIn: false, admin: false })
      })
      expect(screen.queryByTestId('probe')).not.toBeInTheDocument()

      await act(async () => {
        if (appearanceFirst) restore.resolve({ loggedIn: false, admin: false })
        else appearance.resolve(NO_APPEARANCE)
      })
      expect(await screen.findByTestId('probe')).toBeInTheDocument()
      unmount()
    }
  })

  it('applies an allowed appearance and keeps the previous one after a failure or a bad body', async () => {
    localStorage.setItem('design-system-brand', 'purple')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ brandColor: 'red', fontFamily: 'mono', token: TOKEN }), {
        status: 200,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const first = renderWithProviders(<Probe />, { appearance: startAppearanceLoad() })
    expect(await screen.findByTestId('probe')).toBeInTheDocument()
    expect(document.documentElement.getAttribute('data-brand')).toBe('purple')
    expect(document.documentElement.getAttribute('data-font-family')).toBe('sans')
    first.unmount()

    resetAppearanceLoad()
    fetchMock.mockRejectedValueOnce(new TypeError('network'))
    const failed = renderWithProviders(<Probe />, { appearance: startAppearanceLoad() })
    expect(await screen.findByTestId('probe')).toBeInTheDocument()
    expect(document.documentElement.getAttribute('data-brand')).toBe('purple')
    failed.unmount()

    resetAppearanceLoad()
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ brandColor: 'green', fontFamily: 'serif' }), { status: 200 }),
    )
    renderWithProviders(<Probe />, { appearance: startAppearanceLoad() })
    await waitFor(() => expect(document.documentElement.getAttribute('data-brand')).toBe('green'))
    expect(document.documentElement.getAttribute('data-font-family')).toBe('serif')
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('sends the appearance and the restore requests together and the appearance only once', async () => {
    resetAuthSession()
    stubBrowserLanguages(['ja-JP'])
    const pending = new Map<string, number>()
    const fetchMock = vi.fn((path: string) => {
      pending.set(path, (pending.get(path) ?? 0) + 1)
      return new Promise<Response>(() => undefined)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(
      <StrictMode>
        <MemoryRouter initialEntries={['/']}>
          <App />
        </MemoryRouter>
      </StrictMode>,
    )

    await waitFor(() => expect(pending.get('/api/auth/session/refresh')).toBe(1))
    expect(pending.get('/api/appearance')).toBe(1)
    expect(screen.queryByTestId('login-layout')).not.toBeInTheDocument()
    resetAuthSession()
  })
})

describe('DisplaySettingsProvider before and after login', () => {
  it('uses the stored values before login, or the browser language and the OS color scheme', async () => {
    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"language":"en","theme":"dark","fontSize":"lg"}')
    const first = renderWithProviders(<Probe />)
    expect(screen.getByTestId('probe')).toHaveTextContent('en/dark/lg/dark/-')
    expect(screen.getByTestId('probe-text')).toHaveTextContent('Home')
    expect(document.documentElement.lang).toBe('en')
    first.unmount()

    localStorage.removeItem(DISPLAY_SETTINGS_KEY)
    installFakeColorScheme(true)
    renderWithProviders(<Probe />, { languages: ['en-US'] })
    expect(screen.getByTestId('probe')).toHaveTextContent('en/system/md/dark/-')
    await waitFor(() => expect(htmlTheme()).toBe('dark'))
  })

  it('never shows the previous user look after login and saves the new user settings', async () => {
    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"language":"en","theme":"dark","fontSize":"lg"}')
    const log: { loggedIn: boolean; theme: string | null; size: string | null }[] = []
    function Recorder() {
      const login = useLoginState()
      useEffect(() => {
        log.push({
          loggedIn: login.loggedIn,
          theme: htmlTheme(),
          size: document.documentElement.getAttribute('data-font-size'),
        })
      })
      return null
    }
    const login = controllableProvider({ loggedIn: false, admin: false })
    renderWithProviders(
      <>
        <Probe />
        <Recorder />
      </>,
      { provider: login.provider },
    )
    await waitFor(() => expect(htmlTheme()).toBe('dark'))

    await login.set(user({ language: 'ja', theme: 'light', fontSize: 'sm' }))

    expect(screen.getByTestId('probe')).toHaveTextContent(`ja/light/sm/light/${NAME}`)
    const afterLogin = log.filter((entry) => entry.loggedIn)
    expect(afterLogin.length).toBeGreaterThan(0)
    expect(afterLogin.every((entry) => entry.theme === null && entry.size === 'sm')).toBe(true)
    expect(storedJson()).toEqual({ language: 'ja', theme: 'light', fontSize: 'sm' })
  })

  it('draws the restored session with the user settings from the first render and shows the name', async () => {
    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"language":"ja","theme":"light","fontSize":"md"}')
    const restored = user({ language: 'en', theme: 'dark', fontSize: 'lg' })
    renderWithProviders(<Probe />, { provider: { getLoginState: () => restored } })

    expect(await screen.findByTestId('probe')).toHaveTextContent(`en/dark/lg/dark/${NAME}`)
    expect(screen.getByTestId('probe-text')).toHaveTextContent('Home')
    expect(htmlTheme()).toBe('dark')
    expect(document.documentElement.getAttribute('data-font-size')).toBe('lg')
    expect(document.documentElement.lang).toBe('en')
  })

  it('uses the user settings after login even when the browser storage was cleared', async () => {
    const login = controllableProvider({ loggedIn: false, admin: false })
    renderWithProviders(<Probe />, { provider: login.provider })
    await screen.findByTestId('probe')
    await waitForEffects()
    localStorage.clear()

    await login.set(user({ language: 'en', theme: 'system', fontSize: 'lg' }))

    expect(screen.getByTestId('probe')).toHaveTextContent(`en/system/lg/light/${NAME}`)
    expect(storedJson()).toEqual({ language: 'en', theme: 'system', fontSize: 'lg' })

    await login.set({ loggedIn: false, admin: false })
    expect(screen.getByTestId('probe')).toHaveTextContent('en/system/lg/light/-')
  })

  it('follows the OS color scheme while the theme is system, also in a preview, and ignores it otherwise', async () => {
    const os = installFakeColorScheme(true)
    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"theme":"system"}')
    renderWithProviders(<Probe />)
    await waitFor(() => expect(htmlTheme()).toBe('dark'))

    act(() => os.setDark(false))
    expect(screen.getByTestId('probe')).toHaveTextContent('ja/system/md/light/-')
    await waitFor(() => expect(htmlTheme()).toBeNull())

    act(() => captured.value?.setPreview('light'))
    expect(os.listenerCount()).toBe(0)
    act(() => os.setDark(true))
    expect(screen.getByTestId('probe')).toHaveTextContent('ja/light/md/light/-')

    act(() => captured.value?.setPreview('system'))
    expect(screen.getByTestId('probe')).toHaveTextContent('ja/system/md/dark/-')
    act(() => os.setDark(false))
    expect(screen.getByTestId('probe')).toHaveTextContent('ja/system/md/light/-')
  })
})

describe('DisplaySettingsProvider functions of contract C9', () => {
  it('applies saved preferences at once: text, lang, request language and name', async () => {
    const login = controllableProvider(user({ language: 'ja', theme: 'light', fontSize: 'md' }))
    renderWithProviders(<Probe />, { provider: login.provider })
    expect(await screen.findByTestId('probe-text')).toHaveTextContent('ホーム')
    await waitForEffects()

    act(() =>
      captured.value?.applyUserPreferences({
        displayName: '山田 花子',
        language: 'en',
        theme: 'dark',
        fontSize: 'lg',
      }),
    )

    expect(screen.getByTestId('probe-text')).toHaveTextContent('Home')
    expect(screen.getByTestId('probe')).toHaveTextContent('en/dark/lg/dark/山田 花子')
    expect(document.documentElement.lang).toBe('en')
    expect(storedJson()).toEqual({ language: 'en', theme: 'dark', fontSize: 'lg' })
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await apiFetch('/api/items')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Headers(init.headers).get('Accept-Language')).toBe('en')
  })

  it('keeps previews unsaved, per axis, and drops them on clear or on a new login state', async () => {
    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"language":"ja","theme":"light","fontSize":"md"}')
    const login = controllableProvider({ loggedIn: false, admin: false })
    renderWithProviders(<Probe />, { provider: login.provider })
    await screen.findByTestId('probe')
    await waitForEffects()

    act(() => captured.value?.setPreview('dark', 'lg'))
    act(() => captured.value?.setPreview(undefined, 'sm'))
    act(() => captured.value?.setLanguage('en'))
    expect(screen.getByTestId('probe')).toHaveTextContent('en/dark/sm/dark/-')
    expect(storedJson()).toEqual({ language: 'ja', theme: 'light', fontSize: 'md' })

    act(() => captured.value?.clearPreview())
    expect(screen.getByTestId('probe')).toHaveTextContent('ja/light/md/light/-')

    act(() =>
      captured.value?.applyUserPreferences({
        displayName: NAME,
        language: 'en',
        theme: 'dark',
        fontSize: 'lg',
      }),
    )
    expect(screen.getByTestId('probe')).toHaveTextContent('ja/light/md/light/-')

    act(() => captured.value?.setPreview('dark'))
    await login.set(user({ language: 'ja', theme: 'light', fontSize: 'md' }))
    expect(screen.getByTestId('probe')).toHaveTextContent(`ja/light/md/light/${NAME}`)
    await login.set({ loggedIn: false, admin: false })
    expect(screen.getByTestId('probe')).toHaveTextContent('ja/light/md/light/-')
  })

  it('shows the login screen with the settings saved at registration, but not after login', async () => {
    const login = controllableProvider({ loggedIn: false, admin: false })
    renderWithProviders(<Probe />, { provider: login.provider })
    await screen.findByTestId('probe')
    await waitForEffects()

    act(() => captured.value?.setPreview('light'))
    act(() => saveBrowserDisplaySettings({ language: 'en', theme: 'dark', fontSize: 'lg' }))
    expect(screen.getByTestId('probe')).toHaveTextContent('en/dark/lg/dark/-')
    expect(screen.getByTestId('probe-text')).toHaveTextContent('Home')

    await login.set(user({ language: 'ja', theme: 'light', fontSize: 'sm' }))
    act(() => saveBrowserDisplaySettings({ language: 'en', theme: 'dark', fontSize: 'lg' }))
    expect(screen.getByTestId('probe')).toHaveTextContent(`ja/light/sm/light/${NAME}`)
  })

  it('does not undo a change of the design system made in another tab', async () => {
    renderWithProviders(<Probe />)
    await waitFor(() => expect(htmlTheme()).toBeNull())

    act(() => {
      localStorage.setItem('design-system-theme', 'dark')
      window.dispatchEvent(
        new StorageEvent('storage', {
          key: 'design-system-theme',
          newValue: 'dark',
          storageArea: localStorage,
        }),
      )
    })

    await waitFor(() => expect(htmlTheme()).toBe('dark'))
    expect(screen.getByTestId('probe')).toHaveTextContent('ja/system/md/light/-')
    expect(htmlTheme()).toBe('dark')
  })

  it('keeps only the three display settings in the browser storage and no secret', async () => {
    const login = controllableProvider({ loggedIn: false, admin: false })
    renderWithProviders(<Probe />, { provider: login.provider })
    await screen.findByTestId('probe')
    await waitForEffects()

    saveBrowserLanguage('en')
    act(() => saveBrowserDisplaySettings({ language: 'ja', theme: 'dark', fontSize: 'sm' }))
    await login.set({
      loggedIn: true,
      admin: false,
      displayName: NAME,
      preferences: {
        language: 'en',
        theme: 'light',
        fontSize: 'lg',
        token: TOKEN,
        email: EMAIL,
      } as never,
    })
    act(() =>
      captured.value?.applyUserPreferences({
        displayName: NAME,
        language: 'ja',
        theme: 'dark',
        fontSize: 'md',
        email: EMAIL,
      } as never),
    )

    expect(Object.keys(storedJson()).sort()).toEqual(['fontSize', 'language', 'theme'])
    const everything = allStoredValues()
    expect(everything).not.toContain(TOKEN)
    expect(everything).not.toContain(EMAIL)
    expect(everything).not.toContain(NAME)
  })

  it('still draws when the browser storage throws', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('QuotaExceededError')
    })

    renderWithProviders(<Probe />, {
      provider: { getLoginState: () => user({ language: 'en', theme: 'dark', fontSize: 'lg' }) },
    })

    expect(await screen.findByTestId('probe')).toHaveTextContent(`en/dark/lg/dark/${NAME}`)
  })

  it('refuses to be used outside the provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    expect(() => render(<Probe />)).toThrow(/DisplaySettingsProvider/)
  })
})

describe('DisplaySettingsProvider applyOwnProfile (U4)', () => {
  const OWN_NAME = '自分の新しい氏名'

  const own: {
    apply?: (profile: ApplyOwnProfileInput) => void
    seen: ((profile: ApplyOwnProfileInput) => void)[]
    admin?: boolean
  } = { seen: [] }

  /** useApplyOwnProfile が返す関数と、ログイン状態の印を描画の確定ごとに記録する */
  function OwnProbe() {
    const apply = useApplyOwnProfile()
    const login = useLoginState()
    useLayoutEffect(() => {
      own.apply = apply
      own.seen.push(apply)
      own.admin = login.admin
    })
    return null
  }

  async function renderOwn(provider: LoginStateProvider) {
    own.apply = undefined
    own.seen = []
    own.admin = undefined
    renderWithProviders(
      <>
        <Probe />
        <OwnProbe />
      </>,
      { provider },
    )
    await screen.findByTestId('probe')
    await waitForEffects()
  }

  function admin(preferences: LoginState['preferences']): LoginState {
    return { loggedIn: true, admin: true, displayName: NAME, preferences }
  }

  it('applies the name and the language at once: text, lang and request language', async () => {
    const login = controllableProvider(admin({ language: 'ja', theme: 'dark', fontSize: 'lg' }))
    await renderOwn(login.provider)
    expect(screen.getByTestId('probe')).toHaveTextContent(`ja/dark/lg/dark/${NAME}`)

    act(() => own.apply?.({ displayName: OWN_NAME, language: 'en' }))

    await waitFor(() =>
      expect(screen.getByTestId('probe')).toHaveTextContent(`en/dark/lg/dark/${OWN_NAME}`),
    )
    expect(screen.getByTestId('probe-text')).toHaveTextContent('Home')
    await waitFor(() => expect(document.documentElement.lang).toBe('en'))
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await apiFetch('/api/items')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Headers(init.headers).get('Accept-Language')).toBe('en')
  })

  it('keeps the theme and the font size, also a stored value changed in another tab', async () => {
    const login = controllableProvider(admin({ language: 'ja', theme: 'light', fontSize: 'sm' }))
    await renderOwn(login.provider)
    // ほかのタブで保存の値のテーマと文字の大きさが変わった。
    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"language":"ja","theme":"dark","fontSize":"lg"}')

    act(() => own.apply?.({ displayName: OWN_NAME, language: 'en' }))

    await waitFor(() =>
      expect(screen.getByTestId('probe')).toHaveTextContent(`en/light/sm/light/${OWN_NAME}`),
    )
    expect(htmlTheme()).toBeNull()
    expect(document.documentElement.getAttribute('data-font-size')).toBe('sm')
    expect(storedJson()).toEqual({ language: 'en', theme: 'dark', fontSize: 'lg' })
  })

  it('does not save a theme tried as a preview and keeps the preview until it is cleared', async () => {
    const login = controllableProvider(admin({ language: 'ja', theme: 'light', fontSize: 'md' }))
    await renderOwn(login.provider)
    act(() => captured.value?.setPreview('dark'))
    expect(screen.getByTestId('probe')).toHaveTextContent(`ja/dark/md/dark/${NAME}`)

    act(() => own.apply?.({ displayName: OWN_NAME, language: 'en' }))
    await waitFor(() =>
      expect(screen.getByTestId('probe')).toHaveTextContent(`en/dark/md/dark/${OWN_NAME}`),
    )

    act(() => captured.value?.clearPreview())
    await waitFor(() =>
      expect(screen.getByTestId('probe')).toHaveTextContent(`en/light/md/light/${OWN_NAME}`),
    )
  })

  it('does nothing while logged out', async () => {
    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"language":"ja","theme":"light","fontSize":"md"}')
    const login = controllableProvider({ loggedIn: false, admin: false })
    await renderOwn(login.provider)

    act(() => own.apply?.({ displayName: OWN_NAME, language: 'en' }))

    expect(screen.getByTestId('probe')).toHaveTextContent('ja/light/md/light/-')
    expect(document.documentElement.lang).toBe('ja')
    expect(storedJson()).toEqual({ language: 'ja', theme: 'light', fontSize: 'md' })
  })

  it('never changes the admin flag of the login state', async () => {
    const login = controllableProvider(admin({ language: 'ja', theme: 'light', fontSize: 'md' }))
    await renderOwn(login.provider)
    expect(own.admin).toBe(true)

    act(() => own.apply?.({ displayName: OWN_NAME, language: 'en' }))

    await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent(OWN_NAME))
    expect(own.admin).toBe(true)
  })

  it('binds to the newest login state when the function taken earlier is called after a refresh', async () => {
    const login = controllableProvider(admin({ language: 'ja', theme: 'light', fontSize: 'md' }))
    await renderOwn(login.provider)
    const takenAtRender = own.apply

    // 保存の応答を待つ間にトークンの更新が入り、新しいログイン状態が知らされた。
    await login.set(admin({ language: 'ja', theme: 'dark', fontSize: 'lg' }))
    await waitFor(() =>
      expect(screen.getByTestId('probe')).toHaveTextContent(`ja/dark/lg/dark/${NAME}`),
    )

    act(() => takenAtRender?.({ displayName: OWN_NAME, language: 'en' }))

    await waitFor(() =>
      expect(screen.getByTestId('probe')).toHaveTextContent(`en/dark/lg/dark/${OWN_NAME}`),
    )
  })

  it('returns the same function while the login state, the theme and the language change', async () => {
    const login = controllableProvider(admin({ language: 'ja', theme: 'light', fontSize: 'md' }))
    await renderOwn(login.provider)

    await login.set(admin({ language: 'en', theme: 'dark', fontSize: 'lg' }))
    act(() => captured.value?.setPreview('light'))
    act(() => captured.value?.setLanguage('ja'))
    act(() => own.apply?.({ displayName: OWN_NAME, language: 'en' }))
    await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent(OWN_NAME))

    expect(own.seen.length).toBeGreaterThan(1)
    expect(new Set(own.seen).size).toBe(1)
  })

  it('keeps the name out of every browser storage key', async () => {
    const login = controllableProvider(admin({ language: 'ja', theme: 'light', fontSize: 'md' }))
    await renderOwn(login.provider)

    act(() => own.apply?.({ displayName: OWN_NAME, language: 'en' }))
    await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent(OWN_NAME))

    expect(Object.keys(storedJson()).sort()).toEqual(['fontSize', 'language', 'theme'])
    expect(allStoredValues()).not.toContain(OWN_NAME)
  })
})
