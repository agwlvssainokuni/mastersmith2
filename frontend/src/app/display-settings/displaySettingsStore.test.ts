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
// 表示の設定の置き場のテスト（部品 3.2・3.3、D2・D6・D8、W7・W8・W12）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, resetApiClient } from '../../shared/api-client/apiClient'
import { stubBrowserLanguages } from '../testing/renderWithProviders'
import { DISPLAY_SETTINGS_KEY } from './browserStorage'
import {
  applyUserPreferencesFor,
  clearPreview,
  currentScreenLanguage,
  discardStaleBindings,
  getDisplaySettingsSnapshot,
  installLanguageResolver,
  rememberUserSettings,
  resetDisplaySettings,
  saveBrowserDisplaySettings,
  saveBrowserLanguage,
  setAppliedLanguage,
  setLanguagePreviewFor,
  setPreviewFor,
  subscribeDisplaySettings,
} from './displaySettingsStore'

const guest = { loggedIn: false }
const userA = { loggedIn: true }

function stored(): unknown {
  return JSON.parse(localStorage.getItem(DISPLAY_SETTINGS_KEY) ?? 'null')
}

beforeEach(() => {
  stubBrowserLanguages(['ja-JP'])
  localStorage.clear()
  resetDisplaySettings()
  resetApiClient()
})

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
  resetDisplaySettings()
  resetApiClient()
})

describe('displaySettingsStore', () => {
  it('reads the browser storage lazily and again after a reset', () => {
    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"theme":"dark"}')
    expect(getDisplaySettingsSnapshot().stored).toEqual({ theme: 'dark' })

    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"theme":"light"}')
    expect(getDisplaySettingsSnapshot().stored).toEqual({ theme: 'dark' })
    resetDisplaySettings()
    expect(getDisplaySettingsSnapshot().stored).toEqual({ theme: 'light' })
  })

  it('saves the three settings at registration and drops the preview', () => {
    setPreviewFor(guest, 'dark', 'lg')
    const listener = vi.fn()
    const unsubscribe = subscribeDisplaySettings(listener)

    saveBrowserDisplaySettings({ language: 'en', theme: 'system', fontSize: 'sm' })

    expect(stored()).toEqual({ language: 'en', theme: 'system', fontSize: 'sm' })
    expect(getDisplaySettingsSnapshot()).toMatchObject({
      stored: { language: 'en', theme: 'system', fontSize: 'sm' },
      preview: null,
    })
    expect(listener).toHaveBeenCalled()
    unsubscribe()
  })

  it('saves only the language and drops only the language preview', () => {
    saveBrowserDisplaySettings({ language: 'ja', theme: 'dark', fontSize: 'lg' })
    setPreviewFor(guest, 'light')
    setLanguagePreviewFor(guest, 'en')

    saveBrowserLanguage('en')

    expect(stored()).toEqual({ language: 'en', theme: 'dark', fontSize: 'lg' })
    expect(getDisplaySettingsSnapshot().preview).toEqual({
      binding: guest,
      value: { theme: 'light' },
    })
    setLanguagePreviewFor(guest, 'ja')
    saveBrowserLanguage('ja')
    setLanguagePreviewFor(guest, 'en')
    clearPreview()
    saveBrowserLanguage('en')
    expect(getDisplaySettingsSnapshot().preview).toBeNull()
    saveBrowserLanguage('fr' as never)
    expect(stored()).toEqual({ language: 'en', theme: 'dark', fontSize: 'lg' })
  })

  it('merges preview axes for the same login state and starts over for another one', () => {
    setPreviewFor(guest, 'dark', 'lg')
    setPreviewFor(guest, undefined, 'sm')
    expect(getDisplaySettingsSnapshot().preview?.value).toEqual({ theme: 'dark', fontSize: 'sm' })

    setPreviewFor(userA, 'light')
    expect(getDisplaySettingsSnapshot().preview).toEqual({
      binding: userA,
      value: { theme: 'light' },
    })
    setLanguagePreviewFor(userA, 'fr' as never)
    expect(getDisplaySettingsSnapshot().preview?.value).toEqual({ theme: 'light' })

    discardStaleBindings(guest)
    expect(getDisplaySettingsSnapshot().preview).toBeNull()
  })

  it('binds saved preferences to the login state, saves them and drops stale ones', () => {
    applyUserPreferencesFor(userA, {
      displayName: '山田 花子',
      language: 'en',
      theme: 'dark',
      fontSize: 'xl' as never,
    })

    expect(stored()).toEqual({ language: 'en', theme: 'dark', fontSize: 'md' })
    expect(getDisplaySettingsSnapshot().savedUser).toEqual({
      binding: userA,
      value: { displayName: '山田 花子', language: 'en', theme: 'dark', fontSize: 'md' },
    })
    discardStaleBindings(userA)
    expect(getDisplaySettingsSnapshot().savedUser).not.toBeNull()
    discardStaleBindings({ loggedIn: true })
    expect(getDisplaySettingsSnapshot().savedUser).toBeNull()

    rememberUserSettings({ theme: 'light' })
    expect(stored()).toEqual({ language: 'ja', theme: 'light', fontSize: 'md' })
  })

  it('gives the request language from the applied language, or the stored and browser language', async () => {
    expect(currentScreenLanguage()).toBe('ja')
    saveBrowserLanguage('en')
    expect(currentScreenLanguage()).toBe('en')
    setAppliedLanguage('ja')
    expect(currentScreenLanguage()).toBe('ja')

    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    installLanguageResolver()
    await apiFetch('/api/appearance')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Headers(init.headers).get('Accept-Language')).toBe('ja')
  })
})
